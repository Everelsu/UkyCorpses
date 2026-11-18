package com.uky.corpses.corpse;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.uky.corpses.EntityCorpse;

/**
 * How the ground around a body bends it, decided once on the server where it lands: thrown flat from a height, legs
 * or head and arms hanging over an edge, or crumpled up where there's no room to lie.
 */
public final class Situation {

    public static final int NONE = 0, SPLAT = 1, FEET_OVER = 2, HEAD_OVER = 3, CRAMPED = 4;

    /** From the body's centre along it: hips toward the feet, shoulders toward the head (the model is ~1.9 long). */
    private static final double HIPS = 0.23, SHOULDERS = 0.47;

    /** Server, before spawning. fell: died from a fall. Then limbs that would go into a block are pulled in. */
    public static void situate(EntityCorpse b, boolean fell) {
        lay(b, fell);
        tuck(b);
    }

    private static void lay(EntityCorpse b, boolean fell) {
        if (grid(b, fell)) return; // found whole blocks for it; else ledges and holes below
        if (b.pose() == EntityCorpse.SIT) return; // already against a wall
        World w = b.worldObj;
        if (!fits(w, b)) {
            float yaw = b.rotationYaw;
            b.setLocationAndAngles(b.posX, b.posY, b.posZ, yaw + 90, 0);
            if (!fits(w, b)) { // a 1x1 hole: no way to stretch out
                b.setLocationAndAngles(b.posX, b.posY, b.posZ, yaw, 0);
                b.repose(EntityCorpse.SIT);
                b.setSituation(CRAMPED, 0);
                return;
            }
        }
        double rad = Math.toRadians(b.rotationYaw);
        double fx = Math.round(-Math.sin(rad)), fz = Math.round(Math.cos(rad));
        if (slope(w, b, fx, fz)) return;
        // on the back (or side) the feet point forward, face down the head does
        double sign = b.pose() == EntityCorpse.PRONE ? -1 : 1;
        if (over(w, b, fx * sign, fz * sign, HIPS, FEET_OVER, sign)
            || over(w, b, -fx * sign, -fz * sign, SHOULDERS, HEAD_OVER, sign)) {
            if (b.pose() == EntityCorpse.SIDE) b.repose(EntityCorpse.LIE); // hanging over an edge bends on the back
            return;
        }
        if (fell) splat(b);
    }

    /**
     * Whole blocks for the body: sitting in its block if the block in front is level and free for the legs, else
     * lying across two level, free blocks (along its yaw first, then across). False: no such place, the free logic
     * takes over.
     */
    private static boolean grid(EntityCorpse b, boolean fell) {
        World w = b.worldObj;
        double y = b.posY;
        int cx = MathHelper.floor_double(b.posX), cz = MathHelper.floor_double(b.posZ);
        if (b.pose() == EntityCorpse.SIT) {
            double rad = Math.toRadians(b.rotationYaw);
            int fx = (int) Math.round(-Math.sin(rad)), fz = (int) Math.round(Math.cos(rad));
            if (room(w, cx + fx, y, cz + fz, 1)) {
                b.setGrid(true);
                return true;
            }
            b.repose(EntityCorpse.PRONE); // no room for the legs: down on the floor instead
        }
        for (float yaw : new float[] { b.rotationYaw, b.rotationYaw + 90 }) {
            double rad = Math.toRadians(yaw);
            int ax = (int) Math.round(-Math.sin(rad)), az = (int) Math.round(Math.cos(rad));
            if (!room(w, cx, y, cz, 1)) break; // its own block isn't free: nothing on the grid will be
            for (int s = 1; s >= -1; s -= 2) {
                if (!room(w, cx + ax * s, y, cz + az * s, 1)) continue;
                b.setLocationAndAngles(cx + 0.5 + ax * s * 0.5, y, cz + 0.5 + az * s * 0.5, yaw, 0);
                b.setGrid(true);
                if (fell) splat(b);
                return true;
            }
        }
        return false;
    }

    /**
     * Lying across a half step: both ends have a floor, at different heights (a stair, a slab, snow). The body tilts to
     * lie along it, its middle between the two.
     */
    private static boolean slope(World w, EntityCorpse b, double fx, double fz) {
        double front = top(w, b.posX + fx * 0.75, b.posY, b.posZ + fz * 0.75),
            back = top(w, b.posX - fx * 0.75, b.posY, b.posZ - fz * 0.75);
        if (Double.isNaN(front) || Double.isNaN(back)) return false;
        double rise = front - back;
        if (Math.abs(rise) < 0.2 || Math.abs(rise) > 0.65) return false; // a whole block: the edge logic's
        b.setPosition(b.posX, (front + back) / 2, b.posZ);
        b.setLean((float) Math.toDegrees(Math.atan2(rise, 1.5)));
        return true;
    }

    /**
     * The floor at x z nearest to height y (a block up or down at most) with room above it; NaN if none. From the
     * blocks' collision boxes, so a stair's half step counts as one.
     */
    private static double top(World w, double x, double y, double z) {
        double best = Double.NaN;
        // a copy: the world hands out one shared list, which the check for room above refills
        for (Object o : new java.util.ArrayList<Object>(
            w.func_147461_a(AxisAlignedBB.getBoundingBox(x - 0.05, y - 1.1, z - 0.05, x + 0.05, y + 1.1, z + 0.05)))) {
            AxisAlignedBB box = (AxisAlignedBB) o;
            double t = box.maxY;
            if (t > y + 1.1 || t < y - 1.1) continue;
            AxisAlignedBB above = AxisAlignedBB.getBoundingBox(x - 0.1, t + 0.01, z - 0.1, x + 0.1, t + 0.35, z + 0.1);
            if (!w.func_147461_a(above)
                .isEmpty()) continue;
            if (Double.isNaN(best) || Math.abs(t - y) < Math.abs(best - y)) best = t;
        }
        return best;
    }

