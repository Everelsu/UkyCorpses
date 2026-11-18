package com.uky.corpses;

import com.uky.corpses.client.CorpseClient;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> the player who just died, before anything else of the death reaches them: what killed them (a chat
 * component, translated on their side) and what becomes of their things. The death camera's card shows it.
 */
public class MsgDeath implements IMessage {

    /** A body takes the things (MsgCorpse follows unless there was nothing to take); they're kept; they fall out. */
    public static final int BODY = 0, KEPT = 1, DROPPED = 2;

    String cause, why;
    int kind;

    public MsgDeath() {}

    MsgDeath(String cause, String why, int kind) {
        this.cause = cause;
        this.why = why;
        this.kind = kind;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cause = ByteBufUtils.readUTF8String(buf);
        why = ByteBufUtils.readUTF8String(buf);
        kind = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, cause);
        ByteBufUtils.writeUTF8String(buf, why);
        buf.writeByte(kind);
    }

    public static class Handler implements IMessageHandler<MsgDeath, IMessage> {

        @Override
        public IMessage onMessage(MsgDeath m, MessageContext ctx) {
            CorpseClient.onDeath(m.cause, m.why, m.kind);
            return null;
        }
    }
}
