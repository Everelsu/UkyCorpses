package com.uky.corpses.port.physics.jolt;

import javax.vecmath.Vector3f;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.Vec3;
import com.uky.corpses.port.physics.PhysicsShape;

final class JoltShape implements PhysicsShape {

    private static final float MAX_CONVEX_RADIUS = 0.05F;
    final BoxShape shape;
    private final float halfX;
    private final float halfY;
    private final float halfZ;

    JoltShape(float halfX, float halfY, float halfZ) {
        this.halfX = Math.max(1.0E-4F, halfX);
        this.halfY = Math.max(1.0E-4F, halfY);
        this.halfZ = Math.max(1.0E-4F, halfZ);
        float smallest = Math.min(this.halfX, Math.min(this.halfY, this.halfZ));
        float convexRadius = Math.min(0.05F, smallest * 0.5F);
        this.shape = new BoxShape(new Vec3(this.halfX, this.halfY, this.halfZ), convexRadius);
    }

    @Override
    public void getHalfExtents(Vector3f out) {
        out.set(this.halfX, this.halfY, this.halfZ);
    }

    @Override
    public boolean isBox() {
        return true;
    }

    float halfX() {
        return this.halfX;
    }

    float halfY() {
        return this.halfY;
    }

    float halfZ() {
        return this.halfZ;
    }
}
