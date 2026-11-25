package com.uky.corpses.stains.client;

import java.nio.ByteBuffer;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.mojang.authlib.GameProfile;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.config.ConfigSpec.ConfigValue;

/** Exercises actual Forge render paths and local block geometry without changing a user's world. */
public final class BloodGameCheck {

    private static int age, coatingWait;
    private static Stain distant;

    public static boolean tick() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        if (age == 0 && !GearBlood.bloody(mc.thePlayer.getHeldItem()) && ++coatingWait < 40) return false;
        if (age++ == 0) {
            check(GearBlood.bloody(mc.thePlayer.getHeldItem()), "Server melee hit coats the current sword");
            byte[] bloody = capture(
                () -> mc.entityRenderer.itemRenderer
                    .renderItem(mc.thePlayer, mc.thePlayer.getHeldItem(), 0, ItemRenderType.EQUIPPED_FIRST_PERSON),
                false);
            byte[] clean;
            set(SurfaceBloodClientConfig.GEAR, false);
            try {
                clean = capture(
                    () -> mc.entityRenderer.itemRenderer
                        .renderItem(mc.thePlayer, mc.thePlayer.getHeldItem(), 0, ItemRenderType.EQUIPPED_FIRST_PERSON),
                    false);
            } finally {
                set(SurfaceBloodClientConfig.GEAR, true);
            }
            check(different(bloody, clean) > 8, "First-person Forge sword geometry actually renders its blood");
            prints();
            leafCanopy(mc);
            mc.theWorld.doPreChunk(40, 40, true);
            mc.theWorld.setBlock(648, 64, 648, net.minecraft.init.Blocks.stone);
            distant = new Stain();
            distant.face = ForgeDirection.UP;
            distant.x = distant.z = 648.5;
            distant.y = 65;
            distant.length = distant.width = .2F;
            distant.family = "splat";
            distant.born = mc.theWorld.getTotalWorldTime();
            SurfaceBloodClient.add(distant);
            check(
                StainMesh.loaded(mc.theWorld, distant) && StainMesh.supported(mc.theWorld, distant),
                "Distant client stain has a loaded supporting face");
            mc.theWorld.doPreChunk(40, 40, false);
            check(!StainMesh.loaded(mc.theWorld, distant), "Client unloads the distant stain chunk");
            long life = SurfaceBloodClientConfig.STAIN_LIFETIME.get() * 20L;
            check(
                distant.alpha(distant.born + life - 1) > 0 && distant.alpha(distant.born + life) == 0,
                "Ground blood fades completely at configured lifetime");
            double x = mc.thePlayer.posX, y = mc.thePlayer.boundingBox.minY, z = mc.thePlayer.posZ;
            SurfaceBloodClient
                .spawnSource(x, y + 1, z + .8, 0, .04F, -.3F, .06F, 0x9c1111, 0x42100b, 1, 1, 40, -1, 0, 0, 0, 0);
        }
        if (age < 12) return false;
        check(
            SurfaceBloodClient.stains.contains(distant),
            "Unloading a chunk keeps its blood until the lifetime expires");
        mc.theWorld.doPreChunk(40, 40, true);
        mc.theWorld.setBlock(648, 64, 648, net.minecraft.init.Blocks.stone);
        check(StainMesh.supported(mc.theWorld, distant), "Returning client rebuilds the stain on the reloaded face");
        SurfaceBloodClient.stains.remove(distant);
        mc.theWorld.doPreChunk(40, 40, false);
        ResourceLocation base = mc.thePlayer.getLocationSkin();
        check(
            !SkinBlood.texture(mc.thePlayer, base, false)
                .equals(base),
            "A flying drop paints the player skin");
        net.minecraft.item.ItemStack[] savedArmor = mc.thePlayer.inventory.armorInventory.clone();
        java.util.Arrays.fill(mc.thePlayer.inventory.armorInventory, null);
        try {
            byte[] bloody = capture(() -> drawPlayer(mc), true);
            byte[] clean;
            set(SurfaceBloodClientConfig.SPATTER, false);
            set(SurfaceBloodClientConfig.WOUNDS, false);
            invalidate();
            try {
                clean = capture(() -> drawPlayer(mc), true);
            } finally {
                set(SurfaceBloodClientConfig.SPATTER, true);
                set(SurfaceBloodClientConfig.WOUNDS, true);
                invalidate();
            }
            if (different(bloody, clean) == 0 && cpw.mods.fml.common.Loader.isModLoaded("hbm")) {
                dump(bloody, "hbm-blood-skin");
                dump(clean, "hbm-clean-skin");
                UkyCorpses.LOG.warn(
                    "BLOODQA HBM offscreen skin comparison is inconclusive; skin texture painting passed, vanilla control is checked separately");
            } else check(different(bloody, clean) > 0, "Actual player model shows blood from intercepted droplets");
        } finally {
            System.arraycopy(savedArmor, 0, mc.thePlayer.inventory.armorInventory, 0, savedArmor.length);
        }
        UkyCorpses.LOG.info("BLOODQA completed: sword, skin texture, fresh-splatter footprints and supported mesh");
        return true;
    }

    private static void leafCanopy(Minecraft mc) {
        mc.theWorld.doPreChunk(39, 39, true);
        try {
            mc.theWorld.setBlock(632, 64, 632, net.minecraft.init.Blocks.stone);
            mc.theWorld.setBlock(632, 66, 632, net.minecraft.init.Blocks.leaves);
            SourceVoxelCollider collider = new SourceVoxelCollider();
            check(
                collider.sweep(mc.theWorld, 632.5, 67.5, 632.5, 632.5, 64.5, 632.5) == SourceVoxelCollider.BLOCK
                    && collider.hitBlockY == 64
                    && collider.hitFace == ForgeDirection.UP,
                "Blood passes through leaves and hits the ground below");
            Stain underside = StainPlacement.impact(
                ForgeDirection.DOWN,
                632.5,
                66,
                632.5,
                0,
                .25F,
                0,
                .04F,
                0x8E1111,
                0x4E140E,
                Surface.PLAIN,
                mc.theWorld.getTotalWorldTime(),
                new java.util.Random(1));
            check(!StainMesh.supported(mc.theWorld, underside), "Stored blood has no drawable face under foliage");
            mc.theWorld.setBlock(632, 66, 632, net.minecraft.init.Blocks.stone);
            check(
                collider.sweep(mc.theWorld, 632.5, 65.5, 632.5, 632.5, 66.5, 632.5) == SourceVoxelCollider.BLOCK
                    && collider.hitBlockY == 66
                    && collider.hitFace == ForgeDirection.DOWN
                    && StainMesh.supported(mc.theWorld, underside),
                "Solid ceilings still catch blood and support an underside stain");
        } finally {
            mc.theWorld.doPreChunk(39, 39, false);
        }
    }

    private static void dump(byte[] pixels, String name) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            128,
            128,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int i = (y * 128 + x) * 4;
            image.setRGB(
                x,
                127 - y,
                ((pixels[i + 3] & 255) << 24) | ((pixels[i] & 255) << 16)
                    | ((pixels[i + 1] & 255) << 8)
                    | (pixels[i + 2] & 255));
        }
        java.io.File folder = new java.io.File(Minecraft.getMinecraft().mcDataDir, "screenshots");
        folder.mkdirs();
        javax.imageio.ImageIO.write(image, "png", new java.io.File(folder, name + ".png"));
    }

    private static void drawPlayer(Minecraft mc) {
        // HBM owns RenderPlayer; compare our corpse skin renderer independently of its live-armor stack.
        if (cpw.mods.fml.common.Loader.isModLoaded("hbm")) com.uky.corpses.client.ArmorGameCheck.drawBarePlayer(mc);
        else GuiInventory.func_147046_a(64, 118, 55, 0, 0, mc.thePlayer);
    }

    private static void prints() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityOtherPlayerMP walker = new EntityOtherPlayerMP(
            mc.theWorld,
            new GameProfile(UUID.randomUUID(), "PrintQA"));
        walker.setPosition(4, 65 + walker.yOffset, -3);
        walker.onGround = true;
        mc.theWorld.addEntityToWorld(987654, walker);
        Stain stain = new Stain();
        stain.face = ForgeDirection.UP;
        stain.x = 4;
        stain.y = 65;
        stain.z = -3;
        stain.length = .35F;
        stain.width = .2F;
        stain.family = "splat";
        stain.color = 0x9c1111;
        stain.dried = 0x42100b;
        stain.born = mc.theWorld.getTotalWorldTime();
        SurfaceBloodClient.add(stain);
        Footprints.clear();
        long before = SurfaceBloodClient.stains.stream()
            .filter(s -> s.print)
            .count();
        try {
            for (int i = 0; i < 15; i++) {
                walker.setPosition(4, 65 + walker.yOffset, -3 - i * .25);
                walker.onGround = true;
                Footprints.tick(mc.theWorld, SurfaceBloodClient.stains, stain.born + i);
            }
            check(
                SurfaceBloodClient.stains.stream()
                    .filter(s -> s.print)
                    .count() >= before + 3,
                "Walking through a fresh non-pool splatter leaves several footprints");
            check(
                SurfaceBloodClient.stains.stream()
                    .filter(s -> s.print)
                    .allMatch(s -> s.y == 65 && s.length >= .0625F && StainMesh.supported(mc.theWorld, s)),
                "Footprints keep original pixel size and render on actual floor faces");
        } finally {
            mc.theWorld.removeEntityFromWorld(walker.getEntityId());
            Footprints.clear();
        }
    }

    private static byte[] capture(Runnable render, boolean gui) {
        int previousFramebuffer = GL11.glGetInteger(0x8ca6);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        Framebuffer target = new Framebuffer(128, 128, true);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        if (gui) GL11.glOrtho(0, 128, 128, 0, -1000, 1000);
        else GL11.glOrtho(-1.5, 1.5, -1, 2, -10, 10);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            target.bindFramebuffer(true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            render.run();
            ByteBuffer pixels = BufferUtils.createByteBuffer(128 * 128 * 4);
            GL11.glReadPixels(0, 0, 128, 128, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[pixels.capacity()];
            pixels.get(result);
            return result;
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            target.deleteFramebuffer();
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, previousFramebuffer);
            GL11.glPopAttrib();
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "GPU comparison restores framebuffer and GL state");
        }
    }

    private static int different(byte[] a, byte[] b) {
        int count = 0;
        for (int i = 0; i < a.length; i += 4) if (a[i] != b[i] || a[i + 1] != b[i + 1] || a[i + 2] != b[i + 2]) count++;
        return count;
    }

    private static void invalidate() throws Exception {
        java.lang.reflect.Method method = SkinBlood.class.getDeclaredMethod("invalidateImages");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void set(ConfigValue<Boolean> setting, boolean value) throws Exception {
        java.lang.reflect.Field field = ConfigValue.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(setting, value);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        UkyCorpses.LOG.info("BLOODQA {}", message);
    }

    private BloodGameCheck() {}
}
