package com.uky.corpses;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> server: the downed player pressed give up. */
public class MsgGiveUp implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<MsgGiveUp, IMessage> {

        @Override
        public IMessage onMessage(MsgGiveUp m, MessageContext ctx) {
            Downed.giveUp(ctx.getServerHandler().playerEntity);
            return null;
        }
    }
}
