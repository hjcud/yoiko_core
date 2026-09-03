package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.ActiveSkill;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/** Compact skill effects anchored to the source turtle's shell and movement direction. */
public final class TurtleSkillEffectRenderer {
    @FunctionalInterface
    public interface ParticleEmitter {
        void emit(ParticleOptions particle, double x, double y, double z, int count,
                  double offsetX, double offsetY, double offsetZ, double speed, boolean essential);
    }

    private TurtleSkillEffectRenderer() { }

    public static void emitActivation(Entity turtle, ActiveSkill skill, int strength, ParticleEmitter emitter) {
        double x=turtle.getX(),y=turtle.getY()+.17,z=turtle.getZ();
        emitter.emit(particle(skill),x,y,z,strength>=3?8:6,.10,.07,.10,.022,true);

        DustParticleOptions marker=new DustParticleOptions(accent(skill),.58F);
        for(int i=0;i<8;i++){
            double angle=Math.PI*2*i/8.0;
            emitter.emit(marker,x+Math.cos(angle)*.20,y+.02,z+Math.sin(angle)*.20,1,0,0,0,0,true);
        }
        for(int i=0;i<3;i++)emitter.emit(marker,x,y+.12+i*.10,z,1,0,0,0,0,true);
    }

    public static void emitTrail(Entity turtle, ActiveSkill skill, long tick, ParticleEmitter emitter) {
        double radians=Math.toRadians(turtle.getYRot());
        double forwardX=-Math.sin(radians),forwardZ=Math.cos(radians);
        double x=turtle.getX()-forwardX*.16,y=turtle.getY()+.13,z=turtle.getZ()-forwardZ*.16;
        if(tick%3==0)emitter.emit(particle(skill),x,y,z,1,.025,.025,.025,.004,false);
        if(tick%5==0){
            double angle=tick*.42;
            DustParticleOptions marker=new DustParticleOptions(accent(skill),.46F);
            emitter.emit(marker,turtle.getX()+Math.cos(angle)*.15,turtle.getY()+.18,
                    turtle.getZ()+Math.sin(angle)*.15,1,0,0,0,0,false);
        }
    }

    public static ParticleOptions particle(ActiveSkill skill) {
        return switch(skill){
            case SHELLBREAK_START->ParticleTypes.ELECTRIC_SPARK;
            case SAND_SPRINT->ParticleTypes.CLOUD;
            case FLOW_RHYTHM->ParticleTypes.NOTE;
            case MUD_BREAKER->ParticleTypes.POOF;
            case CORAL_CORNER->ParticleTypes.GLOW;
            case SHELL_HOP->ParticleTypes.CRIT;
            case UNTURNED_HEART->ParticleTypes.ENCHANT;
            case FALSE_FOOTPRINTS->ParticleTypes.SMOKE;
            case PUDDLE_SURF->ParticleTypes.SPLASH;
            case DEEP_BREATH->ParticleTypes.BUBBLE_POP;
            case THOUSAND_YEAR_STEP->ParticleTypes.SOUL_FIRE_FLAME;
            case HOMEWARD_WAVE->ParticleTypes.END_ROD;
            case SUNLIT_STRIDE->ParticleTypes.WAX_ON;
            case TAILWIND_SAIL->ParticleTypes.SMALL_GUST;
            case RAINSTEP->ParticleTypes.FALLING_WATER;
            case BREAKAWAY_BOUND->ParticleTypes.CLOUD;
            case LEAD_GUARD->ParticleTypes.ELECTRIC_SPARK;
            case RESERVE_RELEASE->ParticleTypes.BUBBLE_POP;
            case WAKE_CUT->ParticleTypes.SPLASH;
            case FINAL_GAP->ParticleTypes.CRIT;
            case CORNER_CLAIM->ParticleTypes.WAX_ON;
            case SURFACE_CHAIN->ParticleTypes.HAPPY_VILLAGER;
            case LIMIT_SPRINT->ParticleTypes.FLAME;
            case SURGING_SPRAY->ParticleTypes.SPLASH;
            case RECKLESS_PASS->ParticleTypes.CRIT;
        };
    }

    public static SoundEvent sound(ActiveSkill skill) {
        return switch(skill){
            case SHELLBREAK_START,SHELL_HOP,BREAKAWAY_BOUND,FINAL_GAP,RECKLESS_PASS->SoundEvents.BREEZE_JUMP;
            case SAND_SPRINT,TAILWIND_SAIL,WAKE_CUT,SURGING_SPRAY->SoundEvents.BREEZE_SHOOT;
            case FLOW_RHYTHM,RAINSTEP,CORNER_CLAIM->SoundEvents.NOTE_BLOCK_CHIME.value();
            case MUD_BREAKER,SURFACE_CHAIN->SoundEvents.MUD_STEP;
            case CORAL_CORNER,PUDDLE_SURF,HOMEWARD_WAVE,RESERVE_RELEASE,DEEP_BREATH->SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE;
            case UNTURNED_HEART,LEAD_GUARD->SoundEvents.SHIELD_BLOCK;
            case FALSE_FOOTPRINTS->SoundEvents.ILLUSIONER_CAST_SPELL;
            case THOUSAND_YEAR_STEP,LIMIT_SPRINT->SoundEvents.BEACON_ACTIVATE;
            case SUNLIT_STRIDE->SoundEvents.AMETHYST_BLOCK_CHIME;
        };
    }

    private static Vector3f accent(ActiveSkill skill) {
        if(skill.tags().contains("INTERFERENCE"))return new Vector3f(.80F,.28F,1.0F);
        if(skill.tags().contains("PUDDLE")||skill.tags().contains("STAMINA"))return new Vector3f(.18F,.78F,1.0F);
        if(skill.tags().contains("FINAL"))return new Vector3f(1.0F,.72F,.16F);
        if(skill.tags().contains("POWER"))return new Vector3f(1.0F,.32F,.18F);
        if(skill.tags().contains("NAVIGATION")||skill.tags().contains("CORNER"))return new Vector3f(.25F,1.0F,.52F);
        return new Vector3f(.42F,.72F,1.0F);
    }
}
