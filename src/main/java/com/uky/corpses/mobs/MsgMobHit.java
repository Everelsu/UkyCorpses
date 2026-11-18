package com.uky.corpses.mobs;

import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Only the server computes a punch, its ray, damage and resulting physical impulse. */
public final class MsgMobHit implements IMessage {

    public int entity;
    public long generation;
    public boolean valid;

    public MsgMobHit() {}

    public MsgMobHit(EntityMobRagdoll body) {
        entity = body.getEntityId();
        generation = body.physicalGeneration();
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeInt(entity)
            .writeLong(generation);
    }

    @Override
    public void fromBytes(ByteBuf b) {
        valid = b.readableBytes() == 12;
        if (!valid) return;
        entity = b.readInt();
        generation = b.readLong();
        valid = entity >= 0 && generation > 0;
    }

    public static final class Handler implements IMessageHandler<MsgMobHit, IMessage> {

        @Override
        public IMessage onMessage(MsgMobHit m, MessageContext context) {
            if (m.valid) {
                net.minecraft.entity.player.EntityPlayerMP p = context.getServerHandler().playerEntity;
                net.minecraft.world.World world = p.worldObj;
                UkyCorpses.serverTasks.add(() -> {
                    if (p.worldObj != world || !p.isEntityAlive()) return;
                    net.minecraft.entity.Entity target = world.getEntityByID(m.entity);
                    if (!(target instanceof EntityMobRagdoll) || target.isDead
                        || ((EntityMobRagdoll) target).physicalGeneration() != m.generation) return;
                    if (!net.minecraftforge.common.MinecraftForge.EVENT_BUS
                        .post(new net.minecraftforge.event.entity.player.AttackEntityEvent(p, target)))
                        target.hitByEntity(p);
                });
            }
            return null;
        }
    }
}
