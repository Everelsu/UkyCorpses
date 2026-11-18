package com.uky.corpses.stains;

import com.uky.corpses.stains.client.Stain;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class SurfaceBloodCheck {

    public static void main(String[] args) {
        net.minecraft.util.Vec3 point = HitResolver.entry(
            net.minecraft.util.AxisAlignedBB.getBoundingBox(-.3, 0, -.3, .3, 1.8, .3),
            net.minecraft.util.Vec3.createVectorHelper(0, 1, -4),
            net.minecraft.util.Vec3.createVectorHelper(0, 0, 1),
            net.minecraft.util.Vec3.createVectorHelper(0, .9, 0));
        assert Math.abs(point.zCoord + .32) < .00001 && point.yCoord == 1 : "source hit resolved onto the exit face";
        assert DamageCategory.of(net.minecraft.util.DamageSource.magic) == DamageCategory.NONE;
        assert DamageCategory.of(net.minecraft.util.DamageSource.fall) == DamageCategory.FALL;
        assert !com.uky.corpses.stains.client.BloodTypes.named("Skeleton").bleeds;
        assert com.uky.corpses.stains.client.BloodTypes.named("PigZombie").name.equals("rotten");
        assert com.uky.corpses.stains.client.BloodTypes.named("Spider").name.equals("ichor");
        assert com.uky.corpses.stains.client.BloodTypes.named("Cow").color == 0x8E1111;
        assert SurfaceBloodClientConfig.MAX_DROPLETS.get() == 2048;
        assert SurfaceBloodClientConfig.MAX_STAINS.get() == 12000;
        assert SurfaceBloodClientConfig.PALETTE.get() == SurfaceBloodClientConfig.Palette.NATURAL;
        assert Stain.blend(0xff0000, 0x0000ff, 0) == 0xff0000;
        assert Stain.blend(0xff0000, 0x0000ff, 1) == 0x0000ff;
        Stain s = new Stain();
        s.born = 100;
        s.opacity = 1;
        assert s.alpha(100) == 1 && s.dry(100) == 0;
        assert s.dry(100 + SurfaceBloodClientConfig.DRY_TIME.get() * 20L) == 1;
        s.wash = 1;
        assert s.alpha(100) == 0;
        SurfaceBlood.Hit h = new SurfaceBlood.Hit();
        h.entity = 3;
        h.x = 1;
        h.y = 2;
        h.z = 3;
        h.dx = 1;
        h.amount = 4;
        h.fatal = true;
        h.melee = true;
        h.seed = 741;
        h.speed = 5;
        ByteBuf b = Unpooled.buffer();
        try {
            h.toBytes(b);
            SurfaceBlood.Hit read = new SurfaceBlood.Hit();
            read.fromBytes(b);
            assert read.valid && read.entity == 3
                && read.amount == 4
                && read.fatal
                && read.melee
                && read.seed == 741
                && read.speed == 5;
            b.clear();
            h.dx = Float.NaN;
            h.toBytes(b);
            read = new SurfaceBlood.Hit();
            read.fromBytes(b);
            assert !read.valid;
        } finally {
            b.release();
        }
        float[] face = com.uky.corpses.stains.client.SkinBlood
            .uv(com.uky.corpses.corpse.Skeleton.BODY, 4, new double[] { 0, 6, 0 });
        assert face[0] == 24 && face[1] == 26
            && face[2] == 20
            && face[3] == 20
            && face[4] == 28
            && face[5] == 32 : "front torso UV";
        for (int part = 0; part < 6; part++) for (int side = 0; side < 6; side++) {
            float[] lo = com.uky.corpses.corpse.Skeleton.min(part), hi = com.uky.corpses.corpse.Skeleton.max(part);
            double[] center = { (lo[0] + hi[0]) * .5, (lo[1] + hi[1]) * .5, (lo[2] + hi[2]) * .5 };
            float[] uv = com.uky.corpses.stains.client.SkinBlood.uv(part, side, center);
            assert uv[0] >= uv[2] && uv[0] <= uv[4] && uv[1] >= uv[3] && uv[1] <= uv[5] : "wound escaped part face";
        }
        assert com.uky.corpses.stains.client.Stumps.pressure(140, true, false) > .36F
            && com.uky.corpses.stains.client.Stumps.pressure(140, true, false) < .38F;
        assert com.uky.corpses.stains.client.Stumps.pressure(140, false, false) < .01F;
        assert com.uky.corpses.stains.client.BloodVignette.opacity(0, 20, 20, 0) == 0;
        assert com.uky.corpses.stains.client.BloodVignette.opacity(1, 1, 20, 0) <= .9F;
        System.out.println(
            "SurfaceBlood: config/codec, drying/washing, all six-part face UVs, stump decay, item coat and vignette bounds passed");
    }
}
