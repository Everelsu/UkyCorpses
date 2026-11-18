package com.uky.corpses;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Original PR drag keepalive/release; a packet can only maintain the sender's existing grip. */
public final class MsgDragInput implements IMessage {

    public int entity;
    public long life;
    public boolean held, valid;

    public MsgDragInput() {}

    public MsgDragInput(int entity, long life, boolean held) {
        this.entity = entity;
        this.life = life;
        this.held = held;
    }

    @Override
    public void fromBytes(ByteBuf b) {
        valid = b.readableBytes() == 13;
        if (!valid) return;
        entity = b.readInt();
        life = b.readLong();
        int flag = b.readUnsignedByte();
        held = flag == 1;
        valid = entity >= 0 && life > 0 && flag <= 1;
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeInt(entity)
            .writeLong(life)
            .writeByte(held ? 1 : 0);
    }

    public static final class Handler implements IMessageHandler<MsgDragInput, IMessage> {

        @Override
        public IMessage onMessage(MsgDragInput m, MessageContext ctx) {
            if (m.valid) {
                net.minecraft.entity.player.EntityPlayerMP p = ctx.getServerHandler().playerEntity;
                net.minecraft.world.World world = p.worldObj;
                UkyCorpses.serverTasks
                    .add(() -> { if (p.worldObj == world) Reactions.dragInput(p, m.entity, m.life, m.held); });
            }
            return null;
        }
    }
}
