package com.uky.corpses;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Codec boundaries: clients cannot submit a pose through the action channel. */
public final class ReactionCheck {

    public static void main(String[] args) {
        ByteBuf dragBytes = Unpooled.buffer();
        try {
            new MsgDragInput(42, 19, true).toBytes(dragBytes);
            MsgDragInput drag = new MsgDragInput();
            drag.fromBytes(dragBytes);
            assert drag.valid && drag.entity == 42 && drag.life == 19 && drag.held;
            dragBytes.clear()
                .writeInt(42)
                .writeLong(19)
                .writeByte(2);
            drag = new MsgDragInput();
            drag.fromBytes(dragBytes);
            assert !drag.valid;
        } finally {
            dragBytes.release();
        }
        MsgReaction m = new MsgReaction(MsgReaction.STATE);
        m.entity = 42;
        m.dimension = -1;
        m.active = true;
        m.manual = true;
        m.generation = 1;
        m.holder = 17;
        m.heldPart = 4;
        m.x = 29999999;
        m.y = 70;
        m.z = -29999999;
        m.state = new float[78];
        for (int i = 0; i < 78; i += 13) {
            m.state[i] = i / 13F;
            m.state[i + 6] = 1;
        }
        assert MsgReaction.validState(m.state);
        ByteBuf b = Unpooled.buffer();
        try {
            m.toBytes(b);
            MsgReaction copy = new MsgReaction();
            copy.fromBytes(b);
            assert copy.valid && copy.active && copy.manual && !copy.downed;
            assert copy.entity == 42 && copy.dimension == -1 && copy.x == m.x && copy.z == m.z;
            assert Arrays.equals(m.state, copy.state);
            assert copy.holder == 17 && copy.heldPart == 4;
            byte[] packet = new byte[b.writerIndex()];
            b.getBytes(0, packet);
            for (int length = 0; length < packet.length; length++) {
                MsgReaction shortPacket = new MsgReaction();
                shortPacket.fromBytes(Unpooled.wrappedBuffer(Arrays.copyOf(packet, length)));
                assert !shortPacket.valid : "Accepted truncated physical state " + length;
            }
            b.clear();
            new MsgReaction(MsgReaction.UP).toBytes(b);
            assert b.readableBytes() == 1;
            copy = new MsgReaction();
            copy.fromBytes(b);
            assert copy.valid && copy.action == MsgReaction.UP && copy.state == null;
            b.clear()
                .writeByte(254);
            copy = new MsgReaction();
            copy.fromBytes(b);
            assert !copy.valid;
            b.clear()
                .writeByte(MsgReaction.STATE)
                .writeByte(0);
            copy = new MsgReaction();
            copy.fromBytes(b);
            assert !copy.valid;
        } finally {
            b.release();
        }
        m.state[3] = Float.NaN;
        assert !MsgReaction.validState(m.state);
        m.state[3] = 0;
        m.state[7] = 1025;
        assert !MsgReaction.validState(m.state);
        m.state[7] = 0;
        m.state[6] = 0;
        assert !MsgReaction.validState(m.state);
        assert !MsgReaction.validState(new float[77]);
        assert Reactions.mayGetUp(true, false, 0);
        assert !Reactions.mayGetUp(true, true, 1000);
        assert !Reactions.mayGetUp(false, false, Reactions.minimumTicks - 1);
        assert Reactions.mayGetUp(false, false, Reactions.minimumTicks);
        assert !Reactions.automaticRecovery(true, false, Reactions.maximumTicks + 1, Reactions.stillTicks + 1);
        assert !Reactions.automaticRecovery(false, true, Reactions.maximumTicks + 1, Reactions.stillTicks + 1);
        assert !Reactions.automaticRecovery(false, false, Reactions.minimumTicks - 1, Reactions.stillTicks);
        assert !Reactions.automaticRecovery(false, false, Reactions.minimumTicks, Reactions.stillTicks - 1);
        assert Reactions.automaticRecovery(false, false, Reactions.minimumTicks, Reactions.stillTicks);
        assert Reactions.automaticRecovery(false, false, Reactions.maximumTicks, 0);
        System.out.println(
            "Reactions: snapshot/action codec, invalid states, manual/lethal and automatic recovery checks passed");
    }
}
