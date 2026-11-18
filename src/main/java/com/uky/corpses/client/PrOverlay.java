package com.uky.corpses.client;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;

/** Revival eyelid timing/geometry, adapted to the 1.7.10 GUI tessellator (private local port). */
final class PrOverlay {

    private static final int SLICE = 3;
    private static final float FEATHER = 34.0F;
    private static final float CORNER_LEAD = 0.55F;
    private static final float VIGNETTE = 1.0F;
    private static final float VIGNETTE_BASE = 0.84F;
    private static final float VIGNETTE_DRAIN = 0.16F;
    private static final float VIGNETTE_SPREAD_X = 0.85F;
    private static final float VIGNETTE_SPREAD_Y = 0.85F;
    private static final int VIGNETTE_COLUMNS = 40;
    private static final int VIGNETTE_ROWS = 24;
    private static final float VIGNETTE_BEAT = 0.45F;
    private static final float BEAT_TIGHTEN = 1.5F;
    private static final float BEAT_MIN_BITE = 0.02F;
    private static final float SCREEN_FADE_START = 0.45F;
    private static final float SCREEN_FADE_MAX = 0.8F;
    private static final float SCREEN_FADE_BEAT = 0.2F;
    private static final float VIGNETTE_RED_BEAT = 0.35F;
    private static final float VIGNETTE_RED_START = 0.5F;
    private static final float SCREEN_FADE_RED = 0.04F;
    private static final float SCREEN_FADE_FINAL = 0.9F;
    private static final float SATURATION_START = 0.4F;
    private static final float SATURATION_DRAINED = 0.12F;
    private static final float SATURATION_FINAL = 0.0F;
    private static final int OPAQUE = -16777216;
    private static final int CLEAR = 0;
    private static final double BLINK_INTERVAL = 120.0;
    private static final float BLINK_RATE_MIN = 0.65F;
    private static final float BLINK_RATE_MAX = 1.0F;
    private static final float BLINK_QUIET_BEFORE_SHUT_TICKS = 20.0F;
    private static final double BLINK_SPREAD = 55.0;
    private static final float BLINK_MIN_REST_TICKS = 35.0F;
    private static final float BLINK_MIN_LENGTH = 5.0F;
    private static final float BLINK_EXTRA_LENGTH = 9.0F;
    private static final float BLINK_HOLD_MIN_TICKS = 1.0F;
    private static final float BLINK_HOLD_MAX_TICKS = 40.0F;
    private static final float BLINK_MOTION_SLOW = 0.25F;
    private static final float BLINK_CLOSE_SHARE = 0.4F;
    private static final float BLINK_REOPEN_SCALE = 2.2F;
    private static final float FINAL_SHUT_TICKS = 30.0F;
    private static final float FINAL_SHUT_RAMP = 130.0F;
    private static final float[] STRUGGLE_AT = new float[] { 0.0F, 0.208F, 0.285F, 0.454F, 0.515F, 0.592F, 1.0F };
    private static final float[] STRUGGLE_LEVEL = new float[] { 0.0F, 0.6F, 0.34F, 0.9F, 0.9F, 0.6F, 1.0F };

    private PrOverlay() {}

    static void render(int width, int height, float rawClosure, float progress, float remainingTicks, float rawPulse) {
        float closure = clamp(rawClosure, 0.0F, 1.0F);
        float timer = clamp(progress, 0.0F, 1.0F);
        float beat = clamp(rawPulse, 0.0F, 1.0F) * lerp(smoothstep(timer), 0.02F, 1.0F);
        float vignette = clamp(0.84F + 0.16F * timer + beat * 0.45F, 0.0F, 1.0F);
        float fade = 0.8F * smoothstep((timer - 0.45F) / 0.55F) + beat * 0.2F * timer;
        float shutRamp = smoothstep(finalShutProgress(remainingTicks));
        fade = lerp(shutRamp, fade, 0.9F);
        float redOnset = smoothstep((timer - 0.5F) / 0.5F);
        float beatRed = 0.35F * beat * redOnset;
        float fadeRed = 0.04F * shutRamp;
        float centre = (float) height * 0.5F;
        float reach = centre + 34.0F;
        Tessellator consumer = Tessellator.instance;
        consumer.startDrawingQuads();
        drawVignette(consumer, width, height, vignette, beat, fade, beatRed, fadeRed);
        drawLids(consumer, width, height, closure, centre, reach);
        consumer.draw();
    }

