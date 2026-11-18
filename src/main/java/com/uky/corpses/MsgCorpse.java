package com.uky.corpses;

import com.uky.corpses.client.CorpseClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> the player who just died: where the body lies, and what's in it (stacks, levels of experience, stacks lost,
 * minutes only the owner may open it, -1 for ever). Feeds the death camera's card and ukyui's death screen.
 */
public class MsgCorpse implements IMessage {

    int x, y, z, dim, number, items, levels, lost, lock;

    public MsgCorpse() {}

    MsgCorpse(int x, int y, int z, int dim, int number, int items, int levels, int lost, int lock) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.number = number;
        this.items = items;
        this.levels = levels;
        this.lost = lost;
        this.lock = lock;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        dim = buf.readInt();
        number = buf.readInt();
        items = buf.readShort();
        levels = buf.readInt();
        lost = buf.readShort();
        lock = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x)
            .writeInt(y)
            .writeInt(z)
            .writeInt(dim)
            .writeInt(number)
            .writeShort(items)
            .writeInt(levels)
            .writeShort(lost)
            .writeInt(lock);
    }

    public static class Handler implements IMessageHandler<MsgCorpse, IMessage> {

        @Override
        public IMessage onMessage(MsgCorpse m, MessageContext ctx) {
            CorpseClient.onCorpse(m.x, m.y, m.z, m.dim, m.number, m.items, m.levels, m.lost, m.lock);
            return null;
        }
    }
}
