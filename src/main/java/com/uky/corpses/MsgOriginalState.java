package com.uky.corpses;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.uky.corpses.client.CorpseClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Knocked-health/progress presentation accompanies the existing downed lifecycle packet. */
public class MsgOriginalState implements IMessage {

    private int entity, dimension, ticks, held, reviver;
    private UUID owner;
    private float health, maxHealth, progress;
    private boolean active, paused, blocked, crawlMode, valid;

    public MsgOriginalState() {}

    MsgOriginalState(EntityPlayerMP player, int ticks, boolean active, float health, float maxHealth, float progress,
        boolean paused, int held, int reviver) {
        entity = player.getEntityId();
        dimension = player.worldObj.provider.dimensionId;
        owner = player.getUniqueID();
        this.ticks = ticks;
        this.active = active;
        this.health = health;
        this.maxHealth = maxHealth;
        this.progress = progress;
        this.paused = paused;
        this.held = held;
        this.reviver = reviver;
        blocked = OriginalGameplay.blockActionsWhileKnocked;
        crawlMode = CorpseConfig.crawlDowned;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() != 4 * 5 + 8 * 2 + 4 * 3 + 3) return;
        entity = buf.readInt();
        dimension = buf.readInt();
        ticks = buf.readInt();
        owner = new UUID(buf.readLong(), buf.readLong());
        active = buf.readBoolean();
        health = buf.readFloat();
        maxHealth = buf.readFloat();
        progress = buf.readFloat();
        paused = buf.readBoolean();
        held = buf.readInt();
        reviver = buf.readInt();
        int flags = buf.readUnsignedByte();
        blocked = (flags & 1) != 0;
        crawlMode = (flags & 2) != 0;
        valid = flags <= 3 && Float.isFinite(health)
            && Float.isFinite(maxHealth)
            && Float.isFinite(progress)
            && health >= 0
            && health <= maxHealth
            && maxHealth >= 0
            && maxHealth <= 10000
            && progress >= 0
            && progress <= 1
            && held >= 0
            && held <= OriginalGameplay.GIVE_UP_TICKS;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity)
            .writeInt(dimension)
            .writeInt(ticks)
            .writeLong(owner.getMostSignificantBits())
            .writeLong(owner.getLeastSignificantBits())
            .writeBoolean(active)
            .writeFloat(health)
            .writeFloat(maxHealth)
            .writeFloat(progress)
            .writeBoolean(paused)
            .writeInt(held)
            .writeInt(reviver)
            .writeByte((blocked ? 1 : 0) | (crawlMode ? 2 : 0));
    }

    public static class Handler implements IMessageHandler<MsgOriginalState, IMessage> {

        @Override
        public IMessage onMessage(MsgOriginalState message, MessageContext context) {
            if (!message.valid || !context.side.isClient()) return null;
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null || world.provider.dimensionId != message.dimension) return null;
            Entity expected = world.getEntityByID(message.entity);
            CorpseClient.tasks.add(() -> {
                if (Minecraft.getMinecraft().theWorld != world) return;
                Entity entity = world.getEntityByID(message.entity);
                if (entity == null || entity != expected || !message.owner.equals(entity.getUniqueID())) return;
                OriginalGameplay.ClientState old = OriginalGameplay.seen.get(message.entity);
                if (old != null && old.serverTick > message.ticks) return;
                OriginalGameplay.serverBlockActions = message.blocked;
                OriginalGameplay.serverCrawlMode = message.crawlMode;
                if (!message.active) OriginalGameplay.seen.remove(message.entity);
                else OriginalGameplay.seen.put(
                    message.entity,
                    new OriginalGameplay.ClientState(
                        message.health,
                        message.maxHealth,
                        message.progress,
                        message.paused,
                        message.held,
                        message.reviver,
                        message.ticks));
            });
            return null;
        }
    }
}