    static float saturation(float progress, float remainingTicks) {
        float timer = clamp(progress, 0.0F, 1.0F);
        float onset = smoothstep((timer - 0.4F) / 0.6F);
        float saturation = lerp(onset, 1.0F, 0.12F);
        return lerp(smoothstep(finalShutProgress(remainingTicks)), saturation, 0.0F);
    }

    private static void drawVignette(Tessellator consumer, int width, int height, float strength, float beat,
        float fade, float beatRed, float fadeRed) {
        for (int column = 0; column < 40; column++) {
            float x0 = (float) (width * column) / 40.0F;
            float x1 = (float) (width * (column + 1)) / 40.0F;
            float u0 = 2.0F * (float) column / 40.0F - 1.0F;
            float u1 = 2.0F * (float) (column + 1) / 40.0F - 1.0F;

            for (int row = 0; row < 24; row++) {
                float y0 = (float) (height * row) / 24.0F;
                float y1 = (float) (height * (row + 1)) / 24.0F;
                float v0 = 2.0F * (float) row / 24.0F - 1.0F;
                float v1 = 2.0F * (float) (row + 1) / 24.0F - 1.0F;
                int topLeft = vignetteColour(u0, v0, strength, beat, fade, beatRed, fadeRed);
                int topRight = vignetteColour(u1, v0, strength, beat, fade, beatRed, fadeRed);
                int bottomRight = vignetteColour(u1, v1, strength, beat, fade, beatRed, fadeRed);
                int bottomLeft = vignetteColour(u0, v1, strength, beat, fade, beatRed, fadeRed);
                if ((alphaOf(topLeft) | alphaOf(topRight) | alphaOf(bottomRight) | alphaOf(bottomLeft)) > 2) {
                    vertex(consumer, x0, y1, bottomLeft);
                    vertex(consumer, x1, y1, bottomRight);
                    vertex(consumer, x1, y0, topRight);
                    vertex(consumer, x0, y0, topLeft);
                }
            }
        }
    }

    private static void drawLids(Tessellator consumer, int width, int height, float closure, float centre,
        float reach) {
        for (int x = 0; x < width; x += 3) {
            int right = Math.min(width, x + 3);
            float u = (float) (x + right) / (float) width - 1.0F;
            float squeeze = 1.0F - 0.55F * closure * u * u;
            float open = (1.0F - closure) * reach * squeeze - closure * 34.0F;
            float top = centre - open;
            float bottom = centre + open;
            if (top > 0.0F) {
                int lidEdge = (int) Math.min((float) height, top);
                int solid = (int) clamp(top - 34.0F, 0.0F, (float) height);
                if (solid > 0) {
                    band(consumer, x, 0, right, solid, -16777216, -16777216);
                }

                if (lidEdge > solid) {
                    band(consumer, x, solid, right, lidEdge, -16777216, 0);
                }
            }

            if (bottom < (float) height) {
                int lidEdgex = (int) clamp(bottom, 0.0F, (float) height);
                int solidx = (int) clamp(bottom + 34.0F, 0.0F, (float) height);
                if (solidx > lidEdgex) {
                    band(consumer, x, lidEdgex, right, solidx, 0, -16777216);
                }

                if (solidx < height) {
                    band(consumer, x, solidx, right, height, -16777216, -16777216);
                }
            }
        }
    }

    private static void band(Tessellator consumer, int x0, int y0, int x1, int y1, int top, int bottom) {
        vertex(consumer, x0, y0, top);
        vertex(consumer, x0, y1, bottom);
        vertex(consumer, x1, y1, bottom);
        vertex(consumer, x1, y0, top);
    }

    private static int vignetteColour(float u, float v, float strength, float beat, float fade, float beatRed,
        float fadeRed) {
        float tighten = 1.0F + beat * 1.5F;
        float radial = clamp((u * u * 0.85F + v * v * 0.85F) * tighten, 0.0F, 1.0F);
        float ring = smoothstep(radial);
        float alpha = Math.max(1.0F * strength * ring, fade);
        float red = clamp(beatRed * ring + fadeRed, 0.0F, 1.0F);
        return (int) (255.0F * Math.min(0.96F, alpha)) << 24 | (int) (255.0F * red) << 16;
    }

