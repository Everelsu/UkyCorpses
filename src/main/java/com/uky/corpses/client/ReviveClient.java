package com.uky.corpses.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.Downed;
import com.uky.corpses.MsgDowned;
import com.uky.corpses.MsgGiveUp;
import com.uky.corpses.MsgReviveInput;
import com.uky.corpses.OriginalGameplay;
import com.uky.corpses.Reactions;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Revival input, heartbeat, eyelids and knocked-health UI. It never owns or moves the camera. */
public final class ReviveClient extends Gui {

    private static final ResourceLocation HEART = new ResourceLocation("ukycorpses_revive", "heartbeat"),
        ICONS = new ResourceLocation("textures/gui/icons.png"),
        MARKER = new ResourceLocation("ukycorpses_revive", "textures/gui/downed_marker.png");
    private static final boolean MARKER_API = markerApi();
    public static final net.minecraft.client.settings.KeyBinding SELF_ADRENALINE = new net.minecraft.client.settings.KeyBinding(
        "ukycorpses.key.selfAdrenaline",
        org.lwjgl.input.Keyboard.KEY_G,
        "UkyCorpses");
    private final Random random = new Random();
    private World world;
    private EntityPlayer player;
    private int ticks;
    private static int held;
    private static boolean wasHeld;
    private long nextBeat = Long.MIN_VALUE, lastBeat = Long.MIN_VALUE, secondBeat = Long.MIN_VALUE,
        healthHit = Long.MIN_VALUE;
    private float secondVolume, secondPitch, lastHealth = -1, ghostHealth;

