package com.uky.corpses.stains.client;

import java.util.Arrays;
import java.util.Random;

import com.uky.corpses.stains.DamageCategory;
import com.uky.corpses.stains.SurfaceBloodClientConfig;

/** Original per-texel slash, puncture, exit, bruise, shrapnel, spatter and delayed runoff painter. */
final class WoundCanvas {

    final int width, height, copies;
    private int limit = 2000;
    private Pigment blood;
    private int[] index = new int[64], fresh = new int[64], dried = new int[64];
    private long[] appear = new long[64], born = new long[64];
    private float[] shade = new float[64], dryness = new float[64];
    private final boolean[] taken;
    int count;
    private WoundMask mask;
    private Locator locator;
    private boolean armour;
    private long now;
    private long nextAppear = Long.MAX_VALUE;
    private Random random;

    WoundCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.copies = copiesFor(width, height);
        taken = new boolean[width * height * copies];
    }

    static int copiesFor(int width, int height) {
        return width * height <= 4096 ? 8 : (width * height <= 16384 ? 4 : 2);
    }

    void add(int x, int y, int copy, long appearAt, float tone, long bornAt) {
        int fine = this.mask == null ? 1 : this.mask.fine();
        if (fine <= 1) {
            this.addTexel(x, y, copy, appearAt, tone, bornAt);
        } else {
            for (int dy = 0; dy < fine; dy++) {
                for (int dx = 0; dx < fine; dx++) {
                    int tx = x * fine + dx;
                    int ty = y * fine + dy;
                    if (this.mask.base()
                        .solid(tx, ty)) {
                        this.addTexel(tx, ty, copy, appearAt, tone, bornAt);
                    }
                }
            }
        }
    }

    private void addTexel(int x, int y, int copy, long appearAt, float tone, long bornAt) {
        if (x >= 0 && y >= 0
            && x < this.width
            && y < this.height
            && copy >= 0
            && copy < this.copies
            && this.count < this.limit) {
            int i = (copy * this.height + y) * this.width + x;
            if (this.taken[i]) {
                int at = this.find(i);
                if (at >= 0 && tone < this.shade[at]) {
                    this.shade[at] = tone;
                }
            } else {
                this.taken[i] = true;
                if (this.count == this.index.length) {
                    int grown = this.count * 2;
                    this.index = Arrays.copyOf(this.index, grown);
                    this.appear = Arrays.copyOf(this.appear, grown);
                    this.born = Arrays.copyOf(this.born, grown);
                    this.shade = Arrays.copyOf(this.shade, grown);
                    this.dryness = Arrays.copyOf(this.dryness, grown);
                    this.fresh = Arrays.copyOf(this.fresh, grown);
                    this.dried = Arrays.copyOf(this.dried, grown);
                }

                this.index[this.count] = i;
                this.fresh[this.count] = this.blood.color;
                this.dried[this.count] = this.blood.dried;
                this.appear[this.count] = appearAt;
                this.born[this.count] = bornAt;
                this.shade[this.count] = tone;
                this.count++;
                this.nextAppear = Math.min(this.nextAppear, appearAt);
            }
        }
    }

    static long spot(int x, int y, int copy) {
        return (long) copy << 40 | (long) y << 20 | (long) x;
    }

    void wound(WoundMask mask, WoundCanvas.Locator locator, int tx, int ty, float radius, int dv, float damage,
        DamageCategory category, boolean exit, float sx, float sy, boolean armour, Pigment blood, long now,
        Random random) {
        this.blood = blood;
        this.lay(mask);
        this.locator = locator;
        this.armour = armour;
        this.now = now;
        this.random = random;
        switch (category) {
            case SHARP:
                this.slash(tx, ty, radius, dv, damage, sx, sy);
                break;
            case PIERCE:
                if (exit) {
                    this.exitWound(tx, ty, radius, dv, damage);
                } else {
                    this.puncture(tx, ty, radius, dv, damage);
                }
                break;
            case EXPLOSION:
                this.shrapnel(tx, ty, radius, dv, damage);
                break;
            case FALL:
                this.bruise(tx, ty, radius * 0.8F, dv, damage);
                break;
            default:
                this.bruise(tx, ty, radius, dv, damage);
        }

        this.mask = null;
        this.locator = null;
        this.random = null;
    }

    void spatter(WoundMask mask, WoundCanvas.Locator locator, int tx, int ty, int dv, float sx, float sy, float size,
        boolean armour, Pigment blood, long now, Random random) {
        this.blood = blood;
        this.lay(mask);
        this.locator = locator;
        this.armour = armour;
        this.now = now;
        this.random = random;
        int texels = size < 0.03F ? 1 : (size < 0.05F ? 2 : (size < 0.08F ? 3 : 4));
        float length = WoundMath.sqrt(sx * sx + sy * sy);
        boolean glancing = length > 0.35F;
        if (length > 1.0E-4F) {
            sx /= length;
            sy /= length;
        }

        float tone = random.nextFloat() < 0.3F ? 0.86F : 1.0F;
        this.paint(tx, ty, now, tone);

        for (int k = 1; k < texels; k++) {
            if (glancing) {
                this.paint(
                    Math.round((float) tx + sx * (float) k),
                    Math.round((float) ty + sy * (float) k),
                    now + (long) k,
                    k == texels - 1 ? 0.92F : tone);
            } else {
                int[] n = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }[random.nextInt(4)];
                this.paint(tx + n[0], ty + n[1], now, 0.92F);
            }
        }

        if (texels >= 3 && random.nextFloat() < 0.5F) {
            this.paint(tx + random.nextInt(5) - 2, ty + random.nextInt(5) - 2, now + 1L, 0.95F);
        }

        if (texels >= 3 && dv != 0) {
            this.drip(tx, ty + dv, dv, texels - 1 + random.nextInt(3), now + 10L);
        }

        this.mask = null;
        this.locator = null;
        this.random = null;
    }

    private void lay(WoundMask mask) {
        this.mask = mask;
        this.limit = Math.max(this.limit, Math.min(2000 * mask.fine() * mask.fine(), 8000));
    }

    private void slash(int tx, int ty, float radius, int dv, float damage, float sx, float sy) {
        float length = WoundMath.sqrt(sx * sx + sy * sy);
        if (length < 1.0E-4F) {
            sx = 1.0F;
            sy = 0.0F;
        } else {
            sx /= length;
            sy /= length;
        }

        int half = WoundMath.clamp(Math.round(radius + 0.5F + this.random.nextFloat()), 2, 5);
        boolean wide = damage >= 7.0F;
        int below = dv != 0 ? dv : (this.random.nextBoolean() ? 1 : -1);
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;

        for (float k = (float) (-half); k <= (float) half; k += 0.5F) {
            int x = Math.round((float) tx + sx * k);
            int y = Math.round((float) ty + sy * k);
            if (x != lastX || y != lastY) {
                lastX = x;
                lastY = y;
                long at = this.now + (long) ((k + (float) half) * 0.4F);
                boolean end = Math.abs(k) > (float) half - 1.0F;
                this.paint(x, y, at, this.tone(end ? 0.75F : 0.5F));
                if (wide && !end) {
                    this.paint(x, y - below, at, this.tone(0.62F));
                }

                if (this.random.nextFloat() < 0.75F) {
                    this.paint(
                        x,
                        y + below,
                        at + 3L + (long) this.random.nextInt(4),
                        this.random.nextFloat() < 0.25F ? 0.82F : 1.0F);
                }

                if (Math.abs(k) < (float) half * 0.5F && this.random.nextFloat() < 0.4F) {
                    this.paint(x, y + 2 * below, at + 6L + (long) this.random.nextInt(5), 0.92F);
                }
            }
        }

        this.brighten(Math.round((float) tx - sx), Math.round((float) ty - sy) + below);
        if (dv != 0) {
            int drips = damage >= 8.0F ? 3 : (damage >= 4.0F ? 2 : 1);

            for (int d = 0; d < drips; d++) {
                float kx = (this.random.nextFloat() * 2.0F - 1.0F) * (float) half * 0.8F;
                int x = Math.round((float) tx + sx * kx);
                int y = Math.round((float) ty + sy * kx) + 2 * dv;
                this.drip(
                    x,
                    y,
                    dv,
                    WoundMath.clamp(Math.round(2.0F + damage * 0.4F + this.random.nextFloat() * 3.0F), 2, 10),
                    this.now + 8L);
            }
        }
    }

    private void puncture(int tx, int ty, float radius, int dv, float damage) {
        this.paint(tx, ty, this.now, this.tone(0.4F));

        for (int[] n : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            this.paint(tx + n[0], ty + n[1], this.now + 1L, this.tone(0.78F));
        }

        for (int[] n : new int[][] { { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } }) {
            if (this.random.nextFloat() < 0.5F) {
                this.paint(tx + n[0], ty + n[1], this.now + 2L, 1.0F);
            }
        }

        if (radius >= 2.2F) {
            this.ring(tx, ty, 2.0F, 0.4F, this.now + 3L);
        }

        if (dv != 0) {
            this.drip(
                tx,
                ty + dv * (radius >= 2.2F ? 3 : 2),
                dv,
                WoundMath.clamp(Math.round(4.0F + damage * 0.4F + this.random.nextFloat() * 3.0F), 4, 11),
                this.now + 5L);
        }
    }

    private void exitWound(int tx, int ty, float radius, int dv, float damage) {
        float r = radius * 1.2F + 0.5F;
        int reach = (int) Math.ceil((double) (r + 1.5F));

        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                float distance = WoundMath.sqrt((float) (dx * dx + dy * dy)) + (this.random.nextFloat() - 0.5F) * 1.6F;
                if (!(distance > r)) {
                    float tone = distance < 1.1F ? 0.42F
                        : (distance < r * 0.6F ? 0.7F : (this.random.nextFloat() < 0.3F ? 0.85F : 1.0F));
                    this.paint(tx + dx, ty + dy, this.now + (long) (distance * 2.0F), this.tone(tone));
                }
            }
        }

        this.flecks(tx, ty, r, 4 + this.random.nextInt(3));
        if (dv != 0) {
            for (int d = 0; d < 2; d++) {
                int x = tx + this.random.nextInt(3) - 1;
                this.drip(
                    x,
                    ty + dv * (int) Math.ceil((double) r),
                    dv,
                    WoundMath.clamp(Math.round(3.0F + damage * 0.4F + this.random.nextFloat() * 3.0F), 3, 10),
                    this.now + 6L);
            }
        }
    }

    private void bruise(int tx, int ty, float radius, int dv, float damage) {
        float r = radius * 1.15F;
        int reach = (int) Math.ceil((double) (r + 1.2F));

        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                float distance = WoundMath.sqrt((float) (dx * dx + dy * dy)) + (this.random.nextFloat() - 0.5F) * 1.2F;
                if (!(distance > r) && !(this.random.nextFloat() < 0.25F)) {
                    float roll = this.random.nextFloat();
                    float tone = roll < 0.1F ? 0.58F : (roll < 0.75F ? 0.72F : 0.86F);
                    this.paint(
                        tx + dx,
                        ty + dy,
                        this.now + (long) (distance * 4.0F) + (long) this.random.nextInt(3),
                        this.tone(tone));
                }
            }
        }

        this.flecks(tx, ty, r, this.random.nextInt(2));
        if (damage >= 6.0F && dv != 0) {
            this.drip(
                tx,
                ty + dv * (int) Math.ceil((double) (r * 0.7F)),
                dv,
                2 + this.random.nextInt(3),
                this.now + 8L);
        }
    }

    private void shrapnel(int tx, int ty, float radius, int dv, float damage) {
        int pieces = WoundMath.clamp(3 + (int) (damage / 3.0F), 3, 7);
        float spread = radius * 2.5F + 2.0F;

        for (int p = 0; p < pieces; p++) {
            float a = this.random.nextFloat() * (float) (Math.PI * 2);
            float d = p == 0 ? 0.0F : spread * WoundMath.sqrt(this.random.nextFloat());
            int x = tx + Math.round(WoundMath.cos(a) * d);
            int y = ty + Math.round(WoundMath.sin(a) * d);
            long at = this.now + (long) this.random.nextInt(3);
            this.paint(x, y, at, this.tone(0.5F));

            for (int[] n : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                if (this.random.nextFloat() < 0.7F) {
                    this.paint(x + n[0], y + n[1], at + 1L, this.random.nextFloat() < 0.3F ? 0.82F : 1.0F);
                }
            }

            if (p < 2 && dv != 0) {
                this.drip(x, y + 2 * dv, dv, 2 + this.random.nextInt(4), at + 6L);
            }
        }

        this.flecks(tx, ty, spread, 3);
    }

    private void ring(int tx, int ty, float r, float density, long at) {
        int reach = (int) Math.ceil((double) r);

        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                float distance = WoundMath.sqrt((float) (dx * dx + dy * dy));
                if (distance > r - 0.5F && distance <= r + 0.5F && this.random.nextFloat() < density) {
                    this.paint(tx + dx, ty + dy, at, 1.0F);
                }
            }
        }
    }

    private void flecks(int tx, int ty, float r, int count) {
        for (int k = 0; k < count; k++) {
            float a = this.random.nextFloat() * (float) (Math.PI * 2);
            float d = r + 1.2F + this.random.nextFloat() * 1.6F;
            this.paint(
                tx + Math.round(WoundMath.cos(a) * d),
                ty + Math.round(WoundMath.sin(a) * d),
                this.now + 1L,
                0.9F);
        }
    }

    private void drip(int x, int y, int dv, int length, long at) {
        if ((Boolean) SurfaceBloodClientConfig.WOUND_DRIPS.get() && this.blood.flows) {
            for (int step = 0; step < length; step++) {
                long spot = this.dripSpot(x, y);
                if (spot < 0L) {
                    break;
                }

                this.add(
                    spotX(spot),
                    spotY(spot),
                    this.spotCopy(spot),
                    at,
                    step == length - 1 ? 0.7F : 0.92F,
                    this.now);
                at += (long) (4 + this.random.nextInt(4));
                y += dv;
                if (this.random.nextFloat() < 0.12F) {
                    int nx = x + (this.random.nextBoolean() ? 1 : -1);
                    if (this.dripSpot(nx, y) >= 0L) {
                        x = nx;
                    }
                }
            }
        }
    }

    private long dripSpot(int x, int y) {
        long spot = this.locator.locate(x, y, true);
        if (spot < 0L) {
            return -1L;
        } else {
            int sx = spotX(spot);
            int sy = spotY(spot);
            return sx < this.width && sy < this.height && this.mask.solid(sx, sy) ? spot : -1L;
        }
    }

    private float tone(float tone) {
        return this.armour ? Math.max(tone, 0.86F) : tone;
    }

    private void paint(int x, int y, long at, float tone) {
        long spot = this.paintable(x, y);
        if (spot >= 0L) {
            this.add(spotX(spot), spotY(spot), this.spotCopy(spot), at, tone, this.now);
        }
    }

    private void brighten(int x, int y) {
        long spot = this.paintable(x, y);
        if (spot >= 0L) {
            int sx = spotX(spot);
            int sy = spotY(spot);
            int c = this.spotCopy(spot);
            int fine = this.mask.fine();

            for (int dy = 0; dy < fine; dy++) {
                for (int dx = 0; dx < fine; dx++) {
                    int tx = sx * fine + dx;
                    int ty = sy * fine + dy;
                    if (fine <= 1 || this.mask.base()
                        .solid(tx, ty)) {
                        int i = this.find((c * this.height + ty) * this.width + tx);
                        if (i >= 0) {
                            this.shade[i] = 1.3F;
                        } else {
                            this.addTexel(tx, ty, c, this.now + 4L, 1.3F, this.now);
                        }
                    }
                }
            }
        }
    }

    private long paintable(int x, int y) {
        long spot = this.locator.locate(x, y, false);
        if (spot < 0L) {
            return -1L;
        } else {
            int sx = spotX(spot);
            int sy = spotY(spot);
            return sx < this.width && sy < this.height && this.mask.solid(sx, sy) ? spot : -1L;
        }
    }

    private static int spotX(long spot) {
        return (int) (spot & 1048575L);
    }

    private static int spotY(long spot) {
        return (int) (spot >> 20 & 1048575L);
    }

    private int spotCopy(long spot) {
        return Math.min((int) (spot >> 40), this.copies - 1);
    }

    private int find(int texel) {
        for (int i = 0; i < this.count; i++) {
            if (this.index[i] == texel) {
                return i;
            }
        }

        return -1;
    }

    int[] pixels(long now, long dryTicks, float fade) {
        int[] image = new int[width * height * copies];
        int shown = (int) (count * (1 - WoundMath.clamp(fade, 0, 1)));
        for (int i = 0; i < shown; i++) if (appear[i] <= now) {
            float dry = (float) Math.pow(WoundMath.clamp((float) (now - born[i]) / Math.max(1, dryTicks), 0, 1), .6);
            dryness[i] = Math.round(dry * 11) / 11F;
            int rgb = lerp(fresh[i], dried[i], dry);
            float tone = shade[i] > 1 ? WoundMath.lerp(dry, shade[i], 1) : shade[i];
            int r = Math.min(255, (int) ((rgb >> 16 & 255) * tone)), g = Math.min(255, (int) ((rgb >> 8 & 255) * tone)),
                b = Math.min(255, (int) ((rgb & 255) * tone));
            image[index[i]] = 0xff000000 | r << 16 | g << 8 | b;
        }
        return image;
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) WoundMath.lerp(t, from >> 16 & 255, to >> 16 & 255),
            g = (int) WoundMath.lerp(t, from >> 8 & 255, to >> 8 & 255),
            b = (int) WoundMath.lerp(t, from & 255, to & 255);
        return r << 16 | g << 8 | b;
    }

    interface Locator {

        long locate(int x, int y, boolean upright);
    }

    static final class Pigment {

        final int color, dried;
        final boolean flows;

        Pigment(int color, int dried, boolean flows) {
            this.color = color;
            this.dried = dried;
            this.flows = flows;
        }
    }
}
