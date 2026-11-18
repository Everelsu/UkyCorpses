package com.uky.corpses.stains.client;

/** Legacy equivalents of the original painter's Mth operations. */
final class WoundMath {

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static float lerp(float t, float from, float to) {
        return from + t * (to - from);
    }

    static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }

    static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    static float sin(float value) {
        return net.minecraft.util.MathHelper.sin(value);
    }

    static float cos(float value) {
        return net.minecraft.util.MathHelper.cos(value);
    }

    private WoundMath() {}
}
