package com.uky.corpses.stains.client;

import net.minecraftforge.common.util.ForgeDirection;

import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

/** Surface-local oriented stain; age is measured against the world's tick clock. */
public final class Stain {

    public ForgeDirection face;
    public double x, y, z;
    public float length, width, angle, opacity = 1, wash;
    public int color, dried;
    public long born;
    public String family;
    public Surface surface = Surface.PLAIN;
    public StainSprites.Sprite sprite;
    public boolean pooled, print, ran, soaked;
    public float poolArea, levelFrom = -1, levelTo = -1;
    public long rampStart;
    public int rampTicks;

    public float level(double now) {
        if (levelTo < 0) return 1;
        if (rampTicks == 0) return levelTo;
        float t = (float) Math.max(0, Math.min(1, (now - rampStart) / Math.abs(rampTicks)));
        if (rampTicks > 0) t = 1 - (1 - t) * (1 - t);
        return levelFrom + (levelTo - levelFrom) * t;
    }

    public void spread(float to, int ticks, long now) {
        levelFrom = levelTo >= 0 ? level(now) : 0;
        levelTo = Math.max(0, Math.min(1, to));
        rampStart = now;
        rampTicks = ticks;
    }

    public float dry(long now) {
        return Math
            .max(0, Math.min(1, (now - born) / (float) Math.max(1, SurfaceBloodClientConfig.DRY_TIME.get() * 20)));
    }

    public float alpha(long now) {
        long life = Math.max(1, SurfaceBloodClientConfig.STAIN_LIFETIME.get() * 20L), age = Math.max(0, now - born);
        if (print && SurfaceBloodClientConfig.FOOTPRINT_FADE.get() > 0)
            life = SurfaceBloodClientConfig.FOOTPRINT_FADE.get() * 20L;
        return opacity * (1 - wash) * Math.max(0, Math.min(1, (life - age) / (float) Math.max(1, life / 3)));
    }

    public static int blend(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int blue = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | blue;
    }
}