    /**
     * Limbs that would go into a wall (a narrow corridor, a body along a wall, sitting with the opposite wall close)
     * are pulled in: Poses.tuck. Above the floor only: what rests on the floor sinks into it a little on purpose.
     */
    public static void tuck(EntityCorpse b) {
        World w = b.worldObj;
        int mask = 0;
        double floor = b.posY + 0.3 + Math.abs(Math.sin(Math.toRadians(b.lean()))) * 0.9;
        for (int pass = 0; pass < 2; pass++) {
            b.setTuck(mask);
            Skeleton.Shape s = b.shape();
            for (int part : new int[] { Skeleton.HEAD, Skeleton.RARM, Skeleton.LARM, Skeleton.RLEG, Skeleton.LLEG }) {
                if ((mask & 1 << part) != 0 || part == Skeleton.HEAD && b.pose() == EntityCorpse.SIT) continue;
                double[] r = s.bounds(part);
                double y0 = Math.max(b.posY + r[1], floor), y1 = b.posY + r[4];
                if (y1 - y0 < 0.05) continue;
                AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                    b.posX + r[0] + 0.04,
                    y0,
                    b.posZ + r[2] + 0.04,
                    b.posX + r[3] - 0.04,
                    y1,
                    b.posZ + r[5] - 0.04);
                if (!w.func_147461_a(box)
                    .isEmpty()) mask |= 1 << part;
            }
        }
        b.setTuck(mask);
    }

    /** Thrown flat from a height: on the back or face down, limbs flung wide. */
    private static void splat(EntityCorpse b) {
        if (b.pose() == EntityCorpse.SIDE) b.repose(EntityCorpse.LIE);
        if (b.pose() != EntityCorpse.SIT) b.setSituation(SPLAT, 0);
    }

    /** Block x z at floor height y: a level floor (within a pixel) and `height` blocks of air above it. */
    private static boolean room(World w, int x, double y, int z, int height) {
        if (drop(w, x + 0.5, y, z + 0.5) > 0.07) return false;
        for (int by = MathHelper.floor_double(y + 0.07), top = by + height; by < top; by++) {
            AxisAlignedBB box = w.getBlock(x, by, z)
                .getCollisionBoundingBoxFromPool(w, x, by, z);
            if (box != null && box.maxY > y + 0.07) return false;
        }
        return true;
    }

    /**
     * Looks for a drop along (dx, dz) within the body's length; if there is one, slides the body so the joint at
     * `joint` from the centre sits on the edge, and bends what's past it down. floor: which way the floor is for
     * the model (+ on the back, - face down).
     */
    private static boolean over(World w, EntityCorpse b, double dx, double dz, double joint, int situation,
        double floor) {
        for (double t = 0.1; t <= 0.9; t += 0.1) {
            if (drop(w, b.posX + dx * t, b.posY, b.posZ + dz * t) < 0.4) continue;
            double edge = t - 0.05, shift = edge - joint;
            double deep = drop(w, b.posX + dx * (edge + 0.3), b.posY, b.posZ + dz * (edge + 0.3));
            b.setPosition(b.posX + dx * shift, b.posY, b.posZ + dz * shift);
            b.setSituation(situation, (float) (floor * (deep >= 0.9 ? 1.4 : 0.8)));
            return true;
        }
        return false;
    }

    /** How far below y the floor is at x z (2 when there's nothing within a block and a bit). */
    private static double drop(World w, double x, double y, double z) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        for (int by = MathHelper.floor_double(y - 0.01); by >= MathHelper.floor_double(y) - 2; by--) {
            Block block = w.getBlock(bx, by, bz);
            AxisAlignedBB box = block.getCollisionBoundingBoxFromPool(w, bx, by, bz);
            if (box != null && box.maxY <= y + 0.01) return y - box.maxY;
        }
        return 2;
    }

    /** The body's box as it lies now hits no block. */
    private static boolean fits(World w, EntityCorpse b) {
        return w.func_147461_a(
            b.boundingBox.copy()
                .contract(0.05, 0.05, 0.05))
            .isEmpty();
    }

    /**
     * Client: bends the resting angles a (head, right arm, left arm, right leg, left leg; x y z each) for the
     * situation. bend: radians toward the floor, signed.
     */
    public static void apply(int situation, float bend, float[] a) {
        switch (situation) {
            case SPLAT: // flat out, limbs flung wide
                a[3] = a[6] = 0;
                a[5] = 1.3F;
                a[8] = -1.3F;
                a[9] = a[12] = 0;
                a[11] = 0.35F;
                a[14] = -0.35F;
                break;
            case FEET_OVER: // hips on the edge, legs hang
                a[9] = a[12] = bend;
                a[10] = a[13] = 0;
                a[11] *= 0.3F;
                a[14] *= 0.3F;
                break;
            case HEAD_OVER: // shoulders on the edge, head and arms hang
                a[0] = bend * 0.9F;
                a[1] *= 0.3F;
                a[3] = a[6] = bend;
                break;
            case CRAMPED: // folded into a hole: knees up, head down, arms in
                a[0] = 0.9F;
                a[3] = -0.4F;
                a[6] = -0.6F;
                a[9] = a[12] = -0.5F;
                a[10] = 0.2F;
                a[13] = -0.2F;
                break;
            default:
        }
    }
}
