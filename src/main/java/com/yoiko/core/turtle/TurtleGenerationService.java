package com.yoiko.core.turtle;

import com.yoiko.core.data.ServerYoikoAuditSavedData;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import com.yoiko.core.network.TurtleHatchRevealPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TurtleGenerationService {
    public record HatchRequest(TurtleTicketType ticket, ActiveSkill pickupActive, long seed) { }

    private TurtleGenerationService() { }

    public static TurtleData hatch(ServerPlayer player, HatchRequest request) {
        return hatch(player,request,true);
    }

    public static TurtleData hatchFromItem(ServerPlayer player,HatchRequest request){return hatch(player,request,false);}

    private static TurtleData hatch(ServerPlayer player,HatchRequest request,boolean consumeVirtualTicket) {
        TurtleHatchPendingService.ensureReady(player);
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(player.server);
        TurtlePlayerProgress progress = saved.getOrCreatePlayer(player.getUUID());
        if (progress.turtleIds().size() >= 40) throw TurtleLocalizedException.of("yoiko_core.turtle.error.owned_limit",40);
        if (consumeVirtualTicket && progress.ticketCount(request.ticket()) <= 0) throw TurtleLocalizedException.of("yoiko_core.turtle.error.no_hatch_ticket");
        SplittableRandom random = new SplittableRandom(request.seed());
        TurtleRarity rarity = rollRarity(random, progress, request.ticket());
        TurtleArchetype fixed=request.ticket().fixedArchetype();
        TurtleArchetype archetype = fixed!=null?fixed:TurtleArchetype.values()[random.nextInt(TurtleArchetype.values().length)];
        boolean pickupTicket = request.ticket() == TurtleTicketType.PICKUP && request.pickupActive() != null;
        boolean pickupHit = pickupTicket && (progress.pickupActivePity() >= 5 || random.nextInt(100) < 50);
        ActiveSkill active = pickupHit ? request.pickupActive()
                : ActiveSkill.values()[random.nextInt(ActiveSkill.values().length)];
        TurtleStats stats = generateStats(random, rarity, archetype);
        List<String> passives = PassiveGenerationService.generate(random, rarity, active, archetype);
        EnumMap<TurtleSurface, TurtleAptitude> surfaces = generateAptitudes(random, TurtleSurface.class);
        EnumMap<TurtleStrategy, TurtleAptitude> strategies = generateAptitudes(random, TurtleStrategy.class);
        TurtleAptitude[] distances = generateDistanceAptitudes(random);
        Set<String> ownedNames = new HashSet<>();
        saved.ownedBy(player.getUUID()).forEach(value -> ownedNames.add(value.name()));
        // Cosmetic rolls use an independent deterministic stream, so adding colors never changes stats or skills.
        TurtleBodyAppearanceCatalog.Appearance bodyAppearance = TurtleBodyAppearanceCatalog.roll(
                new SplittableRandom(request.seed() ^ 0x54_55_52_54_4C_45_42_4FL));
        TurtleData turtle = new TurtleData(UUID.randomUUID(), player.getUUID(), TurtleNameCatalog.pick(random, ownedNames),
                rarity, archetype, stats.copy(), stats, active, passives, surfaces, strategies,
                distances[0], distances[1], distances[2], bodyAppearance.id(), System.currentTimeMillis());
        if (request.ticket().fixedStrategy() != null) turtle.setStrategy(request.ticket().fixedStrategy());

        // The transaction becomes visible only after every generated field passed validation.
        if (consumeVirtualTicket&&!progress.consumeTicket(request.ticket())) throw TurtleLocalizedException.of("yoiko_core.turtle.error.ticket_consume_failed");
        boolean affectsRarity = request.ticket() == TurtleTicketType.STANDARD || request.ticket() == TurtleTicketType.PICKUP;
        progress.updatePity(rarity, pickupHit, affectsRarity, pickupTicket);
        saved.addTurtle(turtle);
        saved.markChanged();
        TurtleAchievementManager.sync(player, progress);
        ServerYoikoAuditSavedData.get(player.server).addOperational("TURTLE", "TURTLE_HATCHED",
                player.getUUID(), player.getGameProfile().getName(), "id=" + turtle.id() + ",rarity=" + rarity
                        + ",body=" + bodyAppearance.id() + ",rareBody=" + bodyAppearance.rare()
                        + ",ticket=" + request.ticket() + ",seed=" + request.seed());
        double yaw=Math.toRadians(player.getYRot());double x=player.getX()-Math.sin(yaw)*2.0,z=player.getZ()+Math.cos(yaw)*2.0;
        boolean special=passives.stream().map(PassiveSkillCatalog::get).anyMatch(PassiveSkill::special);
        TurtleHatchPendingService.awaitConfirmation(player, turtle.id());
        PacketDistributor.sendToPlayer(player,new TurtleHatchRevealPayload(turtle.id(),x,player.getY(),z,player.getYRot(),rarity,turtle.name(),active.id(),special,turtle.stats().total(),turtle.raceClass().name(),TurtleGuidance.recommendedTraining(turtle),bodyAppearance.id(),request.ticket().name(),request.seed()));
        return turtle;
    }

    private static TurtleRarity rollRarity(SplittableRandom random, TurtlePlayerProgress progress, TurtleTicketType ticket) {
        if (ticket.rareGuaranteed()) return TurtleRarity.RARE;
        if (ticket == TurtleTicketType.EPIC_GUARANTEED) return random.nextInt(6) == 0 ? TurtleRarity.LEGENDARY : TurtleRarity.EPIC;
        if (progress.legendaryPity() >= 79) return TurtleRarity.LEGENDARY;
        if (progress.epicPity() >= 29) return weightedAtLeast(random, TurtleRarity.EPIC);
        if (progress.rarePity() >= 9) return weightedAtLeast(random, TurtleRarity.RARE);
        int roll = random.nextInt(100);
        int cursor = 0;
        for (TurtleRarity rarity : TurtleRarity.values()) {
            cursor += rarity.weight();
            if (roll < cursor) return rarity;
        }
        return TurtleRarity.COMMON;
    }

    private static TurtleRarity weightedAtLeast(SplittableRandom random, TurtleRarity minimum) {
        List<TurtleRarity> pool = Arrays.stream(TurtleRarity.values()).filter(value -> value.ordinal() >= minimum.ordinal()).toList();
        int total = pool.stream().mapToInt(TurtleRarity::weight).sum();
        int roll = random.nextInt(total);
        for (TurtleRarity value : pool) {
            roll -= value.weight();
            if (roll < 0) return value;
        }
        return pool.get(pool.size() - 1);
    }

    static TurtleStats generateStats(SplittableRandom random, TurtleRarity rarity, TurtleArchetype archetype) {
        double spread = switch (rarity) {
            case COMMON -> .16;
            case UNCOMMON -> .14;
            case RARE -> .12;
            case EPIC -> .10;
            case LEGENDARY -> .08;
        };
        for (int attempt = 0; attempt < 20; attempt++) {
            double[] weights = new double[5];
            double weightSum = 0;
            for (TurtleStat stat : TurtleStat.values()) {
                double triangular = (random.nextDouble() + random.nextDouble() - 1.0) * spread;
                weights[stat.ordinal()] = archetype.ratio(stat) * (1.0 + triangular);
                weightSum += weights[stat.ordinal()];
            }
            int[] values = new int[5];
            int used = 0;
            for (int i = 0; i < values.length; i++) {
                values[i] = Math.min(rarity.hatchStatCap(), (int) Math.floor(rarity.hatchBudget() * weights[i] / weightSum));
                used += values[i];
            }
            distribute(values, rarity.hatchBudget() - used, rarity.hatchStatCap());
            int min = Arrays.stream(values).min().orElse(0);
            int max = Arrays.stream(values).max().orElse(0);
            if (max - min <= 38 && Arrays.stream(values).sum() == rarity.hatchBudget()) {
                return new TurtleStats(values[0], values[1], values[2], values[3], values[4]);
            }
        }
        int base = rarity.hatchBudget() / 5;
        int remainder = rarity.hatchBudget() - base * 5;
        int[] fallback = new int[]{base, base, base, base, base};
        distribute(fallback, remainder, rarity.hatchStatCap());
        return new TurtleStats(fallback[0], fallback[1], fallback[2], fallback[3], fallback[4]);
    }

    private static void distribute(int[] values, int amount, int cap) {
        int cursor = 0;
        while (amount > 0) {
            int index = cursor++ % values.length;
            if (values[index] < cap) { values[index]++; amount--; }
            if (cursor > 100_000) throw new IllegalStateException("Unable to distribute turtle stat budget");
        }
    }

    private static <E extends Enum<E>> EnumMap<E, TurtleAptitude> generateAptitudes(SplittableRandom random, Class<E> type) {
        EnumMap<E, TurtleAptitude> result = new EnumMap<>(type);
        boolean hasA = false;
        for (E value : type.getEnumConstants()) {
            TurtleAptitude aptitude = rollAptitude(random);
            result.put(value, aptitude);
            if (aptitude.ordinal() <= TurtleAptitude.A.ordinal()) hasA = true;
        }
        if (!hasA) result.put(type.getEnumConstants()[random.nextInt(type.getEnumConstants().length)], TurtleAptitude.A);
        return result;
    }

    private static TurtleAptitude[] generateDistanceAptitudes(SplittableRandom random) {
        TurtleAptitude[] values = new TurtleAptitude[]{rollAptitude(random), rollAptitude(random), rollAptitude(random)};
        if (Arrays.stream(values).noneMatch(value -> value.ordinal() <= TurtleAptitude.A.ordinal())) values[random.nextInt(3)] = TurtleAptitude.A;
        return values;
    }

    private static TurtleAptitude rollAptitude(SplittableRandom random) {
        int roll = random.nextInt(100);
        if (roll < 7) return TurtleAptitude.S;
        if (roll < 27) return TurtleAptitude.A;
        if (roll < 72) return TurtleAptitude.B;
        if (roll < 92) return TurtleAptitude.C;
        return TurtleAptitude.D;
    }
}
