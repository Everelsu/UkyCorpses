package com.uky.corpses;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.client.RagdollCamera;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class MsgVoidReturn implements IMessage {

    private long life;

    public MsgVoidReturn() {}

    MsgVoidReturn(long life) {
        this.life = life;
    }

    public void fromBytes(ByteBuf b) {
        if (b.readableBytes() == 8) life = b.readLong();
    }

    public void toBytes(ByteBuf b) {
        b.writeLong(life);
    }

    public static final class Handler implements IMessageHandler<MsgVoidReturn, IMessage> {

        public IMessage onMessage(MsgVoidReturn m, MessageContext context) {
            World world = Minecraft.getMinecraft().theWorld;
            CorpseClient.tasks
                .add(() -> { if (Minecraft.getMinecraft().theWorld == world) RagdollCamera.voidReturn(m.life); });
            return null;
        }
    }
}
