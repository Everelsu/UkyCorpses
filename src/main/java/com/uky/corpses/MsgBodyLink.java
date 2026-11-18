package com.uky.corpses;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.client.RagdollCamera;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server binds the death view to one physical life, never an older corpse. */
public final class MsgBodyLink implements IMessage {

    private UUID owner;
    private long life;

    public MsgBodyLink() {}

    MsgBodyLink(UUID owner, long life) {
        this.owner = owner;
        this.life = life;
    }

    public void fromBytes(ByteBuf b) {
        if (b.readableBytes() == 24) {
            owner = new UUID(b.readLong(), b.readLong());
            life = b.readLong();
        }
    }

    public void toBytes(ByteBuf b) {
        b.writeLong(owner.getMostSignificantBits())
            .writeLong(owner.getLeastSignificantBits())
            .writeLong(life);
    }

    public static final class Handler implements IMessageHandler<MsgBodyLink, IMessage> {

        public IMessage onMessage(MsgBodyLink m, MessageContext context) {
            if (m.owner == null) return null;
            World world = Minecraft.getMinecraft().theWorld;
            CorpseClient.tasks
                .add(() -> { if (Minecraft.getMinecraft().theWorld == world) RagdollCamera.bind(m.owner, m.life); });
            return null;
        }
    }
}
