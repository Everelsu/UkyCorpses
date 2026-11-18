package com.uky.corpses.stains.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

/** Original SurfaceBlood stain shader and sun/moon lighting, adapted to the 1.7 Tessellator. */
public final class StainShader {

    private static int program, previous;
    private static boolean attempted, active;
    private static String failure;
    private static final Map<String, Integer> locations = new HashMap<>();
    private static final FloatBuffer fog = BufferUtils.createFloatBuffer(16);

    public static boolean available() {
        return program != 0;
    }

    public static String failureReason() {
        return failure;
    }

    public static void warmup() {
        if (attempted) return;
        attempted = true;
        if (!GLContext.getCapabilities().OpenGL20) {
            failure = "OpenGL 2.0 is unavailable";
            UkyCorpses.LOG.warn("SurfaceBlood stain shader: {}", failure);
            return;
        }
        int vertex = 0, fragment = 0, linked = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, "stain.vsh");
            fragment = compile(GL20.GL_FRAGMENT_SHADER, "stain.fsh");
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, fragment);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked, 16384));
            program = linked;
            UkyCorpses.LOG.info("SurfaceBlood GLSL 1.20 stain shader ready");
        } catch (Exception error) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            failure = error.toString();
            UkyCorpses.LOG.error("SurfaceBlood stain shader unavailable; using fixed-function rendering", error);
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    public static void reload() {
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        attempted = false;
        failure = null;
        locations.clear();
        warmup();
    }

    public static boolean begin(float partial) {
        warmup();
        if (program == 0) return false;
        previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        active = true;
        GL20.glUniform1i(location("Sampler0"), 0);
        GL20.glUniform1i(location("Sampler2"), 1);
        GL20.glUniform4f(location("ColorModulator"), 1, 1, 1, 1);
        GL20.glUniform2f(
            location("TextureSize"),
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT));
        boolean hasFog = GL11.glIsEnabled(GL11.GL_FOG);
        GL20.glUniform1f(location("FogStart"), hasFog ? GL11.glGetFloat(GL11.GL_FOG_START) : 1e10F);
        GL20.glUniform1f(location("FogEnd"), hasFog ? GL11.glGetFloat(GL11.GL_FOG_END) : 2e10F);
        fog.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, fog);
        GL20.glUniform4f(location("FogColor"), fog.get(0), fog.get(1), fog.get(2), fog.get(3));
        GL20.glUniform1f(
            location("Spread"),
            SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.SMOOTH ? 32 : 16);
        GL20.glUniform1f(location("Soft"), SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.SMOOTH ? 1 : 0);
        Minecraft mc = Minecraft.getMinecraft();
        boolean on = SurfaceBloodClientConfig.SPECULAR.get() && mc.theWorld != null && !mc.theWorld.provider.hasNoSky;
        GL20.glUniform1f(location("Specular"), on ? 1 : 0);
        if (mc.theWorld != null && mc.renderViewEntity != null) {
            float angle = mc.theWorld.getCelestialAngleRadians(partial);
            float x = -(float) Math.sin(angle), y = (float) Math.cos(angle);
            float clear = 1 - mc.theWorld.getRainStrength(partial) * .85F;
            float light = Math.min(1, Math.abs(y) * 6) * clear;
            if (y < 0) {
                x = -x;
                y = -y;
                light *= .25F * mc.theWorld.getCurrentMoonPhaseFactor();
            }
            GL20.glUniform3f(location("SunDirection"), x, y, 0);
            GL20.glUniform1f(location("SunLight"), light);
            Vec3 sky = mc.theWorld.getSkyColor(mc.renderViewEntity, partial);
            GL20.glUniform3f(location("SkyColor"), (float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord);
        }
        return true;
    }

    public static void surface(Tessellator tessellator, float level, float wetness, int combinedLight) {
        // Tessellator stores normals in signed bytes: spread/wetness retain 1/127 precision.
        tessellator.setNormal(0, Math.max(0, Math.min(1, level)), Math.max(0, Math.min(1, wetness)));
        tessellator.setBrightness(combinedLight);
    }

    public static void end() {
        if (!active) return;
        GL20.glUseProgram(previous);
        active = false;
    }

    private static int location(String name) {
        Integer index = locations.get(name);
        if (index == null) {
            index = GL20.glGetUniformLocation(program, name);
            locations.put(name, index);
        }
        return index;
    }

    private static int compile(int kind, String filename) throws Exception {
        String source;
        ResourceLocation path = new ResourceLocation("ukycorpses_blood", "shaders/legacy/" + filename);
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(path)
            .getInputStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetShaderInfoLog(shader, 16384);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(filename + ": " + message);
        }
        return shader;
    }

    private StainShader() {}
}
