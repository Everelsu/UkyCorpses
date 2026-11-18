package com.uky.corpses;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.corpse.Ragdoll;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server snapshots only: clients never choose a body's position or persisted pose. */
public class MsgRagdoll implements IMessage {

    private int entity, dimension, severed, serverTick;
    private long generation, sequence;
    private double x, y, z;
    private float impact;
    private float[] state;

    public MsgRagdoll() {}

    MsgRagdoll(EntityCorpse body, long generation, long sequence, double x, double y, double z, int severed,
        float[] state, float impact) {
        this.entity = body.getEntityId();
        this.dimension = body.worldObj.provider.dimensionId;
        this.serverTick = body.ticksExisted;
        this.generation = generation;
        this.sequence = sequence;
        this.x = x;
        this.y = y;
        this.z = z;
        this.severed = severed;
        this.state = state;
        this.impact = impact;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() != 4 + 4 + 4 + 8 + 8 + 8 * 3 + 1 + 4 + 78 * 4) return;
        entity = buf.readInt();
        dimension = buf.readInt();
        serverTick = buf.readInt();
        generation = buf.readLong();
        sequence = buf.readLong();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        severed = buf.readUnsignedByte();
        impact = buf.readFloat();
        state = new float[78];
        for (int i = 0; i < state.length; i++) state[i] = buf.readFloat();
        if (!Ragdoll.validState(state) || !EntityCorpse.validRagdollAnchor(x, y, z)
            || (severed & ~61) != 0
            || !Float.isFinite(impact)
            || impact < 0
            || impact > 100) state = null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity)
            .writeInt(dimension)
            .writeInt(serverTick)
            .writeLong(generation)
            .writeLong(sequence)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeByte(severed)
            .writeFloat(impact);
        for (float value : state) buf.writeFloat(value);
    }

    public static class Handler implements IMessageHandler<MsgRagdoll, IMessage> {

        @Override
        public IMessage onMessage(MsgRagdoll m, MessageContext ctx) {
            if (!ctx.side.isClient()) return null;
            if (m.state == null) return null;
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null || world.provider.dimensionId != m.dimension) return null;
            CorpseClient.tasks.add(() -> {
                if (Minecraft.getMinecraft().theWorld != world) return;
                Entity entity = world.getEntityByID(m.entity);
                if (entity instanceof EntityCorpse && !entity.isDead) ((EntityCorpse) entity).acceptRagdollSnapshot(
                    m.generation,
                    m.sequence,
                    m.serverTick,
                    m.x,
                    m.y,
                    m.z,
                    m.severed,
                    m.state,
                    m.impact);
            });
            return null;
        }
    }
}
