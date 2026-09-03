package com.yoiko.core.turtle;

public enum TurtleTicketType {
    STANDARD,
    PICKUP,
    RARE_FRONT,
    RARE_STEADY,
    RARE_FOLLOW,
    RARE_CLOSER,
    EPIC_GUARANTEED,
    /** Unified item that must be resolved to one of the four internal strategy outcomes before hatching. */
    RARE_STRATEGY_SELECT;

    public TurtleArchetype fixedArchetype(){return switch(this){
        case RARE_FRONT->TurtleArchetype.FRONT;
        case RARE_STEADY->TurtleArchetype.STEADY;
        case RARE_FOLLOW->TurtleArchetype.FOLLOW;
        case RARE_CLOSER->TurtleArchetype.CLOSER;
        default->null;
    };}

    public TurtleStrategy fixedStrategy(){return switch(this){
        case RARE_FRONT->TurtleStrategy.FRONT;
        case RARE_STEADY->TurtleStrategy.STEADY;
        case RARE_FOLLOW->TurtleStrategy.FOLLOW;
        case RARE_CLOSER->TurtleStrategy.CLOSER;
        default->null;
    };}

    public boolean rareGuaranteed(){return fixedArchetype()!=null;}
    public boolean requiresStrategySelection(){return this==RARE_STRATEGY_SELECT;}
}
