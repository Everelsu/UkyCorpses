package com.uky.corpses.medicine;

import net.minecraft.item.ItemStack;

import com.uky.corpses.medicine.client.MedicineClient;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> the one being treated or fed: who is at it, with what, for how many ticks, and whether it can be fought
 * off. ticks 0: it's over (done, given up, fought off, out of reach).
 */
public class MsgFeed implements IMessage {

    public int giver, ticks;
    public boolean forced;
    public ItemStack item;

    public MsgFeed() {}

    MsgFeed(int giver, ItemStack item, int ticks, boolean forced) {
        this.giver = giver;
        this.item = item;
        this.ticks = ticks;
        this.forced = forced;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        giver = buf.readInt();
        ticks = buf.readInt();
        forced = buf.readBoolean();
        item = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(giver)
            .writeInt(ticks)
            .writeBoolean(forced);
        ByteBufUtils.writeItemStack(buf, item);
    }

    public static class Handler implements IMessageHandler<MsgFeed, IMessage> {

        @Override
        public IMessage onMessage(MsgFeed m, MessageContext ctx) {
            MedicineClient.fed(m);
            return null;
        }
    }
}
