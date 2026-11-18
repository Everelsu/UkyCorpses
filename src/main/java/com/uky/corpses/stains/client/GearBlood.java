package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import com.uky.corpses.stains.SurfaceBloodClientConfig;

/** Cosmetic stack identity state; never writes inventory NBT or changes merging/ownership. */
public final class GearBlood {

    private static final ThreadLocal<List<ItemStack>> rendering = ThreadLocal.withInitial(ArrayList::new);
    private static final Map<ItemStack, Coat> attached = new WeakHashMap<>();
    private static final List<Coat> coats = new ArrayList<>();
    private static final Random random = new Random();
    private static final Map<ResourceLocation, BufferedImage> pictures = new LinkedHashMap<>();
    private static boolean reloadListener;

    private static final class Coat {

        float coverage, wash;
        int color, dried, version, rendered = -1;
        final int seed = random.nextInt();
        int drop, width, height;
        long painted;
        float opacity = -1;
        long lastHit, dripUntil, nextDrop;
        EntityLivingBase holder;
        ItemStack stack;
        DynamicTexture texture;
        ResourceLocation id;
    }

    public static boolean canCoat(ItemStack stack) {
        if (stack == null || stack.getItem() instanceof ItemBlock) return false;
        if (stack.getItem() instanceof net.minecraft.item.ItemBow
            || stack.getItemUseAction() == net.minecraft.item.EnumAction.bow) return false;
        for (Class<?> type = stack.getItem()
            .getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getSimpleName()
                .toLowerCase(java.util.Locale.ROOT);
            if (name.contains("gun") || name.contains("bow")
                || name.contains("rifle")
                || name.contains("pistol")
                || name.contains("launcher")
                || name.contains("firearm")
                || name.contains("crossbow")) return false;
        }
        return true;
    }

    public static void hit(EntityLivingBase attacker, float damage, int color, int dried) {
        if (attacker == null || !SurfaceBloodClientConfig.GEAR.get()) return;
        ItemStack stack = attacker.getHeldItem();
        if (!canCoat(stack)) return;
        Coat coat = attached.get(stack);
        if (coat == null) {
            while (coats.size() >= 32) discard(coats.remove(0));
            coat = new Coat();
            coats.add(coat);
            attached.put(stack, coat);
        }
        float added = (float) ((.2F + damage * .025F) * SurfaceBloodClientConfig.preset().wound
            * SurfaceBloodClientConfig.GEAR_COVERAGE.get());
        float share = coat.coverage <= 0 ? 1 : added / (coat.coverage + added);
        coat.color = Stain.blend(coat.color, color, share);
        coat.dried = Stain.blend(coat.dried, dried, share);
        coat.coverage = Math.min(1, coat.coverage + added);
        coat.wash = 0;
        coat.lastHit = attacker.worldObj.getTotalWorldTime();
        coat.dripUntil = coat.lastHit + (long) ((160 + damage * 20) * SurfaceBloodClientConfig.preset().bleed);
        coat.nextDrop = Math
            .min(coat.nextDrop == 0 ? Long.MAX_VALUE : coat.nextDrop, coat.lastHit + 8 + random.nextInt(10));
        coat.holder = attacker;
        coat.stack = stack;
        coat.version++;
    }

    /** The original's ItemStack.copy / incoming-slot hooks call this; both instances share their cosmetic coat. */
    public static void copied(ItemStack from, ItemStack to) {
        if (from != null && to != null) {
            Coat c = attached.get(from);
            if (c != null) attached.put(to, c);
        }
    }

    public static boolean bloody(ItemStack stack) {
        Coat c = stack == null ? null : attached.get(stack);
        return canCoat(stack) && SurfaceBloodClientConfig.GEAR.get() && c != null && c.coverage > 0 && c.wash < 1;
    }

