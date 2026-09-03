package com.yoiko.core.turtle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.HashSet;
import java.util.Set;

public final class PassiveGenerationService {
    private static final int LOADOUT_SIZE = 4;
    private static final List<List<PassiveSkill>> STRUCTURAL_LOADOUTS = buildStructuralLoadouts();

    private PassiveGenerationService() { }

    public static List<String> generate(SplittableRandom random, TurtleRarity rarity, ActiveSkill active,
                                        TurtleArchetype archetype) {
        int requiredSpecial = random.nextInt(100) < rarity.specialPassiveChance() ? 1 : 0;
        List<PassiveSkill> chosen = new ArrayList<>(LOADOUT_SIZE);
        for (int slot = 0; slot < LOADOUT_SIZE; slot++) {
            List<WeightedCandidate> candidates = weightedCompletableCandidates(chosen, requiredSpecial,
                    rarity.minimumSynergies(), active, archetype);
            if (candidates.isEmpty()) throw new IllegalStateException("No completable passive candidate for slot " + slot);
            chosen.add(weightedPick(candidates, random));
        }
        if (!PassiveLoadoutValidator.validComplete(chosen, requiredSpecial, rarity.minimumSynergies(), active, archetype)) {
            throw new IllegalStateException("Generated passive loadout violates invariants");
        }
        return chosen.stream().map(PassiveSkill::id).toList();
    }

    public static List<String> legalRerollCandidates(List<String> current, int slot, ActiveSkill active,
                                                      TurtleArchetype archetype, TurtleRarity rarity) {
        List<String> result = new ArrayList<>();
        for (PassiveSkill candidate : PassiveSkillCatalog.all()) {
            if (candidate.special() && rarity.ordinal() < TurtleRarity.EPIC.ordinal()) continue;
            List<PassiveSkill> loadout = new ArrayList<>();
            for (int index = 0; index < current.size(); index++) {
                loadout.add(index == slot ? candidate : PassiveSkillCatalog.get(current.get(index)));
            }
            int resultingSpecial = (int) loadout.stream().filter(PassiveSkill::special).count();
            if (resultingSpecial > 1) continue;
            // Validate the replacement after the selected passive has left the loadout. This lets a
            // removed special passive become an ordinary passive, while still forbidding two specials.
            if (PassiveLoadoutValidator.validComplete(loadout, resultingSpecial, rarity.minimumSynergies(), active, archetype)) {
                result.add(candidate.id());
            }
        }
        return result;
    }

    private static List<WeightedCandidate> weightedCompletableCandidates(List<PassiveSkill> chosen,
            int requiredSpecial, int minimumSynergies, ActiveSkill active, TurtleArchetype archetype) {
        List<PassiveSkill> catalog = PassiveSkillCatalog.all();
        double[] weights = new double[catalog.size()];
        Set<String> chosenIds = chosen.stream().map(PassiveSkill::id).collect(java.util.stream.Collectors.toSet());
        int remainingAfterCandidate = LOADOUT_SIZE - chosen.size() - 1;
        int permutationCount = remainingAfterCandidate <= 1 ? 1 : remainingAfterCandidate == 2 ? 2 : 6;
        for (List<PassiveSkill> loadout : STRUCTURAL_LOADOUTS) {
            if (!loadout.stream().map(PassiveSkill::id).collect(java.util.stream.Collectors.toSet()).containsAll(chosenIds)) continue;
            if (loadout.stream().filter(PassiveSkill::special).count() != requiredSpecial) continue;
            if (loadout.stream().filter(value -> value.synergizes(active, archetype)).count() < minimumSynergies) continue;
            double product = 1.0;
            for (PassiveSkill skill : loadout) if (!chosenIds.contains(skill.id())) product *= baseWeight(skill, active, archetype);
            double contribution = product * permutationCount;
            for (PassiveSkill skill : loadout) {
                if (!chosenIds.contains(skill.id())) weights[catalog.indexOf(skill)] += contribution;
            }
        }
        List<WeightedCandidate> result = new ArrayList<>();
        for (int i=0; i<catalog.size(); i++) if (weights[i] > 0 && Double.isFinite(weights[i])) result.add(new WeightedCandidate(catalog.get(i), weights[i]));
        result.sort(Comparator.comparing(value -> value.skill().id()));
        return result;
    }

    private static int baseWeight(PassiveSkill candidate, ActiveSkill active, TurtleArchetype archetype) {
        return candidate.synergizes(active, archetype) ? 135 : 100;
    }

    private static List<List<PassiveSkill>> buildStructuralLoadouts() {
        List<PassiveSkill> all = PassiveSkillCatalog.all();
        List<List<PassiveSkill>> result = new ArrayList<>();
        for (int a=0; a<all.size()-3; a++) for (int b=a+1; b<all.size()-2; b++)
            for (int c=b+1; c<all.size()-1; c++) for (int d=c+1; d<all.size(); d++) {
                List<PassiveSkill> loadout=List.of(all.get(a),all.get(b),all.get(c),all.get(d));
                int specials=(int)loadout.stream().filter(PassiveSkill::special).count();
                if (specials<=1 && PassiveLoadoutValidator.validComplete(loadout,specials,0,
                        ActiveSkill.SHELLBREAK_START,TurtleArchetype.BALANCED)) result.add(loadout);
            }
        return List.copyOf(result);
    }

    private static PassiveSkill weightedPick(List<WeightedCandidate> candidates, SplittableRandom random) {
        double total = candidates.stream().mapToDouble(WeightedCandidate::weight).sum();
        double roll = random.nextDouble(total);
        for (WeightedCandidate candidate : candidates) {
            roll -= candidate.weight();
            if (roll <= 0) return candidate.skill();
        }
        return candidates.get(candidates.size() - 1).skill();
    }

    private record WeightedCandidate(PassiveSkill skill, double weight) { }
}
