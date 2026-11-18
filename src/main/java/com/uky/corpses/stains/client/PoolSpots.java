package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraftforge.common.util.ForgeDirection;

import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

final class PoolSpots {

    private final double[] x = new double[64], y = new double[64], z = new double[64];
    private final float[] area = new float[64];
    private final long[] time = new long[64];
    private final List<Stain> puddles = new ArrayList<>();
    private int next;

    PoolSpots() {
        clear();
    }

    boolean catchDrop(Stain hit, float size, boolean drip, long now, Random random) {
        if (!SurfaceBloodClientConfig.POOLS.get() || hit.face != ForgeDirection.UP) return false;
        Stain best = null;
        double closest = Double.MAX_VALUE;
        puddles.removeIf(puddle -> puddle.alpha(now) <= 0);
        for (Stain puddle : puddles) {
            if (Math.abs(puddle.y - hit.y) > .02 || puddle.dry(now) >= 1) continue;
            double dx = puddle.x - hit.x, dz = puddle.z - hit.z, distance = dx * dx + dz * dz;
            double reach = Math.sqrt(puddle.poolArea / Math.PI) + .0625;
            if (distance < reach * reach && distance < closest) {
                best = puddle;
                closest = distance;
            }
        }
        float contribution = dropArea(size);
        if (best != null) {
            feed(best, contribution, now);
            return true;
        }
        if (!drip || size < .02F) return false;
        int count = 0;
        double cx = hit.x, cz = hit.z;
        for (int i = 0; i < 64; i++) if (now - time[i] <= 400 && Math.abs(y[i] - hit.y) <= .02) {
            double dx = x[i] - hit.x, dz = z[i] - hit.z;
            if (dx * dx + dz * dz <= .0196) {
                count++;
                cx += x[i];
                cz += z[i];
                contribution += area[i];
            }
        }
        if (count < 2) {
            x[next] = hit.x;
            y[next] = hit.y;
            z[next] = hit.z;
            area[next] = contribution;
            time[next] = now;
            next = (next + 1) % 64;
            return false;
        }
        for (int i = 0; i < 64; i++) {
            double dx = x[i] - hit.x, dz = z[i] - hit.z;
            if (Math.abs(y[i] - hit.y) <= .02 && dx * dx + dz * dz <= .0196) time[i] = Long.MIN_VALUE / 2;
        }
        StainSprites.Sprite sprite = StainSprites.choose("pool", random);
        boolean pixel = SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL;
        float scale = pixel ? 1 / 16F : 1 / 32F,
            angle = pixel ? random.nextInt(4) * (float) (Math.PI / 2) : random.nextFloat() * (float) (Math.PI * 2);
        Stain pool = StainPlacement.place(
            ForgeDirection.UP,
            cx / (count + 1),
            hit.y,
            cz / (count + 1),
            angle,
            sprite,
            scale,
            scale,
            sprite.texelWidth / 2,
            sprite.texelHeight / 2,
            pixel);
        pool.family = "pool";
        pool.pooled = true;
        pool.surface = hit.surface;
        pool.opacity = .96F;
        pool.color = hit.color;
        pool.dried = hit.dried;
        pool.born = now;
        pool.spread(0, 0, now);
        feed(pool, contribution * pool.surface.pool(), now);
        remember(pool);
        SurfaceBloodClient.add(pool);
        return true;
    }

    static float dropArea(float size) {
        float radius = size * 1.6F;
        return (float) Math.PI * radius * radius * 3;
    }

    private static void feed(Stain pool, float area, long now) {
        pool.poolArea += area * SurfaceBloodClientConfig.POOL_SIZE.get()
            .floatValue();
        float target = Math.min(1, (float) Math.sqrt(pool.poolArea / Math.PI) * 1.1F);
        if (target > pool.level(now) + .004F) pool.spread(target, -50, now);
    }

    void remember(Stain stain) {
        if (puddles.size() >= 128) puddles.remove(0);
        puddles.add(stain);
    }

    void clear() {
        puddles.clear();
        java.util.Arrays.fill(time, Long.MIN_VALUE / 2);
        next = 0;
    }
}
