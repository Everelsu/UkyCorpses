package com.uky.corpses.amp;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.client.CorpseClient;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class MsgMask implements IMessage {

    public int entity, dimension, mask;
    public boolean valid, player;
    public java.util.UUID owner;

    public MsgMask() {}

    MsgMask(EntityLivingBase target) {
        entity = target.getEntityId();
        dimension = target.dimension;
        mask = AmputationState.mask(target);
        player = target instanceof net.minecraft.entity.player.EntityPlayer;
        owner = target.getUniqueID();
        valid = true;
    }

    static void send(EntityLivingBase entity) {
        UkyCorpses.net.sendToAllAround(
            new MsgMask(entity),
            new NetworkRegistry.TargetPoint(entity.dimension, entity.posX, entity.posY, entity.posZ, 128));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = buf.readableBytes() >= 10;
        if (!valid) return;
        entity = buf.readInt();
        dimension = buf.readInt();
        mask = buf.readUnsignedByte();
        int flags = buf.readUnsignedByte();
        player = (flags & 1) != 0;
        valid = entity >= 0 && (mask & ~61) == 0 && (flags & ~1) == 0 && buf.readableBytes() == (player ? 16 : 0);
        if (valid && player) owner = new java.util.UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity)
            .writeInt(dimension)
            .writeByte(mask)
            .writeByte(player ? 1 : 0);
        if (player) buf.writeLong(owner.getMostSignificantBits())
            .writeLong(owner.getLeastSignificantBits());
    }

    public static final class Handler implements IMessageHandler<MsgMask, IMessage> {

        @Override
        public IMessage onMessage(MsgMask packet, MessageContext context) {
            if (!packet.valid || !context.side.isClient()) return null;
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null || world.provider.dimensionId != packet.dimension) return null;
            CorpseClient.tasks.add(() -> {
                if (Minecraft.getMinecraft().theWorld != world) return;
                Entity entity = world.getEntityByID(packet.entity);
                if (entity instanceof EntityLivingBase
                    && (!packet.player || entity instanceof net.minecraft.entity.player.EntityPlayer
                        && packet.owner.equals(entity.getUniqueID())))
                    entity.getEntityData()
                        .setByte(AmputationState.KEY, (byte) packet.mask);
            });
            return null;
        }
    }
}
