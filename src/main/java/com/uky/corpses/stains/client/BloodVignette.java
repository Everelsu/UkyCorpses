package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** SurfaceBlood 0.1.5 health flash, low-health pulse and up to six sliding edge splats. */
public final class BloodVignette {

    private static final Random random = new Random();
    private static final List<Splat> splats = new ArrayList<>();
    private static EntityPlayerSP player;
    private static float health = -1, flash, previous;
    private static int ticks;

    private static final class Splat {

        float x, y, size;
        int age, life;
        StainSprites.Sprite sprite;
    }

    public static void tick() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        previous = flash;
        ticks++;
        if (p != player || p == null || !p.isEntityAlive()) {
            player = p;
            health = -1;
            flash = previous = 0;
            splats.clear();
        }
        if (p == null || !p.isEntityAlive()) return;
        float current = p.getHealth();
        if (health >= 0 && current < health) {
            float lost = health - current;
            flash = Math.min(1, flash + lost / Math.max(1, p.getMaxHealth()) * 3);
            if (lost >= 3) for (int i = 0; i < 1 + Math.min(3, (int) (lost / 4)) && splats.size() < 6; i++) {
                Splat s = new Splat();
                s.x = random.nextBoolean() ? random.nextFloat() * .25F : .75F + random.nextFloat() * .25F;
                s.y = random.nextFloat() * .8F;
                s.size = .12F + random.nextFloat() * .14F;
                s.life = 70 + random.nextInt(60);
                s.sprite = StainSprites.choose("splat", random);
                splats.add(s);
            }
        }
        health = current;
        flash *= .93F;
        splats.removeIf(s -> ++s.age >= s.life);
    }

    public static float opacity(float flash, float health, float maximum, float ticks) {
        float low = Math.max(0, Math.min(1, 1 - health / (Math.max(1, maximum) * .4F)));
        return Math.max(0, Math.min(.9F, flash * .85F + low * .55F * (.75F + .25F * (float) Math.sin(ticks * .35F))));
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !com.uky.corpses.stains.SurfaceBlood.enabled
            || !SurfaceBloodClientConfig.VIGNETTE.get()
            || Minecraft.getMinecraft()
                .isGamePaused())
            return;
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null || !p.isEntityAlive()) return;
        float strength = SurfaceBloodClientConfig.VIGNETTE_STRENGTH.get()
            .floatValue(),
            alpha = opacity(
                previous + (flash - previous) * event.partialTicks,
                p.getHealth(),
                p.getMaxHealth(),
                ticks + event.partialTicks) * strength;
        int[] colors = SurfaceBloodClient.colors();
        int color = colors[0];
        int width = event.resolution.getScaledWidth(), height = event.resolution.getScaledHeight();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        try {
            String style = SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL ? "pixel" : "smooth";
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(new ResourceLocation("ukycorpses_blood", "textures/gui/vignette_" + style + ".png"));
            quad(0, 0, width, height, 0, 0, 1, 1, color, Math.min(1, alpha));
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(StainSprites.texture());
            for (Splat s : splats) {
                float age = s.age + event.partialTicks,
                    fade = 1 - Math.max(0, Math.min(1, (age - s.life * .4F) / (s.life * .6F)));
                int size = (int) (s.size * height);
                quad(
                    (int) (s.x * width - size / 2F),
                    (int) (s.y * height + age * .15F * height / 100 - size / 2F),
                    size,
                    size,
                    s.sprite.u,
                    s.sprite.v,
                    s.sprite.w,
                    s.sprite.h,
                    color,
                    .85F * fade * Math.min(1, strength));
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void quad(int x, int y, int w, int h, float u, float v, float uw, float vh, int color, float a) {
        GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, a);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, -90, u, v + vh);
        t.addVertexWithUV(x + w, y + h, -90, u + uw, v + vh);
        t.addVertexWithUV(x + w, y, -90, u + uw, v);
        t.addVertexWithUV(x, y, -90, u, v);
        t.draw();
    }
}
