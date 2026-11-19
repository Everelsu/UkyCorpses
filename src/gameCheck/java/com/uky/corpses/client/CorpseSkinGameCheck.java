package com.uky.corpses.client;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class CorpseSkinGameCheck {

    public static void run() {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager.instance.renderEngine = mc.getTextureManager();
        CorpseClient.renderer.setRenderManager(RenderManager.instance);
        if (Loader.isModLoaded("hbm")) checkCanceledHelmet(mc);
        Framebuffer target = new Framebuffer(64, 64, true);
        try {
            for (int height : new int[] { 32, 64 }) {
                DynamicTexture skin = new DynamicTexture(64, height);
                int[] pixels = skin.getTextureData();
                for (int row = 0; row < 16; row++) for (int col = 0; col < 64; col++)
                    pixels[row * 64 + col] = col < 32 ? 0xFF0044FF : row < 12 ? 0xFF00FF00 : 0;
                skin.updateDynamicTexture();
                ResourceLocation id = mc.getTextureManager()
                    .getDynamicTextureLocation("qa_headwear", skin);
                try {
                    draw(mc, target, id);
                    ByteBuffer color = GLAllocation.createDirectByteBuffer(4);
                    GL11.glReadPixels(32, 50, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
                    if ((color.get(1) & 255) < 200 || (color.get(2) & 255) > 30)
                        throw new AssertionError("Armored corpse lost headwear on 64x" + height + " skin");
                    GL11.glReadPixels(32, 35, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
                    if ((color.get(2) & 255) < 200 || (color.get(1) & 255) > 100)
                        throw new AssertionError("Transparent headwear occluded the face on 64x" + height + " skin");
                } finally {
                    mc.getTextureManager()
                        .deleteTexture(id);
                }
            }
        } finally {
            target.deleteFramebuffer();
            mc.getFramebuffer()
                .bindFramebuffer(true);
        }
    }

    private static void checkCanceledHelmet(Minecraft mc) {
        RenderPlayer renderer = new RenderPlayer();
        ItemStack previous = mc.thePlayer.getCurrentArmor(3);
        CancelHelmetFrame cancel = new CancelHelmetFrame(renderer);
        try {
            net.minecraft.item.Item helmet = (net.minecraft.item.Item) Class.forName("com.hbm.items.ModItems")
                .getField("ajro_helmet")
                .get(null);
            mc.thePlayer.inventory.armorInventory[3] = new ItemStack(helmet);
            MinecraftForge.EVENT_BUS.register(cancel);
            renderer.doRender(mc.thePlayer, 0, 0, 0, 0, 0);
            if (!cancel.hidden || renderer.modelBipedMain.bipedHeadwear.isHidden)
                throw new AssertionError("Canceled HBM helmet frame leaked hidden headwear");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not equip HBM test helmet", failure);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
            mc.thePlayer.inventory.armorInventory[3] = previous;
        }
    }

    public static final class CancelHelmetFrame {

        private final RenderPlayer renderer;
        private boolean hidden;

        CancelHelmetFrame(RenderPlayer renderer) {
            this.renderer = renderer;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void pre(RenderPlayerEvent.Pre event) {
            if (event.renderer != renderer) return;
            hidden = renderer.modelBipedMain.bipedHeadwear.isHidden;
            event.setCanceled(true);
        }
    }

    private static void draw(Minecraft mc, Framebuffer target, ResourceLocation skin) {
        target.framebufferClear();
        target.bindFramebuffer(true);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(-.5, .5, .5, -.5, -4, 4);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            ItemStack[] armor = { null, null, new ItemStack(Items.iron_chestplate), null };
            CorpseClient.renderer.drawPhysical(
                mc.thePlayer,
                skin,
                armor,
                null,
                Skeleton.snapshot(new Mat4(), new float[15]),
                RenderCorpse.ALIVE,
                0,
                0,
                0);
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        }
    }

    private CorpseSkinGameCheck() {}
}
