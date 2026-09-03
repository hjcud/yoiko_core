package com.yoiko.core.relic;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.entity.SpawnBucketChosenEvent;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.FriendshipUpdatedEvent;
import com.cobblemon.mod.common.api.events.pokemon.ShinyChanceCalculationEvent;
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.spawning.SpawnBucket;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.RelicEffectHudPayload.ValueKind;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.event.DailyServerEventManager;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;

public final class CobblemonRelicBridge {
    private static boolean registered;

    private CobblemonRelicBridge() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.LOW, CobblemonRelicBridge::onExperienceGained);
        CobblemonEvents.POKEMON_CATCH_RATE.subscribe(Priority.LOW, CobblemonRelicBridge::onPokemonCatchRate);
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(
                Priority.LOW, CobblemonRelicBridge::onPokeBallCaptureCalculated);
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(Priority.LOW, CobblemonRelicBridge::onFriendshipUpdated);
        CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe(Priority.LOW, CobblemonRelicBridge::onShinyChanceCalculation);
        CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(Priority.LOW, CobblemonRelicBridge::onSpawnBucketChosen);
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.HIGHEST, CobblemonRelicBridge::onBattleFainted);
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.LOW, CobblemonRelicBridge::onBattleVictory);
    }

    public static boolean isRegistered() {
        return registered;
    }

    private static void onExperienceGained(ExperienceGainedEvent.Pre event) {
        if (event.getExperience() <= 0 || isYoikoSidemodSource(event.getSource())) {
            return;
        }
        ServerPlayer player = event.getPokemon().getOwnerPlayer();
        if (player == null) {
            return;
        }
        double bonusPercent = Math.max(0.0D, RelicManager.effectBonus(player, "pokemon_exp_multiplier_bonus"));
        if (bonusPercent <= 0.0D) {
            return;
        }
        int boostedExperience = percentBoost(event.getExperience(), bonusPercent);
        if (boostedExperience > event.getExperience()) {
            debug(player, "pokemon_exp_bonus: {} -> {} (+{}%) pokemon={}",
                    event.getExperience(), boostedExperience, bonusPercent, event.getPokemon().getSpecies().getResourceIdentifier());
            event.setExperience(boostedExperience);
        }
    }

    private static void onPokemonCatchRate(PokemonCatchRateEvent event) {
        if (!(event.getThrower() instanceof ServerPlayer player)) {
            return;
        }
        double bonusPercent = Math.max(0.0D, RelicManager.effectBonus(player, "pokemon_catch_rate_bonus"));
        if (bonusPercent <= 0.0D || event.getCatchRate() <= 0.0F) {
            return;
        }
        float baseRate = event.getCatchRate();
        float boostedRate = (float) (baseRate * (1.0D + bonusPercent / 100.0D));
        debug(player, "catch_rate_bonus: {} -> {} (+{}%)", baseRate, boostedRate, bonusPercent);
        event.setCatchRate(boostedRate);
    }

    private static void onPokeBallCaptureCalculated(PokeBallCaptureCalculatedEvent event) {
        if (!(event.getThrower() instanceof ServerPlayer player)
                || event.getCaptureResult().isSuccessfulCapture()) {
            return;
        }
        double returnChance = Math.max(
                0.0D, Math.min(100.0D, RelicManager.effectBonus(player, "failed_pokeball_return_chance")));
        if (returnChance <= 0.0D || player.getRandom().nextDouble() * 100.0D >= returnChance) {
            return;
        }

        var pokeBallEntity = event.getPokeBallEntity();
        var pokeBall = pokeBallEntity.getPokeBall();
        pokeBallEntity.getCaptureFuture().whenComplete((captured, error) -> {
            if (error != null || Boolean.TRUE.equals(captured) || player.getServer() == null) {
                return;
            }
            player.getServer().execute(() -> {
                if (!player.isAlive() || player.hasDisconnected()) {
                    return;
                }
                ItemStack returnedBall = pokeBall.stack(1);
                if (!player.getInventory().add(returnedBall)) {
                    player.drop(returnedBall, false);
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS, 0.8F, 1.25F);
                RelicHudNotifier.proc(player, "failed_pokeball_return_chance", ValueKind.NONE, 0.0D);
                debug(player, "failed_pokeball_return: returned {}", pokeBall.getName());
            });
        });
    }

    /**
     * Cobblemon 1.7.3 exposes faint only after Showdown has accepted lethal damage. Restoring
     * the backing Pokemon here guarantees that a wild target is not persisted at zero HP.
     * Keeping it active inside the same Showdown turn requires an upstream pre-damage hook;
     * this compatibility guard intentionally avoids injecting into Cobblemon internals.
     */
    private static void onBattleFainted(BattleFaintedEvent event) {
        if (event.getKilled().getActor().getType() != ActorType.WILD) {
            return;
        }
        ServerPlayer protector = null;
        for (ServerPlayer player : event.getBattle().getPlayers()) {
            if (RelicManager.effectBonus(player, "mercy_hp_floor") >= 100.0D) {
                protector = player;
                break;
            }
        }
        if (protector == null) {
            return;
        }

        event.getKilled().getEffectedPokemon().setCurrentHealth(1);
        event.getKilled().getOriginalPokemon().setCurrentHealth(1);
        event.getKilled().getPostBattlePokemonOperations().add(pokemon -> {
            pokemon.getEffectedPokemon().setCurrentHealth(1);
            pokemon.getOriginalPokemon().setCurrentHealth(1);
            return kotlin.Unit.INSTANCE;
        });
        event.getKilled().sendUpdate();
        RelicHudNotifier.proc(protector, "mercy_hp_floor", ValueKind.NONE, 0.0D);
    }

    private static void onBattleVictory(BattleVictoryEvent event) {
        if (event.getWasWildCapture()) {
            return;
        }

        boolean entirelyWildVictory = !event.getLosers().isEmpty()
                && event.getLosers().stream().allMatch(actor -> actor.getType() == ActorType.WILD);

        for (ServerPlayer player : event.getBattle().getPlayers()) {
            cureVictoryStatus(event, player);
            restoreVictoryMovePp(event, player);
            if (entirelyWildVictory) {
                healAfterWildVictory(event, player);
            }
        }
    }

    private static void healAfterWildVictory(BattleVictoryEvent event, ServerPlayer player) {
        double healPercent = Math.min(12.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "battle_victory_heal_percent")));
        if (healPercent <= 0.0D) {
            return;
        }

        BattlePokemon weakest = null;
        double weakestRatio = Double.MAX_VALUE;
        for (var winner : event.getWinners()) {
            if (!winner.isForPlayer(player)) {
                continue;
            }
            for (BattlePokemon candidate : winner.getPokemonList()) {
                int health = candidate.getEffectedPokemon().getCurrentHealth();
                int maximum = candidate.getEffectedPokemon().getMaxHealth();
                if (health <= 0 || health >= maximum || maximum <= 0) {
                    continue;
                }
                double ratio = health / (double) maximum;
                if (ratio < weakestRatio) {
                    weakest = candidate;
                    weakestRatio = ratio;
                }
            }
        }
        if (weakest == null) {
            return;
        }

        int currentHealth = weakest.getEffectedPokemon().getCurrentHealth();
        int maximumHealth = weakest.getEffectedPokemon().getMaxHealth();
        int healAmount = Math.max(1, (int) Math.ceil(maximumHealth * healPercent / 100.0D));
        int healedHealth = Math.min(maximumHealth, currentHealth + healAmount);
        weakest.getEffectedPokemon().setCurrentHealth(healedHealth);
        weakest.getOriginalPokemon().setCurrentHealth(healedHealth);
        weakest.getPostBattlePokemonOperations().add(pokemon -> {
            pokemon.getEffectedPokemon().setCurrentHealth(healedHealth);
            pokemon.getOriginalPokemon().setCurrentHealth(healedHealth);
            return kotlin.Unit.INSTANCE;
        });
        weakest.sendUpdate();
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.45F, 1.35F);
        RelicHudNotifier.proc(player, "battle_victory_heal_percent", ValueKind.HEAL,
                healedHealth - currentHealth);
        debug(player, "victory_ribbon: {} -> {} (+{}%, pokemon={})",
                currentHealth, healedHealth, healPercent,
                weakest.getEffectedPokemon().getSpecies().getResourceIdentifier());
    }

    private static void cureVictoryStatus(BattleVictoryEvent event, ServerPlayer player) {
        double cureChance = Math.min(80.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "battle_victory_status_cure_chance")));
        if (cureChance <= 0.0D || player.getRandom().nextDouble() * 100.0D >= cureChance) {
            return;
        }

        BattlePokemon target = null;
        double lowestHealthRatio = Double.MAX_VALUE;
        for (var winner : event.getWinners()) {
            if (!winner.isForPlayer(player)) {
                continue;
            }
            for (BattlePokemon candidate : winner.getPokemonList()) {
                int health = candidate.getEffectedPokemon().getCurrentHealth();
                int maximum = candidate.getEffectedPokemon().getMaxHealth();
                if (health <= 0 || maximum <= 0 || candidate.getEffectedPokemon().getStatus() == null) {
                    continue;
                }
                double ratio = health / (double) maximum;
                if (ratio < lowestHealthRatio) {
                    target = candidate;
                    lowestHealthRatio = ratio;
                }
            }
        }
        if (target == null) {
            return;
        }

        BattlePokemon cured = target;
        var curedStatus = cured.getEffectedPokemon().getStatus().getStatus().getShowdownName();
        cured.getEffectedPokemon().setStatus(null);
        cured.getOriginalPokemon().setStatus(null);
        cured.getPostBattlePokemonOperations().add(pokemon -> {
            pokemon.getEffectedPokemon().setStatus(null);
            pokemon.getOriginalPokemon().setStatus(null);
            return kotlin.Unit.INSTANCE;
        });
        cured.sendUpdate();
        player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.PLAYERS, 0.5F, 1.35F);
        RelicHudNotifier.proc(player, "battle_victory_status_cure_chance", ValueKind.NONE, 0.0D);
        debug(player, "purification_ribbon: cured {} ({}%, pokemon={})",
                curedStatus, cureChance, cured.getEffectedPokemon().getSpecies().getResourceIdentifier());
    }

    private static void restoreVictoryMovePp(BattleVictoryEvent event, ServerPlayer player) {
        double restoreChance = Math.min(50.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "battle_victory_move_pp_restore_chance")));
        if (restoreChance <= 0.0D) {
            return;
        }

        MoveRecoveryTarget best = null;
        for (var winner : event.getWinners()) {
            if (!winner.isForPlayer(player)) {
                continue;
            }
            for (BattlePokemon candidate : winner.getPokemonList()) {
                var moves = candidate.getEffectedPokemon().getMoveSet().getMovesWithNulls();
                for (int index = 0; index < moves.size(); index++) {
                    Move move = moves.get(index);
                    if (move == null) {
                        continue;
                    }
                    int missingPp = Math.max(0, move.getMaxPp() - move.getCurrentPp());
                    if (missingPp > 0 && (best == null || missingPp > best.missingPp())) {
                        best = new MoveRecoveryTarget(candidate, index, missingPp, move.getName());
                    }
                }
            }
        }
        if (best == null || player.getRandom().nextDouble() * 100.0D >= restoreChance) {
            return;
        }

        MoveRecoveryTarget restored = best;
        restoreMoveAt(restored.pokemon().getEffectedPokemon(), restored.moveIndex());
        restoreMoveAt(restored.pokemon().getOriginalPokemon(), restored.moveIndex());
        restored.pokemon().getPostBattlePokemonOperations().add(pokemon -> {
            restoreMoveAt(pokemon.getEffectedPokemon(), restored.moveIndex());
            restoreMoveAt(pokemon.getOriginalPokemon(), restored.moveIndex());
            return kotlin.Unit.INSTANCE;
        });
        restored.pokemon().sendUpdate();
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.5F, 1.55F);
        RelicHudNotifier.proc(player, "battle_victory_move_pp_restore_chance", ValueKind.PP_RESTORE,
                restored.missingPp());
        debug(player, "restoration_score: restored move {} by {} PP ({}%, pokemon={})",
                restored.moveName(), restored.missingPp(), restoreChance,
                restored.pokemon().getEffectedPokemon().getSpecies().getResourceIdentifier());
    }

    private static void restoreMoveAt(Pokemon pokemon, int moveIndex) {
        if (pokemon == null || moveIndex < 0 || moveIndex >= pokemon.getMoveSet().getMovesWithNulls().size()) {
            return;
        }
        Move move = pokemon.getMoveSet().getMovesWithNulls().get(moveIndex);
        if (move == null || move.getCurrentPp() >= move.getMaxPp()) {
            return;
        }
        move.setCurrentPp(move.getMaxPp());
        move.update();
    }

    private static void onFriendshipUpdated(FriendshipUpdatedEvent event) {
        ServerPlayer player = event.getPokemon().getOwnerPlayer();
        if (player == null) {
            return;
        }
        double bonusPercent = Math.max(0.0D, RelicManager.effectBonus(player, "pokemon_friendship_gain_bonus"));
        if (bonusPercent <= 0.0D) {
            return;
        }

        int oldFriendship = event.getPokemon().getFriendship();
        int baseNewFriendship = event.getNewFriendshipInitial();
        int gain = baseNewFriendship - oldFriendship;
        if (gain <= 0) {
            return;
        }
        int boostedGain = Math.max(gain, (int) Math.round(gain * (1.0D + bonusPercent / 100.0D)));
        int boostedFriendship = Math.max(0, Math.min(255, oldFriendship + boostedGain));
        debug(player, "pokemon_friendship_bonus: old={} baseNew={} gain={} boostedNew={} (+{}%) pokemon={}",
                oldFriendship, baseNewFriendship, gain, boostedFriendship, bonusPercent, event.getPokemon().getSpecies().getResourceIdentifier());
        event.setNewFriendship(boostedFriendship);
    }

    private static void onShinyChanceCalculation(ShinyChanceCalculationEvent event) {
        if (!event.getPokemon().isWild()) {
            return;
        }
        event.addModificationFunction((chance, player, pokemon) -> {
            double eventMultiplier = DailyServerEventManager.shinyMultiplier(
                    player == null ? null : player.server, pokemon);
            double bonusPercent = player == null ? 0.0D
                    : Math.max(0.0D, RelicManager.effectBonus(player, "natural_shiny_chance_bonus"));
            float boostedRate = (float) (chance
                    / Math.max(1.0D, eventMultiplier)
                    / (1.0D + bonusPercent / 100.0D));
            if (player != null && Float.compare(chance, boostedRate) != 0) {
                debug(player, "shiny_luck rate: {} -> {} (event x{}, relic +{}%) pokemon={}",
                        chance, boostedRate, eventMultiplier, bonusPercent,
                        pokemon.getSpecies().getResourceIdentifier());
            }
            return boostedRate;
        });
    }

    private static void onSpawnBucketChosen(SpawnBucketChosenEvent event) {
        Entity causeEntity = event.getSpawnCause().getEntity();
        if (!(causeEntity instanceof ServerPlayer player)) {
            return;
        }
        Map<SpawnBucket, Float> weights = event.getBucketWeights();
        if (weights.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (Map.Entry<SpawnBucket, Float> entry : weights.entrySet()) {
            double bonusPercent = spawnBucketBonus(player, entry.getKey());
            if (bonusPercent <= 0.0D || entry.getValue() <= 0.0F) {
                continue;
            }
            float baseWeight = entry.getValue();
            float boostedWeight = (float) (baseWeight * (1.0D + bonusPercent / 100.0D));
            debug(player, "spawn bucket {}: {} -> {} (+{}%)",
                    entry.getKey().getName(), baseWeight, boostedWeight, bonusPercent);
            entry.setValue(boostedWeight);
            changed = true;
        }
        if (changed) {
            chooseWeightedBucket(player, weights, event);
        }
    }

    private static double spawnBucketBonus(ServerPlayer player, SpawnBucket bucket) {
        String name = bucket.getName().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (name) {
            case "common" -> RelicManager.effectBonus(player, "pokemon_common_spawn_weight_bonus");
            case "rare" -> RelicManager.effectBonus(player, "pokemon_rare_spawn_weight_bonus");
            case "ultra_rare", "ultrarare" -> RelicManager.effectBonus(player, "pokemon_ultra_rare_spawn_weight_bonus");
            default -> 0.0D;
        };
    }

    private static void chooseWeightedBucket(ServerPlayer player, Map<SpawnBucket, Float> weights, SpawnBucketChosenEvent event) {
        float total = 0.0F;
        for (float weight : weights.values()) {
            total += Math.max(0.0F, weight);
        }
        if (total <= 0.0F) {
            return;
        }

        float roll = player.getRandom().nextFloat() * total;
        for (Map.Entry<SpawnBucket, Float> entry : weights.entrySet()) {
            roll -= Math.max(0.0F, entry.getValue());
            if (roll <= 0.0F) {
                event.setBucket(entry.getKey());
                return;
            }
        }
    }

    private static int percentBoost(int base, double bonusPercent) {
        double multiplier = 1.0D + bonusPercent / 100.0D;
        return Math.max(base, (int) Math.round(base * multiplier));
    }

    private static boolean isYoikoSidemodSource(ExperienceSource source) {
        return source instanceof SidemodExperienceSource sidemodSource
                && YoikoServerCore.MODID.equals(sidemodSource.getSidemodId());
    }

    private record MoveRecoveryTarget(
            BattlePokemon pokemon, int moveIndex, int missingPp, String moveName
    ) {
    }

    private static void debug(ServerPlayer player, String message, Object... args) {
        if (YoikoCommonConfig.DEBUG_COBBLEMON_RELIC_EVENTS.get()) {
            YoikoServerCore.LOGGER.info("[Yoiko Relic QA] {} {}", player.getGameProfile().getName(), org.slf4j.helpers.MessageFormatter.arrayFormat(message, args).getMessage());
        }
    }
}