    public static void init() {
        cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(SELF_ADRENALINE);
        ReviveClient client = new ReviveClient();
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance()
            .bus()
            .register(client);
        if (Minecraft.getMinecraft()
            .getResourceManager() instanceof IReloadableResourceManager)
            ((IReloadableResourceManager) Minecraft.getMinecraft()
                .getResourceManager()).registerReloadListener(manager -> PrDesaturation.reload());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld || player != mc.thePlayer) {
            if (world != null || player != null) OriginalGameplay.clearClient();
            if (player != null) {
                Reactions.forgetClient(player.getEntityId());
                Downed.seen.remove(player.getEntityId());
            }
            reset();
            world = mc.theWorld;
            player = mc.thePlayer;
        }
        ticks++;
        DragArmPose.tick();
        if (player == null || world == null) return;
        int dragged = Reactions.draggedEntity(player);
        if (dragged >= 0 && ticks % 10 == 0) {
            Entity target = world.getEntityByID(dragged);
            if (target != null) UkyCorpses.net
                .sendToServer(new com.uky.corpses.MsgDragInput(dragged, Reactions.physicalLife(target), true));
        }
        boolean downed = downed(mc);
        while (SELF_ADRENALINE.isPressed())
            if (downed && mc.currentScreen == null && com.uky.corpses.medicine.Medicine.selfAdrenaline)
                UkyCorpses.net.sendToServer(new com.uky.corpses.MsgSelfAdrenaline());
        boolean holding = downed && mc.currentScreen == null && mc.gameSettings.keyBindJump.getIsKeyPressed();
        if (holding) {
            held = Math.min(OriginalGameplay.GIVE_UP_TICKS, held + 1);
            UkyCorpses.net.sendToServer(new MsgGiveUp(true));
        } else {
            if (wasHeld) UkyCorpses.net.sendToServer(new MsgGiveUp(false));
            held = 0;

        }
        wasHeld = holding;
        if (!downed && !Reactions.isDragging(player)
            && mc.currentScreen == null
            && !player.isSneaking()
            && mc.gameSettings.keyBindUseItem.getIsKeyPressed()
            && ticks % 3 == 0) {
            EntityPlayer target = hit(mc);
            if (target != null) UkyCorpses.net.sendToServer(new MsgReviveInput(target.getEntityId()));
        }
        if (downed) {
            heartbeat(mc);
            OriginalGameplay.ClientState state = OriginalGameplay.seen.get(player.getEntityId());
            if (state != null) {
                if (lastHealth >= 0 && state.health < lastHealth) {
                    ghostHealth = lastHealth;
                    healthHit = world.getTotalWorldTime();
                } else if (lastHealth < 0 || world.getTotalWorldTime() - healthHit >= 20) ghostHealth = state.health;
                lastHealth = state.health;
            }
        } else {
            nextBeat = lastBeat = secondBeat = Long.MIN_VALUE;
            lastHealth = -1;
            PrDesaturation.release();
        }
    }

    private void reset() {
        held = 0;
        wasHeld = false;

        nextBeat = lastBeat = secondBeat = healthHit = Long.MIN_VALUE;
        lastHealth = -1;
        PrDesaturation.release();
        DragArmPose.clear();
    }

    private static boolean downed(Minecraft mc) {
        return mc.thePlayer != null && mc.thePlayer.getHealth() > 0 && Downed.is(mc.thePlayer);
    }

    /** Original ray length is five blocks, but successful revival also requires torso distance <= 2.5 blocks. */
    private static EntityPlayer hit(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        Vec3 from = mc.thePlayer.getPosition(1), direction = mc.thePlayer.getLook(1);
        Vec3 end = from.addVector(direction.xCoord * 5, direction.yCoord * 5, direction.zCoord * 5);
        MovingObjectPosition block = mc.theWorld.rayTraceBlocks(from, end);
        double nearest = block == null ? 5 : from.distanceTo(block.hitVec);
        EntityPlayer chosen = null;
        for (Object candidate : mc.theWorld.playerEntities) {
            EntityPlayer target = (EntityPlayer) candidate;
            if (target == mc.thePlayer || !Downed.is(target) || Reactions.isDragged(target)) continue;
            Skeleton.Shape shape = Reactions.physicalShape(target, 1);
            if (shape == null) {
                MovingObjectPosition hit = target.boundingBox.calculateIntercept(from, end);
                if (target.getDistanceSqToEntity(mc.thePlayer) <= 6.25 && hit != null
                    && from.distanceTo(hit.hitVec) < nearest) {
                    nearest = from.distanceTo(hit.hitVec);
                    chosen = target;
                }
                continue;
            }
            double[] torso = shape.parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
            if (mc.thePlayer.getDistanceSq(target.posX + torso[0], target.posY + torso[1], target.posZ + torso[2])
                > 6.25) continue;
            double distance = shape.ray(
                from.xCoord - target.posX,
                from.yCoord - target.posY,
                from.zCoord - target.posZ,
                direction.xCoord,
                direction.yCoord,
                direction.zCoord,
                nearest,
                null);
            if (distance >= 0 && distance < nearest) {
                nearest = distance;
                chosen = target;
            }
        }
        return chosen;
    }

    private void heartbeat(Minecraft mc) {
        long now = world.getTotalWorldTime();
        if (secondBeat != Long.MIN_VALUE && now >= secondBeat) {
            playBeat(mc, secondVolume, secondPitch);
            secondBeat = Long.MIN_VALUE;
        }
        if (nextBeat == Long.MIN_VALUE) nextBeat = now + 8;
        if (now >= nextBeat) {
            float progress = progress(remaining(0));
            float volume = .5F + .5F * progress, pitch = .8F + .32F * progress;
            playBeat(mc, volume, pitch);
            lastBeat = now;
            secondBeat = now + Math.max(2, Math.round(5 - 2 * progress));
            secondVolume = volume * .66F;
            secondPitch = pitch * .92F;
            nextBeat = now + Math.max(8, Math.round(30 - 18 * progress));
        }
    }

    private static void playBeat(Minecraft mc, float volume, float pitch) {
        Entity camera = mc.renderViewEntity == null ? mc.thePlayer : mc.renderViewEntity;
        if (camera == null) return;
        mc.getSoundHandler()
            .playSound(
                new PositionedSoundRecord(
                    HEART,
                    volume,
                    pitch,
                    (float) camera.posX,
                    (float) camera.posY,
                    (float) camera.posZ));
    }

    private float remaining(float partial) {
        MsgDowned state = Downed.seen.get(player.getEntityId());
        if (state == null) return Float.MAX_VALUE;
        OriginalGameplay.ClientState detail = OriginalGameplay.seen.get(player.getEntityId());
        return Math
            .max(0, state.left - (detail != null && detail.paused ? 0 : DownedClient.ticks - state.at + partial));
    }

    private float progress(float remaining) {
        MsgDowned state = Downed.seen.get(player.getEntityId());
        return state == null ? 0 : MathHelper.clamp_float(1 - remaining / Math.max(1, state.leftMax), 0, 1);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void pre(RenderGameOverlayEvent.Pre event) {
        if (com.uky.corpses.OriginalGameplay.crawlPresentation()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!downed(mc)) return;
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            if (player != null) PrDesaturation.render(
                mc,
                PrOverlay.saturation(progress(remaining(event.partialTicks)), remaining(event.partialTicks)),
                event.partialTicks);
        } else if (OriginalGameplay.hideHudWhenDowned
            || event.type == RenderGameOverlayEvent.ElementType.HEALTH && OriginalGameplay.showDownedHealth
                && OriginalGameplay.seen.containsKey(mc.thePlayer.getEntityId()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void overlay(RenderGameOverlayEvent.Post event) {
        if (com.uky.corpses.OriginalGameplay.crawlPresentation()) return;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        int w = event.resolution.getScaledWidth(), h = event.resolution.getScaledHeight();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            if (downed(mc) && player != null) {
                float left = remaining(event.partialTicks), drain = progress(left);
                MsgDowned state = Downed.seen.get(player.getEntityId());
                double time = world.getTotalWorldTime() + event.partialTicks;
                float pulse = lastBeat == Long.MIN_VALUE ? 0 : Math.max(0, 1 - (float) (time - lastBeat) / 7);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glShadeModel(GL11.GL_SMOOTH);
                PrOverlay.render(
                    w,
                    h,
                    PrOverlay.closure(drain, left, state == null ? 0 : state.leftMax, time),
                    drain,
                    left,
                    pulse * pulse);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                OriginalGameplay.ClientState detail = OriginalGameplay.seen.get(player.getEntityId());
                if (detail != null) {
                    if (OriginalGameplay.showBeingRevivedPrompt && detail.progress > 0) {
                        centre(
                            mc,
                            "Being revived: " + Math.round(detail.progress * 100) + "%",
                            w,
                            h / 2 - 42,
                            colour(detail.progress));
                        bar(
                            w / 2 - 55,
                            h / 2 - 29,
                            110,
                            5,
                            detail.progress,
                            0xF0000000 | colour(detail.progress),
                            0xB0202024);
                    }
                    if (OriginalGameplay.showDownedHealth) hearts(mc, detail, w, h);
                }
                if (OriginalGameplay.showDownedPrompt) surrender(mc, w, surrenderY(h), event.partialTicks);
            } else if (OriginalGameplay.showRevivePrompt) {
                EntityPlayer target = hit(mc);
                OriginalGameplay.ClientState state = target == null ? null
                    : OriginalGameplay.seen.get(target.getEntityId());
                if (state != null && state.reviver == mc.thePlayer.getEntityId() && state.progress > 0) centre(
                    mc,
                    "Reviving: " + Math.round(state.progress * 100) + "%",
                    w,
                    h / 2 + 26,
                    colour(state.progress));
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static int surrenderY(int h) {
        return Math.max(
            24,
            h - Math.max(
                75,
                Math.max(
                    net.minecraftforge.client.GuiIngameForge.left_height,
                    net.minecraftforge.client.GuiIngameForge.right_height) + 25));
    }

    public static void surrender(Minecraft mc, int w, int y, float partial) {
        float progress = MathHelper
            .clamp_float((held + (wasHeld ? partial : 0)) / OriginalGameplay.GIVE_UP_TICKS, 0, 1);
        String key = GameSettings.getKeyDisplayString(mc.gameSettings.keyBindJump.getKeyCode());
        String label = net.minecraft.util.StatCollector.translateToLocalFormatted("ukycorpses.downed.holdGiveup", key);
        Fancy.text(
            mc.fontRenderer,
            label,
            (w - mc.fontRenderer.getStringWidth(label)) / 2F,
            y + 5,
            progress > 0 ? Palette.text() : Palette.dim(),
            1,
            true);
        float x = w / 2F - 48;
        Fancy.rect(x, y + 17, x + 96, y + 18, Palette.dim(), .3F);
        if (progress > 0) Fancy.rect(x, y + 17, x + 96 * progress, y + 18, Palette.accent(), 1);
    }

    private void hearts(Minecraft mc, OriginalGameplay.ClientState state, int w, int h) {
        int current = MathHelper.ceiling_float_int(Math.min(state.health, state.maxHealth)),
            ghost = MathHelper.ceiling_float_int(Math.min(ghostHealth, state.maxHealth));
        int count = MathHelper.ceiling_float_int(state.maxHealth / 2), rows = (count + 9) / 10;
        int rowHeight = Math.max(10 - (rows - 2), 3), left = w / 2 - 91, base = h - 39;
        long now = world.getTotalWorldTime(), since = healthHit == Long.MIN_VALUE ? Long.MAX_VALUE : now - healthHit;
        boolean blink = since < 20 && (20 - since) / 3 % 2 == 1;
        int v = mc.theWorld.getWorldInfo()
            .isHardcoreModeEnabled() ? 45 : 0;
        random.setSeed(now * 312871);
        mc.getTextureManager()
            .bindTexture(ICONS);
        GL11.glColor4f(1, 1, 1, 1);
        for (int i = count - 1; i >= 0; i--) {
            int x = left + i % 10 * 8, y = base - i / 10 * rowHeight + (current <= 4 ? random.nextInt(2) : 0);
            drawTexturedModalRect(x, y, blink ? 25 : 16, v, 9, 9);
            if (blink && i * 2 < ghost) drawTexturedModalRect(x, y, i * 2 + 1 == ghost ? 79 : 70, v, 9, 9);
            if (i * 2 < current) drawTexturedModalRect(x, y, i * 2 + 1 == current ? 61 : 52, v, 9, 9);
        }
    }

    private static void centre(Minecraft mc, String text, int w, int y, int colour) {
        mc.fontRenderer
            .drawStringWithShadow(text, (w - mc.fontRenderer.getStringWidth(text)) / 2, y, 0xFF000000 | colour);
    }

    private static int colour(float progress) {
        int[] from = progress < .333F ? new int[] { 255, 48, 48 }
            : progress < .666F ? new int[] { 255, 150, 0 } : new int[] { 255, 235, 0 };
        int[] to = progress < .333F ? new int[] { 255, 150, 0 }
            : progress < .666F ? new int[] { 255, 235, 0 } : new int[] { 60, 255, 60 };
        float t = progress * 3 % 1;
        return (int) (from[0] + (to[0] - from[0]) * t) << 16 | (int) (from[1] + (to[1] - from[1]) * t) << 8
            | (int) (from[2] + (to[2] - from[2]) * t);
    }

    private static void soft(int x, int y, int w, int h, int colour) {
        drawRect(x + 1, y, x + w - 1, y + h, colour);
        drawRect(x, y + 1, x + w, y + h - 1, colour);
    }

    private static void bar(int x, int y, int w, int h, float progress, int colour, int track) {
        soft(x, y, w, h, track);
        int fill = MathHelper.ceiling_float_int(w * MathHelper.clamp_float(progress, 0, 1));
        if (fill >= w) soft(x, y, w, h, colour);
        else if (fill > 0) {
            if (fill > 1) drawRect(x + 1, y, x + fill, y + h, colour);
            drawRect(x, y + 1, x + fill, y + h - 1, colour);
        }
    }

    private static boolean blocks(boolean attack) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!attack && mc.thePlayer != null && com.uky.corpses.medicine.Medicine.maySelfAdrenaline(mc.thePlayer))
            return false;
        return mc.thePlayer != null
            && (Reactions.isDragging(mc.thePlayer) || downed(mc) && (attack || OriginalGameplay.clientBlocksActions()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.buttonstate && event.button == 1 && mc.thePlayer != null && mc.currentScreen == null) {
            int dragged = Reactions.draggedEntity(mc.thePlayer);
            Entity target = dragged < 0 ? null : mc.theWorld.getEntityByID(dragged);
            if (target != null) {
                UkyCorpses.net
                    .sendToServer(new com.uky.corpses.MsgDragInput(dragged, Reactions.physicalLife(target), false));
                event.setCanceled(true);
                return;
            }
        }
        if (event.buttonstate && event.button == 0 && blocks(true)) event.setCanceled(true);
        else if (event.buttonstate && event.button == 1 && downed(Minecraft.getMinecraft()) && blocks(false))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.entityPlayer == Minecraft.getMinecraft().thePlayer
            && blocks(event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attack(AttackEntityEvent event) {
        if (event.entityPlayer == Minecraft.getMinecraft().thePlayer && blocks(true)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void hand(RenderHandEvent event) {
        if (OriginalGameplay.hideHudWhenDowned && downed(Minecraft.getMinecraft())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void marker(RenderWorldLastEvent event) {
        if (com.uky.corpses.OriginalGameplay.crawlPresentation()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!MARKER_API || !OriginalGameplay.showDownedMarker || mc.thePlayer == null || mc.theWorld == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            mc.getTextureManager()
                .bindTexture(MARKER);
            for (Object value : mc.theWorld.playerEntities) {
                EntityPlayer target = (EntityPlayer) value;
                if (target == mc.thePlayer || !Downed.is(target)) continue;
                Skeleton.Shape shape = Reactions.physicalShape(target, event.partialTicks);
                if (shape == null) continue;
                double[] at = shape.parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
                double x = target.posX + at[0], y = target.posY + at[1], z = target.posZ + at[2];
                double distance = Math.sqrt(mc.renderViewEntity.getDistanceSq(x, y, z));
                float alpha = (float) Math.max(0, Math.min(1, (96 - distance) / 16));
                if (alpha <= .01F) continue;
                double time = mc.theWorld.getTotalWorldTime() + event.partialTicks + target.getEntityId() * 7;
                GL11.glPushMatrix();
                GL11.glTranslated(
                    x - RenderManager.renderPosX,
                    y + OriginalGameplay.downedMarkerHeight + Math.sin(time * .11) * .07 - RenderManager.renderPosY,
                    z - RenderManager.renderPosZ);
                GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
                GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
                GL11.glRotatef((float) (time * 2.2 % 360), 0, 0, 1);
                Tessellator t = Tessellator.instance;
                t.startDrawingQuads();
                t.setColorRGBA_F(1, 1, 1, alpha);
                t.setBrightness(15728880);
                t.addVertexWithUV(-.25, -.25, 0, 0, 1);
                t.addVertexWithUV(.25, -.25, 0, 1, 1);
                t.addVertexWithUV(.25, .25, 0, 1, 0);
                t.addVertexWithUV(-.25, .25, 0, 0, 0);
                t.draw();
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static boolean markerApi() {
        try {
            Class.forName(com.uky.corpses.compat.LegacyIds.INTERACTION_API, false, ReviveClient.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }
}
