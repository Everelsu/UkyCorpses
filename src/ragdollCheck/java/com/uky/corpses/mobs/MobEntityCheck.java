package com.uky.corpses.mobs;

import java.util.Arrays;

import javax.vecmath.Vector3f;

import com.uky.corpses.mobs.physics.MobRigType;
import com.uky.corpses.mobs.physics.RagdollBodyFactory.BodyProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Real packet boundaries plus the original torso birth/motion numbers, without a game or display. */
public final class MobEntityCheck {

    public static void run() {
        com.uky.corpses.port.physics.PhysTransform box = new com.uky.corpses.port.physics.PhysTransform();
        box.origin.set(0, 0, 2);
        float[] half = { 1, .5F, .25F };
        double[] rayHit = new double[3];
        assert MobRay.part(
            new com.uky.corpses.port.physics.PhysTransform[] { box },
            half,
            new double[] { 0, 0, 0 },
            new double[] { 0, 0, 1 },
            3,
            rayHit) == 0;
        assert Math.abs(rayHit[2] - 1.75) < 1e-6;
        assert MobRay.part(
            new com.uky.corpses.port.physics.PhysTransform[] { box },
            half,
            new double[] { 2, 0, 0 },
            new double[] { 0, 0, 1 },
            3,
            rayHit) == -1;
        box.setRotation(new javax.vecmath.Quat4f(0, (float) Math.sin(Math.PI / 4), 0, (float) Math.cos(Math.PI / 4)));
        assert MobRay.part(
            new com.uky.corpses.port.physics.PhysTransform[] { box },
            half,
            new double[] { 0, 0, 0 },
            new double[] { 0, 0, 1 },
            3,
            rayHit) == 0;
        assert Math.abs(rayHit[2] - 1) < 1e-5;

        MsgMobRagdoll packet = new MsgMobRagdoll();
        packet.entity = 17;
        packet.dimension = -1;
        packet.age = 23;
        packet.generation = 12;
        packet.sequence = 2;
        packet.x = 29999999;
        packet.y = 70;
        packet.z = -29999999;
        packet.state = new float[10 * 13];
        for (int i = 0; i < 10; i++) {
            packet.state[i * 13] = i;
            packet.state[i * 13 + 6] = 1;
            packet.state[i * 13 + 7] = 2;
        }
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            assert buffer.readableBytes() == 53 + 10 * 52;
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(0, bytes);
            MsgMobRagdoll copy = new MsgMobRagdoll();
            copy.fromBytes(buffer);
            assert copy.valid && copy.entity == 17 && copy.generation == 12 && Arrays.equals(packet.state, copy.state);
            for (int size = 0; size < bytes.length; size++) {
                ByteBuf shortBuffer = Unpooled.wrappedBuffer(Arrays.copyOf(bytes, size));
                try {
                    copy = new MsgMobRagdoll();
                    copy.fromBytes(shortBuffer);
                    assert !copy.valid : "truncated mob state accepted " + size;
                } finally {
                    shortBuffer.release();
                }
            }
            bytes[52] = 65;
            copy = new MsgMobRagdoll();
            ByteBuf tooMany = Unpooled.wrappedBuffer(bytes);
            try {
                copy.fromBytes(tooMany);
                assert !copy.valid;
            } finally {
                tooMany.release();
            }
        } finally {
            buffer.release();
        }
        packet.state[6] = 0;
        buffer = Unpooled.buffer();
        try {
            boolean rejected = false;
            try {
                packet.toBytes(buffer);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assert rejected : "zero quaternion";
        } finally {
            buffer.release();
        }
        assert !MsgMobRagdoll.validAnchor(Double.NaN, 0, 0);
        assert !MsgMobRagdoll.validAnchor(30000002, 0, 0);
        assert !MsgMobRagdoll.validAnchor(0, 4097, 0);
        assert MobBirth.torsoHeight(MobRigType.HUMANOID_STANDARD, BodyProfile.DEFAULT, false, 1, 1) == 1.2F;
        assert MobBirth.torsoHeight(MobRigType.HUMANOID_STANDARD, BodyProfile.DEFAULT, true, 1, 1) == .6F;
        assert MobBirth.torsoHeight(MobRigType.QUADRUPED, BodyProfile.COW, false, 1, 1) == 1.07F;
        assert MobBirth.torsoHeight(MobRigType.QUADRUPED, BodyProfile.COW, true, 1, 1) == .54F;
        assert MobBirth.torsoHeight(MobRigType.SPIDER, BodyProfile.CAVE_SPIDER, false, 1, 1) == .39375F;
        assert MobBirth.torsoHeight(MobRigType.EQUINE, BodyProfile.DONKEY, true, 1, 1) == .63125F * .87F;
        assert MobBirth.missingParts(61) == 62 && MobBirth.missingParts(1) == 2 && MobBirth.missingParts(4) == 32;
        Vector3f velocity = MobRagdolls.inherited(new Vector3f(.1F, 0, 0), new Vector3f(.2F, 0, 0));
        assert velocity.x == 4;
        velocity = MobRagdolls.inherited(new Vector3f(.1F, 0, 0), new Vector3f(100, 0, 0));
        assert velocity.x == 2 : "teleport must not become velocity";
        Vector3f hit = MobRagdolls.killingImpulse(new Vector3f(1, 0, 0), true);
        assert hit.x == 25 && Math.abs(hit.y - 14.5F) < 1e-6;
        Vector3f kick = MobRagdolls.explosionKick(0, 0, 0, 0, new double[] { 0, 0, 0 });
        assert Math.abs(kick.length() - 35) < 1e-4 && kick.y > 0;
        System.out.println(
            "Mob entities: bounded snapshot codec, exact torso heights, missing-part mapping and death motion passed");
    }
}
