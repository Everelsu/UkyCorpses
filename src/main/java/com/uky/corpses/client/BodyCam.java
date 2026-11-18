package com.uky.corpses.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.uky.corpses.BlockCorpse;
import com.uky.corpses.EntityCorpse;
import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Settle;
import com.uky.corpses.corpse.Skeleton;

/**
 * Seeing out of your own body, the way FirstPersonModel does it for the living, applied to a body that falls and lies:
 * the eye is fixed in the torso (never in the head, whose turns would swing it about the neck into arms and walls), a
 * little in front of the chest and above the neck, where the hidden head would be; the camera only ever turns about it.
 * Then nothing of the body can come between the eye and the near plane, nothing has to be hidden but the head, and the
 * body seen from there is whole.
 *
 * Which way the eye looks is not the corpse's head either (from there it would stare at the sky or into the floor):
 * restView picks a view per pose that shows where you lie and part of you, and the player looks around from it.
 */
public final class BodyCam {

    /**
     * The eye in torso pixels (-y toward the head, -z the chest): standing, on the back or side, face down, sitting.
     */
    private static final double[] STAND = { 0, -3.5, -4.6 }, BACK = { 0, -3.5, -4.6 }, FACE = { 0, -5, 1.5 },
        SITTING = { 0, -3.5, -3.4 };
    private static final float[] NO_ANGLES = new float[Settle.N];

    /** A body the camera can be in, this frame. */
    public static final class Pose {

        /** Frame from the feet spot (x y z, world), limbs, and the same resting (for restView). */
        public final Mat4 frame = new Mat4(), rest = new Mat4();
        public final float[] angles = new float[Settle.N], restAngles = new float[Settle.N];
        public double x, y, z;
        public int pose;
        /** 0 standing .. 1 down. */
        public float k;
        private final Mat4 tmp = new Mat4();
        private final double[] p = new double[3];
    }

    private BodyCam() {}

    /** A body (entity or block) as it is drawn this frame, without its bobbing on water. */
    public static void corpse(EntityCorpse c, float pt, Pose out) {
        double[] off = out.p;
        c.frameNow(pt, out.angles, off, out.frame);
        out.x = c.lastTickPosX + (c.posX - c.lastTickPosX) * pt + off[0];
        out.y = c.lastTickPosY + (c.posY - c.lastTickPosY) * pt + off[1];
        out.z = c.lastTickPosZ + (c.posZ - c.lastTickPosZ) * pt + off[2];
        out.pose = c.pose();
        Settle s = c.settle;
        out.k = !s.active() ? 1 : s.flying(pt) ? s.progress(pt) : s.tip(pt);
        Skeleton.Shape rest = c.shape();
        out.rest.set(rest.frame);
        System.arraycopy(rest.angles, 0, out.restAngles, 0, Settle.N);
    }

    /** p died lying downed (drawn as they lay until their body comes; no blow to flash red for). */
    public static boolean diedDowned(EntityPlayer p) {
        return DownedClient.wasDown(p);
    }

    /** p drawn as their body until it comes (as they lay downed, or as they stood and fell); false if not. */
    public static boolean provisional(EntityPlayer p, float pt, Pose out) {
        return DownedClient.deadPose(p, pt, out) || JustDied.pose(p, pt, out);
    }

    /** Where the eye is, world: rigid in the torso, sliding from standing to the lying pose's spot as it goes down. */
    public static double[] eye(Pose b, double[] out) {
        double[] to = b.pose == Poses.PRONE ? FACE : b.pose == Poses.SIT ? SITTING : BACK;
        double s = Math.max(0, Math.min(1, b.k));
        s = s * s * (3 - 2 * s);
        Skeleton.part(b.frame, Skeleton.BODY, NO_ANGLES, b.tmp);
        b.tmp.apply(
            STAND[0] + (to[0] - STAND[0]) * s,
            STAND[1] + (to[1] - STAND[1]) * s,
            STAND[2] + (to[2] - STAND[2]) * s,
            out);
        out[0] += b.x;
        out[1] += b.y;
        out[2] += b.z;
        return out;
    }

