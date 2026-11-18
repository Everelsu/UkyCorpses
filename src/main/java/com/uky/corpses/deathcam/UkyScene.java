package com.uky.corpses.deathcam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.UkyCorpses;

/**
 * UkyUI's death scene over the death camera, when UkyUI is installed and replaces the death screen: its colours for
 * what killed you, the failing tape (scanlines, a rolling band, dropouts, grain, tracking errors), the headline
 * resolving out of the noise split into colour channels, its line for the cause, the eye closing with the heartbeat,
 * and its way out (the tape tears, the picture collapses to a bright line) and back in (its backdrop lifting off the
 * new life). The camera stays ours: the picture under all this is still the view from the body.
 *
 * Everything of UkyUI's is read by name (DeathTracker, DeathTheme, Theme, UiConfig): neither mod needs the other to
 * build. Timings and figures are GuiDeathScreen's, so the two read as one scene.
 */
final class UkyScene {

    /** Seconds: the way out; the way back in. */
    static final float EXIT = 0.42F, ENTER = 0.85F;
    /** When the headline starts resolving; when the line under it comes. */
    private static final float TITLE_AT = 0.30F, MESSAGE_AT = 1.25F;
    /** How long one glitched frame of the headline is held: tape speed, not a strobe. */
    private static final float SAMPLE = 0.10F;
    private static final char[] GLYPHS = "#%&@$/\\|<>*+=~^:;!?01".toCharArray();

    // ---- UkyUI, by name ----
    private static boolean looked;
    private static Method detect, pick, message, quality;
    private static Field bleed, accent, red, cyan, jitter, vhs, replace, intensity;
    private static Field text, dim, uiAccent, background;

