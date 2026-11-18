package com.uky.corpses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITickableTextureObject;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.MouseEvent;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.uky.corpses.CorpsePart;
import com.uky.corpses.EntityCorpse;
import com.uky.corpses.MsgUse;
import com.uky.corpses.TileCorpse;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The crosshair on a body hits its limbs, not boxes around it. Entity bodies do that through CorpsePart; a body lying
 * as blocks only gets the bits of limb over its own two blocks from the block ray trace, so an arm flung out over the
 * next block is caught here: after the game's own pick each tick (a tickable texture is the hook that runs between the
 * pick and the clicks), the ray is tried against every nearby body's limbs and the nearest hit wins. That's also what
 * lets an entity body be reached as far as a block, not only the 3 blocks the game gives entities. The limb under the
 * crosshair gets an outline of its own.
 *
 * Clicks on an entity body don't go the game's way (MsgUse says why): they're sent as MsgUse, and a right-click held
 * down is sent again every 4 ticks, as the game repeats one.
 */
public final class CorpsePick extends AbstractTexture implements ITickableTextureObject {

    private static final int[] HIT = new int[1];
    /** The entity body a right-click is held on, the button, ticks held; null when none. */
    private static EntityCorpse held;
    private static int heldButton, heldTicks, heldPart;

    static void init() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .loadTickableTexture(new ResourceLocation(UkyCorpses.MODID, "pick"), new CorpsePick());
    }

    @Override
    public void loadTexture(IResourceManager m) {}

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            refine(mc);
        } catch (Exception ex) {
            // a pick that couldn't be refined stays the game's own
        }
        if (held == null) return;
        if (mc.theWorld == null || held.worldObj != mc.theWorld
            || mc.currentScreen != null
            || !Mouse.isButtonDown(heldButton)
            || entityBody(mc.objectMouseOver) != held) held = null;
        else if (++heldTicks % 4 == 0) UkyCorpses.net.sendToServer(new MsgUse(held.getEntityId(), false, heldPart));
    }

    /** A right-click is being held on a body lying as an entity (the game doesn't know: the press was ours). */
    static boolean holding() {
        return held != null;
    }

    /** A click on an entity body: ours, not the game's (block bodies go the way of any block). */
    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.button < 0 || !e.buttonstate) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.thePlayer == null || !mc.inGameHasFocus) return;
        GameSettings g = mc.gameSettings;
        boolean use = e.button - 100 == g.keyBindUseItem.getKeyCode(),
            hit = e.button - 100 == g.keyBindAttack.getKeyCode();
        EntityCorpse b = use || hit ? entityBody(mc.objectMouseOver) : null;
        if (b == null) return;
        e.setCanceled(true);
        mc.thePlayer.swingItem();
        int part = selectedPart(mc, b);
        UkyCorpses.net.sendToServer(new MsgUse(b.getEntityId(), hit, part));
        if (use) {
            held = b;
            heldButton = e.button;
            heldTicks = 0;
            heldPart = part;
        }
    }

    private static int selectedPart(Minecraft mc, EntityCorpse body) {
        EntityLivingBase view = mc.renderViewEntity;
        if (view == null) return Skeleton.BODY;
        Vec3 from = view.getPosition(1), look = view.getLook(1);
        return ray(body, from, look, mc.playerController.getBlockReachDistance() + 1) >= 0 ? HIT[0] : Skeleton.BODY;
    }

    /** The body lying as an entity that t is on, by a limb or whole; null if none. */
    private static EntityCorpse entityBody(MovingObjectPosition t) {
        if (t == null || t.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return null;
        EntityCorpse b = t.entityHit instanceof CorpsePart ? ((CorpsePart) t.entityHit).body
            : t.entityHit instanceof EntityCorpse ? (EntityCorpse) t.entityHit : null;
        return b == null || b.isDead || b.host != null ? null : b;
    }

    private static void refine(Minecraft mc) {
        EntityLivingBase v = mc.renderViewEntity;
        if (mc.theWorld == null || v == null || mc.playerController == null) return;
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 from = v.getPosition(1.0F), look = v.getLook(1.0F);
        MovingObjectPosition cur = mc.objectMouseOver;
        double best = cur == null || cur.typeOfHit == MovingObjectPosition.MovingObjectType.MISS || cur.hitVec == null
            ? reach
            : cur.hitVec.distanceTo(from);
        TileCorpse found = null;
        EntityCorpse foundBody = null;
        for (Object o : mc.theWorld.loadedTileEntityList) {
            if (!(o instanceof TileCorpse)) continue;
            double t = ray(((TileCorpse) o).body, from, look, reach);
            if (t >= 0 && t < best - 1e-3) {
                best = t;
                found = (TileCorpse) o;
            }
        }
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityCorpse)) continue;
            double t = ray((EntityCorpse) o, from, look, reach);
            if (t >= 0 && t < best - 1e-3) {
                best = t;
                found = null;
                foundBody = (EntityCorpse) o;
            }
        }
        if (found == null && foundBody == null) return;
        Vec3 hit = from.addVector(look.xCoord * best, look.yCoord * best, look.zCoord * best);
        mc.objectMouseOver = found != null ? new MovingObjectPosition(found.xCoord, found.yCoord, found.zCoord, 1, hit)
            : new MovingObjectPosition(foundBody, hit);
    }

    /** How far along the look from `from` b's limbs are hit, -1 if they aren't (or b is gone, or far off). */
    private static double ray(EntityCorpse b, Vec3 from, Vec3 look, double reach) {
        if (b == null || b.isDead
            || !b.ragdollActive() && b.getDistanceSq(from.xCoord, from.yCoord, from.zCoord) > (reach + 3) * (reach + 3))
            return -1;
        return b.drawnShape(1)
            .ray(
                from.xCoord - b.posX,
                from.yCoord - b.posY,
                from.zCoord - b.posZ,
                look.xCoord,
                look.yCoord,
                look.zCoord,
                reach,
                HIT);
    }

    /** The body under the crosshair, by a limb entity or by one of its blocks; null if none. */
    static EntityCorpse target(Minecraft mc, MovingObjectPosition t) {
        if (t == null) return null;
        if (t.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            if (t.entityHit instanceof CorpsePart) return ((CorpsePart) t.entityHit).body;
            return t.entityHit instanceof EntityCorpse ? (EntityCorpse) t.entityHit : null;
        }
        if (t.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || mc.theWorld.getBlock(t.blockX, t.blockY, t.blockZ) != UkyCorpses.block) return null;
        return TileCorpse.bodyAt(mc.theWorld, t.blockX, t.blockY, t.blockZ);
    }

    /** Instead of a box around the body's block: the outline of the limb the crosshair is on. */
    @SubscribeEvent
    public void onHighlight(DrawBlockHighlightEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityCorpse b = target(mc, e.target);
        if (b == null) return;
        if (e.target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) e.setCanceled(true);
        EntityLivingBase v = mc.renderViewEntity;
        Vec3 from = v.getPosition(e.partialTicks), look = v.getLook(e.partialTicks);
        double reach = mc.playerController.getBlockReachDistance() + 1;
        Skeleton.Shape s = b.drawnShape(e.partialTicks);
        if (s.ray(
            from.xCoord - b.posX,
            from.yCoord - b.posY,
            from.zCoord - b.posZ,
            look.xCoord,
            look.yCoord,
            look.zCoord,
            reach,
            HIT) < 0) return;
        outline(
            s.parts[HIT[0]],
            HIT[0],
            b.posX - RenderManager.renderPosX,
            b.posY - RenderManager.renderPosY,
            b.posZ - RenderManager.renderPosZ);
    }

    /** The twelve edges of a part's box, a hair outside it, the way the game outlines blocks. */
    private static void outline(Mat4 part, int i, double x, double y, double z) {
        float[] lo = Skeleton.min(i), hi = Skeleton.max(i);
        double[][] c = new double[8][3];
        for (int k = 0; k < 8; k++) part.apply(
            (k & 1) == 0 ? lo[0] - 0.3 : hi[0] + 0.3,
            (k & 2) == 0 ? lo[1] - 0.3 : hi[1] + 0.3,
            (k & 4) == 0 ? lo[2] - 0.3 : hi[2] + 0.3,
            c[k]);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(0, 0, 0, 0.4F);
        GL11.glLineWidth(2);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        for (int k = 0; k < 8; k++) for (int bit = 1; bit < 8; bit <<= 1) if ((k & bit) == 0) {
            int j = k | bit;
            t.addVertex(x + c[k][0], y + c[k][1], z + c[k][2]);
            t.addVertex(x + c[j][0], y + c[j][1], z + c[j][2]);
        }
        t.draw();
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
