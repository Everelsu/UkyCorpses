package com.uky.corpses.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

/** PR's independent colour-convolve/blit passes; never occupies another mod's global post-shader slot. */
final class PrDesaturation {

    private static ShaderGroup group;
    private static Framebuffer input;
    private static int width, height;
    private static boolean failed;

    private PrDesaturation() {}

    static void render(Minecraft mc, float saturation, float partial) {
        if (failed || saturation >= .995F || !OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled())
            return;
        try {
            if (group == null || input != mc.getFramebuffer()) {
                release();
                input = mc.getFramebuffer();
                group = new ShaderGroup(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    input,
                    new ResourceLocation("ukycorpses", "shaders/post/revive_desaturate.json"));
            }
            if (width != mc.displayWidth || height != mc.displayHeight) {
                group.createBindFramebuffers(width = mc.displayWidth, height = mc.displayHeight);
            }
            List<?> passes = ObfuscationReflectionHelper
                .getPrivateValue(ShaderGroup.class, group, "listShaders", "field_148031_d");
            for (Object pass : passes) {
                ShaderUniform value = ((Shader) pass).getShaderManager()
                    .func_147991_a("Saturation");
                if (value != null) value.func_148090_a(Math.max(0, Math.min(1, saturation)));
            }
            int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            try {
                group.loadShaderGroup(partial);
                input.bindFramebuffer(true);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            } finally {
                input.bindFramebuffer(true);
                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glPopMatrix();
                GL11.glMatrixMode(matrixMode);
                GL11.glPopAttrib();
            }
        } catch (Exception error) {
            UkyCorpses.LOG.warn("Revival desaturation is unavailable", error);
            release();
            failed = true;
        }
    }

    static void release() {
        if (group != null) group.deleteShaderGroup();
        group = null;
        input = null;
        width = height = 0;
    }

    static void reload() {
        release();
        failed = false;
    }
}
