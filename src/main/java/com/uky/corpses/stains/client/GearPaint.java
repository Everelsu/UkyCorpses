package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;
import java.util.Random;

/** Original flat-sprite coating, with NativeImage ABGR mapped to BufferedImage ARGB. */
public final class GearPaint {

    public static BufferedImage flat(BufferedImage source, int seed, int rgb, float dry, float coverage, float wash,
        float opacity, int drops) {
        int w = source.getWidth(), h = source.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float diagonal = (float) Math.sqrt(w * w + h * h);
        Random noise = new Random(seed);
        float[] lanes = new float[w + h];
        for (int i = 0; i < lanes.length; i++)
            lanes[i] = noise.nextFloat() < .35F ? noise.nextFloat() * .22F : noise.nextFloat() * .05F;
        float best = -1;
        int tipX = -1, tipY = -1;
        float threshold = 1 - (.1F + .42F * coverage * (1 - wash));
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float roll = noise.nextFloat();
            int under = source.getRGB(x, y);
            if ((under >>> 24) == 0) continue;
            float rx = x + .5F, ry = h - y - .5F;
            float reach = (float) Math.sqrt(rx * rx + ry * ry) / diagonal;
            if (reach > best) {
                best = reach;
                tipX = x;
                tipY = y;
            }
            float depth = reach + lanes[x + y] + (roll - .5F) * .06F - threshold;
            boolean speck = depth < 0 && depth > -.18F && roll > .93F;
            if (depth >= 0 || speck) {
                float thick = speck ? .6F : clamp(depth / .12F, .4F, 1) * (roll < .3F ? .6F : 1);
                image.setRGB(x, y, tint(rgb, under, (.55F + .4F * thick) * opacity, dry, roll));
            }
        }
        if (tipX >= 0) for (int k = 1; k <= drops && tipY + k < h; k++) {
            if ((source.getRGB(tipX, tipY + k) >>> 24) != 0) continue;
            float tone = k == drops ? .85F : 1;
            int r = Math.min(255, (int) ((rgb >> 16 & 255) * tone));
            int g = Math.min(255, (int) ((rgb >> 8 & 255) * tone));
            int b = Math.min(255, (int) ((rgb & 255) * tone));
            image.setRGB(tipX, tipY + k, Math.round(opacity * 255) << 24 | r << 16 | g << 8 | b);
        }
        return image;
    }

    private static int tint(int rgb, int under, float alpha, float dry, float roll) {
        int ur = under >> 16 & 255, ug = under >> 8 & 255, ub = under & 255;
        float lum = (.299F * ur + .587F * ug + .114F * ub) / 255;
        float tone = .8F + .6F * lum + (roll - .5F) * .16F;
        float shine = lum > .6F ? .4F * (1 - dry) * (lum - .6F) / .4F : 0;
        float br = Math.min(255, (rgb >> 16 & 255) * tone + 255 * shine);
        float bg = Math.min(255, (rgb >> 8 & 255) * tone + 200 * shine);
        float bb = Math.min(255, (rgb & 255) * tone + 190 * shine);
        float a = alpha * (lum < .2F ? .6F : 1);
        return 0xff000000 | (int) (ur + a * (br - ur)) << 16
            | (int) (ug + a * (bg - ug)) << 8
            | (int) (ub + a * (bb - ub));
    }

    private static float clamp(float n, float min, float max) {
        return Math.max(min, Math.min(max, n));
    }

    private GearPaint() {}
}
