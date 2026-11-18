package com.uky.corpses;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> server: "take everything" in the body's chest. The owner gets it back into the original slots. */
public class MsgTakeAll implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<MsgTakeAll, IMessage> {

        @Override
        public IMessage onMessage(MsgTakeAll m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            UkyCorpses.serverTasks.add(() -> take(p));
            return null;
        }
    }

    private static void take(EntityPlayerMP p) {
        if (!(p.openContainer instanceof ContainerCorpse)) return;
        Object inv = ((ContainerCorpse) p.openContainer).body();
        if (!(inv instanceof EntityCorpse)) return;
        EntityCorpse b = (EntityCorpse) inv;
        if (!b.isUseableByPlayer(p)) return;
        boolean empty = b.giveTo(p);
        p.sendContainerToPlayer(p.inventoryContainer); // armour is not part of the chest window
        p.worldObj.playSoundAtEntity(p, "random.pop", 0.4F, 0.8F);
        if (empty) p.closeScreen(); // nothing left to take (closing may end the body: Corpses.keepEmpty)
        else p.addChatMessage(new ChatComponentTranslation("ukycorpses.msg.full"));
    }
}
