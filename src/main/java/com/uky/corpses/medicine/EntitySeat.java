package com.uky.corpses.medicine;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Invisible, motionless, never saved: a stunned player rides it, which sits them down and takes their legs away. */
public final class EntitySeat extends Entity {

    private int lonely;

    public EntitySeat(World w) {
        super(w);
        setSize(0.1F, 0.1F);
        noClip = true;
        ignoreFrustumCheck = true;
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        ticksExisted++; // no physics. Nobody on it for a second (logout, death, left for good): it's done
        if (worldObj.isRemote) return;
        if (riddenByEntity != null && !riddenByEntity.isDead) lonely = 0;
        else if (++lonely > 20) setDead();
    }

    /** Feet end up 0.7 under the seat: the model's hip is 0.75 above them, and that is what sits on the floor. */
    @Override
    public double getMountedYOffset() {
        return -0.2;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource s, float a) {
        return false;
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound t) {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound t) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound t) {}
}
