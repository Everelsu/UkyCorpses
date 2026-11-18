package com.uky.corpses.amp;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Installed HitResolver plus the original standard-humanoid ray boxes and bucket fallback. */
final class HitResolver {

    static final class Hit {

        final Part part;
        final Vec3 point, direction;
        final boolean sharp;

        Hit(Part part, Vec3 point, Vec3 direction, boolean sharp) {
            this.part = part;
            this.point = point;
            this.direction = direction;
            this.sharp = sharp;
        }
    }

    static boolean sharp(ItemStack item) {
        return item != null && (item.getItem() instanceof ItemSword || item.getItem() instanceof ItemAxe
            || item.getItem() instanceof ItemShears);
    }

    static Hit resolve(EntityLivingBase victim, DamageSource source) {
        if (source == null || source.getSourceOfDamage() == null && source.getEntity() == null) return null;
        String type = source.damageType;
        if ("fall".equals(type) || "inFire".equals(type) || "onFire".equals(type) || "magic".equals(type)) return null;
        Vec3 origin, direction;
        boolean sharp;
        Entity direct = source.getSourceOfDamage();
        if (source.isProjectile() && direct != null) {
            direction = Vec3.createVectorHelper(direct.motionX, direct.motionY, direct.motionZ);
            if (direction.lengthVector() < .001) return null;
            direction = direction.normalize();
            origin = Vec3.createVectorHelper(direct.posX, direct.posY, direct.posZ);
            sharp = true;
        } else if (source.getEntity() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getEntity();
            direction = attacker.getLookVec();
            if (direction.lengthVector() < .001) direction = Vec3.createVectorHelper(
                victim.posX - attacker.posX,
                victim.posY - attacker.posY,
                victim.posZ - attacker.posZ);
            if (direction.lengthVector() < .001) return null;
            direction = direction.normalize();
            Vec3 eye = Vec3
                .createVectorHelper(attacker.posX, attacker.boundingBox.minY + attacker.getEyeHeight(), attacker.posZ),
                centre = Vec3
                    .createVectorHelper(victim.posX, victim.boundingBox.minY + victim.height * .5, victim.posZ);
            double reach = Math.max(4.5, eye.distanceTo(centre) + victim.width + 1);
            Vec3 end = eye.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach);
            MovingObjectPosition clip = victim.boundingBox.expand(.05, .05, .05)
                .calculateIntercept(eye, end);
            origin = clip == null ? closest(eye, end, centre) : clip.hitVec;
            sharp = sharp(attacker.getHeldItem());
        } else return null;
        double yaw = Math.toRadians(victim.renderYawOffset), c = Math.cos(yaw), s = Math.sin(yaw),
            dx = origin.xCoord - victim.posX, dz = origin.zCoord - victim.posZ;
        double ox = -(dx * c + dz * s), oy = origin.yCoord - victim.boundingBox.minY, oz = -dx * s + dz * c,
            rx = -(direction.xCoord * c + direction.zCoord * s), rz = -direction.xCoord * s + direction.zCoord * c;
        Part part = rayPart(ox, oy, oz, rx, direction.yCoord, rz);
        Vec3 point = origin;
        if (part == null) {
            double reach = origin.distanceTo(Vec3.createVectorHelper(victim.posX, victim.posY, victim.posZ))
                + victim.height
                + victim.width
                + 1;
            MovingObjectPosition clip = victim.boundingBox.expand(.05, .05, .05)
                .calculateIntercept(
                    origin,
                    origin.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach));
            if (clip != null) point = clip.hitVec;
            dx = point.xCoord - victim.posX;
            dz = point.zCoord - victim.posZ;
            part = bucket(
                -(dx * c + dz * s),
                (point.yCoord - victim.boundingBox.minY) / victim.height,
                Math.max(.4, victim.width));
        }
        return new Hit(part, point, direction, sharp);
    }

    private static Vec3 closest(Vec3 start, Vec3 end, Vec3 target) {
        double x = end.xCoord - start.xCoord, y = end.yCoord - start.yCoord, z = end.zCoord - start.zCoord,
            l = x * x + y * y + z * z;
        if (l < 1e-6) return target;
        double t = Math.max(
            0,
            Math.min(
                1,
                ((target.xCoord - start.xCoord) * x + (target.yCoord - start.yCoord) * y
                    + (target.zCoord - start.zCoord) * z) / l));
        return start.addVector(x * t, y * t, z * t);
    }

    static Part bucket(double x, double relativeY, double width) {
        if (relativeY > .85) return Part.HEAD;
        if (relativeY < .55) return x < 0 ? Part.LEFT_LEG : Part.RIGHT_LEG;
        if (Math.abs(x) > width * .3) return x < 0 ? Part.LEFT_ARM : Part.RIGHT_ARM;
        return Part.TORSO;
    }

    private static final Part[] ORDER = { Part.TORSO, Part.HEAD, Part.LEFT_LEG, Part.RIGHT_LEG, Part.LEFT_ARM,
        Part.RIGHT_ARM };
    private static final double[][] BOXES = { { 0, 1.3, 0, .25, .4, .15 }, { 0, 1.85, 0, .2, .2, .2 },
        { -.125, .55, 0, .125, .45, .15 }, { .125, .55, 0, .125, .45, .15 }, { -.35, 1.17, 0, .1, .35, .1 },
        { .35, 1.17, 0, .1, .35, .1 } };

    static Part rayPart(double ox, double oy, double oz, double dx, double dy, double dz) {
        double best = -1;
        Part hit = null;
        for (int i = 0; i < BOXES.length; i++) {
            double t = rayBox(ox, oy, oz, dx, dy, dz, BOXES[i]);
            if (t >= 0 && (best < 0 || t < best)) {
                best = t;
                hit = ORDER[i];
            }
        }
        return hit;
    }

    static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz, double[] box) {
        double lo = 0, hi = Double.POSITIVE_INFINITY;
        double[] origin = { ox, oy, oz }, direction = { dx, dy, dz };
        for (int k = 0; k < 3; k++) {
            double min = box[k] - box[k + 3], max = box[k] + box[k + 3];
            if (Math.abs(direction[k]) < 1e-9) {
                if (origin[k] < min || origin[k] > max) return -1;
                continue;
            }
            double a = (min - origin[k]) / direction[k], b = (max - origin[k]) / direction[k];
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
            if (hi < lo) return -1;
        }
        return lo;
    }

    private HitResolver() {}
}
