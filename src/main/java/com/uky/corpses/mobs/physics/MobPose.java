package com.uky.corpses.mobs.physics;

import java.util.EnumMap;
import java.util.Map;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

/** Source MobPose data without the modern client/model capture dependencies. */
public final class MobPose {

    public static final class PartPose {

        public final float xRot, yRot, zRot;

        public PartPose(float x, float y, float z) {
            xRot = x;
            yRot = y;
            zRot = z;
        }
    }

    public static final class PartTransform {

        public final Vector3f offset, halfExtents;
        public final Quat4f rotation;

        public PartTransform(Vector3f offset, Quat4f rotation, Vector3f halfExtents) {
            this.offset = new Vector3f(offset);
            this.rotation = new Quat4f(rotation);
            this.halfExtents = new Vector3f(halfExtents);
        }
    }

    private final Map<PhysicsPart, PartPose> poses;
    private final Map<PhysicsPart, PartTransform> transforms;
    private final Vector3f origin;

    public MobPose(Map<PhysicsPart, PartPose> poses) {
        this(poses, null, null);
    }

    public MobPose(Map<PhysicsPart, PartPose> poses, Map<PhysicsPart, PartTransform> transforms, Vector3f origin) {
        this.poses = new EnumMap<>(PhysicsPart.class);
        if (poses != null) this.poses.putAll(poses);
        this.transforms = new EnumMap<>(PhysicsPart.class);
        if (transforms != null) this.transforms.putAll(transforms);
        this.origin = origin == null ? null : new Vector3f(origin);
    }

    public MobPose withOrigin(Vector3f value) {
        return new MobPose(poses, transforms, value);
    }

    public Vector3f getOrigin() {
        return origin == null ? null : new Vector3f(origin);
    }

    public Quat4f rotationFor(PhysicsPart part, Quat4f base) {
        PartTransform t = transforms.get(part);
        if (t != null) return new Quat4f(t.rotation);
        PartPose p = poses.get(part);
        return p == null ? new Quat4f(base) : RagdollBodyFactory.modelPartRotation(base, p.xRot, p.yRot, p.zRot);
    }

    public Vector3f centerFor(PhysicsPart part) {
        PartTransform t = transforms.get(part);
        if (t == null || origin == null) return null;
        Vector3f out = new Vector3f(origin);
        out.add(t.offset);
        return out;
    }

    public Vector3f extentsFor(PhysicsPart part) {
        PartTransform t = transforms.get(part);
        if (t == null) return null;
        Vector3f h = t.halfExtents;
        return h.x > 1e-4F && h.y > 1e-4F && h.z > 1e-4F ? new Vector3f(h) : null;
    }
}
