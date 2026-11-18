package com.uky.corpses.mobs;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.corpse.RagdollWorld;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** One strictly bounded server snapshot, tied to the entity's physical generation and immutable birth anchor. */
public final class MsgMobRagdoll implements IMessage {

    public int entity, dimension, age;
    public long generation, sequence;
    public double x, y, z;
    public float[] state;
    public boolean valid;

    public MsgMobRagdoll() {}

    MsgMobRagdoll(EntityMobRagdoll body, long sequence) {
        entity = body.getEntityId();
        dimension = body.dimension;
        age = body.loadedAge();
        generation = body.physicalGeneration();
        this.sequence = sequence;
        double[] at = body.birthOrigin();
        x = at[0];
        y = at[1];
        z = at[2];
        state = body.snapshot();
        valid = true;
    }

    public static boolean validAnchor(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y)
            && Double.isFinite(z)
            && Math.abs(x) <= 30000001
            && Math.abs(z) <= 30000001
            && Math.abs(y) <= 4096;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        state = null;
        if (buffer.readableBytes() < 53) return;
        entity = buffer.readInt();
        dimension = buffer.readInt();
        age = buffer.readInt();
        generation = buffer.readLong();
        sequence = buffer.readLong();
        x = buffer.readDouble();
        y = buffer.readDouble();
        z = buffer.readDouble();
        int count = buffer.readUnsignedByte();
        if (entity < 0 || age < 0
            || generation <= 0
            || sequence < 0
            || count < 1
            || count > 64
            || buffer.readableBytes() != count * 13 * 4
            || !validAnchor(x, y, z)) return;
        float[] incoming = new float[count * 13];
        for (int i = 0; i < incoming.length; i++) incoming[i] = buffer.readFloat();
        if (!RagdollWorld.RigScene.validState(incoming)) return;
        state = incoming;
        valid = true;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!RagdollWorld.RigScene.validState(state) || !validAnchor(x, y, z)
            || entity < 0
            || generation <= 0
            || sequence < 0
            || age < 0) throw new IllegalArgumentException("Invalid mob physics snapshot");
        buffer.writeInt(entity)
            .writeInt(dimension)
            .writeInt(age)
            .writeLong(generation)
            .writeLong(sequence)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeByte(state.length / 13);
        for (float value : state) buffer.writeFloat(value);
    }

    public static final class Handler implements IMessageHandler<MsgMobRagdoll, IMessage> {

        @Override
        public IMessage onMessage(MsgMobRagdoll packet, MessageContext context) {
            if (!context.side.isClient() || !packet.valid) return null;
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null || world.provider.dimensionId != packet.dimension) return null;
            CorpseClient.tasks.add(() -> {
                if (Minecraft.getMinecraft().theWorld != world) return;
                Entity entity = world.getEntityByID(packet.entity);
                if (entity instanceof EntityMobRagdoll && !entity.isDead) ((EntityMobRagdoll) entity).acceptSnapshot(
                    packet.generation,
                    packet.sequence,
                    packet.age,
                    packet.x,
                    packet.y,
                    packet.z,
                    packet.state);
            });
            return null;
        }
    }
}
