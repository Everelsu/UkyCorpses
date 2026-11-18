package com.uky.corpses.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.uky.corpses.CorpseConfig;
import com.uky.corpses.Downed;
import com.uky.corpses.EntityCorpse;
import com.uky.corpses.MsgDowned;
import com.uky.corpses.MsgGiveUp;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Settle;
import com.uky.corpses.corpse.Situation;
import com.uky.corpses.corpse.Skeleton;
import com.uky.corpses.deathcam.DeathCamClient;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Downed players on screen: drawn as a body (face down crawling, or against the wall) instead of standing, a
 * countdown over the others, the HUD, a crawl instead of walking, and X to give up. While down your own camera lies on
 * the floor at your body's eyes and you see your arms pull you along; your box is a crawl's, so you get under a
 * one-block gap. The just-dead are drawn here too, until their body takes over (JustDied).
 */
public final class DownedClient {

    /** Ticks the fall takes. */
    private static final int FALL = 12;
    public static int ticks;
    /** Holding right-click on someone else's body: counted here too, only to draw the progress. */
    private int searching;
    /** The body being searched, from the click on it; null when none. */
    private EntityCorpse searchBody;
    /** Right-click down last tick: a search starts on a fresh click, not on one still held from before. */
    private boolean usePressed;

    private final RenderCorpse body;
    private final KeyBinding giveUp = new KeyBinding(
        "key.ukycorpses.giveup",
        Keyboard.KEY_X,
        "key.categories.ukycorpses");
    static DownedClient instance;
    /** The crawl camera, while you're down in first person. */
    private ViewEye eye;
    /** Crawl camera: how far it's still easing in from where the standing view was; its push off walls. */
    private final double[] shift = new double[3], e3 = new double[3], at = new double[3];
    private boolean fresh;
    private long frameAt;
    private final BodyCam.Pose pose = new BodyCam.Pose();
    /** How far down the last lyingFrame was, 0..1. */
    private float lastK;
    /** The local player was made low by us. */
    private boolean lowered;
    /** Other players whose box we made a crawl's (entity ids), to give back. */
    private final Set<Integer> reshaped = new HashSet<>();
    private final float[] angles = new float[Settle.N];
    private final Mat4 frame = new Mat4();

    /**
     * Another player's turn, eased on our side: their look comes over the wire in 5.6-degree steps with nothing in
     * between, and vanilla swings the body after the head. Standing that's a twitch; lying, the whole body jumps.
     */
    private static final class Look {

        float body, head, pitch;
        long at;
    }

    private final Map<Integer, Look> looks = new HashMap<>();

    DownedClient(RenderCorpse body) {
        this.body = body;
        instance = this;
        ClientRegistry.registerKeyBinding(giveUp);
    }

    /** Crawl at `scale` of walking (0 once too weak); never jump or sneak. */
    private static final class Pinned extends MovementInputFromOptions {

        float scale;

        Pinned(GameSettings s) {
            super(s);
        }

        @Override
        public void updatePlayerMoveState() {
            super.updatePlayerMoveState();
            moveStrafe *= scale;
            moveForward *= scale;
            jump = sneak = false;
        }
    }

