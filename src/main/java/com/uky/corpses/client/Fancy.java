package com.uky.corpses.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * Text that moves letter by letter, after NoMusicPlayer's subtitles: the letters pop in one after another on a spring,
 * tremble (held for a moment each, so it reads as shaking, not as buzzing), ride a slow wave, and split into a red and
 * a cyan copy. All of it scales to zero: with everything at 0 it's plain text.
 */
public final class Fancy {

    /** Seconds between one letter popping in and the next; how long a pop takes. */
    private static final float STAGGER = 0.035F, POP = 0.32F;
    /** Times a second the tremble picks a new offset. */
    private static final float SHAKE_RATE = 24;

    public float age, shake, wave, split;
    public int seed;
    /** Off in the world, where a shadow lies in the same plane as its letter and flickers. */
    public boolean shadow = true;

    /** age: seconds shown (letters pop in from 0); shake, wave, split: pixels. */
    public Fancy set(float age, float shake, float wave, float split) {
        this.age = age;
        this.shake = shake;
        this.wave = wave;
        this.split = split;
        return this;
    }

    /** s centred on x, its top at y. */
    public void centred(FontRenderer f, String s, float x, float y, int rgb, float alpha) {
        draw(f, s, x - f.getStringWidth(s) / 2F, y, rgb, alpha);
    }

    public void draw(FontRenderer f, String s, float x, float y, int rgb, float alpha) {
        int held = (int) (age * SHAKE_RATE);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) { // a formatting code: not drawn, the line keeps its colour
                i++;
                continue;
            }
            int w = f.getCharWidth(c);
            float local = (age - i * STAGGER) / POP;
            if (local <= 0) break; // the rest hasn't come yet
            float a = alpha * Math.min(1, local * 3);
            float pop = local < 1 ? 1 + 0.6F * (float) (Math.sin(local * Math.PI * 2.2) * Math.exp(-local * 3.2)) : 1;
            float dx = shake * hash(held, i, seed),
                dy = shake * hash(held, i, seed + 7) + wave * (float) Math.sin(age * 4 + i * 0.5);
            if (c != ' ') {
                GL11.glPushMatrix();
                GL11.glTranslatef(x + dx + w / 2F, y + dy + 4, 0);
                GL11.glScalef(pop, pop, 1);
                GL11.glTranslatef(-w / 2F, -4, 0);
                String one = String.valueOf(c);
                if (split > 0.05F) {
                    text(f, one, -split, 0, 0xFF4438, 0.45F * a, false);
                    text(f, one, split, 0, 0x5FD2FF, 0.45F * a, false);
                }
                text(f, one, 0, 0, rgb, a, shadow);
                GL11.glPopMatrix();
            }
            x += w;
        }
    }

    /** -1..1, the same for the same (step, letter, salt). */
    private static float hash(int step, int letter, int salt) {
        int h = step * 73856093 ^ letter * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 32767.5F - 1;
    }

    public static void text(FontRenderer f, String s, float x, float y, int rgb, float alpha, boolean shadow) {
        int a = (int) (Math.min(1, alpha) * 255);
        if (a <= 4 || s.isEmpty()) return; // below 4 the font reads alpha as "none given" and draws opaque
        GL11.glPushMatrix();
        GL11.glTranslatef(x - (int) x, y - (int) y, 0);
        f.drawString(s, (int) x, (int) y, a << 24 | rgb & 0xFFFFFF, shadow);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_BLEND);
    }

    /** A rectangle in floats (whole pixels step visibly at large GUI scales), 0xRRGGBB and alpha. */
    public static void rect(float x0, float y0, float x1, float y1, int rgb, float alpha) {
        if (alpha <= 0 || x1 <= x0) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, Math.min(1, alpha));
        t.addVertex(x0, y1, 0);
        t.addVertex(x1, y1, 0);
        t.addVertex(x1, y0, 0);
        t.addVertex(x0, y0, 0);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** rgb fading from alpha a0 on the left to a1 on the right. */
    public static void gradH(float x0, float y0, float x1, float y1, int rgb, float a0, float a1) {
        if (x1 <= x0) return;
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(r, g, b, a0);
        t.addVertex(x0, y1, 0);
        t.setColorRGBA_F(r, g, b, a1);
        t.addVertex(x1, y1, 0);
        t.addVertex(x1, y0, 0);
        t.setColorRGBA_F(r, g, b, a0);
        t.addVertex(x0, y0, 0);
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** A one-pixel outline just inside x0..x1, y0..y1. */
    public static void frame(float x0, float y0, float x1, float y1, int rgb, float alpha) {
        rect(x0, y0, x1, y0 + 1, rgb, alpha);
        rect(x0, y1 - 1, x1, y1, rgb, alpha);
        rect(x0, y0 + 1, x0 + 1, y1 - 1, rgb, alpha);
        rect(x1 - 1, y0 + 1, x1, y1 - 1, rgb, alpha);
    }

    /** The screen's edges in rgb, strongest at the very edge (UkyUI's vignette). */
    public static void edges(float w, float h, int rgb, float strength) {
        int steps = 18;
        float band = Math.max(w, h) * 0.035F / steps * 3;
        for (int i = 0; i < steps; i++) {
            float r = 1 - (float) i / steps, o = i * band, a = r * r * strength;
            rect(0, o, w, o + band, rgb, a);
            rect(0, h - o - band, w, h - o, rgb, a);
            rect(o, o + band, o + band, h - o - band, rgb, a);
            rect(w - o - band, o + band, w - o, h - o - band, rgb, a);
        }
    }
}
