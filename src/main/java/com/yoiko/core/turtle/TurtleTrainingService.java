package com.yoiko.core.turtle;

import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.reward.YoikoResetClock;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class TurtleTrainingService {
    private TurtleTrainingService() { }

    public static void train(ServerPlayer player, TurtleData turtle, TurtleTrainingType type, long expectedRevision) {
        verifyOwner(player, turtle);
        if (TurtleRacingManager.get().timeTrial().isActiveTurtle(turtle.id())) throw TurtleLocalizedException.of("yoiko_core.turtle.error.train_time_trial");
        TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
        if (turtle.revision() != expectedRevision) throw TurtleLocalizedException.of("yoiko_core.turtle.error.changed_reopen");
        if (turtle.trainingCount() >= 24) throw TurtleLocalizedException.of("yoiko_core.turtle.error.training_complete");
        if (!canTrain(turtle,type)) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.training_cap");
        }
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(player.server);
        TurtlePlayerProgress progress = saved.getOrCreatePlayer(player.getUUID());
        progress.refreshTraining(YoikoResetClock.dailyPeriodKey(System.currentTimeMillis()));
        if (progress.availableTraining() <= 0) throw TurtleLocalizedException.of("yoiko_core.turtle.error.no_training_count");
        turtle.applyTraining(type.primary(), type.secondary());
        if (!progress.consumeTraining()) throw TurtleLocalizedException.of("yoiko_core.turtle.error.training_consume_failed");
        saved.markChanged();
        ServerYoikoAuditSavedData.get(player.server).addOperational("TURTLE", "TURTLE_TRAINED", player.getUUID(),
                player.getGameProfile().getName(), "id=" + turtle.id() + ",type=" + type + ",count=" + turtle.trainingCount());
    }

    public static void resetTraining(ServerPlayer player,TurtleData turtle,long expectedRevision){
        verifyOwner(player,turtle);
        if(turtle.revision()!=expectedRevision)throw TurtleLocalizedException.of("yoiko_core.turtle.error.changed_reopen");
        if(turtle.trainingCount()<=0)throw TurtleLocalizedException.of("yoiko_core.turtle.error.training_reset_empty");
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(player.server);
        TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
        if(TurtleRacingManager.get().timeTrial().isActiveTurtle(turtle.id()))
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.training_reset_time_trial");
        ItemStack ticket=ItemStack.EMPTY;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            ItemStack candidate=player.getInventory().getItem(slot);
            if(candidate.is(com.yoiko.core.registry.YoikoItems.TURTLE_TRAINING_RESET_TICKET.get())){ticket=candidate;break;}
        }
        if(ticket.isEmpty())throw TurtleLocalizedException.of("yoiko_core.turtle.error.no_training_reset_ticket");
        turtle.resetTraining();
        if(!player.isCreative())ticket.shrink(1);
        saved.markChanged();
        ServerYoikoAuditSavedData.get(player.server).addOperational("TURTLE","TURTLE_TRAINING_RESET",
                player.getUUID(),player.getGameProfile().getName(),"id="+turtle.id());
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("yoiko_core.turtle.message.training_reset",turtle.displayName()));
    }

    public static List<String> rerollCandidates(ServerPlayer player, TurtleData turtle, int slot) {
        verifyOwner(player, turtle);
        if (turtle.rerollCredits() <= 0 || turtle.rerollsUsed() >= 4) return List.of();
        return previewRerollCandidates(player,turtle,slot);
    }

    public static List<String> previewRerollCandidates(ServerPlayer player,TurtleData turtle,int slot){
        verifyOwner(player,turtle);
        return PassiveGenerationService.legalRerollCandidates(turtle.passives(),slot,turtle.activeSkill(),
                turtle.archetype(),turtle.rarity()).stream().filter(value->!value.equals(turtle.passives().get(slot))).toList();
    }

    public static boolean canTrain(TurtleData turtle,TurtleTrainingType type){
        return turtle.trainingCount()<24
                &&turtle.stats().get(type.primary())+2<=turtle.rarity().finalStatCap()
                &&turtle.stats().get(type.secondary())+1<=turtle.rarity().finalStatCap();
    }

    public static void consumePassiveReroll(ServerPlayer player,TurtleData turtle,long expectedRevision){
        verifyOwner(player, turtle);
        if (turtle.revision() != expectedRevision) throw TurtleLocalizedException.of("yoiko_core.turtle.error.changed_reopen");
        turtle.consumePassiveReroll();
        TurtleRacingSavedData.get(player.server).markChanged();
    }

    public static void applyPreparedPassive(ServerPlayer player,TurtleData turtle,int slot,String passiveId,long expectedRevision){
        verifyOwner(player,turtle);
        if(turtle.revision()!=expectedRevision)throw TurtleLocalizedException.of("yoiko_core.turtle.error.changed_reopen");
        List<String> legal=PassiveGenerationService.legalRerollCandidates(turtle.passives(),slot,turtle.activeSkill(),turtle.archetype(),turtle.rarity());
        if(!legal.contains(passiveId)||passiveId.equals(turtle.passives().get(slot)))throw TurtleLocalizedException.of("yoiko_core.turtle.error.invalid_passive");
        turtle.applyPreparedPassive(slot,passiveId);
        TurtleRacingSavedData.get(player.server).markChanged();
    }

    private static void verifyOwner(ServerPlayer player, TurtleData turtle) {
        if (!turtle.ownerId().equals(player.getUUID())) throw TurtleLocalizedException.of("yoiko_core.turtle.error.not_owner");
    }
}