    /**
     * The view a body lying still rests on, in the world: {yaw, pitch, roll} as Minecraft's camera takes them. On the
     * back: along yourself past the arm toward the feet, a little down; face down: along the floor toward your hands;
     * on the side: ahead; sitting: at your lap. To the side the head is turned to, tilted a few degrees that way.
     */
    public static void restView(Pose b, float[] out) {
        double[] d = b.p;
        b.rest.dir(0, -1, 0, d); // toward the head
        double hx = d[0], hz = d[2], hl = Math.sqrt(hx * hx + hz * hz);
        b.rest.dir(0, 0, -1, d); // the chest
        double cx = d[0], cy = d[1], cz = d[2], cl = Math.sqrt(cx * cx + cz * cz);
        if (hl < 0.3) { // upright: the head is up, the chest is ahead
            hx = cx;
            hz = cz;
            hl = Math.max(cl, 1e-6);
        }
        hx /= hl;
        hz /= hl;
        Skeleton.part(b.rest, Skeleton.HEAD, b.restAngles, b.tmp);
        b.tmp.dir(0, 0, -1, d); // where the face looks (the part's matrix is in pixels: made unit)
        double fl0 = Math.max(1e-9, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]));
        double fx = d[0] / fl0, fy = d[1] / fl0, fz = d[2] / fl0;
        double rx = -hz, rz = hx; // a quarter turn from the head's way
        double side = fx * rx + fz * rz;
        float sign = side < -0.02 ? -1 : 1;
        double vx, vz;
        float pitch, roll;
        switch (b.pose) {
            case Poses.PRONE:
                vx = hx * 0.94 + rx * sign * 0.34; // 20 degrees off the head's way
                vz = hz * 0.94 + rz * sign * 0.34;
                pitch = 10;
                roll = 6 * sign;
                break;
            case Poses.SIDE:
                if (cl > 0.3) {
                    vx = cx / cl;
                    vz = cz / cl;
                } else {
                    vx = rx * sign;
                    vz = rz * sign;
                }
                pitch = 5;
                roll = 6 * sign;
                break;
            case Poses.SIT: {
                double fl = Math.sqrt(fx * fx + fz * fz);
                if (fl > 0.2) {
                    vx = fx / fl;
                    vz = fz / fl;
                } else {
                    vx = hx;
                    vz = hz;
                }
                pitch = (float) Math.max(10, Math.min(40, -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, fy))))));
                roll = 3 * sign;
                break;
            }
            default: // on the back
                vx = hx * -0.5 + rx * sign * 0.87; // 120 degrees off the head's way: past the arm, toward the feet
                vz = hz * -0.5 + rz * sign * 0.87;
                pitch = 10;
                roll = 7 * sign;
        }
        out[0] = (float) Math.toDegrees(Math.atan2(-vx, vz));
        out[1] = pitch;
        out[2] = roll;
    }

    private static final List<AxisAlignedBB> boxes = new ArrayList<>();

    /**
     * Moves p (world) to at least r from every block around it (a block body's own blocks don't count: the camera
     * is in that body), by at most max. False if that isn't enough (inside a solid block: p is left alone).
     */
    public static boolean clear(World w, double[] p, double r, double max) {
        double ox = p[0], oy = p[1], oz = p[2];
        for (int pass = 0; pass < 4; pass++) {
            AxisAlignedBB near = AxisAlignedBB
                .getBoundingBox(p[0] - r, p[1] - r, p[2] - r, p[0] + r, p[1] + r, p[2] + r);
            solids(w, near);
            boolean moved = false;
            for (AxisAlignedBB b : boxes) {
                if (b.maxX <= p[0] - r || b.minX >= p[0] + r
                    || b.maxY <= p[1] - r
                    || b.minY >= p[1] + r
                    || b.maxZ <= p[2] - r
                    || b.minZ >= p[2] + r) continue;
                // out the nearest way: each axis, toward whichever face of the box is closer
                double px = p[0] < (b.minX + b.maxX) / 2 ? b.minX - r - p[0] : b.maxX + r - p[0];
                double py = p[1] < (b.minY + b.maxY) / 2 ? b.minY - r - p[1] : b.maxY + r - p[1];
                double pz = p[2] < (b.minZ + b.maxZ) / 2 ? b.minZ - r - p[2] : b.maxZ + r - p[2];
                double ax = Math.abs(px), ay = Math.abs(py), az = Math.abs(pz);
                if (ax <= ay && ax <= az) p[0] += px;
                else if (ay <= az) p[1] += py;
                else p[2] += pz;
                moved = true;
            }
            if (!moved) break;
        }
        double dx = p[0] - ox, dy = p[1] - oy, dz = p[2] - oz;
        if (dx * dx + dy * dy + dz * dz <= max * max) return true;
        p[0] = ox;
        p[1] = oy;
        p[2] = oz;
        return false;
    }

    /** Collision boxes of the blocks in box, block bodies left out. */
    private static void solids(World w, AxisAlignedBB box) {
        boxes.clear();
        int x0 = MathHelper.floor_double(box.minX), x1 = MathHelper.floor_double(box.maxX),
            y0 = MathHelper.floor_double(box.minY) - 1, y1 = MathHelper.floor_double(box.maxY),
            z0 = MathHelper.floor_double(box.minZ), z1 = MathHelper.floor_double(box.maxZ);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            if (!w.blockExists(x, y, z)) continue;
            Block b = w.getBlock(x, y, z);
            if (b instanceof BlockCorpse) continue;
            try {
                b.addCollisionBoxesToList(w, x, y, z, box, boxes, null);
            } catch (RuntimeException e) { // a modded block that wants an entity there: it doesn't count
            }
        }
    }

    /**
     * How far up an eye at p, looking fy down (the forward vector's y), must go for the game to see it above the
     * surface of the water or lava it's in (vanilla's own test, at the near plane); 0 when it's not just under one.
     */
    public static double surface(World w, double[] p, double fy) {
        double probe = p[1] + 0.05 * Math.min(0, fy) - 0.03;
        int x = MathHelper.floor_double(p[0]), z = MathHelper.floor_double(p[2]), j = MathHelper.floor_double(probe);
        if (!liquid(w, x, j, z)) return 0;
        if (liquid(w, x, j + 1, z)) {
            if (liquid(w, x, j + 2, z)) return 0; // deep: still coming up
            j++;
        }
        double top = j + 1 - (BlockLiquid.getLiquidHeightPercent(w.getBlockMetadata(x, j, z)) - 1 / 9.0);
        double need = top + 0.04 - probe;
        return need > 0 && need < 1.2 ? need : 0;
    }

    private static boolean liquid(World w, int x, int y, int z) {
        return w.getBlock(x, y, z)
            .getMaterial()
            .isLiquid();
    }

    /** Distance from the camera to the corners of the near plane: what a wall must be kept beyond. */
    public static double nearCorner(float fovDegrees, double aspect) {
        double t = Math.tan(Math.toRadians(fovDegrees) / 2);
        return 0.05 * Math.sqrt(1 + t * t * (1 + aspect * aspect));
    }
}
