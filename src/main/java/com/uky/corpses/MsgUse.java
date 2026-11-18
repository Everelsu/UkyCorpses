package com.uky.corpses;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: a click on a body lying as an entity, a right-click (repeated while held: the search, the drag) or
 * a blow; sent by CorpsePick in place of vanilla's. Vanilla's lets a player reach an entity 3 blocks, and on the
 * server only 3 from their feet if a ray from their eyes doesn't reach its eyes, which for a body in the grass or on a
 * stair it often doesn't: the click was dropped without a word. Here it counts as far as a block does, to the nearest
 * limb, and goes through the same events vanilla's would (the downed and the stunned can't).
 */
public class MsgUse implements IMessage {

    private int entity;
    private boolean hit;

    public MsgUse() {}

    public MsgUse(int entity, boolean hit) {
        this.entity = entity;
        this.hit = hit;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entity = buf.readInt();
        hit = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity)
            .writeBoolean(hit);
    }

    public static class Handler implements IMessageHandler<MsgUse, IMessage> {

        @Override
        public IMessage onMessage(MsgUse m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            UkyCorpses.serverTasks.add(() -> m.use(p));
            return null;
        }
    }

    private void use(EntityPlayerMP p) {
        Entity e = p.worldObj.getEntityByID(entity);
        if (!(e instanceof EntityCorpse) || e.isDead || p.isDead || !reach(p, (EntityCorpse) e)) return;
        EntityCorpse b = (EntityCorpse) e;
        if (hit) {
            if (!MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(p, b))) b.hitByEntity(p);
        } else if (!MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(p, b))) b.interactFirst(p);
    }

    /**
     * From p's eyes to the nearest point of b's limbs, no further than a block can be reached (with vanilla's slack).
     */
    private static boolean reach(EntityPlayerMP p, EntityCorpse b) {
        double r = p.theItemInWorldManager.getBlockReachDistance() + 1.5;
        double ex = p.posX, ey = p.posY + p.getEyeHeight(), ez = p.posZ;
        for (AxisAlignedBB box : b.parts()) {
            double dx = Math.max(box.minX, Math.min(box.maxX, ex)) - ex,
                dy = Math.max(box.minY, Math.min(box.maxY, ey)) - ey,
                dz = Math.max(box.minZ, Math.min(box.maxZ, ez)) - ez;
            if (dx * dx + dy * dy + dz * dz <= r * r) return true;
        }
        return false;
    }
}
