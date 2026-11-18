package com.uky.corpses.amp;

import net.minecraft.nbt.NBTTagCompound;

import io.netty.buffer.Unpooled;

public final class LivingAmputationCheck {

    public static void run() {
        NBTTagCompound data = new NBTTagCompound();
        assert !AmputationState.mark(data, Part.TORSO);
        assert AmputationState.mark(data, Part.LEFT_ARM);
        assert !AmputationState.mark(data, Part.LEFT_ARM);
        assert AmputationState.read(data) == 8;
        NBTTagCompound saved = (NBTTagCompound) data.copy();
        assert AmputationState.read(saved) == 8;
        data.setByte(AmputationState.KEY, (byte) 255);
        assert AmputationState.read(data) == 61;
        assert !AmputationState.allowedHead(10, 1, 20, .2);
        assert AmputationState.allowedHead(5, 1, 20, .2);
        assert !AmputationState.allowedHead(1, 0, 20, 0);
        assert AmputationState.allowedHead(20, 0, 20, 1);
        assert AmputationState.overkill(10, 4) == 6;
        assert AmputationState.overkill(2, 4) == 0;
        assert HitResolver.rayPart(0, 1.85, -2, 0, 0, 1) == Part.HEAD;
        assert HitResolver.rayPart(-.35, 1.17, -2, 0, 0, 1) == Part.LEFT_ARM;
        assert HitResolver.rayPart(.125, .55, -2, 0, 0, 1) == Part.RIGHT_LEG;
        assert HitResolver.rayPart(3, 1, -2, 0, 0, 1) == null;
        assert HitResolver.bucket(-.4, .7, .6) == Part.LEFT_ARM;
        assert HitResolver.bucket(0, .9, .6) == Part.HEAD;
        assert HitResolver.bucket(-.1, .3, .6) == Part.LEFT_LEG;
        for (int length = 0; length < 10; length++) {
            MsgMask packet = new MsgMask();
            packet.fromBytes(
                Unpooled.buffer(length)
                    .writeZero(length));
            assert !packet.valid;
        }
        MsgMask mask = new MsgMask();
        mask.fromBytes(
            Unpooled.buffer(10)
                .writeInt(12)
                .writeInt(-1)
                .writeByte(8)
                .writeByte(0));
        assert mask.valid && mask.mask == 8;
        mask = new MsgMask();
        mask.fromBytes(
            Unpooled.buffer(10)
                .writeInt(12)
                .writeInt(-1)
                .writeByte(2)
                .writeByte(0));
        assert !mask.valid;
        for (int seed = 0; seed < 100; seed++) {
            net.minecraft.util.Vec3 push = LivingAmputation.push(
                new java.util.Random(seed),
                Part.LEFT_ARM,
                net.minecraft.util.Vec3.createVectorHelper(1, 0, 0),
                net.minecraft.util.Vec3.createVectorHelper(0, 0, 1));
            assert push.lengthVector() <= AmputationConfig.LIMB_MAX_LAUNCH_SPEED.get() + 1e-6;
            assert push.yCoord >= 0;
        }
        System.out.println(
            "Living AMP: persistent masks, headless thresholds, ray boxes, codec boundaries and bounded source launch passed");
    }

    private LivingAmputationCheck() {}
}
