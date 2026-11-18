package com.uky.corpses;

import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.client.ReactionClient;
import com.uky.corpses.corpse.Ragdoll;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Clients request an action; only the server supplies positions and physical state. */
public final class MsgReaction implements IMessage {

    public static final int TOGGLE = 0, UP = 1, STATE = 2;
    public int action, entity, dimension, mask;
    public long uuidMost, uuidLeast, generation;
    public int age, holder, heldPart;
    public boolean active, manual, downed, valid = true;
    public double x, y, z;
    public float[] state;

    public MsgReaction() {}

    public MsgReaction(int action) {
        this.action = action;
    }

    static MsgReaction gone(int entity, int dimension, java.util.UUID uuid, long generation, int age) {
        MsgReaction m = new MsgReaction(STATE);
        m.entity = entity;
        m.dimension = dimension;
        m.uuidMost = uuid.getMostSignificantBits();
        m.uuidLeast = uuid.getLeastSignificantBits();
        m.generation = generation;
        m.age = age;
        return m;
    }

    @Override
    public void fromBytes(ByteBuf b) {
        if (b.readableBytes() < 1) {
            valid = false;
            return;
        }
        action = b.readUnsignedByte();
        if (action != STATE) {
            valid = action == TOGGLE || action == UP;
            return;
        }
        if (b.readableBytes() < 37) {
            valid = false;
            return;
        }
        entity = b.readInt();
        dimension = b.readInt();
        uuidMost = b.readLong();
        uuidLeast = b.readLong();
        generation = b.readLong();
        age = b.readInt();
        int flags = b.readUnsignedByte();
        active = (flags & 1) != 0;
        manual = (flags & 2) != 0;
        downed = (flags & 4) != 0;
        valid = entity >= 0 && generation > 0 && age >= 0 && (flags & ~7) == 0;
        if (!valid) return;
        if (!active) return;
        if (b.readableBytes() < 30 + 78 * 4) {
            valid = false;
            return;
        }
        x = b.readDouble();
        y = b.readDouble();
        z = b.readDouble();
        mask = b.readUnsignedByte();
        holder = b.readInt();
        heldPart = b.readUnsignedByte();
        state = new float[78];
        for (int i = 0; i < state.length; i++) state[i] = b.readFloat();
        valid = holder >= 0 && heldPart < 6
            && entity >= 0
            && Double.isFinite(x)
            && Double.isFinite(y)
            && Double.isFinite(z)
            && Math.abs(x) <= 30000001
            && Math.abs(z) <= 30000001
            && Math.abs(y) <= 4096
            && generation > 0
            && age >= 0
            && (mask & ~61) == 0
            && validState(state);
    }

    public static boolean validState(float[] s) {
        return Ragdoll.validState(s);
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeByte(action);
        if (action != STATE) return;
        b.writeInt(entity)
            .writeInt(dimension)
            .writeLong(uuidMost)
            .writeLong(uuidLeast)
            .writeLong(generation)
            .writeInt(age)
            .writeByte((active ? 1 : 0) | (manual ? 2 : 0) | (downed ? 4 : 0));
        if (!active) return;
        b.writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeByte(mask)
            .writeInt(holder)
            .writeByte(heldPart);
        for (float f : state) b.writeFloat(f);
    }

    public static final class Server implements IMessageHandler<MsgReaction, IMessage> {

        @Override
        public IMessage onMessage(MsgReaction m, MessageContext ctx) {
            if (m.valid && (m.action == TOGGLE || m.action == UP))
                UkyCorpses.serverTasks.add(() -> Reactions.request(ctx.getServerHandler().playerEntity, m.action));
            return null;
        }
    }

    public static final class Client implements IMessageHandler<MsgReaction, IMessage> {

        @Override
        public IMessage onMessage(MsgReaction m, MessageContext ctx) {
            if (m.valid && m.action == STATE) {
                net.minecraft.world.World world = ReactionClient.currentWorld();
                net.minecraft.entity.Entity expected = world == null ? null : world.getEntityByID(m.entity);
                CorpseClient.tasks.add(
                    () -> {
                        if (world != null && expected != null && world.getEntityByID(m.entity) == expected)
                            ReactionClient.accept(m, world);
                    });
            }
            return null;
        }
    }
}
