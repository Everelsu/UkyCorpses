package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import com.uky.corpses.stains.DamageCategory;
import com.uky.corpses.stains.SurfaceBloodClientConfig;

/** Captures actual legacy model quads and maps hits to their UVs before painting the original texel wounds. */
public final class ModelWounds {

    private static final Map<UUID, Body> bodies = new LinkedHashMap<>();
    private static final Map<ModelBox, TexturedQuad[]> geometry = new java.util.WeakHashMap<>();
    private static final FloatBuffer buffer = java.nio.ByteBuffer.allocateDirect(64)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer();
    private static Field quads;
    private static int frame;

    private static final class Body {

        final List<Hit> wounds = new ArrayList<>();
        final Map<ModelRenderer, Map<ResourceLocation, Baked>> textures = new IdentityHashMap<>();
        Matrix4f view;
        int revision;
        long healed = -1;
    }

    private static final class Hit {

        double x, y, z;
        float damage, wash, score = Float.MAX_VALUE;
        int color, dried, capture = -1;
        long born;
        DamageCategory category;
        boolean spatter;
        ModelRenderer part;
        float[] uv;
    }

    private static final class Baked {

        DynamicTexture texture;
        ResourceLocation id;
        BufferedImage source;
        int revision;
        boolean painted;
        long phase;
    }

    public static void hit(Entity entity, double x, double y, double z, float damage, int color, int dried,
        DamageCategory category) {
        hit(entity, x, y, z, damage, color, dried, category, false);
    }

    static void hit(Entity entity, double x, double y, double z, float damage, int color, int dried,
        DamageCategory category, boolean spatter) {
        Body body = bodies.computeIfAbsent(entity.getUniqueID(), key -> new Body());
        Hit hit = new Hit();
        hit.x = x;
        hit.y = y;
        hit.z = z;
        hit.damage = damage;
        hit.spatter = spatter;
        hit.color = color;
        hit.dried = dried;
        hit.category = category;
        hit.born = entity.worldObj.getTotalWorldTime();
        if (body.wounds.size() >= 12) body.wounds.remove(0);
        body.wounds.add(hit);
        body.revision++;
        while (bodies.size() > 96) {
            UUID first = bodies.keySet()
                .iterator()
                .next();
            release(bodies.remove(first));
        }
    }

    public static void frame() {
        frame++;
    }

    public static boolean has(Entity entity) {
        return bodies.containsKey(entity.getUniqueID());
    }

    public static boolean hasPainted(Entity entity) {
        Body body = bodies.get(entity.getUniqueID());
        return body != null && body.textures.values()
            .stream()
            .anyMatch(
                material -> material.values()
                    .stream()
                    .anyMatch(baked -> baked.painted));
    }

    public static void begin(Entity entity) {
        Body body = bodies.get(entity.getUniqueID());
        if (body != null && body.wounds.stream()
            .anyMatch(hit -> hit.capture < 0)) body.view = matrix();
    }

