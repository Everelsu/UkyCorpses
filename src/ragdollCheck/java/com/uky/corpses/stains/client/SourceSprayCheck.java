package com.uky.corpses.stains.client;

import java.util.Random;

import net.minecraft.entity.passive.EntityCow;

import com.uky.corpses.stains.DamageCategory;

public final class SourceSprayCheck {

    public static void run() {
        assert com.uky.corpses.stains.SurfaceBloodClientConfig.hitScale(1, false) < .2F;
        assert com.uky.corpses.stains.SurfaceBloodClientConfig.hitScale(2, false) < .4F;
        assert com.uky.corpses.stains.SurfaceBloodClientConfig.hitScale(4, false) == 1;
        assert com.uky.corpses.stains.SurfaceBloodClientConfig.hitScale(1, true) == 1;

        for (int i = 0; i < 100; i++) {
            BloodVec cone = SourceSprayEmitter.cone(new Random(i), new BloodVec(0, 0, 1), .5F);
            assert Math.abs(cone.length() - 1) < .00001 && cone.z >= Math.cos(.5) - .00001;
        }
        BloodVec from = new BloodVec(0, 2, 0), to = new BloodVec(3, 1, -2);
        BloodVec velocity = SourceDrops.aim(from, to, .028F, 12);
        double x = from.x, y = from.y, z = from.z, vx = velocity.x, vy = velocity.y, vz = velocity.z;
        float drag = 1 - Math.min(.5F, .0022F / .028F);
        for (int i = 0; i < 12; i++) {
            vx *= drag;
            vy = (vy - .055F) * drag;
            vz *= drag;
            x += vx;
            y += vy;
            z += vz;
        }
        assert new BloodVec(x, y, z).distanceTo(to) < .00001 : "original ballistic aim missed";
        EntityCow cow = new EntityCow(null);
        cow.setPosition(0, 1, 0);
        for (DamageCategory category : DamageCategory.values()) {
            Capture first = new Capture(), second = new Capture();
            SourceSprayEmitter a = new SourceSprayEmitter(first), b = new SourceSprayEmitter(second);
            BloodTypes.Type blood = BloodTypes.named("Cow");
            a.emit(cow, new BloodVec(0, 2, -.5), new BloodVec(0, 0, 1), 8, 0, category, false, 711, blood);
            b.emit(cow, new BloodVec(0, 2, -.5), new BloodVec(0, 0, 1), 8, 0, category, false, 711, blood);
            for (int i = 0; i < 20; i++) {
                a.tick();
                b.tick();
            }
            assert first.count == second.count && first.hash == second.hash : "packet seed did not reproduce "
                + category;
            assert category == DamageCategory.NONE ? first.count == 0 : first.count > 0;
        }
        Capture weak = new Capture(), fatal = new Capture();
        SourceSprayEmitter small = new SourceSprayEmitter(weak), killing = new SourceSprayEmitter(fatal);
        BloodTypes.Type red = BloodTypes.named("Cow");
        small.emit(cow, new BloodVec(0, 2, -.5), new BloodVec(0, 0, 1), 1, 0, DamageCategory.SHARP, false, 12, red);
        killing.emit(cow, new BloodVec(0, 2, -.5), new BloodVec(0, 0, 1), 1, 0, DamageCategory.SHARP, true, 12, red);
        for (int tick = 0; tick < 20; tick++) {
            small.tick();
            killing.tick();
        }
        assert weak.count < fatal.count : "a weak nonfatal hit must emit fewer droplets than a fatal hit";
        assert SourceGunshotMist.strength(4, 20) == 0 && SourceGunshotMist.strength(11, 9) == 3;
        assert SourceGunshotMist.exits(11, 8, .5);
        SourceClouds clouds = new SourceClouds();
        clouds.add(0, 0, 0, .03F, 0x8e1111, 0, 0);
        clouds.add(.1, 0, 0, .03F, 0x8e1111, 0, 0);
        assert clouds.count() == 1 : "nearby young water clouds did not merge";
        for (int i = 1; i <= 300; i++) clouds.add(i, 0, 0, .03F, 0x8e1111, 0, 0);
        assert clouds.count() == 256 : "water clouds exceeded original limit";
        clouds.clear();
        assert clouds.count() == 0;
        System.out.println(
            "SurfaceBlood: source cone, ballistic aim, deterministic per-damage spray/streams and gunshot thresholds passed");
    }

    private static final class Capture extends SourceDrops {

        int count;
        long hash = 1;

        @Override
        void spawn(double x, double y, double z, float vx, float vy, float vz, float size, int color, int dried,
            int flags, float shade, int life) {
            assert Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
            assert Float.isFinite(vx) && Float.isFinite(vy) && Float.isFinite(vz) && size > 0 && life > 0;
            count++;
            hash = hash * 31 + Float.floatToIntBits(vx);
            hash = hash * 31 + Float.floatToIntBits(vy);
            hash = hash * 31 + Float.floatToIntBits(vz);
        }

        @Override
        void burst(double x, double y, double z, boolean big, boolean flip, int color) {}
    }

    private SourceSprayCheck() {}
}
