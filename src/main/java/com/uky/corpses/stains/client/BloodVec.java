package com.uky.corpses.stains.client;

/** Immutable vector operations used by SurfaceBlood's original emitter. */
final class BloodVec {

    static final BloodVec ZERO = new BloodVec(0, 0, 0);
    final double x, y, z;

    BloodVec(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    BloodVec add(double x, double y, double z) {
        return new BloodVec(this.x + x, this.y + y, this.z + z);
    }

    BloodVec add(BloodVec v) {
        return add(v.x, v.y, v.z);
    }

    BloodVec subtract(BloodVec v) {
        return add(-v.x, -v.y, -v.z);
    }

    BloodVec scale(double scale) {
        return new BloodVec(x * scale, y * scale, z * scale);
    }

    double lengthSqr() {
        return x * x + y * y + z * z;
    }

    double length() {
        return Math.sqrt(lengthSqr());
    }

    BloodVec normalize() {
        double length = length();
        return length < 1e-8 ? ZERO : scale(1 / length);
    }

    double distanceToSqr(BloodVec v) {
        return distanceToSqr(v.x, v.y, v.z);
    }

    double distanceToSqr(double x, double y, double z) {
        double dx = this.x - x, dy = this.y - y, dz = this.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    double distanceTo(BloodVec v) {
        return Math.sqrt(distanceToSqr(v));
    }

    BloodVec cross(BloodVec v) {
        return new BloodVec(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
    }

    BloodVec yRot(float angle) {
        float co = net.minecraft.util.MathHelper.cos(angle), si = net.minecraft.util.MathHelper.sin(angle);
        return new BloodVec(x * co + z * si, y, z * co - x * si);
    }

    static BloodVec at(net.minecraft.entity.Entity entity) {
        return new BloodVec(entity.posX, entity.posY, entity.posZ);
    }

    static BloodVec motion(net.minecraft.entity.Entity entity) {
        return new BloodVec(entity.motionX, entity.motionY, entity.motionZ);
    }

    static BloodVec center(net.minecraft.util.AxisAlignedBB box) {
        return new BloodVec((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
    }
}