    public static void tick() {
        if (SurfaceBloodClient.world() == null) return;
        long now = SurfaceBloodClient.world()
            .getTotalWorldTime();
        for (Iterator<Coat> it = coats.iterator(); it.hasNext();) {
            Coat c = it.next();
            if (!canCoat(c.stack)) {
                discard(c);
                it.remove();
                continue;
            }
            EntityLivingBase h = c.holder;
            if (h != null && h.isEntityAlive()
                && h.worldObj == SurfaceBloodClient.world()
                && h.worldObj.getEntityByID(h.getEntityId()) == h) {
                boolean held = h.getHeldItem() != null && attached.get(h.getHeldItem()) == c;
                boolean wet = held && h.isInWater(), rain = held && h.worldObj.isRaining()
                    && h.worldObj.canBlockSeeTheSky(
                        (int) Math.floor(h.posX),
                        (int) Math.floor(h.posY + h.height),
                        (int) Math.floor(h.posZ));
                if (wet || rain) {
                    c.wash = Math.min(1, c.wash + (wet ? 1 / 60F : 1 / 400F));
                    c.version++;
                }
                boolean dripping = held && SurfaceBloodClientConfig.GEAR_DRIPS.get()
                    && now < c.dripUntil
                    && c.coverage >= .15F
                    && c.wash <= 0
                    && !h.isInWater();
                if (!dripping) {
                    if (c.drop != 0) {
                        c.drop = 0;
                        c.version++;
                    }
                } else if (now >= c.nextDrop) {
                    c.drop++;
                    c.version++;
                    if (c.drop <= 2) c.nextDrop = now + 6 + random.nextInt(8);
                    else {
                        c.drop = 0;
                        c.nextDrop = now + 14 + random.nextInt(16) + (c.dripUntil - now < 80 ? 30 : 0);
                        net.minecraft.util.Vec3 look = h.getLookVec();
                        SurfaceBloodClient.spray(
                            h.posX + look.xCoord * .45,
                            h.posY + h.getEyeHeight() - .35,
                            h.posZ + look.zCoord * .45,
                            (float) h.motionX * .5F,
                            -.02F,
                            (float) h.motionZ * .5F,
                            .028F + random.nextFloat() * .012F,
                            false);
                    }
                }
            }
            float naturalFade = fade(
                now - c.lastHit,
                SurfaceBloodClientConfig.GEAR_LASTS.get(),
                SurfaceBloodClientConfig.GEAR_FADE.get());
            if (naturalFade > c.wash) {
                c.wash = naturalFade;
                c.version++;
            }
            if (c.wash >= 1) {
                discard(c);
                it.remove();
                continue;
            }
            if (SurfaceBloodClientConfig.GEAR_DROPPED.get())
                for (Object object : SurfaceBloodClient.world().loadedEntityList) if (object instanceof EntityItem) {
                    EntityItem item = (EntityItem) object;
                    ItemStack dropped = item.getEntityItem();
                    if (dropped != null && item.age <= 40
                        && h != null
                        && item.getDistanceSqToEntity(h) < 16
                        && dropped.getItem() == c.stack.getItem()
                        && dropped.getItemDamage() == c.stack.getItemDamage()
                        && ItemStack.areItemStackTagsEqual(dropped, c.stack)
                        && h.getHeldItem() != c.stack) {
                        attached.putIfAbsent(dropped, c);
                    }
                    if (attached.get(dropped) == c && item.isInWater()) {
                        c.wash = Math.min(1, c.wash + 1 / 60F);
                        c.version++;
                    }
                }
        }
    }

    static float fade(long age, int lasts, int fade) {
        return Math.max(0, Math.min(1, (age - lasts * 20L) / (float) Math.max(1, fade * 20L)));
    }

    public static ResourceLocation overlay(ItemStack stack) {
        if (!canCoat(stack)) return null;
        if (SurfaceBloodClient.world() == null || !bloody(stack)) return null;
        Coat c = attached.get(stack);
        long now = SurfaceBloodClient.world()
            .getTotalWorldTime();
        ensureReloadListener();
        long dryTicks = Math.max(1, SurfaceBloodClientConfig.DRY_TIME.get() * 20L);
        boolean drying = now - c.lastHit < dryTicks;
        float opacity = SurfaceBloodClientConfig.GEAR_OPACITY.get()
            .floatValue();
        if (c.id != null && c.rendered == c.version && c.opacity == opacity && (!drying || now - c.painted < 40))
            return c.id;
        IIcon icon = stack.getIconIndex();
        if (icon == null) return null;
        ResourceLocation iconId = new ResourceLocation(icon.getIconName());
        ResourceLocation source = new ResourceLocation(
            iconId.getResourceDomain(),
            "textures/items/" + iconId.getResourcePath() + ".png");
        BufferedImage base = picture(source);
        if (base == null) return null;
        float dry = (float) Math.pow(Math.max(0, Math.min(1, (now - c.lastHit) / (float) dryTicks)), .6);
        BufferedImage image = GearPaint
            .flat(base, c.seed, Stain.blend(c.color, c.dried, dry), dry, c.coverage, c.wash, opacity, c.drop);
        c.width = image.getWidth();
        c.height = image.getHeight();
        if (c.texture == null) {
            c.texture = new DynamicTexture(c.width, c.height);
            c.id = Minecraft.getMinecraft()
                .getTextureManager()
                .getDynamicTextureLocation("ukycorpses_blood_weapon", c.texture);
        }
        image.getRGB(0, 0, c.width, c.height, c.texture.getTextureData(), 0, c.width);
        c.texture.updateDynamicTexture();
        c.rendered = c.version;
        c.painted = now;
        c.opacity = opacity;
        return c.id;
    }

