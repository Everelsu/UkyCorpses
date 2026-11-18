package com.uky.corpses.corpse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * A blast throws the body: the explosion that reached a player is remembered for the tick, and if it kills them the
 * body flies off away from it, a short arc worked out here against the blocks (no tunnelling into hillsides), and
 * lands where the arc ends. Clients replay the arc (Settle.THROWN); the body only exists at the landing spot.
 */
public final class Throw {

    /** Player -> {x, y, z, size, world time} of the last blast that reached them. */
    private static final Map<UUID, double[]> blasts = new HashMap<>();
    private static final int MAX_TICKS = 40;

    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate e) {
        if (e.world.isRemote) return;
        long now = e.world.getTotalWorldTime();
        for (Entity en : e.getAffectedEntities()) if (en instanceof EntityPlayer) blasts.put(
            en.getUniqueID(),
            new double[] { e.explosion.explosionX, e.explosion.explosionY, e.explosion.explosionZ,
                e.explosion.explosionSize, now });
    }

    /** The blast that just reached p ({x, y, z, size}), or null; consumed. */
    public static double[] take(EntityPlayer p) {
        double[] b = blasts.remove(p.getUniqueID());
        if (b == null || p.worldObj.getTotalWorldTime() - (long) b[4] > 2) return null;
        return b;
    }

    /**
     * Flight of a body from feet spot (x, y, z) away from blast. Null if it can't go anywhere; else {x, y, z (feet on
     * the floor where it lands), ticks in the air, apex (highest point above the straight line), yaw of the flight}.
     */
    public static double[] fly(World w, double x, double y, double z, double[] blast) {
        double dx = x - blast[0], dz = z - blast[2], dy = y + 0.9 - blast[1];
        double size = Math.max(1, blast[3]), dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double power = MathHelper.clamp_double(1 - dist / (size * 2), 0.15, 1) * Math.min(size, 6) / 4;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.05) { // right on top of it: any way
            double a = w.rand.nextDouble() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            flat = 1;
        }
        double vx = dx / flat * (0.25 + 0.45 * power), vz = dz / flat * (0.25 + 0.45 * power), vy = 0.3 + 0.35 * power;
        double sx = x, sy = y, sz = z, top = y;
        int t = 0;
        boolean landed = false;
        while (t < MAX_TICKS) {
            t++;
            // horizontal first: a wall stops it and it drops down along it
            if (vx != 0 || vz != 0) {
                if (free(w, x + vx, y, z + vz)) {
                    x += vx;
                    z += vz;
                } else vx = vz = 0;
            }
            if (vy > 0) {
                if (free(w, x, y + vy, z)) y += vy;
                else vy = 0;
            } else {
                double floor = floor(w, x, y, z, -vy + 0.01);
                if (!Double.isNaN(floor)) {
                    y = floor;
                    landed = true;
                } else y += vy;
            }
            top = Math.max(top, y);
            if (landed) break;
            vy -= 0.08;
            vx *= 0.98;
            vz *= 0.98;
            if (!w.blockExists(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z)))
                return null;
        }
        if (!landed) return null;
        if (t < 3 || (x - sx) * (x - sx) + (z - sz) * (z - sz) < 0.25) return null; // hardly moved: just falls
        // how far the top of the arc is over the middle of the straight line from start to end
        double apex = Math.max(0.25, top - (sy + y) / 2);
        float yaw = (float) Math.toDegrees(Math.atan2(-(x - sx), z - sz));
        return new double[] { x, y, z, t, Math.min(apex, 3), yaw };
    }

    /** A crouched ball where a flying body is: no block in it. */
    private static boolean free(World w, double x, double y, double z) {
        return w.func_147461_a(AxisAlignedBB.getBoundingBox(x - 0.25, y + 0.05, z - 0.25, x + 0.25, y + 0.6, z + 0.25))
            .isEmpty();
    }

    /** The top of what's below feet y within `reach`, NaN if nothing. */
    private static double floor(World w, double x, double y, double z, double reach) {
        List<?> boxes = w
            .func_147461_a(AxisAlignedBB.getBoundingBox(x - 0.25, y - reach, z - 0.25, x + 0.25, y + 0.05, z + 0.25));
        double best = Double.NaN;
        for (Object o : boxes) {
            AxisAlignedBB b = (AxisAlignedBB) o;
            if (b.maxY <= y + 0.05 && (Double.isNaN(best) || b.maxY > best)) best = b.maxY;
        }
        return best;
    }
}