    public static void part(Entity entity, ModelRenderer part, float scale, ResourceLocation texture) {
        Body body = bodies.get(entity.getUniqueID());
        if (body == null || texture == null) return;
        boolean armor = texture.getResourcePath()
            .contains("textures/models/armor/");
        if (armor && !SurfaceBloodClientConfig.ARMOUR_WOUNDS.get()) return;
        if (body.view != null && !armor) {
            Matrix4f inverse = null;
            for (Hit hit : body.wounds) if (hit.capture < 0 || hit.capture == frame) {
                if (inverse == null) inverse = Matrix4f.invert(matrix(), null);
                if (inverse == null) break;
                Vector4f point = Matrix4f.transform(
                    body.view,
                    new Vector4f(
                        (float) (hit.x - RenderManager.renderPosX),
                        (float) (hit.y - RenderManager.renderPosY),
                        (float) (hit.z - RenderManager.renderPosZ),
                        1),
                    null);
                point = Matrix4f.transform(inverse, point, null);
                for (ModelBox box : part.cubeList) for (TexturedQuad quad : faces(box)) {
                    float[] uv = project(quad, point.x / scale, point.y / scale, point.z / scale);
                    if (uv != null && uv[6] < hit.score) {
                        hit.score = uv[6];
                        hit.uv = uv;
                        hit.part = part;
                        hit.capture = frame;
                        body.revision++;
                    }
                }
            }
        }
        if (body.wounds.stream()
            .noneMatch(hit -> hit.part == part)) return;
        try {
            BufferedImage source = SkinBlood.source(texture);
            if (source == null) return;
            Map<ResourceLocation, Baked> materials = body.textures.computeIfAbsent(part, key -> new HashMap<>());
            Baked baked = materials.get(texture);
            long now = entity.worldObj.getTotalWorldTime(), phase = now / 20;
            if (baked == null || baked.revision != body.revision || baked.phase != phase || baked.source != source) {
                BufferedImage image = SkinBlood.copyImage(source);
                for (Hit hit : body.wounds) if (hit.part == part && hit.uv != null) paint(image, hit, now);
                if (baked == null || baked.source.getWidth() != source.getWidth()
                    || baked.source.getHeight() != source.getHeight()) {
                    if (baked != null) Minecraft.getMinecraft()
                        .getTextureManager()
                        .deleteTexture(baked.id);
                    baked = new Baked();
                    baked.texture = new DynamicTexture(image.getWidth(), image.getHeight());
                    baked.id = Minecraft.getMinecraft()
                        .getTextureManager()
                        .getDynamicTextureLocation("ukycorpses_blood_model", baked.texture);
                    materials.put(texture, baked);
                }
                image.getRGB(
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight(),
                    baked.texture.getTextureData(),
                    0,
                    image.getWidth());
                baked.painted = false;
                for (int y = 0; y < image.getHeight() && !baked.painted; y++)
                    for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) != source.getRGB(x, y)) {
                        baked.painted = true;
                        break;
                    }
                baked.texture.updateDynamicTexture();
                baked.revision = body.revision;
                baked.phase = phase;
                baked.source = source;
            }
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(baked.id);
        } catch (java.io.IOException failure) {
            com.uky.corpses.UkyCorpses.LOG.debug("Cannot paint captured model texture {}", texture, failure);
        }
    }

    private static void paint(BufferedImage image, Hit hit, long now) {
        int w = image.getWidth(), h = image.getHeight();
        float[] uv = hit.uv;
        WoundCanvas canvas = new WoundCanvas(w, h);
        WoundCanvas.Locator locate = (x, y,
            upright) -> x >= uv[2] * w && x < uv[4] * w && y >= uv[3] * h && y < uv[5] * h ? WoundCanvas.spot(x, y, 0)
                : -1;
        float radius = (float) (Math.max(1, Math.min(3.2, .8 + Math.sqrt(hit.damage) * .5))
            * SurfaceBloodClientConfig.WOUND_SIZE.get()
            * SurfaceBloodClientConfig.preset().wound);
        if (!(hit.spatter ? SurfaceBloodClientConfig.SPATTER.get() : SurfaceBloodClientConfig.WOUNDS.get())) return;
        if (hit.spatter) canvas.spatter(
            new WoundMask(image),
            locate,
            (int) (uv[0] * w),
            (int) (uv[1] * h),
            1,
            0,
            1,
            hit.damage,
            false,
            new WoundCanvas.Pigment(hit.color, hit.dried, true),
            hit.born,
            new Random(hit.born * 31));
        else canvas.wound(
            new WoundMask(image),
            locate,
            (int) (uv[0] * w),
            (int) (uv[1] * h),
            radius,
            1,
            hit.damage,
            hit.category,
            false,
            1,
            1,
            false,
            new WoundCanvas.Pigment(hit.color, hit.dried, true),
            hit.born,
            new Random(hit.born * 31));
        int[] pixels = canvas.pixels(now, Math.max(1, SurfaceBloodClientConfig.DRY_TIME.get() * 20L), hit.wash);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (pixels[y * w + x] != 0)
            image.setRGB(x, y, image.getRGB(x, y) & 0xff000000 | pixels[y * w + x] & 0xffffff);
    }

    /** Original WoundPainter's nearest parallelogram projection, using the model's real vertex UVs. */
    public static float[] project(TexturedQuad quad, float x, float y, float z) {
        PositionTextureVertex[] v = quad.vertexPositions;
        if (v.length != 4) return null;
        double ax = v[1].vector3D.xCoord - v[0].vector3D.xCoord, ay = v[1].vector3D.yCoord - v[0].vector3D.yCoord,
            az = v[1].vector3D.zCoord - v[0].vector3D.zCoord;
        double bx = v[3].vector3D.xCoord - v[0].vector3D.xCoord, by = v[3].vector3D.yCoord - v[0].vector3D.yCoord,
            bz = v[3].vector3D.zCoord - v[0].vector3D.zCoord;
        double dx = x - v[0].vector3D.xCoord, dy = y - v[0].vector3D.yCoord, dz = z - v[0].vector3D.zCoord;
        double aa = ax * ax + ay * ay + az * az, bb = bx * bx + by * by + bz * bz, ab = ax * bx + ay * by + az * bz,
            det = aa * bb - ab * ab;
        if (det < 1e-10) return null;
        double da = dx * ax + dy * ay + dz * az, db = dx * bx + dy * by + dz * bz;
        float a = (float) Math.max(0, Math.min(1, (da * bb - db * ab) / det)),
            b = (float) Math.max(0, Math.min(1, (db * aa - da * ab) / det));
        float u = v[0].texturePositionX + a * (v[1].texturePositionX - v[0].texturePositionX)
            + b * (v[3].texturePositionX - v[0].texturePositionX);
        float t = v[0].texturePositionY + a * (v[1].texturePositionY - v[0].texturePositionY)
            + b * (v[3].texturePositionY - v[0].texturePositionY);
        float u0 = 1, t0 = 1, u1 = 0, t1 = 0;
        for (PositionTextureVertex vertex : v) {
            u0 = Math.min(u0, vertex.texturePositionX);
            t0 = Math.min(t0, vertex.texturePositionY);
            u1 = Math.max(u1, vertex.texturePositionX);
            t1 = Math.max(t1, vertex.texturePositionY);
        }
        double ex = ax * a + bx * b - dx, ey = ay * a + by * b - dy, ez = az * a + bz * b - dz;
        return new float[] { u, t, u0, t0, u1, t1, (float) (ex * ex + ey * ey + ez * ez) };
    }

    private static TexturedQuad[] faces(ModelBox box) {
        TexturedQuad[] cached = geometry.get(box);
        if (cached != null) return cached;
        try {
            if (quads == null)
                for (Field field : ModelBox.class.getDeclaredFields()) if (field.getType() == TexturedQuad[].class) {
                    field.setAccessible(true);
                    quads = field;
                    break;
                }
            cached = quads == null ? new TexturedQuad[0] : (TexturedQuad[]) quads.get(box);
            geometry.put(box, cached);
            return cached;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot capture legacy model quads", failure);
        }
    }

    private static Matrix4f matrix() {
        buffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        Matrix4f matrix = new Matrix4f();
        matrix.load(buffer);
        return matrix;
    }

    public static void tick(net.minecraft.entity.EntityLivingBase entity) {
        Body body = bodies.get(entity.getUniqueID());
        if (body == null) return;
        long now = entity.worldObj.getTotalWorldTime();
        if (entity.getHealth() < entity.getMaxHealth()) body.healed = -1;
        else if (body.healed < 0) body.healed = now;
        boolean rain = entity.worldObj.isRaining() && entity.worldObj.canBlockSeeTheSky(
            (int) Math.floor(entity.posX),
            (int) Math.floor(entity.posY + entity.height),
            (int) Math.floor(entity.posZ));
        for (Hit hit : body.wounds) if (entity.isInWater() || rain) {
            hit.wash = Math.min(1, hit.wash + (entity.isInWater() ? 1 / 80F : 1 / 500F));
            body.revision++;
        }
        if (body.wounds.removeIf(
            hit -> hit.wash >= 1 || body.healed >= 0
                && now - body.healed > (hit.spatter ? SurfaceBloodClientConfig.SPATTER_LASTS.get() * 20L : 300)))
            body.revision++;
    }

    private static void release(Body body) {
        for (Map<ResourceLocation, Baked> material : body.textures.values())
            for (Baked baked : material.values()) Minecraft.getMinecraft()
                .getTextureManager()
                .deleteTexture(baked.id);
    }

    public static void clear() {
        invalidateImages();
        bodies.clear();
        geometry.clear();
    }

    static void invalidateImages() {
        for (Body body : bodies.values()) {
            release(body);
            body.textures.clear();
        }
    }

    private ModelWounds() {}
}
