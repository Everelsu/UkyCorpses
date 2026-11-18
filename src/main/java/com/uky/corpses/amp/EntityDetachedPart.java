package com.uky.corpses.amp;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/** A detached limb carries one native rigid body and no inventory or player-camera identity. */
public final class EntityDetachedPart extends Entity implements IEntityAdditionalSpawnData {

    final net.minecraft.item.ItemStack[] armor = new net.minecraft.item.ItemStack[4];
    int part, lifetime = 1200;
    String owner = "", kind = "player";
    final Quat4f rotation = new Quat4f(0, 0, 0, 1);
    final Vector3f velocity = new Vector3f(), angular = new Vector3f();

    public EntityDetachedPart(World world) {
        super(world);
        setSize(.5F, .5F);
        ignoreFrustumCheck = true;
    }

    @Override
    protected void entityInit() {
        for (int i = 0; i < 4; i++) dataWatcher.addObject(20 + i, i == 3 ? 1F : 0F);
    }

    void syncRotation() {
        dataWatcher.updateObject(20, rotation.x);
        dataWatcher.updateObject(21, rotation.y);
        dataWatcher.updateObject(22, rotation.z);
        dataWatcher.updateObject(23, rotation.w);
    }

    static boolean launch(EntityLivingBase victim, Part lost, Vec3 blow, int lifetime) {
        EntityDetachedPart limb = new EntityDetachedPart(victim.worldObj);
        limb.part = lost.index;
        limb.lifetime = Math.max(20, lifetime);
        limb.owner = victim.getCommandSenderName();
        limb.kind = victim instanceof EntityPlayer ? "player"
            : victim instanceof net.minecraft.entity.monster.EntitySkeleton ? "skeleton" : "zombie";
        for (int i = 0; i < 4; i++) {
            net.minecraft.item.ItemStack worn = victim.getEquipmentInSlot(i + 1);
            armorCopy(limb, i, worn);
        }
        double offsetX = lost == Part.LEFT_ARM ? -.375
            : lost == Part.RIGHT_ARM ? .375 : lost == Part.LEFT_LEG ? -.11875 : lost == Part.RIGHT_LEG ? .11875 : 0;
        double offsetY = lost == Part.HEAD ? .625 : lost == Part.LEFT_LEG || lost == Part.RIGHT_LEG ? -.75 : 0;
        double yaw = Math.toRadians(180 - victim.renderYawOffset), cos = Math.cos(yaw), sin = Math.sin(yaw);
        double[] at = { offsetX * cos, 1.2 + offsetY, -offsetX * sin };
        limb.setPosition(victim.posX + at[0], victim.boundingBox.minY + at[1], victim.posZ + at[2]);
        limb.rotation.set(new javax.vecmath.AxisAngle4f(0, 1, 0, (float) yaw));
        Vec3 outward = Vec3.createVectorHelper(at[0], 0, at[2]);
        if (outward.lengthVector() > .001) outward = outward.normalize();
        Vec3 push = LivingAmputation.push(victim.getRNG(), lost, outward, blow);
        double carriedX = victim.motionX * 20, carriedY = victim.motionY * 20, carriedZ = victim.motionZ * 20;
        if (victim.onGround && carriedY < 0) carriedY = 0;
        limb.velocity
            .set((float) (carriedX + push.xCoord), (float) (carriedY + push.yCoord), (float) (carriedZ + push.zCoord));
        double hx = push.xCoord, hz = push.zCoord, length = Math.sqrt(hx * hx + hz * hz);
        if (length < .01) {
            double angle = victim.getRNG()
                .nextDouble() * Math.PI
                * 2;
            hx = Math.cos(angle);
            hz = Math.sin(angle);
            length = 1;
        }
        hx /= length;
        hz /= length;
        double scale = lost == Part.HEAD ? .55 : 1, tumble = AmputationConfig.LIMB_TUMBLE.get() * scale
            * (.6 + .8 * victim.getRNG()
                .nextDouble());
        limb.angular.set(
            (float) (hz * tumble),
            (float) ((victim.getRNG()
                .nextDouble() - .5) * .5 * tumble),
            (float) (-hx * tumble));
        if (lost != Part.HEAD && tumble > 0) {
            javax.vecmath.AxisAngle4f tilt = new javax.vecmath.AxisAngle4f(
                (float) hz,
                0,
                (float) -hx,
                (float) Math.toRadians(
                    20 + victim.getRNG()
                        .nextDouble() * 30));
            Quat4f extra = new Quat4f();
            extra.set(tilt);
            extra.mul(limb.rotation);
            limb.rotation.set(extra);
        }
        limb.syncRotation();
        LoosePhysics.ensure(limb);
        boolean spawned = victim.worldObj.spawnEntityInWorld(limb);
        if (!spawned) LoosePhysics.remove(limb);
        return spawned;
    }