    private static int alphaOf(int packed) {
        return packed >>> 24;
    }

    private static void vertex(Tessellator consumer, float x, float y, int packed) {
        consumer.setColorRGBA_I(packed & 0xFFFFFF, alphaOf(packed));
        consumer.addVertex(x, y, 0);
    }

    static float closure(float progress, float remainingTicks, float durationTicks, double time) {
        float timer = clamp(progress, 0.0F, 1.0F);
        float sag = 0.03F + 0.3F * timer * timer;
        int nearest = MathHelper.floor_double(time / 120.0);
        float blink = 0.0F;

        for (int index = nearest - 1; index <= nearest + 1; index++) {
            double slot = (double) index * 120.0;
            float slotTimer = timerAt(slot, time, timer, durationTicks);
            if (!(noise(index, 668265263) >= (double) lerp(slotTimer, 0.65F, 1.0F))) {
                float droop = slotTimer * slotTimer;
                float hold = lerp(droop, 1.0F, 40.0F);
                float heaviness = 1.0F + 0.25F * droop;
                double start = slot + (noise(index, -1640531527) * 2.0 - 1.0) * blinkSpread(hold, heaviness);
                float motion = (5.0F + (float) noise(index, -2048144789) * 9.0F) * heaviness;
                float into = (float) (time - start);
                if (!(into < 0.0F) && !(into > blinkLength(motion, hold))) {
                    float remainingAtEnd = remainingTicks + into - blinkLength(motion, hold);
                    if (!(remainingAtEnd < 180.0F)) {
                        blink = Math.max(blink, blinkProfile(into, motion, hold));
                    }
                }
            }
        }

        float closure = sag + blink * (1.0F - sag);
        return Math.min(1.0F, Math.max(closure, finalShut(remainingTicks)));
    }

    private static float timerAt(double slot, double now, float timerNow, float durationTicks) {
        return durationTicks <= 0.0F ? timerNow : clamp(timerNow - (float) (now - slot) / durationTicks, 0.0F, 1.0F);
    }

    private static float finalShutProgress(float remainingTicks) {
        return clamp(1.0F - (remainingTicks - 30.0F) / 130.0F, 0.0F, 1.0F);
    }

    private static float finalShut(float remainingTicks) {
        float ramp = finalShutProgress(remainingTicks);
        if (ramp <= 0.0F) {
            return 0.0F;
        } else {
            for (int key = 1; key < STRUGGLE_AT.length; key++) {
                if (!(ramp > STRUGGLE_AT[key])) {
                    float span = STRUGGLE_AT[key] - STRUGGLE_AT[key - 1];
                    float into = span <= 0.0F ? 1.0F : (ramp - STRUGGLE_AT[key - 1]) / span;
                    return lerp(smoothstep(into), STRUGGLE_LEVEL[key - 1], STRUGGLE_LEVEL[key]);
                }
            }

            return 1.0F;
        }
    }

    private static float blinkProfile(float into, float motion, float hold) {
        float close = motion * 0.4F;
        float reopen = (motion - close) * 2.2F;
        if (into < close) {
            return smoothstep(into / close);
        } else {
            return into < close + hold ? 1.0F : smoothstep(1.0F - (into - close - hold) / reopen);
        }
    }

    private static float blinkLength(float motion, float hold) {
        float close = motion * 0.4F;
        return close + hold + (motion - close) * 2.2F;
    }

    private static double blinkSpread(float hold, float heaviness) {
        float longest = blinkLength(14.0F * heaviness, hold);
        double safe = (120.0 - (double) longest - 35.0) / 2.0;
        return Math.max(0.0, Math.min(55.0, safe));
    }

    private static double noise(int index, int salt) {
        int h = index * 374761393 + salt;
        h = (h ^ h >>> 13) * 1274126177;
        h ^= h >>> 16;
        return (double) (h & 16777215) / 1.6777215E7;
    }

    private static float smoothstep(float x) {
        float t = clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
}