    /** UkyUI is here and its death screen is on (its screens.replaceDeathScreen); read every time, it can change. */
    static boolean on() {
        if (!looked) {
            looked = true;
            try {
                Class<?> tracker = Class.forName("com.console.uky.client.death.DeathTracker"),
                    theme = Class.forName("com.console.uky.client.death.DeathTheme"),
                    look = Class.forName("com.console.uky.client.render.Theme"),
                    cfg = Class.forName("com.console.uky.config.UiConfig");
                detect = tracker.getMethod("detect");
                pick = theme.getMethod("pickMessage");
                message = theme.getMethod("message", int.class);
                bleed = theme.getField("bleed");
                accent = theme.getField("accent");
                red = theme.getField("chromaRed");
                cyan = theme.getField("chromaCyan");
                jitter = theme.getField("jitter");
                vhs = theme.getField("vhs");
                text = look.getField("text");
                dim = look.getField("textDim");
                uiAccent = look.getField("accent");
                background = look.getField("background");
                replace = cfg.getField("replaceDeathScreen");
                intensity = cfg.getField("deathIntensity");
                try {
                    quality = Class.forName("com.console.uky.config.Quality")
                        .getMethod("deathIntensity");
                } catch (ReflectiveOperationException ex) {
                    quality = null; // an older UkyUI: full strength
                }
            } catch (ReflectiveOperationException | LinkageError ex) {
                detect = null; // no UkyUI, or one without its death scene
            }
        }
        if (detect == null) return false;
        try {
            return replace.getBoolean(null);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    /** What the lines under the headline say; null for nothing. */
    static final class Lines {

        String cause, things, prompt, after;
        boolean hardcore;
        /** How far the prompt has faded in. */
        float promptIn = 1;
    }

    private final Random noise = new Random();
    /** UkyUI's DeathTheme for this death, and which of its lines. */
    private Object theme;
    private String line = "";
    private boolean told;
    private long lastFrame = System.nanoTime();
    private float delta, glitch, nextGlitch = 1.6F, sampleAge = SAMPLE, sampledShake;
    private String sampled = "", sampledFrom = "";

    UkyScene(boolean told) {
        retheme(told);
    }

    /**
     * Asks UkyUI what killed them. It reads the server's word through CorpseClient.lastDeath when there is one, and
     * guesses when there isn't: so once the word comes (told), it's asked again, as long as nothing's shown yet.
     */
    void retheme(boolean told) {
        if (this.told) return;
        this.told = told;
        try {
            theme = detect.invoke(null);
            line = (String) message.invoke(theme, pick.invoke(theme));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            UkyCorpses.LOG.warn("UkyUI's death theme not read", ex);
            theme = null;
        }
    }

    boolean settled(float t) {
        return told || t >= TITLE_AT;
    }

    private int of(Field f, int own) {
        try {
            return f.getInt(f.getDeclaringClass() == bleed.getDeclaringClass() ? theme : null) & 0xFFFFFF;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return own;
        }
    }

    private float of(Field f) {
        try {
            return f.getFloat(theme);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 1;
        }
    }

    private static float strength() {
        try {
            float k = (float) intensity.getDouble(null);
            return quality == null ? k : k * (Float) quality.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 1;
        }
    }

    /**
     * Over the camera's view (the HUD pass). t: scene seconds; land: when the body came down; exit: 0 while it runs,
     * up to 1 as the way out plays; pulse: the heartbeat, 0..1; words: false while a menu is open over it.
     */
    void draw(FontRenderer font, int w, int h, float t, float land, float exit, float pulse, Lines l, boolean words) {
        long now = System.nanoTime();
        delta = Math.min((now - lastFrame) / 1e9F, 0.1F);
        lastFrame = now;
        glitch();
        int bleedC = of(bleed, 0x2A0C0C), accentC = of(accent, 0xC8A24B), redC = of(red, 0xFF4438),
            cyanC = of(cyan, 0x5FD2FF), textC = of(text, 0xFFFFFF), dimC = of(dim, 0xA0A0A0),
            uiAccentC = of(uiAccent, 0xC8A24B), bg = of(background, 0x0A0A0A);
        float jit = of(jitter), tape = of(vhs), k = strength();

        // The picture doesn't cut out here, it's the camera: the cause's colour bleeds into it and it goes dim as the
        // body comes to rest, black that is slightly the wrong colour.
        float dimmed = smooth((t - land) / 3F);
        rect(0, 0, w, h, argb(bg, 0.10F + 0.35F * dimmed));
        float wash = 0.30F + 0.30F * clamp(1 - t / 0.8F);
        gradV(0, 0, w, h, argb(bleedC, wash), argb(bleedC, wash * 0.35F));
        float flash = clamp(1 - t / 0.35F);
        if (flash > 0.01F) rect(0, 0, w, h, argb(redC, 0.32F * flash * flash));

        tape(w, h, t, tape * k * (1 + exit * 2.5F), pulse, accentC);
        if (words) {
            float top = h * 0.30F;
            title(font, w, top, t, exit, pulse, jit, k, l.hardcore, redC, cyanC, textC, accentC);
            lines(font, w, top, t, exit, l, redC, textC, dimC, uiAccentC);
            prompt(font, w, h, t, exit, l, dimC);
        }
        // over everything: the eye closing, with the heart
        vignette(w, h, (0.72F + 0.24F * pulse) * (0.45F + 0.55F * smooth(t / 1.5F)) * (1 - exit * 0.85F));
    }

    private void glitch() {
        if (glitch > 0) {
            glitch -= delta;
            return;
        }
        nextGlitch -= delta;
        if (nextGlitch <= 0) {
            glitch = 0.06F + noise.nextFloat() * 0.10F;
            nextGlitch = 1.2F + noise.nextFloat() * 2.6F;
        }
    }

    /** Scanlines, a band rolling down, dropouts and grain; a burst of tears in a tracking error. */
    private void tape(int w, int h, float t, float amount, float pulse, int accentC) {
        if (amount <= 0.01F) return;
        for (float y = 0; y < h; y += 3) rect(0, y, w, y + 1, argb(0, 0.22F * amount));
        float travel = t * 0.22F % 1.45F, bandY = (travel - 0.2F) * h, bandH = 16 + 12 * pulse;
        if (bandY > -bandH && bandY < h) {
            int edge = argb(accentC, 0), core = argb(accentC, 0.075F * amount);
            gradV(0, bandY, w, bandY + bandH / 2, edge, core);
            gradV(0, bandY + bandH / 2, w, bandY + bandH, core, edge);
            rect(0, bandY + bandH / 2, w, bandY + bandH / 2 + 0.6F, argb(0xFFFFFF, 0.08F * amount));
        }
        int tears = (int) (3 * amount) + (glitch > 0 ? 9 : 0);
        for (int i = 0; i < tears; i++) {
            float y = noise.nextInt(Math.max(1, h)), tw = 20 + noise.nextFloat() * w * 0.45F,
                x = noise.nextFloat() * (w - tw);
            rect(x, y, x + tw, y + 1 + noise.nextInt(2), argb(0xFFFFFF, (0.04F + noise.nextFloat() * 0.08F) * amount));
        }
        int specks = (int) (w * h / 1100 * Math.min(amount, 2));
        for (int i = 0; i < specks; i++) {
            int gx = noise.nextInt(Math.max(1, w)), gy = noise.nextInt(Math.max(1, h));
            rect(gx, gy, gx + 1, gy + 1, argb(0xFFFFFF, 0.03F + noise.nextFloat() * 0.07F));
        }
    }

    /** The headline, out of the noise, in three channels; the rule under it opening from the centre. */
    private void title(FontRenderer font, int w, float cy, float t, float exit, float pulse, float jit, float k,
        boolean hardcore, int redC, int cyanC, int textC, int accentC) {
        float in = outCubic((t - TITLE_AT) / 0.85F) * (1 - exit);
        if (in <= 0.01F) return;
        String title = I18n.format(hardcore ? "deathScreen.title.hardcore" : "deathScreen.title");
        float corruption = clamp((0.55F - in) / 0.55F) * 0.30F + (glitch > 0 ? 0.12F : 0) + exit * 0.85F;
        float shake = (pulse * 0.6F + (glitch > 0 ? 1.4F : 0)) * jit * k;
        String shown = sample(title, corruption * Math.max(0.4F, k), shake);
        float scale = 2.55F - 0.45F * in, cx = w / 2F;
        float split = (2.4F * (1 - in) + 0.5F + pulse * 0.8F + exit * 14) * jit * Math.max(0.35F, k);
        GL11.glPushMatrix();
        GL11.glTranslatef(cx + sampledShake, cy, 0);
        GL11.glScalef(scale, scale, 1);
        int half = font.getStringWidth(title) / 2;
        DeathCamClient.text(font, shown, -half - split, 0, redC, 0.45F * in, false);
        DeathCamClient.text(font, shown, -half + split, 0, cyanC, 0.45F * in, false);
        DeathCamClient.text(font, shown, -half, 0, textC, in, false);
        GL11.glPopMatrix();
        float rule = half * scale * 1.15F * outCubic((t - TITLE_AT) / 1.2F), ry = cy + 12 * scale;
        int lit = argb(accentC, 0.85F * in), gone = argb(accentC, 0);
        gradH(cx - rule, ry, cx, ry + 1, gone, lit);
        gradH(cx, ry, cx + rule, ry + 1, lit, gone);
    }

    /** The headline with some of its letters swapped for junk, and its shake: rolled again every SAMPLE seconds. */
    private String sample(String s, float amount, float shake) {
        if (amount <= 0.01F) {
            sampledShake = 0;
            return sampled = sampledFrom = s;
        }
        sampleAge += delta;
        if (sampleAge < SAMPLE && s.equals(sampledFrom)) return sampled;
        sampleAge = 0;
        sampledFrom = s;
        sampledShake = shake > 0.01F ? (noise.nextFloat() - 0.5F) * shake : 0;
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++)
            if (c[i] != ' ' && noise.nextFloat() < amount) c[i] = GLYPHS[noise.nextInt(GLYPHS.length)];
        return sampled = new String(c);
    }

    /** UkyUI's line for the cause; what the server said killed them; the body; hardcore's warning. */
    private void lines(FontRenderer font, int w, float top, float t, float exit, Lines l, int redC, int textC, int dimC,
        int accentC) {
        float in = outCubic((t - MESSAGE_AT) / 0.9F) * clamp(1 - exit * 2.5F);
        if (in <= 0.01F) return;
        float y = top + 40;
        if (!line.isEmpty()) {
            centred(font, line, w, y, textC, 0.82F * in);
            y += 16;
        }
        if (l.cause != null) {
            centred(font, font.trimStringToWidth(l.cause, w - 16), w, y, dimC, 0.80F * in);
            y += 12;
        }
        if (l.things != null) {
            centred(font, font.trimStringToWidth(l.things, w - 16), w, y, accentC, 0.80F * in);
            y += 12;
        }
        if (l.hardcore) centred(font, I18n.format("deathScreen.hardcoreInfo"), w, y + 4, redC, 0.85F * in);
    }

    /** The way on, breathing rather than blinking; the countdown under it. */
    private static void prompt(FontRenderer font, int w, int h, float t, float exit, Lines l, int dimC) {
        if (l.prompt == null || l.prompt.isEmpty()) return;
        float in = l.promptIn * clamp(1 - exit * 4);
        if (in <= 0.01F) return;
        float breathe = 0.62F + 0.30F * (float) Math.sin(t * 2);
        centred(font, l.prompt, w, h * 0.78F, dimC, breathe * in);
        if (l.after != null) centred(font, l.after, w, h * 0.78F + 12, dimC, 0.5F * in);
    }

    /**
     * Over everything, the way out: the frame collapses to a bright line and goes, ending black. The respawn is asked
     * for once it's black, and the black holds until the new life is there.
     */
    static void collapse(int w, int h, float exit) {
        float c = clamp((exit - 0.5F) / 0.5F);
        if (c <= 0) return;
        float half = h * 0.5F * c, cy = h * 0.5F;
        rect(0, 0, w, half, 0xFF000000);
        rect(0, h - half, w, h, 0xFF000000);
        float thick = Math.max(0.5F, h * (1 - c) * 0.06F), bright = c < 0.85F ? c : 1 - (c - 0.85F) / 0.15F;
        rect(0, cy - thick, w, cy + thick, argb(0xFFFFFF, 0.85F * bright));
    }

    /** Over everything, the way back in: UkyUI's backdrop and vignette lifting off the world, as it enters a world. */
    static void enter(int w, int h, float progress) {
        float o = 1 - outCubic(progress);
        if (o <= 0) return;
        int bg = 0x0A0A0A;
        if (on()) try {
            bg = background.getInt(null) & 0xFFFFFF;
        } catch (ReflectiveOperationException ex) {
            // its default then
        }
        rect(0, 0, w, h, argb(bg, o));
        vignette(w, h, 0.8F * o);
    }

    /**
     * The heartbeat as UkyUI draws it, a sharp knock and a softer one close behind, on the camera's own beats (seconds
     * into the scene) so the picture and the sound agree.
     */
    static float pulse(float t, float[] beats) {
        float p = 0;
        for (int i = 0; i < beats.length && beats[i] <= t; i++) {
            float k = 1 - 0.5F * i / beats.length;
            p = Math.max(p, Math.max(thump((t - beats[i]) / 0.62F), 0.55F * thump((t - beats[i]) / 0.62F - 0.17F)) * k);
        }
        return Math.min(1, p);
    }

    private static float thump(float phase) {
        if (phase < 0 || phase > 0.22F) return 0;
        float s = (float) Math.sin(phase / 0.22F * Math.PI);
        return s * s * s;
    }

    // ---- drawing, as UkyUI's Draw ----

    private static void centred(FontRenderer font, String s, int w, float y, int rgb, float alpha) {
        DeathCamClient.text(font, s, (w - font.getStringWidth(s)) / 2F, y, rgb, alpha, true);
    }

    private static int argb(int rgb, float alpha) {
        return (int) (clamp(alpha) * 255) << 24 | rgb & 0xFFFFFF;
    }

    private static void color(int argb) {
        GL11.glColor4f((argb >> 16 & 255) / 255F, (argb >> 8 & 255) / 255F, (argb & 255) / 255F, (argb >>> 24) / 255F);
    }

    private static void begin() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void rect(float x0, float y0, float x1, float y1, int argb) {
        if (argb >>> 24 == 0) return;
        begin();
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x0, y0);
        GL11.glEnd();
        end();
    }

    private static void gradV(float x0, float y0, float x1, float y1, int top, int bottom) {
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        color(bottom);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        color(top);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x0, y0);
        GL11.glEnd();
        end();
    }

    private static void gradH(float x0, float y0, float x1, float y1, int left, int right) {
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        color(left);
        GL11.glVertex2f(x0, y1);
        color(right);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y0);
        color(left);
        GL11.glVertex2f(x0, y0);
        GL11.glEnd();
        end();
    }

    /** UkyUI's frame of black around the edges, strongest at the very edge. */
    private static void vignette(float w, float h, float strength) {
        int steps = 18;
        float band = Math.max(w, h) * 0.035F / steps * 3;
        for (int i = 0; i < steps; i++) {
            float r = 1 - (float) i / steps, o = i * band;
            int c = argb(0, r * r * strength);
            rect(0, o, w, o + band, c);
            rect(0, h - o - band, w, h - o, c);
            rect(o, 0, o + band, h, c);
            rect(w - o - band, 0, w - o, h, c);
        }
    }

    private static float clamp(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3 - 2 * x);
    }

    private static float outCubic(float x) {
        x = 1 - clamp(x);
        return 1 - x * x * x;
    }
}
