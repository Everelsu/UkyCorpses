package com.uky.corpses.stains.client;

import java.util.Random;

import com.uky.corpses.stains.SurfaceBloodClientConfig;

public final class SourceGunshotMist {

    public static final float SLOW = 4.0F;
    public static final float FAST = 11.0F;
    private static final int MAX_PARCELS = 160;

    private SourceGunshotMist() {}

    public static float strength(float speed, float damage) {
        float t = WoundMath.clamp((speed - 4.0F) / 7.0F, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t) * WoundMath.sqrt(Math.max(damage, 0.0F));
    }

    public static boolean exits(float speed, float damage, double depth) {
        return speed >= 4.0F && (double) (speed * WoundMath.sqrt(Math.max(damage, 0.0F)) * 0.12F) > depth;
    }

    public static void emit(SourceDrops droplets, BloodVec entry, BloodVec exit, BloodVec dir, float speed,
        float damage, BloodTypes.Type blood, Random random) {
        float strength = strength(speed, damage) * ((Double) SurfaceBloodClientConfig.AMOUNT.get()).floatValue()
            * blood.amount
            * SurfaceBloodClientConfig.preset().spray;
        if (!(strength <= 0.0F) && (Boolean) SurfaceBloodClientConfig.MIST.get()) {
            BloodVec way = dir.lengthSqr() > 1.0E-6 ? dir.normalize() : new BloodVec(0.0, 0.0, 1.0);
            float push = WoundMath.clamp(speed / 11.0F, 0.6F, 1.6F);
            int back = WoundMath.clamp(Math.round(10.0F * strength), 0, 53);
            throwCone(
                droplets,
                entry,
                way.scale(-1.0),
                0.9F,
                back,
                70.0F,
                0.4F * push,
                0.8F * push,
                way.scale(-0.02),
                blood,
                random);
            if (exit != null) {
                int forward = WoundMath.clamp(Math.round(26.0F * strength), 0, 160 - back);
                throwCone(
                    droplets,
                    exit,
                    way,
                    0.45F,
                    forward,
                    110.0F,
                    0.8F * push,
                    1.8F * push,
                    way.scale(0.04 + 0.03 * (double) push),
                    blood,
                    random);
            }
        }
    }

    private static void throwCone(SourceDrops droplets, BloodVec from, BloodVec axis, float halfAngle, int count,
        float microns, float slow, float fast, BloodVec air, BloodTypes.Type blood, Random random) {
        for (int i = 0; i < count; i++) {
            BloodVec d = SourceSprayEmitter.cone(random, axis, halfAngle * WoundMath.sqrt(random.nextFloat()));
            float size = WoundMath.clamp(microns * (float) Math.exp(random.nextGaussian() * 0.6), 15.0F, 400.0F);
            float v = WoundMath.lerp(random.nextFloat(), slow, fast);
            float eddy = 0.7F + random.nextFloat() * 0.6F;
            boolean marks = size >= 50.0F;
            droplets.mist(
                from.x,
                from.y,
                from.z,
                (float) d.x * v,
                (float) d.y * v,
                (float) d.z * v,
                size,
                (float) air.x * eddy,
                (float) air.y * eddy,
                (float) air.z * eddy,
                blood.color,
                blood.dried,
                marks,
                0.85F + random.nextFloat() * 0.3F,
                20 + random.nextInt(20));
        }
    }
}