    private static void armorCopy(EntityDetachedPart limb, int slot, net.minecraft.item.ItemStack stack) {
        if (stack != null) limb.armor[slot] = stack.copy();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            rotation.set(
                dataWatcher.getWatchableObjectFloat(20),
                dataWatcher.getWatchableObjectFloat(21),
                dataWatcher.getWatchableObjectFloat(22),
                dataWatcher.getWatchableObjectFloat(23));
            return;
        }
        if (ticksExisted >= lifetime) {
            setDead();
            return;
        }
        LoosePhysics.ensure(this);
    }

    @Override
    public void setDead() {
        if (worldObj != null && !worldObj.isRemote) LoosePhysics.remove(this);
        super.setDead();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        for (int i = 0; i < 4; i++) if (armor[i] != null) {
            NBTTagCompound item = new NBTTagCompound();
            armor[i].writeToNBT(item);
            tag.setTag("Armor" + i, item);
        }
        tag.setInteger("Part", part);
        tag.setInteger("Lifetime", Math.max(1, lifetime - ticksExisted));
        tag.setString("Owner", owner);
        tag.setString("Kind", kind);
        tag.setFloat("Qx", rotation.x);
        tag.setFloat("Qy", rotation.y);
        tag.setFloat("Qz", rotation.z);
        tag.setFloat("Qw", rotation.w);
        tag.setFloat("Vx", velocity.x);
        tag.setFloat("Vy", velocity.y);
        tag.setFloat("Vz", velocity.z);
        tag.setFloat("Wx", angular.x);
        tag.setFloat("Wy", angular.y);
        tag.setFloat("Wz", angular.z);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        for (int i = 0; i < 4; i++) armor[i] = tag.hasKey("Armor" + i, 10)
            ? net.minecraft.item.ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Armor" + i))
            : null;
        part = tag.getInteger("Part");
        if (part < 0 || part > 5 || part == 1) {
            setDead();
            return;
        }
        lifetime = Math.max(1, Math.min(24000, tag.getInteger("Lifetime")));
        owner = tag.getString("Owner");
        kind = tag.getString("Kind");
        rotation.set(tag.getFloat("Qx"), tag.getFloat("Qy"), tag.getFloat("Qz"), tag.getFloat("Qw"));
        float length = rotation.x * rotation.x + rotation.y * rotation.y
            + rotation.z * rotation.z
            + rotation.w * rotation.w;
        if (!Float.isFinite(length) || length < .01F) rotation.set(0, 0, 0, 1);
        else rotation.normalize();
        velocity.set(safe(tag.getFloat("Vx")), safe(tag.getFloat("Vy")), safe(tag.getFloat("Vz")));
        angular.set(safe(tag.getFloat("Wx")), safe(tag.getFloat("Wy")), safe(tag.getFloat("Wz")));
        syncRotation();
    }

    private static float safe(float value) {
        return Float.isFinite(value) ? Math.max(-100, Math.min(100, value)) : 0;
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        writeEntityToNBT(tag);
        cpw.mods.fml.common.network.ByteBufUtils.writeTag(buf, tag);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        NBTTagCompound tag = cpw.mods.fml.common.network.ByteBufUtils.readTag(buf);
        if (tag != null) readEntityFromNBT(tag);
    }
}