    private static BufferedImage picture(ResourceLocation source) {
        if (pictures.containsKey(source)) return pictures.get(source);
        BufferedImage image = null;
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(source)
            .getInputStream()) {
            image = ImageIO.read(in);
            if (image != null && (image.getWidth() > 256 || image.getHeight() > 256)) image = null;
        } catch (java.io.IOException | RuntimeException error) {}
        if (pictures.size() >= 128) pictures.remove(
            pictures.keySet()
                .iterator()
                .next());
        pictures.put(source, image);
        return image;
    }

    private static void ensureReloadListener() {
        if (reloadListener) return;
        Object manager = Minecraft.getMinecraft()
            .getResourceManager();
        if (manager instanceof net.minecraft.client.resources.IReloadableResourceManager) {
            reloadListener = true;
            ((net.minecraft.client.resources.IReloadableResourceManager) manager).registerReloadListener(resource -> {
                pictures.clear();
                for (Coat coat : coats) {
                    if (coat.id != null) Minecraft.getMinecraft()
                        .getTextureManager()
                        .deleteTexture(coat.id);
                    coat.id = null;
                    coat.texture = null;
                    coat.rendered = -1;
                }
            });
        }
    }

    /** Geometry hooks retain the exact stack, including client-only copied coats, across nested render passes. */
    public static void pushItem(ItemStack stack) {
        rendering.get()
            .add(stack);
    }

    public static void popItem() {
        List<ItemStack> slots = rendering.get();
        if (!slots.isEmpty()) slots.remove(slots.size() - 1);
    }

    public static void drawHeld() {
        List<ItemStack> slots = rendering.get();
        if (!slots.isEmpty()) drawHeld(slots.get(slots.size() - 1));
    }

    public static void drawHeld(ItemStack stack) {
        ResourceLocation texture = overlay(stack);
        if (texture == null) return;
        org.lwjgl.opengl.GL11.glPushAttrib(
            org.lwjgl.opengl.GL11.GL_ENABLE_BIT | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_CURRENT_BIT
                | org.lwjgl.opengl.GL11.GL_POLYGON_BIT);
        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11
                .glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glDepthMask(false);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL);
            org.lwjgl.opengl.GL11.glPolygonOffset(-1, -1);
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            Coat coat = attached.get(stack);
            org.lwjgl.opengl.GL11.glPushMatrix();
            try {
                org.lwjgl.opengl.GL11.glTranslatef(0, 0, .0005F);
                net.minecraft.client.renderer.ItemRenderer.renderItemIn2D(
                    net.minecraft.client.renderer.Tessellator.instance,
                    1,
                    0,
                    0,
                    1,
                    coat.width,
                    coat.height,
                    .0635F);
            } finally {
                org.lwjgl.opengl.GL11.glPopMatrix();
            }
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }

    public static void drawGui(ItemStack stack, int x, int y) {
        if (!SurfaceBloodClientConfig.GEAR_HOTBAR.get()) return;
        ResourceLocation texture = overlay(stack);
        if (texture == null) return;
        org.lwjgl.opengl.GL11.glPushAttrib(
            org.lwjgl.opengl.GL11.GL_ENABLE_BIT | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_CURRENT_BIT
                | org.lwjgl.opengl.GL11.GL_POLYGON_BIT);
        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11
                .glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDepthMask(false);
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            itemQuad(x, y, 16, 16, 150);
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }

    private static void itemQuad(double x, double y, double width, double height, double z) {
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(0, 0, 1);
        t.addVertexWithUV(x, y + height, z, 0, 1);
        t.addVertexWithUV(x + width, y + height, z, 1, 1);
        t.addVertexWithUV(x + width, y, z, 1, 0);
        t.addVertexWithUV(x, y, z, 0, 0);
        t.draw();
    }

    private static void discard(Coat c) {
        if (c.id != null) Minecraft.getMinecraft()
            .getTextureManager()
            .deleteTexture(c.id);
        attached.values()
            .removeIf(value -> value == c);
    }

    public static void clear() {
        for (Coat c : coats) if (c.id != null) Minecraft.getMinecraft()
            .getTextureManager()
            .deleteTexture(c.id);
        coats.clear();
        attached.clear();
        pictures.clear();
    }

    private GearBlood() {}
}
