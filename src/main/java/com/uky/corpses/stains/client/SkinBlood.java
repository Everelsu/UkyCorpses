package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.Reactions;
import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Skeleton;
import com.uky.corpses.stains.DamageCategory;
import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

/** SurfaceBlood's bounded body wounds, painted through the six 1.7 ModelBiped boxes' real UV rectangles. */
public final class SkinBlood {

    private static final int[][] UV = { { 0, 0 }, { 16, 16 }, { 40, 16 }, { 40, 16 }, { 0, 16 }, { 0, 16 } };
    private static final Map<String, Body> bodies = new LinkedHashMap<>();
    private static final Map<String, Baked> textures = new LinkedHashMap<>();
    private static final Map<ResourceLocation, BufferedImage> sourceImages = new LinkedHashMap<ResourceLocation, BufferedImage>(
        64,
        .75F,
        true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, BufferedImage> eldest) {
            return size() > 64;
        }
    };
    private static boolean reloadListener;
    private static java.lang.reflect.Field downloadedImageField;

    private static final class Body {

        final List<Wound> wounds = new ArrayList<>();
        int revision;
        Entity owner;
        long healed = -1;
    }

    private static final class Wound {

        int part, face, color, dried;
        double[] local;
        float radius, wash;
        boolean armor, spatter;
        float damage;
        DamageCategory category;
        long born;
    }

    private static final class Baked {

        DynamicTexture texture;
        ResourceLocation id;
        int revision, width, height;
        long phase;
        BufferedImage source;
    }

    private static String key(Entity e) {
        return e instanceof EntityCorpse ? "c:" + e.getUniqueID()
            : e instanceof EntityPlayer ? "p:" + e.getCommandSenderName()
                : e.getUniqueID()
                    .toString();
    }

    private static Body body(Entity entity) {
        String key = key(entity);
        Body body = bodies.get(key);
        if (entity instanceof EntityCorpse && body == null) {
            body = bodies.get("p:" + ((EntityCorpse) entity).owner());
            if (body != null && body.owner instanceof EntityLivingBase
                && !((EntityLivingBase) body.owner).isEntityAlive()) bodies.put(key, body);
            else body = null;
        }
        if (entity instanceof EntityPlayer && body != null && body.owner != entity) {
            for (Object value : entity.worldObj.loadedEntityList)
                if (value instanceof EntityCorpse && ((EntityCorpse) value).owner()
                    .equals(entity.getCommandSenderName())) bodies.putIfAbsent(key((Entity) value), body);
            bodies.remove(key);
            java.util.Iterator<Map.Entry<String, Baked>> it = textures.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<String, Baked> entry = it.next();
                if (entry.getKey()
                    .startsWith(key + "|")) {
                    Minecraft.getMinecraft()
                        .getTextureManager()
                        .deleteTexture(entry.getValue().id);
                    it.remove();
                }
            }
            body = null;
        }
        return body;
    }

    public static Skeleton.Shape shape(Entity e) {
        if (e instanceof EntityCorpse) return ((EntityCorpse) e).drawnShape(1);
        Skeleton.Shape shape = Reactions.physicalShape(e, 1);
        if (shape != null) return shape;
        if (!(e instanceof EntityPlayer)) return null;
        EntityPlayer p = (EntityPlayer) e;
        Mat4 frame = new Mat4();
        float[] angles = new float[15];
        angles[0] = (float) Math.toRadians(p.rotationPitch);
        angles[1] = (float) Math.toRadians(p.rotationYawHead - p.renderYawOffset);
        Skeleton.place(frame, 0, p.renderYawOffset, 0, 0, Float.NaN, 0);
        frame.moved(0, p.boundingBox.minY - p.posY, 0);
        return Skeleton.snapshot(frame, angles);
    }

    public static void hit(Entity e, double x, double y, double z, float damage, int color, int dried) {
        hit(e, x, y, z, damage, color, dried, DamageCategory.BLUNT);
    }

    public static void hit(Entity e, double x, double y, double z, float damage, int color, int dried,
        DamageCategory category) {
        add(e, x, y, z, damage, color, dried, category, false);
    }

    public static void spatter(Entity e, double x, double y, double z, float size, int color, int dried) {
        add(e, x, y, z, size, color, dried, DamageCategory.BLUNT, true);
    }

    private static void add(Entity e, double x, double y, double z, float damage, int color, int dried,
        DamageCategory category, boolean spatter) {
        if (!(spatter ? SurfaceBloodClientConfig.SPATTER.get() : SurfaceBloodClientConfig.WOUNDS.get())) return;
        Skeleton.Shape shape = shape(e);
        if (shape == null) {
            ModelWounds.hit(e, x, y, z, damage, color, dried, category, spatter);
            return;
        }
        Wound wound = new Wound();
        double best = Double.MAX_VALUE;
        for (int part = 0; part < 6; part++) {
            Mat4 inverse = new Mat4();
            if (!shape.parts[part].invert(inverse)) continue;
            double[] p = inverse.apply(x - e.posX, y - e.posY, z - e.posZ, new double[3]);
            float[] lo = Skeleton.min(part), hi = Skeleton.max(part);
            double error = 0;
            for (int axis = 0; axis < 3; axis++) {
                double d = Math.max(Math.max(lo[axis] - p[axis], p[axis] - hi[axis]), 0);
                error += d * d;
                p[axis] = Math.max(lo[axis], Math.min(hi[axis], p[axis]));
            }
            if (error < best) {
                best = error;
                wound.part = part;
                wound.local = p;
                double distance = Double.MAX_VALUE;
                for (int face = 0; face < 6; face++) {
                    int axis = face / 2;
                    double d = Math.abs(p[axis] - (face % 2 == 0 ? lo[axis] : hi[axis]));
                    if (d < distance) {
                        distance = d;
                        wound.face = face;
                    }
                }
            }
        }
        if (wound.local == null) return;
        int slot = wound.part == Skeleton.HEAD ? 4 : wound.part >= Skeleton.RLEG ? 2 : 3;
        wound.armor = e instanceof EntityLivingBase && ((EntityLivingBase) e).getEquipmentInSlot(slot) != null;
        if (wound.armor && !SurfaceBloodClientConfig.ARMOUR_WOUNDS.get()) return;
        wound.color = color;
        wound.dried = dried;
        wound.born = e.worldObj.getTotalWorldTime();
        wound.damage = damage;
        wound.spatter = spatter;
        wound.category = category;
        wound.radius = (float) (Math.max(1, Math.min(3.2, .8 + Math.sqrt(Math.max(0, damage)) * .5))
            * SurfaceBloodClientConfig.WOUND_SIZE.get()
            * SurfaceBloodClientConfig.preset().wound);
        body(e);
        Body body = bodies.computeIfAbsent(key(e), k -> new Body());
        body.owner = e;
        while (body.wounds.size() >= 12) body.wounds.remove(0);
        body.wounds.add(wound);
        body.revision++;
        body.healed = -1;
        while (bodies.size() > 96) bodies.remove(
            bodies.keySet()
                .iterator()
                .next());
    }

    /** Returns UV point and face clipping rectangle in the vanilla 64x32 texture coordinate system. */
    public static float[] uv(int part, int face, double[] local) {
        float[] lo = Skeleton.min(part), hi = Skeleton.max(part);
        float w = hi[0] - lo[0], h = hi[1] - lo[1], d = hi[2] - lo[2];
        float x = (float) (local[0] - lo[0]), y = (float) (local[1] - lo[1]), z = (float) (local[2] - lo[2]);
        float u0 = UV[part][0], v0 = UV[part][1], u, v, fw, fh;
        switch (face) {
            case 0:
                u = u0 + z;
                v = v0 + d + y;
                fw = d;
                fh = h;
                v0 += d;
                break;
            case 1:
                u0 += d + w;
                u = u0 + d - z;
                v = v0 + d + y;
                fw = d;
                fh = h;
                v0 += d;
                break;
            case 2:
                u0 += d;
                u = u0 + x;
                v = v0 + z;
                fw = w;
                fh = d;
                break;
            case 3:
                u0 += d + w;
                u = u0 + x;
                v = v0 + d - z;
                fw = w;
                fh = d;
                break;
            case 4:
                u0 += d;
                u = u0 + x;
                v = v0 + d + y;
                fw = w;
                fh = h;
                v0 += d;
                break;
            default:
                u0 += d + w + d;
                u = u0 + w - x;
                v = v0 + d + y;
                fw = w;
                fh = h;
                v0 += d;
                break;
        }
        return new float[] { u, v, u0, v0, u0 + fw, v0 + fh };
    }

    public static ResourceLocation texture(Entity e, ResourceLocation base, boolean armor) {
        return texture(e, 63, base, armor);
    }

    public static ResourceLocation texture(Entity e, int mask, ResourceLocation base, boolean armor) {
        if (e == null || e.worldObj == null) return base;
        Body body = body(e);
        if (body == null || body.wounds.isEmpty() || armor && !SurfaceBloodClientConfig.ARMOUR_WOUNDS.get())
            return base;
        long now = e.worldObj.getTotalWorldTime(), phase = now / 20;
        String cache = key(e) + "|" + base + "|" + mask + "|" + armor;
        Baked b = textures.get(cache);
        ensureReloadListener();
        try {
            BufferedImage original = source(base);
            if (original == null) return base;
            if (b != null && b.revision == body.revision && b.phase == phase && b.source == original) return b.id;
            BufferedImage image = copyImage(original);
            int width = image.getWidth(), height = image.getHeight();
            if (b != null && (b.width != width || b.height != height)) {
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .deleteTexture(b.id);
                textures.remove(cache);
                b = null;
            }
            for (Wound wound : body.wounds) {
                if (wound.armor != armor || (mask & 1 << wound.part) == 0
                    || !(wound.spatter ? SurfaceBloodClientConfig.SPATTER.get()
                        : SurfaceBloodClientConfig.WOUNDS.get()))
                    continue;
                float[] uv = uv(wound.part, wound.face, wound.local);
                float sx = width / 64F, sy = !armor && width == height ? sx : height / 32F;
                float fade = 1 - wound.wash;
                if (body.healed >= 0) {
                    long lasts = wound.spatter ? SurfaceBloodClientConfig.SPATTER_LASTS.get() * 20L : 300;
                    fade *= Math.max(0, Math.min(1, (lasts - (now - body.healed)) / (float) Math.min(100, lasts)));
                }
                paintOriginal(image, wound, uv, sx, sy, now, fade);

            }
            if (b == null) {
                b = new Baked();
                b.width = width;
                b.height = height;
                b.texture = new DynamicTexture(width, height);
                b.id = Minecraft.getMinecraft()
                    .getTextureManager()
                    .getDynamicTextureLocation("ukycorpses_blood_wound", b.texture);
                textures.put(cache, b);
            }
            image.getRGB(0, 0, width, height, b.texture.getTextureData(), 0, width);
            b.texture.updateDynamicTexture();
            b.revision = body.revision;
            b.phase = phase;
            b.source = original;
            while (textures.size() > 160) {
                String first = textures.keySet()
                    .iterator()
                    .next();
                Baked removed = textures.remove(first);
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .deleteTexture(removed.id);
            }
            return b.id;
        } catch (java.io.IOException | RuntimeException error) {
            return base;
        }
    }

    private static void paintOriginal(BufferedImage image, Wound wound, float[] uv, float sx, float sy, long now,
        float fade) {
        WoundCanvas canvas = new WoundCanvas(image.getWidth(), image.getHeight());
        WoundMask mask = new WoundMask(image);
        int x0 = (int) Math.floor(uv[2] * sx), y0 = (int) Math.floor(uv[3] * sy), x1 = (int) Math.ceil(uv[4] * sx),
            y1 = (int) Math.ceil(uv[5] * sy);
        WoundCanvas.Locator locator = (x, y,
            upright) -> x >= x0 && x < x1 && y >= y0 && y < y1 ? WoundCanvas.spot(x, y, 0) : -1L;
        if (wound.spatter) canvas.spatter(
            mask,
            locator,
            (int) (uv[0] * sx),
            (int) (uv[1] * sy),
            wound.face == 2 || wound.face == 3 ? 0 : 1,
            0,
            1,
            wound.damage,
            wound.armor,
            new WoundCanvas.Pigment(wound.color, wound.dried, true),
            wound.born,
            new Random(wound.born * 31 + wound.part * 7 + wound.face));
        else canvas.wound(
            mask,
            locator,
            (int) (uv[0] * sx),
            (int) (uv[1] * sy),
            wound.radius * Math.min(sx, sy),
            wound.face == 2 || wound.face == 3 ? 0 : 1,
            wound.damage,
            wound.category,
            false,
            1,
            1,
            wound.armor,
            new WoundCanvas.Pigment(wound.color, wound.dried, true),
            wound.born,
            new Random(wound.born * 31 + wound.part * 7 + wound.face));
        int[] pixels = canvas.pixels(now, Math.max(1, SurfaceBloodClientConfig.DRY_TIME.get() * 20L), 1 - fade);
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++)
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
                int pixel = pixels[y * image.getWidth() + x];
                if ((pixel >>> 24) != 0) image.setRGB(x, y, (image.getRGB(x, y) & 0xff000000) | (pixel & 0xffffff));
            }
    }

    public static void tick(EntityLivingBase e) {
        ModelWounds.tick(e);
        Body body = body(e);
        if (body == null) return;
        if (e.getHealth() < e.getMaxHealth()) body.healed = -1;
        else if (body.healed < 0) body.healed = e.worldObj.getTotalWorldTime();
        boolean wet = e.isInWater();
        boolean rain = e.worldObj.isRaining() && e.worldObj
            .canBlockSeeTheSky((int) Math.floor(e.posX), (int) Math.floor(e.posY + e.height), (int) Math.floor(e.posZ));
        for (Wound w : body.wounds) if (wet || rain) {
            w.wash = Math.min(1, w.wash + (wet ? 1 / 80F : 1 / 500F));
            body.revision++;
        }
        body.wounds.removeIf(
            w -> w.wash >= 1 || body.healed >= 0 && e.worldObj.getTotalWorldTime() - body.healed
                > (w.spatter ? SurfaceBloodClientConfig.SPATTER_LASTS.get() * 20L : 300));
    }

    public static void clear() {
        ModelWounds.clear();
        invalidateImages();
        bodies.clear();
    }

    private static void ensureReloadListener() {
        if (reloadListener) return;
        Object resources = Minecraft.getMinecraft()
            .getResourceManager();
        if (resources instanceof net.minecraft.client.resources.IReloadableResourceManager) {
            reloadListener = true;
            ((net.minecraft.client.resources.IReloadableResourceManager) resources).registerReloadListener(manager -> {
                invalidateImages();
                StainSprites.clear();
            });
        }
    }

    private static void invalidateImages() {
        ModelWounds.invalidateImages();
        for (Baked b : textures.values()) Minecraft.getMinecraft()
            .getTextureManager()
            .deleteTexture(b.id);
        textures.clear();
        sourceImages.clear();
    }

    static BufferedImage source(ResourceLocation base) throws java.io.IOException {
        ensureReloadListener();
        Object texture = Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(base);
        if (texture instanceof net.minecraft.client.renderer.ThreadDownloadImageData) {
            // A completed skin download replaces the CPU image; identity also invalidates same-tick baked wounds.
            try {
                if (downloadedImageField == null) {
                    for (java.lang.reflect.Field field : net.minecraft.client.renderer.ThreadDownloadImageData.class
                        .getDeclaredFields()) if (field.getType() == BufferedImage.class) {
                            field.setAccessible(true);
                            downloadedImageField = field;
                            break;
                        }
                }
                if (downloadedImageField == null)
                    throw new java.io.IOException("Downloaded skin image field is unavailable");
                BufferedImage image = (BufferedImage) downloadedImageField.get(texture);
                if (image == null || image.getWidth() > 2048 || image.getHeight() > 2048) return null;
                sourceImages.put(base, image);
                return image;
            } catch (ReflectiveOperationException | SecurityException error) {
                throw new java.io.IOException("Cannot access downloaded skin image", error);
            }
        }
        BufferedImage image = sourceImages.get(base);
        if (image == null) {
            try (InputStream in = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(base)
                .getInputStream()) {
                image = ImageIO.read(in);
            }
            if (image == null || image.getWidth() > 2048 || image.getHeight() > 2048) return null;
            sourceImages.put(base, image);
        }
        return image;
    }

    static BufferedImage copyImage(BufferedImage original) {
        BufferedImage image = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] pixels = original.getRGB(0, 0, original.getWidth(), original.getHeight(), null, 0, original.getWidth());
        image.setRGB(0, 0, original.getWidth(), original.getHeight(), pixels, 0, original.getWidth());
        return image;
    }

    private SkinBlood() {}
}
