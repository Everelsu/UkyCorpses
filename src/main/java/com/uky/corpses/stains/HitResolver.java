package com.uky.corpses.stains;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

final class HitResolver {

    static void resolve(SurfaceBlood.Hit hit, EntityLivingBase victim, Entity direct, Entity attacker,
        DamageCategory category) {
        AxisAlignedBB box = victim.boundingBox;
        Vec3 center = Vec3
            .createVectorHelper((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        Vec3 impact, direction, origin;
        if (category == DamageCategory.FALL) {
            impact = Vec3.createVectorHelper(victim.posX, box.minY + .05, victim.posZ);
            direction = Vec3.createVectorHelper(0, -1, 0);
        } else {
            if (category == DamageCategory.PIERCE && direct != null && direct != attacker) {
                Vec3 velocity = Vec3.createVectorHelper(direct.motionX, direct.motionY, direct.motionZ);
                hit.speed = (float) velocity.lengthVector();
                direction = velocity.dotProduct(velocity) > 1e-6 ? velocity.normalize()
                    : Vec3.createVectorHelper(direct.posX, direct.posY, direct.posZ)
                        .subtract(center)
                        .normalize();
                origin = Vec3.createVectorHelper(
                    direct.posX - direction.xCoord * (victim.width + 2),
                    direct.posY - direction.yCoord * (victim.width + 2),
                    direct.posZ - direction.zCoord * (victim.width + 2));
            } else if (attacker instanceof EntityLivingBase && direct == attacker) {
                origin = Vec3.createVectorHelper(attacker.posX, attacker.posY + attacker.getEyeHeight(), attacker.posZ);
                direction = attacker.getLookVec();
                if (direction == null || direction.dotProduct(direction) < 1e-6) direction = origin.subtract(center);
                if (direction.dotProduct(direction) < 1e-6) direction = Vec3.createVectorHelper(0, 0, 1);
                direction = direction.normalize();
            } else if (direct != null && direct.getDistanceSq(center.xCoord, center.yCoord, center.zCoord) > 1e-4) {
                direction = Vec3.createVectorHelper(direct.posX, direct.posY, direct.posZ)
                    .subtract(center)
                    .normalize();
                double reach = victim.width + victim.height + 1;
                origin = center
                    .addVector(-direction.xCoord * reach, -direction.yCoord * reach, -direction.zCoord * reach);
            } else {
                float yaw = victim.getRNG()
                    .nextFloat() * (float) (Math.PI * 2);
                direction = Vec3
                    .createVectorHelper(
                        net.minecraft.util.MathHelper.sin(yaw),
                        -.1,
                        net.minecraft.util.MathHelper.cos(yaw))
                    .normalize();
                Vec3 aim = center.addVector(0, victim.height * .15, 0);
                origin = aim.addVector(-direction.xCoord * 4, -direction.yCoord * 4, -direction.zCoord * 4);
            }
            impact = entry(box, origin, direction, center);
        }
        hit.x = impact.xCoord;
        hit.y = impact.yCoord;
        hit.z = impact.zCoord;
        hit.dx = (float) direction.xCoord;
        hit.dy = (float) direction.yCoord;
        hit.dz = (float) direction.zCoord;
    }

    static Vec3 entry(AxisAlignedBB box, Vec3 origin, Vec3 direction, Vec3 fallback) {
        double reach = origin.distanceTo(fallback) + box.getAverageEdgeLength() + 2;
        Vec3 end = origin.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach);
        MovingObjectPosition hit = box.expand(.02, .02, .02)
            .calculateIntercept(origin, end);
        if (hit != null) return hit.hitVec;
        double dx = end.xCoord - origin.xCoord, dy = end.yCoord - origin.yCoord, dz = end.zCoord - origin.zCoord;
        double length = dx * dx + dy * dy + dz * dz;
        double t = length < 1e-6 ? 0
            : Math.max(
                0,
                Math.min(
                    1,
                    ((fallback.xCoord - origin.xCoord) * dx + (fallback.yCoord - origin.yCoord) * dy
                        + (fallback.zCoord - origin.zCoord) * dz) / length));
        return Vec3.createVectorHelper(
            Math.max(box.minX, Math.min(box.maxX, origin.xCoord + dx * t)),
            Math.max(box.minY, Math.min(box.maxY, origin.yCoord + dy * t)),
            Math.max(box.minZ, Math.min(box.maxZ, origin.zCoord + dz * t)));
    }

    private HitResolver() {}
}
