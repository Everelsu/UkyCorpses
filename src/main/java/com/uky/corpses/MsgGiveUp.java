package com.uky.corpses;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> server: the downed player pressed give up. */
public class MsgGiveUp implements IMessage {

    private boolean holding = true;

    public MsgGiveUp() {}

    public MsgGiveUp(boolean holding) {
        this.holding = holding;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        holding = buf.isReadable() && buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(holding);
    }

    public static class Handler implements IMessageHandler<MsgGiveUp, IMessage> {

        @Override
        public IMessage onMessage(MsgGiveUp m, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            net.minecraft.world.World world = player.worldObj;
            UkyCorpses.serverTasks.add(() -> { if (player.worldObj == world) Downed.giveUpInput(player, m.holding); });
            return null;
        }
    }
}
