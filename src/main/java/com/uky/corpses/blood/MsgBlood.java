package com.uky.corpses.blood;

import com.uky.corpses.blood.client.BloodClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server -> players near: one splat lies at x y z (the floor, lifted a hair). The seed lays out its spots. A pool
 * (grow > 0) starts after `delay` ticks and spreads out over `grow` ticks.
 */
public class MsgBlood implements IMessage {

    public float x, y, z, size;
    public int seed;
    public short delay, grow;

    public MsgBlood() {}

    public MsgBlood(float x, float y, float z, float size, int seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.size = size;
        this.seed = seed;
    }

    MsgBlood(float x, float y, float z, float size, int seed, int delay, int grow) {
        this(x, y, z, size, seed);
        this.delay = (short) delay;
        this.grow = (short) grow;
    }

    @Override
    public void fromBytes(ByteBuf b) {
        x = b.readFloat();
        y = b.readFloat();
        z = b.readFloat();
        size = b.readFloat();
        seed = b.readInt();
        delay = b.readShort();
        grow = b.readShort();
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeFloat(x)
            .writeFloat(y)
            .writeFloat(z)
            .writeFloat(size)
            .writeInt(seed)
            .writeShort(delay)
            .writeShort(grow);
    }

    public static class Handler implements IMessageHandler<MsgBlood, IMessage> {

        @Override
        public IMessage onMessage(MsgBlood m, MessageContext ctx) {
            BloodClient.add(m);
            return null;
        }
    }
}
