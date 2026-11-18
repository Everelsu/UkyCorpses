package com.uky.corpses;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Held-use keepalive; the server validates the downed target and 2.5-block range. */
public class MsgReviveInput implements IMessage {

    private int entity = -1;

    public MsgReviveInput() {}

    public MsgReviveInput(int entity) {
        this.entity = entity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() == 4) entity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity);
    }

    public static class Handler implements IMessageHandler<MsgReviveInput, IMessage> {

        @Override
        public IMessage onMessage(MsgReviveInput message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            World world = player.worldObj;
            UkyCorpses.serverTasks.add(
                () -> {
                    if (player.worldObj == world && player.isEntityAlive()) Downed.reviveInput(player, message.entity);
                });
            return null;
        }
    }
}
