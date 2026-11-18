package com.uky.corpses;

import java.lang.reflect.Field;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** The server state codec rejects corrupt/truncated data instead of creating a physical body from it. */
public final class RagdollPacketCheck {

    public static void run(float[] state) {
        ByteBuf fixture = Unpooled.buffer();
        fixture.writeInt(7)
            .writeInt(0)
            .writeInt(40)
            .writeLong(123)
            .writeLong(2)
            .writeDouble(100)
            .writeDouble(64)
            .writeDouble(-100)
            .writeByte(0)
            .writeFloat(1);
        for (float f : state) fixture.writeFloat(f);
        assert fixture.readableBytes() == 369;
        assert accepts(fixture.copy());
        assert !accepts(fixture.copy(0, 368));
        ByteBuf bad = fixture.copy();
        bad.writeByte(0);
        assert !accepts(bad);
        bad = fixture.copy();
        bad.setDouble(28, Double.NaN);
        assert !accepts(bad);
        bad = fixture.copy();
        bad.setByte(52, 1 << 1);
        assert !accepts(bad);
        bad = fixture.copy();
        bad.setFloat(53, Float.NaN);
        assert !accepts(bad);
        bad = fixture.copy();
        bad.setFloat(57, Float.POSITIVE_INFINITY);
        assert !accepts(bad);
        bad = fixture.copy();
        for (int i = 3; i < 7; i++) bad.setFloat(57 + i * 4, 0);
        assert !accepts(bad);
        MsgUse old = new MsgUse();
        old.fromBytes(
            Unpooled.buffer()
                .writeInt(7)
                .writeBoolean(false));
        assert number(old, "part") == 1;
        MsgUse selected = new MsgUse(7, true, 5);
        ByteBuf data = Unpooled.buffer();
        selected.toBytes(data);
        MsgUse decoded = new MsgUse();
        decoded.fromBytes(data);
        assert number(decoded, "part") == 5;
        decoded.fromBytes(
            Unpooled.buffer()
                .writeInt(7));
        assert number(decoded, "entity") < 0;
    }

    private static boolean accepts(ByteBuf data) {
        try {
            MsgRagdoll message = new MsgRagdoll();
            message.fromBytes(data);
            Field state = MsgRagdoll.class.getDeclaredField("state");
            state.setAccessible(true);
            return state.get(message) != null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        } finally {
            data.release();
        }
    }

    private static int number(Object value, String name) {
        try {
            Field field = value.getClass()
                .getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private RagdollPacketCheck() {}
}