    /** p's state if they're lying alive (downed, or crawling out from under something); null otherwise. */
    private static MsgDowned lying(EntityPlayer p) {
        MsgDowned d = Downed.seen.get(p.getEntityId());
        return d == null || d.diedAt >= 0 ? null : d;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        ticks++;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        // the dead are drawn until their body shows; after that, or once they're back (same entity id), forget them
        if (mc.theWorld != null) for (Iterator<MsgDowned> it = Downed.seen.values()
            .iterator(); it.hasNext();) {
                MsgDowned d = it.next();
                if (d.diedAt < 0) continue;
                Entity who = mc.theWorld.getEntityByID(d.entity);
                // kept while they stay dead: with no body coming they lie as they lay until they're back
                if (who == null || ticks - d.diedAt > 5 && who instanceof EntityLivingBase
                    && ((EntityLivingBase) who).getHealth() > 0) it.remove();
            }
        looks.keySet()
            .retainAll(Downed.seen.keySet());
        if (mc.theWorld == null) bodies.clear();
        else for (Iterator<Map.Entry<Integer, EntityCorpse>> it = bodies.entrySet()
            .iterator(); it.hasNext();) {
                Entity who = mc.theWorld.getEntityByID(
                    it.next()
                        .getKey());
                if (!(who instanceof EntityLivingBase) || ((EntityLivingBase) who).getHealth() > 0) it.remove();
            }
        JustDied.tick(mc, ticks);
        if (p == null) {
            Downed.seen.clear();
            reshaped.clear();
            lowered = false;
            dropEye(mc);
            return;
        }
        // a body as blocks or as an entity (whose right-click CorpsePick holds, not the game)
        EntityCorpse aim = CorpsePick.target(mc, mc.objectMouseOver);
        // one click on a body not yours starts the search; it runs on while you stay near (EntityCorpse.tickSearch)
        boolean press = mc.gameSettings.keyBindUseItem.getIsKeyPressed() || CorpsePick.holding(),
            fresh = press && !usePressed;
        usePressed = press;
        if (searchBody == null && aim != null
            && mc.currentScreen == null
            && fresh
            && !p.isSneaking()
            && !aim.owner()
                .equals(p.getCommandSenderName())) {
            searchBody = aim;
            searching = 0;
        }
        if (searchBody != null && (mc.currentScreen != null || searchBody.isDead
            || p.getDistanceSq(searchBody.posX, searchBody.posY, searchBody.posZ) > EntityCorpse.SEARCH_REACH
            || searching >= EntityCorpse.search())) searchBody = null;
        searching = searchBody != null ? searching + 1 : 0;
        for (MsgDowned d : Downed.seen.values()) if (d.diedAt >= 0 && Float.isNaN(d.deadYaw)) {
            // as they lay when they died: the body that comes next starts from exactly that
            Entity who = mc.theWorld.getEntityByID(d.entity);
            if (!(who instanceof EntityPlayer)) continue;
            EntityPlayer dead = (EntityPlayer) who;
            d.deadYaw = dead.renderYawOffset;
            d.deadArmor = new ItemStack[4];
            for (int i = 0; i < 4; i++) d.deadArmor[i] = dead.getCurrentArmor(i);
        }
        MsgDowned me = lying(p);
        // the server says you died a moment before your health reaches 0: you stay down, in the crawl camera, until
        // the death camera takes over from exactly there
        MsgDowned dying = Downed.seen.get(p.getEntityId());
        boolean going = me == null && dying != null && dying.diedAt >= 0 && ticks - dying.diedAt <= 10;
        if ((me != null || going) != p.movementInput instanceof Pinned)
            p.movementInput = me != null || going ? new Pinned(mc.gameSettings)
                : new MovementInputFromOptions(mc.gameSettings);
        shapes(mc, p, me != null || going);
        boolean cam = (me != null || going) && p.getHealth() > 0
            && mc.gameSettings.thirdPersonView == 0
            && !DeathCamClient.busy();
        if (cam && eye == null) {
            eye = new ViewEye(mc.theWorld, p);
            fresh = true;
        } else if (!cam) dropEye(mc);
        if (going) ((Pinned) p.movementInput).scale = 0;
        if (me == null) return;
        ((Pinned) p.movementInput).scale = me.up ? 0.45F : me.crawl ? CorpseConfig.crawlSpeed / 100F : 0;
        if (!me.up && giveUp.isPressed()) UkyCorpses.net.sendToServer(new MsgGiveUp());
    }

