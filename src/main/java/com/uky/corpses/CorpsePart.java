package com.uky.corpses;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;

/**
 * Client only: a piece of a body's limb, what the crosshair hits instead of the body's whole box (like the dragon's
 * parts: World lists them through EntityCorpse.getParts, never spawned). It carries the body's entity id, so a click
 * on it reaches the server as a click on the body.
 */
public class CorpsePart extends Entity {

    public final EntityCorpse body;
    public final int index;

    public CorpsePart(EntityCorpse body, int index) {
        super(body.worldObj);
        this.body = body;
        this.index = index;
        setSize(0.2F, 0.2F);
    }

    /** Sits exactly on box, with the body's id. */
    void fit(AxisAlignedBB box) {
        setEntityId(body.getEntityId());
        posX = prevPosX = lastTickPosX = (box.minX + box.maxX) / 2;
        posY = prevPosY = lastTickPosY = box.minY;
        posZ = prevPosZ = lastTickPosZ = (box.minZ + box.maxZ) / 2;
        boundingBox.setBB(box);
    }

    @Override
    protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}

    @Override
    public boolean canBeCollidedWith() {
        return !body.isDead;
    }

    @Override
    public boolean interactFirst(EntityPlayer p) {
        return body.interactFirst(p);
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        return false;
    }

    @Override
    public boolean hitByEntity(Entity e) {
        return true;
    }

    @Override
    public boolean isEntityEqual(Entity e) {
        return this == e || body == e;
    }

    /** Exactly the limb: no margin around it as other entities get. */
    @Override
    public float getCollisionBorderSize() {
        return 0;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }
}
