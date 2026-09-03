package com.yoiko.core.turtle.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.Level;

/** Client-visible race presentation. Physics and results remain server-simulated. */
public final class RaceTurtleEntity extends Turtle {
    private static final EntityDataAccessor<String> APPEARANCE = SynchedEntityData.defineId(RaceTurtleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> BODY_APPEARANCE = SynchedEntityData.defineId(RaceTurtleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(RaceTurtleEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> ACTIVE_SKILL = SynchedEntityData.defineId(RaceTurtleEntity.class, EntityDataSerializers.BOOLEAN);

    public RaceTurtleEntity(EntityType<? extends Turtle> type, Level level) {
        super(type, level);
        setAge(-24_000);
        setNoAi(true);
        setInvulnerable(true);
        setSilent(true);
        setNoGravity(true);
        noPhysics = true;
        noCulling = true;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(APPEARANCE, "natural");
        builder.define(BODY_APPEARANCE, "natural");
        builder.define(OWNER, Optional.empty());
        builder.define(ACTIVE_SKILL, false);
    }

    public String appearance() { return entityData.get(APPEARANCE); }
    public void setAppearance(String value) { entityData.set(APPEARANCE, value); }
    public String bodyAppearance() { return entityData.get(BODY_APPEARANCE); }
    public void setBodyAppearance(String value) { entityData.set(BODY_APPEARANCE, value); }
    public Optional<UUID> ownerId() { return entityData.get(OWNER); }
    public void setOwnerId(UUID value) { entityData.set(OWNER, Optional.ofNullable(value)); }
    public boolean activeSkillHighlighted() { return entityData.get(ACTIVE_SKILL); }
    public void setActiveSkillHighlighted(boolean value) { entityData.set(ACTIVE_SKILL, value); }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 160.0 * 160.0; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
}