    /**
     * Boxes: yours a crawl's while you lie (until there's room to stand), others' flat and wide while they're down so
     * a reviver's click finds them anywhere along the body.
     */
    private void shapes(Minecraft mc, EntityPlayerSP p, boolean low) {
        if (low) {
            Downed.height(p, Downed.LOW);
            lowered = true;
        } else if (lowered && Downed.room(p)) {
            Downed.height(p, Downed.TALL);
            lowered = false;
        }
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer other = (EntityPlayer) o;
            if (other == p) continue;
            MsgDowned d = lying(other);
            float w = d == null ? 0.6F : d.up ? 0.6F : 1.6F, h = d == null ? Downed.TALL : Downed.LOW;
            if (d == null && !reshaped.remove(other.getEntityId())) continue;
            if (d != null) reshaped.add(other.getEntityId());
            if (other.width == w && other.height == h) continue;
            other.width = w;
            other.height = h;
            AxisAlignedBB b = other.boundingBox;
            b.setBounds(
                other.posX - w / 2,
                b.minY,
                other.posZ - w / 2,
                other.posX + w / 2,
                b.minY + h,
                other.posZ + w / 2);
        }
    }

    private void dropEye(Minecraft mc) {
        if (eye == null) return;
        if (mc.renderViewEntity == eye) mc.renderViewEntity = mc.thePlayer;
        if (mc.thePlayer != null) mc.thePlayer.ignoreFrustumCheck = false;
        if (RenderCorpse.noHead == mc.thePlayer) {
            RenderCorpse.noHead = null;
            RenderCorpse.eyeAt = null;
            RenderCorpse.viewShift[0] = RenderCorpse.viewShift[1] = RenderCorpse.viewShift[2] = 0;
        }
        eye = null;
    }

    /**
     * The crawl camera: your own eye, fixed in your body's torso as it's drawn this frame (the same eye the death
     * camera uses), looking where you look. It eases in from the standing view as you go down, and keeps off walls;
     * your body is drawn moved along with it.
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || eye == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            crawlCamera(mc, e.renderTickTime);
        } catch (RuntimeException ex) { // never worth a crash: back to the plain camera
            UkyCorpses.LOG.warn("Crawl camera broke, plain camera", ex);
            dropEye(mc);
        }
    }

    private void crawlCamera(Minecraft mc, float pt) {
        EntityPlayerSP p = mc.thePlayer;
        MsgDowned me = p == null ? null : Downed.seen.get(p.getEntityId());
        if (me == null || mc.theWorld == null) return;
        float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt,
            pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        double y = Math.toRadians(yaw), x = Math.toRadians(pitch);
        double fx = -Math.sin(y) * Math.cos(x), fy = -Math.sin(x), fz = Math.cos(y) * Math.cos(x);
        long now = System.nanoTime();
        float dt = frameAt == 0 || mc.isGamePaused() ? 0 : Math.min(0.05F, (now - frameAt) / 1e9F);
        frameAt = now;
        lyingFrame(p, me, pt, pose.frame);
        System.arraycopy(angles, 0, pose.angles, 0, Settle.N);
        pose.pose = me.pose;
        pose.k = lastK;
        pose.x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        pose.y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - p.yOffset;
        pose.z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        BodyCam.eye(pose, e3);
        if (fresh) { // from the standing view (the game's camera is 0.1 behind its eye)
            fresh = false;
            EntityLivingBase v = mc.renderViewEntity == eye ? p : mc.renderViewEntity;
            shift[0] = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt - 0.1 * fx - e3[0];
            shift[1] = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt - 0.1 * fy - e3[1];
            shift[2] = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt - 0.1 * fz - e3[2];
            if (shift[0] * shift[0] + shift[1] * shift[1] + shift[2] * shift[2] > 4) shift[0] = shift[1] = shift[2] = 0;
        }
        float decay = (float) Math.exp(-6 * dt);
        for (int i = 0; i < 3; i++) {
            shift[i] *= decay;
            at[i] = e3[i] + shift[i];
        }
        double[] before = { at[0], at[1], at[2] };
        BodyCam.clear(mc.theWorld, at, BodyCam.nearCorner(mc.gameSettings.fovSetting, aspect(mc)) + 0.02, 0.3);
        for (int i = 0; i < 3; i++) RenderCorpse.viewShift[i] = shift[i] + at[i] - before[i];
        eye.place(at[0] + 0.1 * fx, at[1] + 0.1 * fy, at[2] + 0.1 * fz, yaw, pitch);
        mc.renderViewEntity = eye;
        p.ignoreFrustumCheck = true; // its box is at the feet, the body is drawn from there along the floor
        RenderCorpse.noHead = p;
        RenderCorpse.eyeAt = at;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    static double aspect(Minecraft mc) {
        return mc.displayHeight > 0 ? mc.displayWidth / (double) mc.displayHeight : 16 / 9.0;
    }

    /**
     * A player who died lying downed, as drawn this frame until their body takes over: false if p isn't one. For the
     * death camera, which starts out of it.
     */
    static boolean deadPose(EntityPlayer p, float pt, BodyCam.Pose out) {
        MsgDowned d = Downed.seen.get(p.getEntityId());
        DownedClient c = instance;
        if (d == null || d.diedAt < 0 || c == null) return false;
        c.lyingFrame(p, d, pt, out.frame);
        System.arraycopy(c.angles, 0, out.angles, 0, Settle.N);
        out.pose = d.pose;
        out.k = c.lastK;
        out.x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        out.y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - p.yOffset;
        out.z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        out.rest.set(out.frame);
        System.arraycopy(c.angles, 0, out.restAngles, 0, Settle.N);
        return true;
    }

    /** No floating first-person hand: your body's own arms are there instead. */
    @SubscribeEvent
    public void onHand(RenderHandEvent e) {
        if (eye != null && Minecraft.getMinecraft().renderViewEntity == eye) e.setCanceled(true);
    }

    /**
     * How a lying player is drawn this frame, relative to their feet: angles into this.angles, frame into out. Face
     * down they pull themselves along, one arm and the other, the body rising a little on each pull; a blast that
     * knocked them down tumbles them through the air first.
     */
    private Mat4 lyingFrame(EntityPlayer p, MsgDowned d, float pt, Mat4 out) {
        System.arraycopy(Settle.alive(d.pose), 0, angles, 0, Settle.N);
        float t = ticks - d.since + pt;
        boolean dead = d.diedAt >= 0;
        if (d.pose == Poses.PRONE) {
            float swing = p.limbSwing - p.limbSwingAmount * (1 - pt);
            float amount = dead ? 0
                : Math.min(1F, 1.6F * (p.prevLimbSwingAmount + (p.limbSwingAmount - p.prevLimbSwingAmount) * pt));
            float s = MathHelper.sin(swing * 0.6662F), c = MathHelper.cos(swing * 0.6662F);
            angles[1] = 0.2F * s * amount; // looks one way, then the other
            angles[3] = -3.15F + 0.45F * Math.max(0, s) * amount; // pulls with the right arm...
            angles[6] = -3.15F + 0.45F * Math.max(0, -s) * amount; // ...then the left
            angles[5] = 0.25F + 0.15F * c * amount;
            angles[8] = -0.25F + 0.15F * c * amount;
            angles[9] = 0.08F + 0.3F * Math.max(0, -s) * amount; // and pushes with the other leg
            angles[12] = 0.08F + 0.3F * Math.max(0, s) * amount;
            angles[11] = 0.12F + 0.1F * s * amount;
            angles[14] = -0.12F + 0.1F * s * amount;
        }
        float k = d.up ? 1 : Math.min(1F, t / FALL);
        k = k * k * (3 - 2 * k);
        lastK = k;
        float body = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * pt,
            head = p.prevRotationYawHead
                + MathHelper.wrapAngleTo180_float(p.rotationYawHead - p.prevRotationYawHead) * pt,
            pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        if (!dead && p != Minecraft.getMinecraft().thePlayer) {
            Look l = looks.get(p.getEntityId());
            long now = System.nanoTime();
            if (l == null) {
                looks.put(p.getEntityId(), l = new Look());
                l.body = body;
                l.head = head;
                l.pitch = pitch;
            } else {
                float dt = Math.min(0.1F, (now - l.at) / 1e9F);
                float slow = 1 - (float) Math.exp(-5 * dt), fast = 1 - (float) Math.exp(-12 * dt);
                l.body += MathHelper.wrapAngleTo180_float(body - l.body) * slow; // a body on the floor turns slowly
                l.head += MathHelper.wrapAngleTo180_float(head - l.head) * fast;
                l.pitch += (pitch - l.pitch) * fast;
            }
            l.at = now;
            body = l.body;
            head = l.head;
            pitch = l.pitch;
        }
        float yaw = d.pose == Poses.SIT ? d.yaw
            : !Float.isNaN(d.deadYaw) ? d.deadYaw // dead: lies as it lay
                : body;
        if (!dead) { // the head goes where they look, as far as a neck lets it
            float turn = (float) Math.toRadians(MathHelper.wrapAngleTo180_float(head - yaw)),
                tilt = (float) Math.toRadians(pitch);
            if (d.pose == Poses.SIT) {
                angles[0] = clamp(tilt, -0.6F, 0.9F);
                angles[1] = clamp(turn, -1.2F, 1.2F);
            } else if (d.pose == Poses.PRONE) {
                // face down the head points along the floor at x = -90 degrees; z turns it about the vertical
                angles[0] = clamp(tilt - (float) Math.PI / 2, -2.4F, -0.5F);
                angles[2] = -clamp(turn, -1F, 1F);
            }
            angles[0] += 0.04F * MathHelper.sin((ticks + pt) * 0.11F); // breathing
        }
        float spin = Float.NaN, hips = 0;
        if (d.blast && t < 30) { // tumbling through the air, landing face down
            float left = (float) Math.exp(-t / 5);
            spin = -90 - 330 * left;
            hips = Skeleton.HIPS * left;
        }
        return Skeleton.posed(out, d.pose, yaw, k, 0, spin, hips, angles, Situation.NONE, 0);
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre e) {
        EntityPlayer p = e.entityPlayer;
        RenderCorpse.remember(p);
        MsgDowned d = Downed.seen.get(p.getEntityId());
        // died while down: keep drawing them as they lay until their body takes over (it starts from this pose)
        if (d != null && d.diedAt >= 0 && bodyOf(p) != null) {
            Downed.seen.remove(p.getEntityId());
            d = null;
        }
        float pt = e.partialRenderTick;
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - RenderManager.renderPosX;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - p.yOffset - RenderManager.renderPosY;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - RenderManager.renderPosZ;
        if (d == null) {
            EntityCorpse b = bodyOf(p);
            // their body is them now (it starts where they stood); a player newer than it is someone respawned
            if (b != null && (p.getHealth() <= 0 || p.ticksExisted > b.ticksExisted)) e.setCanceled(true);
            else if (p.getHealth() <= 0) {
                e.setCanceled(true);
                JustDied.draw(body, p, pt, x, y, z);
            }
            return;
        }
        e.setCanceled(true);
        lyingFrame(p, d, pt, frame);
        ItemStack[] armor = d.deadArmor;
        if (armor == null) { // a dead player's inventory empties a tick later: what they wore is kept from before
            armor = new ItemStack[4];
            for (int i = 0; i < 4; i++) armor[i] = p.getCurrentArmor(i);
        }
        body.draw(
            p,
            ((AbstractClientPlayer) p).getLocationSkin(),
            armor,
            null,
            angles,
            frame,
            0,
            0,
            RenderCorpse.ALIVE,
            x,
            y,
            z);
        if (d.diedAt < 0 && !d.up && p != Minecraft.getMinecraft().thePlayer) tag(p, d, pt, x, y, z);
    }

    // ---- the tag over someone lying downed ----

    private final Fancy tagText = new Fancy();

    {
        tagText.shadow = false;
    }

    /**
     * Over someone lying downed, where a name tag would be: their name, and under it the seconds they have left
     * (trembling and red for the last five) or, while someone's getting them up, how far along that is. Not for the one
     * you're getting up yourself: your HUD says it, right under the crosshair.
     */
    private void tag(EntityPlayer p, MsgDowned d, float pt, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (d.percent >= 0 && d.reviver == mc.thePlayer.getEntityId()) return;
        FontRenderer font = RenderManager.instance.getFontRenderer();
        float now = (ticks + pt) / 20F;
        int left = Math.max(0, d.left - (ticks - d.at));
        boolean revived = d.percent >= 0, low = !revived && left <= 100;
        String big = revived ? "\u2191 " + d.percent + "%" : String.valueOf(secs(left));
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(p.getCommandSenderName());
        float pulse = low ? 0.5F + 0.5F * MathHelper.sin(now * 12) : 0;
        RenderManager rm = RenderManager.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 0.95, z);
        GL11.glNormal3f(0, 1, 0);
        GL11.glRotatef(-rm.playerViewY, 0, 1, 0);
        GL11.glRotatef(rm.playerViewX, 1, 0, 0);
        GL11.glScalef(-0.025F, -0.025F, 0.025F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        int wide = Math.max(font.getStringWidth(name), font.getStringWidth(big) * 3 / 2) / 2 + 3;
        Fancy.rect(-wide, -2, wide, 22, 0, 0.3F);
        Fancy.rect(
            -wide,
            21,
            -wide + 2 * wide * (revived ? d.percent / 100F : left / (float) Math.max(1, d.leftMax)),
            22,
            revived ? Palette.OK : low ? Palette.danger() : Palette.text(),
            0.9F);
        Fancy.text(font, name, -font.getStringWidth(name) / 2F, 0, Palette.dim(), 0.9F, false);
        GL11.glTranslatef(0, 9, 0);
        GL11.glScalef(1.5F, 1.5F, 1);
        tagText.set(now + 10, low ? 0.4F + 0.6F * pulse : 0, 0, low ? 0.6F * pulse : 0)
            .centred(font, big, 0, 0, revived ? Palette.OK : low ? Palette.danger() : Palette.text(), 1);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    // ---- the HUD ----

    private final Fancy title = new Fancy(), status = new Fancy(), count = new Fancy(), small = new Fancy();
    /** The bars, eased: the server's numbers come in steps. */
    private float shownLeft = 1, shownRevive, shownWork;
    /** Client tick your reviving or searching began (the words pop in from it), -1 when not. */
    private int workFrom = -1;

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        int id = mc.thePlayer.getEntityId(), w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
        FontRenderer font = mc.fontRenderer;
        float pt = e.partialTicks;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        try {
            MsgDowned me = lying(mc.thePlayer);
            if (me != null && !me.up) downedHud(mc, font, me, w, h, pt);
            else {
                MsgDowned them = null;
                for (MsgDowned d : Downed.seen.values()) if (d.reviver == id && d.percent >= 0) them = d;
                if (them != null) work(
                    font,
                    w,
                    h,
                    pt,
                    StatCollector.translateToLocalFormatted("ukycorpses.downed.reviving", name(mc, them.entity)),
                    them.percent / 100F,
                    Palette.OK,
                    them.percent + "%");
                else if (searching > 0 && searching < EntityCorpse.search()) work(
                    font,
                    w,
                    h,
                    pt,
                    StatCollector.translateToLocal("ukycorpses.searching"),
                    (searching + pt) / EntityCorpse.search(),
                    Palette.accent(),
                    String.format("%.1f", (EntityCorpse.search() - searching) / 20F));
                else {
                    workFrom = -1;
                    bodyHints(mc, font, id, w, h);
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    /**
     * Lying downed yourself: the edges of the screen beat red with your heart, faster the less time you have; at the
     * top the title, trembling more and more and splitting with each beat; under it what's happening and a bar of the
     * time left, or, once someone's getting you up, of how far they are, green, with the heart calming down. What you
     * can
     * do at the bottom, out of the way.
     */
    private void downedHud(Minecraft mc, FontRenderer font, MsgDowned me, int w, int h, float pt) {
        float t = (ticks - me.since + pt) / 20F;
        boolean alone = me.alone >= 0, revived = me.percent >= 0;
        int left = Math.max(0, (alone ? me.alone : me.left) - (ticks - me.at));
        int full = Math.max(1, alone ? me.aloneMax : me.leftMax);
        float frac = Math.min(1, left / (float) full), u = revived ? 0 : 1 - frac;
        shownLeft += (frac - shownLeft) * 0.2F;
        shownRevive += ((revived ? me.percent / 100F : 0) - shownRevive) * 0.2F;
        int danger = Palette.danger(), text = Palette.text(), dim = Palette.dim();

        // the heart: two knocks a beat, faster as it runs out, slow while someone's at it
        float phase = t * (revived ? 0.8F : 1 + 1.8F * u);
        float pulse = Math.max(thump(phase % 1), 0.55F * thump((phase - 0.17F) % 1 + (phase < 0.17F ? 1 : 0)));
        int edge = revived ? Palette.OK : danger;
        Fancy.rect(0, 0, w, h, edge, (0.05F + 0.10F * u) * (0.6F + 0.4F * pulse));
        Fancy.edges(w, h, edge, (0.35F + 0.35F * pulse) * (0.6F + 0.4F * u));

        float top = h * 0.2F;
        GL11.glPushMatrix();
        GL11.glTranslatef(w / 2F, top, 0);
        GL11.glScalef(2.2F, 2.2F, 1);
        title.set(t, 0.25F + 0.9F * u + 0.5F * pulse, 0, (0.2F + 0.8F * u) * pulse)
            .centred(font, StatCollector.translateToLocal("ukycorpses.downed.title"), 0, 0, revived ? text : danger, 1);
        GL11.glPopMatrix();

        String what = revived
            ? StatCollector.translateToLocalFormatted("ukycorpses.downed.revived", name(mc, me.reviver))
            : StatCollector.translateToLocal(alone ? "ukycorpses.downed.alone" : "ukycorpses.downed.bleed");
        status.set(t - 0.3F, 0, 0.6F, 0)
            .centred(font, what, w / 2F, top + 26, revived ? Palette.OK : text, 0.95F);

        float bw = Math.min(180, w * 0.4F), bx = w / 2F - bw / 2, by = top + 40;
        float in = Math.min(1, Math.max(0, (t - 0.4F) / 0.4F));
        Fancy.rect(bx, by, bx + bw, by + 3, 0, 0.55F * in);
        if (revived) Fancy.rect(bx, by, bx + bw * shownRevive, by + 3, Palette.OK, in);
        else {
            Fancy.rect(bx, by, bx + bw * shownLeft, by + 3, danger, 0.9F * in);
            Fancy.rect(
                bx + bw * shownLeft - 1,
                by - 1,
                bx + bw * shownLeft,
                by + 4,
                0xFFFFFF,
                (0.5F + 0.5F * pulse) * in);
        }
        String num = revived ? me.percent + "%" : String.valueOf(secs(left));
        GL11.glPushMatrix();
        GL11.glTranslatef(w / 2F, by + 8, 0);
        GL11.glScalef(1.5F, 1.5F, 1);
        count.set(t - 0.4F, !revived && left <= 100 ? 0.5F + 0.8F * pulse : 0, 0, 0)
            .centred(font, num, 0, 0, revived ? Palette.OK : u > 0.7F ? danger : text, in);
        GL11.glPopMatrix();

        float hy = h - 66;
        if (!alone && !revived && CorpseConfig.friendRevive) small.set(t - 0.8F, 0, 0, 0)
            .centred(font, StatCollector.translateToLocal("ukycorpses.downed.hint"), w / 2F, hy, dim, 0.85F);
        small.set(t - 0.9F, 0, 0, 0)
            .centred(
                font,
                StatCollector.translateToLocalFormatted(
                    "ukycorpses.downed.giveup",
                    GameSettings.getKeyDisplayString(giveUp.getKeyCode())),
                w / 2F,
                hy + 11,
                dim,
                0.7F);
    }

    /**
     * Getting someone up, or going through a body: under the crosshair, what you're doing, a thin bar filling in color,
     * and how far along (percent, or seconds left).
     */
    private void work(FontRenderer font, int w, int h, float pt, String what, float frac, int color, String how) {
        if (workFrom < 0) {
            workFrom = ticks;
            shownWork = frac;
        }
        float t = (ticks - workFrom + pt) / 20F;
        shownWork += (Math.min(1, frac) - shownWork) * 0.25F;
        float y = h / 2F + 14, bw = 90, bx = w / 2F - bw / 2;
        small.set(t, 0, 0.5F, 0)
            .centred(font, what, w / 2F, y, Palette.text(), 0.95F);
        Fancy.rect(bx, y + 11, bx + bw, y + 13, 0, 0.55F);
        Fancy.rect(bx, y + 11, bx + bw * shownWork, y + 13, color, 1);
        Fancy.text(font, how, w / 2F - font.getStringWidth(how) / 2F, y + 16, Palette.dim(), 0.8F, true);
    }

    /** A sharp knock and nothing for the rest of the beat (phase 0..1). */
    private static float thump(float phase) {
        if (phase < 0 || phase > 0.22F) return 0;
        float s = (float) Math.sin(phase / 0.22F * Math.PI);
        return s * s * s;
    }

    /**
     * p's body that has just come into being (it's still coming down, or only just lay down), as an entity or a
     * block; null if none. An older body of theirs lying nearby doesn't count.
     */
    static EntityCorpse bodyOf(EntityPlayer p) {
        // once found, it's theirs for as long as they stay dead: the scene can last longer than a body counts as fresh,
        // and without it they'd be drawn again, falling a second time beside their own body
        EntityCorpse known = bodies.get(p.getEntityId());
        if (known != null && p.getHealth() <= 0
            && !known.isDead
            && (known.host == null || !known.host.isInvalid() && known.host.body == known)) return known;
        bodies.remove(p.getEntityId());
        AxisAlignedBB near = p.boundingBox; // well above too: a drowned body comes up to the surface
        EntityCorpse b = EntityCorpse.findFresh(
            p.worldObj,
            AxisAlignedBB.getBoundingBox(
                near.minX - 16,
                near.minY - 12,
                near.minZ - 16,
                near.maxX + 16,
                near.maxY + 48,
                near.maxZ + 16),
            p.getCommandSenderName(),
            200);
        if (b != null && p.getHealth() <= 0) bodies.put(p.getEntityId(), b);
        return b;
    }

    /** The body each dead player (entity id) turned into, while they're dead. */
    private static final Map<Integer, EntityCorpse> bodies = new HashMap<>();

    /** p died while lying downed (drawn as they lay, not standing). */
    static boolean wasDown(EntityPlayer p) {
        MsgDowned d = Downed.seen.get(p.getEntityId());
        return d != null && d.diedAt >= 0;
    }

    private static String name(Minecraft mc, int entity) {
        Entity e = mc.theWorld.getEntityByID(entity);
        return e == null ? "?" : e.getCommandSenderName();
    }

    private final Fancy hint = new Fancy();
    /** The body the hints are on, and since when (nanoTime): they pop in once per body looked at. */
    private EntityCorpse hinted;
    private long hintedAt;

    /**
     * Dragging a body: whose, and how to let go. Else, looking at one: what a click does and what Shift+click does,
     * under the crosshair, so a dragged body or a search doesn't come as a surprise.
     */
    private void bodyHints(Minecraft mc, FontRenderer font, int me, int w, int h) {
        EntityCorpse dragged = null;
        for (Object o : mc.theWorld.loadedEntityList)
            if (o instanceof EntityCorpse && ((EntityCorpse) o).draggedBy() == me) dragged = (EntityCorpse) o;
        EntityCorpse aim = dragged != null ? dragged : CorpsePick.target(mc, mc.objectMouseOver);
        if (aim == null || mc.currentScreen != null) {
            hinted = null;
            return;
        }
        if (hinted != aim) {
            hinted = aim;
            hintedAt = System.nanoTime();
        }
        float t = (System.nanoTime() - hintedAt) / 1e9F * 2.5F, y = h / 2F + 12;
        if (dragged != null) {
            hint.set(t, 0, 0.4F, 0)
                .centred(
                    font,
                    StatCollector.translateToLocalFormatted("ukycorpses.hint.dragging", aim.owner()),
                    w / 2F,
                    h * 0.22F,
                    Palette.accent(),
                    1);
            hint.set(t - 0.2F, 0, 0, 0)
                .centred(
                    font,
                    StatCollector.translateToLocal("ukycorpses.hint.letGo"),
                    w / 2F,
                    h * 0.22F + 11,
                    Palette.dim(),
                    0.85F);
            return;
        }
        boolean mine = aim.owner()
            .equals(mc.thePlayer.getCommandSenderName());
        hint.set(t, 0, 0, 0)
            .centred(
                font,
                StatCollector.translateToLocal(mine ? "ukycorpses.hint.open" : "ukycorpses.hint.search"),
                w / 2F,
                y,
                Palette.text(),
                0.75F);
        if (com.uky.corpses.CorpseConfig.drag) hint.set(t - 0.15F, 0, 0, 0)
            .centred(
                font,
                StatCollector.translateToLocal("ukycorpses.hint.drag"),
                w / 2F,
                y + 10,
                Palette.dim(),
                0.65F);
    }

    private static int secs(int ticks) {
        return (ticks + 19) / 20;
    }
}
