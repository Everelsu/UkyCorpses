package com.uky.corpses;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** The server chooses and consumes the real inventory stack. */
public final class MsgSelfAdrenaline implements IMessage {

    private boolean valid = true;

    public void fromBytes(ByteBuf data) {
        valid = !data.isReadable();
    }

    public void toBytes(ByteBuf data) {}

    public static final class Handler implements IMessageHandler<MsgSelfAdrenaline, IMessage> {

        public IMessage onMessage(MsgSelfAdrenaline message, MessageContext context) {
            net.minecraft.entity.player.EntityPlayerMP player = context.getServerHandler().playerEntity;
            net.minecraft.world.World world = player.worldObj;
            if (message.valid) UkyCorpses.serverTasks.add(
                () -> { if (player.worldObj == world && player.isEntityAlive()) Downed.selfAdrenaline(player, -1); });
            return null;
        }
    }
}
