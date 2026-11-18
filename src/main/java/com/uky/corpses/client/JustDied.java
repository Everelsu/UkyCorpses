package com.uky.corpses.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Settle;
import com.uky.corpses.corpse.Situation;
import com.uky.corpses.corpse.Skeleton;

/**
 * Players the moment they die, before their body arrives (a tick or two): no vanilla tip-over in red and no puff of
 * smoke. They're drawn as the body will start, standing the way they stood, and the stance is handed to the body
 * (Settle.stance) so it starts from exactly that. If no body comes (nothing to leave behind), they fall on their own.
 */
final class JustDied {

    /** Ticks to wait for the body before falling without it; longer for your own when the server said one comes. */
    private static final int WAIT = 6, WAIT_COMING = 40;

    private static final class Seen {

        int tick, pose, look;
        float yaw;
        float[] stance;
        Settle fall;
        /** As worn and held the moment they died: the inventory empties a tick later. */
        ItemStack[] armor;
        ItemStack held;
    }

    private static final Map<Integer, Seen> seen = new HashMap<>();
    private static final float[] angles = new float[Settle.N], target = new float[Settle.N];
    private static final Mat4 frame = new Mat4();

    private JustDied() {}

    /** Client tick start. */
    static void tick(Minecraft mc, int now) {
        if (mc.theWorld == null) {
            seen.clear();
            return;
        }
        for (Iterator<Map.Entry<Integer, Seen>> it = seen.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Integer, Seen> en = it.next();
            Object o = mc.theWorld.getEntityByID(en.getKey());
            if (!(o instanceof EntityPlayer) || ((EntityPlayer) o).getHealth() > 0) it.remove();
        }
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getHealth() > 0) continue;
            p.deathTime = 0; // vanilla tips them over in red and pops them into smoke at 20
            if (DownedClient.wasDown(p)) { // died lying downed: drawn as they lay, the body goes slack from there
                seen.remove(p.getEntityId());
                continue;
            }
            Seen s = see(p, now);
            if (s.fall == null && now - s.tick > (p == mc.thePlayer && bodyComing() ? WAIT_COMING : WAIT)
                && DownedClient.bodyOf(p) == null) {
                // nothing came: they go down on their own, face down or on the back
                s.pose = (p.getEntityId() & 1) == 0 ? Poses.PRONE : Poses.LIE;
                s.look = Poses.look(p.getEntityId() >> 1 & 3, (p.getEntityId() & 8) != 0, p.getEntityId() * 31, 0);
                s.fall = new Settle();
                s.fall.begin(now, p.getEntityId(), Settle.FALL, s.pose, 0, s.stance, 0, 0, 0, s.yaw, 0, 0, false);
            }
            if (s.fall != null) {
                Poses.rest(s.pose, s.look, Situation.NONE, 0, target);
                s.fall.advance(now, target);
            }
        }
    }

    /** The server told this client it died a moment ago and a body takes its things: it's on its way. */
    private static boolean bodyComing() {
        CorpseClient.Death d = CorpseClient.lastDeath;
        return d != null && d.kind == com.uky.corpses.MsgDeath.BODY && System.currentTimeMillis() - d.at < 4000;
    }

    /** The moment p is first seen dead: how they stood, handed on to their body. */
    private static Seen see(EntityPlayer p, int now) {
        Seen s = seen.get(p.getEntityId());
        if (s != null) return s;
        s = new Seen();
        s.tick = now;
        s.yaw = p.renderYawOffset;
        s.stance = stance(p);
        s.armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) s.armor[i] = p.getCurrentArmor(i);
        s.held = p.getHeldItem();
        Settle.remember(p.getCommandSenderName(), s.stance);
        seen.put(p.getEntityId(), s);
        return s;
    }

    /**
     * Their limbs as the player model has them now (ModelBiped.setRotationAngles, walking swing, head turn, held
     * item), for the body to start from.
     */
    static float[] stance(EntityPlayer p) {
        float[] a = new float[Settle.N];
        float f = p.limbSwing, f1 = Math.min(1F, p.limbSwingAmount);
        float c = MathHelper.cos(f * 0.6662F), cb = MathHelper.cos(f * 0.6662F + (float) Math.PI);
        a[0] = (float) Math.toRadians(MathHelper.clamp_float(p.rotationPitch, -60, 60));
        a[1] = (float) Math.toRadians(MathHelper.wrapAngleTo180_float(p.rotationYawHead - p.renderYawOffset));
        a[3] = cb * f1;
        a[6] = c * f1;
        a[5] = 0.05F;
        a[8] = -0.05F;
        ItemStack held = p.getHeldItem();
        if (held != null) a[3] = a[3] * 0.5F - (float) Math.PI / 10;
        a[9] = c * 1.4F * f1;
        a[12] = cb * 1.4F * f1;
        return a;
    }

    /** Draws p as their body-to-be if they're dead and the body isn't there yet. True: drawn (or hidden). */
    static boolean draw(RenderCorpse r, EntityPlayer p, float pt, double x, double y, double z) {
        if (DownedClient.wasDown(p)) return false;
        Seen s = see(p, DownedClient.ticks); // dead since this very frame: caught before the next tick
        float k = 0;
        int pose = Poses.LIE;
        if (s.fall != null && s.fall.active()) {
            s.fall.limbs(pt, angles);
            k = s.fall.tip(pt);
            pose = s.pose;
        } else if (s.fall != null) {
            Poses.rest(s.pose, s.look, Situation.NONE, 0, angles);
            k = 1;
            pose = s.pose;
        } else System.arraycopy(s.stance, 0, angles, 0, Settle.N);
        Skeleton.posed(frame, pose, s.yaw, k, 0, Float.NaN, 0, angles, Situation.NONE, 0);
        r.draw(
            p,
            ((AbstractClientPlayer) p).getLocationSkin(),
            s.armor,
            s.held,
            angles,
            frame,
            0,
            0,
            RenderCorpse.ALIVE,
            x,
            y,
            z);
        return true;
    }

    /** p's body-to-be as drawn this frame, for the death camera before the real body shows; false if none. */
    static boolean pose(EntityPlayer p, float pt, BodyCam.Pose out) {
        Seen s = seen.get(p.getEntityId());
        if (s == null || DownedClient.wasDown(p)) return false;
        float k = 0;
        int pose = Poses.LIE;
        if (s.fall != null) {
            if (s.fall.active()) s.fall.limbs(pt, out.angles);
            else Poses.rest(s.pose, s.look, Situation.NONE, 0, out.angles);
            k = s.fall.active() ? s.fall.tip(pt) : 1;
            pose = s.pose;
        } else System.arraycopy(s.stance, 0, out.angles, 0, Settle.N);
        Skeleton.posed(out.frame, pose, s.yaw, k, 0, Float.NaN, 0, out.angles, Situation.NONE, 0);
        out.pose = pose;
        out.k = k;
        out.x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        out.y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - p.yOffset;
        out.z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        // where it will lie, for the view to rest on
        Poses.rest(pose, s.look, Situation.NONE, 0, out.restAngles);
        Skeleton.posed(out.rest, pose, s.yaw, 1, 0, Float.NaN, 0, out.restAngles, Situation.NONE, 0);
        return true;
    }

}
