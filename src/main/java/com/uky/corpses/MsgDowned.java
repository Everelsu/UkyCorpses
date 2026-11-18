package com.uky.corpses;

import com.uky.corpses.client.DownedClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> everyone in the dimension, every few ticks: how a downed player lies, how long they have, who's getting
 * them up. Drives their pose, the label over them, and the HUD of the downed one and of their reviver. pose < 0: up.
 * up: back on their feet but with no room to stand, crawling until there is.
 */
public class MsgDowned implements IMessage {

    public int entity, pose;
    public float yaw;
    public boolean crawl;
    /** Ticks: bleeding left, alone left (-1 with a friend near). */
    public int left, alone;
    /** -1: nobody on it. */
    public int percent, reviver;
    /** Client tick it was first seen down, for the fall; not sent. */
    public int since;
    /**
     * Client, not sent: the tick this came (the countdowns run on from it between packets); the most bleeding and
     * alone time seen this time down (what a full bar is).
     */
    public int at, leftMax, aloneMax;
    /** Client: died while down, at this client tick; drawn as they lay until their body appears. Not sent. */
    public int diedAt = -1;
    /** Knocked down by a blast (thrown, tumbling); up again but crawling under something low. */
    public boolean blast, up;
    /** Client, once dead: the yaw they lay at and what they wore then (the body that comes next starts from it). */
    public float deadYaw = Float.NaN;
    public net.minecraft.item.ItemStack[] deadArmor;

    public MsgDowned() {}

    MsgDowned(int entity, int pose, float yaw, boolean crawl, int left, int alone, int percent, int reviver) {
        this.entity = entity;
        this.pose = pose;
        this.yaw = yaw;
        this.crawl = crawl;
        this.left = left;
        this.alone = alone;
        this.percent = percent;
        this.reviver = reviver;
    }

    static MsgDowned crawling(int entity) {
        MsgDowned m = new MsgDowned(entity, Downed.CRAWL_POSE, 0, true, 0, -1, -1, -1);
        m.up = true;
        return m;
    }

    static MsgDowned gone(int entity) {
        return new MsgDowned(entity, -1, 0, false, 0, -1, -1, -1);
    }

    static MsgDowned died(int entity) {
        return new MsgDowned(entity, -2, 0, false, 0, -1, -1, -1);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entity = buf.readInt();
        pose = buf.readByte();
        yaw = buf.readFloat();
        crawl = buf.readBoolean();
        left = buf.readInt();
        alone = buf.readInt();
        percent = buf.readByte();
        reviver = buf.readInt();
        int flags = buf.readByte();
        blast = (flags & 1) != 0;
        up = (flags & 2) != 0;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity)
            .writeByte(pose)
            .writeFloat(yaw)
            .writeBoolean(crawl)
            .writeInt(left)
            .writeInt(alone)
            .writeByte(percent)
            .writeInt(reviver)
            .writeByte((blast ? 1 : 0) | (up ? 2 : 0));
    }

    public static class Handler implements IMessageHandler<MsgDowned, IMessage> {

        @Override
        public IMessage onMessage(MsgDowned m, MessageContext ctx) {
            net.minecraft.world.World world = net.minecraft.client.Minecraft.getMinecraft().theWorld;
            net.minecraft.entity.Entity entity = world == null ? null : world.getEntityByID(m.entity);
            com.uky.corpses.client.CorpseClient.tasks.add(() -> {
                if (world == null || net.minecraft.client.Minecraft.getMinecraft().theWorld != world
                    || world.getEntityByID(m.entity) != entity
                    || m.pose >= 0 && entity == null) return;
                apply(m);
            });
            return null;
        }

        private static void apply(MsgDowned m) {
            MsgDowned last = Downed.seen.get(m.entity);
            if (m.pose == -2 && last != null) {
                last.crawl = false;
                last.percent = -1;
                last.diedAt = DownedClient.ticks;
                return;
            }
            if (m.pose < 0) {
                Downed.seen.remove(m.entity);
                return;
            }
            MsgDowned old = Downed.seen.get(m.entity);
            m.since = old != null ? old.since : DownedClient.ticks;
            m.at = DownedClient.ticks;
            m.leftMax = Math.max(m.left, old != null ? old.leftMax : 0);
            m.aloneMax = m.alone < 0 ? 0 : Math.max(m.alone, old != null && old.alone >= 0 ? old.aloneMax : 0);
            Downed.seen.put(m.entity, m);
            return;
        }
    }
}
