package com.uky.corpses;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

/**
 * A body lying as a block (Corpses.asBlock). Holds an EntityCorpse that is never spawned: same inventory, lock,
 * search and looks; the tile ticks it, saves it and sends it to clients. With asBlock off it turns back into a plain
 * entity body.
 */
public class TileCorpse extends TileEntity {

    public EntityCorpse body;
    /** Read before the world was set: made into the body on the first tick. */
    private NBTTagCompound pending;
    private boolean removing;

    /** Server: take over a freshly made body, already positioned where it lies. */
    void adopt(EntityCorpse b) {
        body = b;
        b.host = this;
        b.changed = this::resend;
        resend();
    }

    private void resend() {
        if (worldObj == null || worldObj.isRemote) return;
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        if (body == null && pending != null) {
            body = load(pending);
            pending = null;
        }
        if (body == null) return;
        if (!worldObj.isRemote && !Corpses.asBlock) { // switched off: lie on as an entity body
            release();
            return;
        }
        body.ticksExisted++;
        body.tickBody();
    }

    /** Server: the body leaves its blocks and lies on as an entity (blocks switched off, or picked up to drag). */
    EntityCorpse release() {
        EntityCorpse b = body;
        b.host = null;
        b.changed = null;
        body = null;
        removing = true;
        worldObj.spawnEntityInWorld(b);
        BlockCorpse.removeBody(worldObj, xCoord, yCoord, zCoord);
        return b;
    }

    /** Body gone (emptied, despawned): its blocks go with it. */
    void gone() {
        if (removing) return;
        removing = true;
        BlockCorpse.removeBody(worldObj, xCoord, yCoord, zCoord);
    }

    /** Block broken: the body comes apart, the things spill; unless the body already went. */
    void broken() {
        removing = true;
        if (body != null && !body.isDead) body.breakApart();
    }

    private EntityCorpse load(NBTTagCompound tag) {
        EntityCorpse b = new EntityCorpse(worldObj);
        b.setLocationAndAngles(tag.getDouble("bx"), tag.getDouble("by"), tag.getDouble("bz"), tag.getFloat("yaw"), 0);
        b.readEntityFromNBT(tag.getCompoundTag("body"));
        b.host = this;
        if (!worldObj.isRemote) b.changed = this::resend;
        return b;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        pending = tag;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        if (body == null && pending != null) // never ticked: keep what was loaded
            for (Object k : pending.func_150296_c()) tag.setTag(
                (String) k,
                pending.getTag((String) k)
                    .copy());
        super.writeToNBT(tag);
        if (body != null) writeBody(tag);
    }

    private void writeBody(NBTTagCompound tag) {
        NBTTagCompound b = new NBTTagCompound();
        body.writeEntityToNBT(b);
        tag.setTag("body", b);
        tag.setDouble("bx", body.posX);
        tag.setDouble("by", body.posY);
        tag.setDouble("bz", body.posZ);
        tag.setFloat("yaw", body.rotationYaw);
    }

    @Override
    public Packet getDescriptionPacket() {
        if (body == null) return null;
        NBTTagCompound tag = new NBTTagCompound();
        writeBody(tag);
        body.writeFresh(tag); // how it fell, which a save forgets on purpose
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound tag = pkt.func_148857_g();
        if (body == null) {
            body = load(tag);
            body.readFresh(tag);
            return;
        }
        body.setLocationAndAngles(
            tag.getDouble("bx"),
            tag.getDouble("by"),
            tag.getDouble("bz"),
            tag.getFloat("yaw"),
            0);
        body.readEntityFromNBT(tag.getCompoundTag("body"));
    }

    /** The body of the block body at x y z (its main block or its second one), null if none. */
    public static EntityCorpse bodyAt(IBlockAccess w, int x, int y, int z) {
        TileCorpse t = BlockCorpse.master(w, x, y, z);
        return t == null ? null : t.body;
    }

    /**
     * The body reaches into the next block and above this one; while it's coming down it is drawn wherever its player
     * stood or flew, so then it's drawn whatever the frustum says.
     */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (worldObj != null && worldObj.isRemote && body != null && (body.settle.active() || body.ragdollActive()))
            return INFINITE_EXTENT_AABB;
        return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord - 1, zCoord - 2, xCoord + 3, yCoord + 2, zCoord + 3);
    }
}
