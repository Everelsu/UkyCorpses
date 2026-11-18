package com.uky.corpses.corpse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Matrix3f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;

/** Server-owned articulated body. Clients interpolate the same six transforms, including detached limbs. */
public final class Ragdoll {

    public static boolean enabled = true;
    public static final int STRIDE = 13, STATE_SIZE = Skeleton.PARTS * STRIDE;
    private static final float PIXEL = .9375F / 16;
    private static final int[] ANGLE = { 0, -1, 3, 6, 9, 12 };
    private static final float[] MASS = { 2.4F, 18, 1.4F, 1.4F, 4.6F, 4.6F };
    private JoltBridge jolt;
    private net.minecraft.world.World simulationWorld;
    private double simulationX, simulationY, simulationZ;
    private final RigidBody[] bodies = new RigidBody[Skeleton.PARTS];
    private final Generic6DofConstraint[] joints = new Generic6DofConstraint[Skeleton.PARTS];
    private final Transform[] before = new Transform[Skeleton.PARTS], after = new Transform[Skeleton.PARTS];
    private List<double[]> terrainBoxes = new ArrayList<>();
    private int severed, absent, held = -1, quietTicks;
    private Vector3f holdTarget;
    private boolean dragPhysics, passThrough;
    private final float[] dragFriction = new float[6], dragCcd = new float[6], dragRadius = new float[6];

    public Ragdoll(Mat4 frame, float[] angles, Vector3f velocity, int pose, long seed) {
        this(initial(frame, angles, velocity, pose, seed), 0);
    }

    public Ragdoll(float[] state, int mask) {
        if (!validState(state)) throw new IllegalArgumentException("Invalid ragdoll state");
        // Bullet bodies hold pose/math only; the shared Jolt world owns all simulation and terrain.
        severed = mask & 61;
        for (int i = 0; i < bodies.length; i++) {
            Transform at = decode(state, i);
            float[] lo = Skeleton.min(i), hi = Skeleton.max(i);
            bodies[i] = box(
                at,
                (hi[0] - lo[0]) * PIXEL / 2,
                (hi[1] - lo[1]) * PIXEL / 2,
                (hi[2] - lo[2]) * PIXEL / 2,
                MASS[i] * PhysicsConfig.massScale * PhysicsConfig.weight[i]);
            int j = i * STRIDE;
            bodies[i].setLinearVelocity(new Vector3f(state[j + 7], state[j + 8], state[j + 9]));
            bodies[i].setAngularVelocity(new Vector3f(state[j + 10], state[j + 11], state[j + 12]));
            before[i] = new Transform(at);
            after[i] = new Transform(at);
        }
        OriginalHumanoid.applyAerodynamicDrag(bodies);
        for (int i = 0; i < bodies.length; i++) if (i != Skeleton.BODY && (severed & 1 << i) == 0) join(i);
    }

    private void join(int i) {
        joints[i] = OriginalHumanoid.joint(bodies[Skeleton.BODY], bodies[i], after[Skeleton.BODY], after[i], i);
    }

    private static float[] initial(Mat4 frame, float[] angles, Vector3f velocity, int pose, long seed) {
        float[] state = new float[STATE_SIZE];
        Mat4 part = new Mat4();
        for (int i = 0; i < Skeleton.PARTS; i++) {
            Skeleton.part(frame, i, angles, part);
            Transform at = transform(part);
            Vector3f center = center(i);
            at.basis.transform(center);
            at.origin.add(center);
            int j = i * STRIDE;
            put(at, state, j);
            state[j + 7] = velocity.x * PhysicsConfig.initialVelocityScale;
            state[j + 8] = velocity.y * PhysicsConfig.initialVelocityScale;
            state[j + 9] = velocity.z * PhysicsConfig.initialVelocityScale;
            Vector3f spin = new Vector3f();
            state[j + 10] = spin.x;
            state[j + 11] = spin.y;
            state[j + 12] = spin.z;
        }
        return state;
    }

    private RigidBody box(Transform at, float x, float y, float z, float mass) {
        BoxShape shape = new BoxShape(new Vector3f(x, y, z));
        Vector3f inertia = new Vector3f();
        if (mass > 0) shape.calculateLocalInertia(mass, inertia);
        RigidBodyConstructionInfo info = new RigidBodyConstructionInfo(
            mass,
            new DefaultMotionState(at),
            shape,
            inertia);
        info.friction = PhysicsConfig.friction;
        info.restitution = PhysicsConfig.restitution;
        info.linearDamping = .10F;
        info.angularDamping = .15F;
        info.additionalDamping = true;
        RigidBody body = new RigidBody(info);
        if (mass > 0) {
            body.setCcdMotionThreshold(.15F);
            body.setCcdSweptSphereRadius(Math.max(.005F, Math.min(x, Math.min(y, z)) * .8F));
            body.setSleepingThresholds(.3F, .3F);
            body.forceActivationState(4);
        }
        return body;
    }

    public void terrain(List<double[]> boxes) {
        boolean same = boxes.size() == terrainBoxes.size();
        for (int i = 0; same && i < boxes.size(); i++) same = Arrays.equals(boxes.get(i), terrainBoxes.get(i));
        if (same) return;
        terrainBoxes = boxes;
        wake();
    }

    public boolean absent(int part) {
        return (absent & (1 << part)) != 0;
    }

    public void hideParts(int mask) {
        int added = (mask & 61) & ~absent;
        for (int i = 0; i < bodies.length; i++) if ((added & (1 << i)) != 0) {
            sever(i, new Vector3f());
            bodies[i].setLinearVelocity(new Vector3f());
            bodies[i].setAngularVelocity(new Vector3f());
            bodies[i].setMassProps(0, new Vector3f());
            bodies[i].updateInertiaTensor();
        }
        absent |= added;
    }

    public float takeWallSpeed() {
        return jolt == null ? 0 : jolt.takeWallSpeed();
    }

    public boolean active() {
        return true;
    }

    public boolean sleeping() {
        return held < 0 && quietTicks >= 20;
    }

    public int severed() {
        return severed;
    }

    private void wake() {
        quietTicks = 0;
        for (RigidBody body : bodies) body.activate();
    }

    public void impulse(int part, Vector3f impulse) {
        if (part < 0 || part >= bodies.length || impulse == null) return;
        wake();
        bodies[part].applyCentralImpulse(impulse);
    }

    /** Original death hit: common-body momentum and inertia, then the hit part's additional whip. */
    public void deathHit(int part, Vector3f impulse) {
        deathHit(part, impulse, null);
    }

    public void deathHit(int part, Vector3f impulse, Vector3f impactPoint) {
        if (part < 0 || part >= bodies.length || impulse == null) return;
        wake();
        OriginalHumanoid.applyHit(bodies, part, impulse, impactPoint, groundSupportHeight());
    }

    public void velocityKick(Vector3f kick) {
        velocityKick(kick, null);
    }

    public void velocityKick(Vector3f kick, Vector3f blastCenter) {
        if (kick == null) return;
        wake();
        OriginalHumanoid.applyGlobalVelocityKick(bodies, kick, blastCenter);
    }

    private float groundSupportHeight() {
        float total = 0, x = 0, z = 0, lowest = Float.POSITIVE_INFINITY;
        Vector3f center = new Vector3f(), lo = new Vector3f(), hi = new Vector3f();
        for (RigidBody body : bodies) {
            if (body.getInvMass() <= 0) continue;
            float mass = 1 / body.getInvMass();
            body.getCenterOfMassPosition(center);
            total += mass;
            x += center.x * mass;
            z += center.z * mass;
            body.getAabb(lo, hi);
            lowest = Math.min(lowest, lo.y);
        }
        x /= total;
        z /= total;
        float surface = Float.NaN;
        for (double[] box : terrainBoxes) {
            if (x < box[0] || x > box[3]
                || z < box[2]
                || z > box[5]
                || box[4] > lowest + .1
                || box[4] < Math.floor(lowest - .5)) continue;
            if (Float.isNaN(surface) || box[4] > surface) surface = (float) box[4];
        }
        return surface;
    }

    public void impulseAt(int part, Vector3f impulse, Vector3f lever) {
        if (part < 0 || part >= bodies.length || impulse == null || lever == null) return;
        wake();
        bodies[part].applyImpulse(impulse, lever);
    }

    public boolean sever(int part, Vector3f impulse) {
        if (part < 0 || part >= bodies.length || part == Skeleton.BODY || (severed & 1 << part) != 0) return false;
        joints[part] = null;
        severed |= 1 << part;
        impulse(part, impulse);
        return true;
    }

    /** AMP launch values are added velocity in blocks/second, independent of part mass. */
    public boolean severVelocity(int part, Vector3f velocity) {
        if (!sever(part, new Vector3f())) return false;
        Vector3f current = new Vector3f();
        bodies[part].getLinearVelocity(current);
        current.add(velocity);
        bodies[part].setLinearVelocity(current);
        return true;
    }

    public void hold(int part, Vector3f target) {
        held = part >= 0 && part < bodies.length && target != null ? part : -1;
        holdTarget = held < 0 ? null : new Vector3f(target);
        if (held >= 0) {
            if (!dragPhysics) for (int i = 0; i < bodies.length; i++) {
                dragFriction[i] = bodies[i].getFriction();
                dragCcd[i] = bodies[i].getCcdMotionThreshold();
                dragRadius[i] = bodies[i].getCcdSweptSphereRadius();
                if (i != held && ((severed & 1 << held) != 0 || (severed & 1 << i) != 0)) continue;
                bodies[i].setFriction(Math.min(.35F, dragFriction[i]));
                bodies[i].setCcdMotionThreshold(Math.min(.08F, dragCcd[i]));
                bodies[i].setCcdSweptSphereRadius(Math.max(.12F, dragRadius[i]));
            }
            dragPhysics = true;
            wake();
        } else if (dragPhysics) {
            for (int i = 0; i < bodies.length; i++) {
                bodies[i].setFriction(dragFriction[i]);
                bodies[i].setCcdMotionThreshold(dragCcd[i]);
                bodies[i].setCcdSweptSphereRadius(dragRadius[i]);
            }
            dragPhysics = false;
        }
    }

    /** Fixed six 120 Hz steps per game tick. Sleeping bodies retain their physical pose indefinitely. */
    public float tick() {
        for (int i = 0; i < bodies.length; i++) before[i].set(after[i]);
        if (held >= 0) {
            Vector3f want = new Vector3f(holdTarget), velocity = new Vector3f();
            want.sub(after[held].origin);
            want.scale(20);
            float horizontal = (float) Math.sqrt(want.x * want.x + want.z * want.z);
            if (horizontal > 10) {
                want.x *= 10 / horizontal;
                want.z *= 10 / horizontal;
            }
            bodies[held].getLinearVelocity(velocity);
            want.y = want.y > 1.1F ? 1.1F : want.y < 0 ? Math.min(0, velocity.y) : want.y;
            bodies[held].setLinearVelocity(want);
            if (held != Skeleton.BODY && (severed & 1 << held) == 0) {
                bodies[Skeleton.BODY].getLinearVelocity(velocity);
                velocity.x += (want.x - velocity.x) * .65F;
                velocity.z += (want.z - velocity.z) * .65F;
                horizontal = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
                if (horizontal > 9) {
                    velocity.x *= 9 / horizontal;
                    velocity.z *= 9 / horizontal;
                }
                bodies[Skeleton.BODY].setLinearVelocity(velocity);
            }
            for (int i = 0; i < bodies.length; i++) {
                if (i != held && ((severed & 1 << held) != 0 || (severed & 1 << i) != 0)) continue;
                bodies[i].getAngularVelocity(velocity);
                velocity.scale(.38F);
                float limit = i == Skeleton.BODY ? .9F : 1.15F;
                if (velocity.length() > limit) velocity.scale(limit / velocity.length());
                bodies[i].setAngularVelocity(velocity);
            }
        }
        OriginalHumanoid.clampSpeeds(bodies);
        if (jolt == null) jolt = new JoltBridge(bodies, joints, simulationWorld, simulationX, simulationY, simulationZ);
        jolt.hideParts(absent);
        jolt.passThrough(passThrough);
        jolt.terrain(terrainBoxes);
        float nativeImpact = jolt.step(severed, !Float.isNaN(groundSupportHeight()));
        quietTicks = OriginalHumanoid.stationary(bodies) ? quietTicks + 1 : 0;
        for (int i = 0; i < bodies.length; i++) bodies[i].getWorldTransform(after[i]);
        return nativeImpact;
    }

    public void guideFlight(Vector3f target, float acceleration, float spin, boolean kick) {
        Vector3f velocity = new Vector3f(), delta = new Vector3f();
        bodies[Skeleton.BODY].getLinearVelocity(delta);
        delta.sub(target, delta);
        float limit = acceleration * .05F;
        if (!kick && delta.length() > limit) delta.scale(limit / delta.length());
        for (int i = 0; i < bodies.length; i++) if ((absent & 1 << i) == 0) {
            bodies[i].getLinearVelocity(velocity);
            velocity.add(delta);
            bodies[i].setLinearVelocity(velocity);
            if (spin > 0) bodies[i].setAngularVelocity(new Vector3f(spin, spin * .2F, spin * .3F));
        }
        passThrough = true;
        quietTicks = 0;
    }

    public void endFlight() {
        passThrough = false;
        if (jolt != null) jolt.passThrough(false);
    }

    public void bindWorld(net.minecraft.world.World world, double x, double y, double z) {
        if (simulationWorld != world || simulationX != x || simulationY != y || simulationZ != z) disposePhysics();
        simulationWorld = world;
        simulationX = x;
        simulationY = y;
        simulationZ = z;
    }

    public void disposePhysics() {
        if (jolt != null) {
            jolt.close();
            jolt = null;
        }
    }

    public double[] bounds() {
        double[] b = { 1e9, 1e9, 1e9, -1e9, -1e9, -1e9 };
        for (Transform at : after) for (int k = 0; k < 3; k++) {
            double p = k == 0 ? at.origin.x : k == 1 ? at.origin.y : at.origin.z;
            b[k] = Math.min(b[k], p - 2);
            b[k + 3] = Math.max(b[k + 3], p + 2);
        }
        return b;
    }

    public float[] state() {
        float[] state = new float[STATE_SIZE];
        Vector3f v = new Vector3f();
        for (int i = 0; i < bodies.length; i++) {
            int j = i * STRIDE;
            put(after[i], state, j);
            bodies[i].getLinearVelocity(v);
            state[j + 7] = v.x;
            state[j + 8] = v.y;
            state[j + 9] = v.z;
            bodies[i].getAngularVelocity(v);
            state[j + 10] = v.x;
            state[j + 11] = v.y;
            state[j + 12] = v.z;
        }
        return state;
    }

    /** Exact shape and finite bounded transforms are required at saves and network boundaries. */
    public static boolean validState(float[] state) {
        if (state == null || state.length != STATE_SIZE) return false;
        for (float f : state) if (!Float.isFinite(f) || Math.abs(f) > 1000000) return false;
        for (int i = 0; i < Skeleton.PARTS; i++) {
            int j = i * STRIDE;
            double q = 0;
            for (int k = 3; k < 7; k++) q += state[j + k] * (double) state[j + k];
            if (q < .25 || q > 4) return false;
            for (int k = 7; k < STRIDE; k++) if (Math.abs(state[j + k]) > 1024) return false;
        }
        return true;
    }

    public void applyState(float[] state, int mask) {
        if (!validState(state)) throw new IllegalArgumentException("Invalid ragdoll snapshot");
        for (int i = 0; i < bodies.length; i++) {
            before[i].set(after[i]);
            after[i].set(decode(state, i));
            bodies[i].setWorldTransform(after[i]);
            int j = i * STRIDE;
            bodies[i].setLinearVelocity(new Vector3f(state[j + 7], state[j + 8], state[j + 9]));
            bodies[i].setAngularVelocity(new Vector3f(state[j + 10], state[j + 11], state[j + 12]));
            if ((mask & 1 << i) != 0 && joints[i] != null) {
                joints[i] = null;
            }
        }
        severed = mask & 61;
    }

    public Skeleton.Shape shape(float partial, double dx, double dy, double dz) {
        partial = Math.max(0, Math.min(1, partial));
        Transform torso = sample(Skeleton.BODY, partial), inverse = new Transform(torso);
        inverse.inverse();
        float[] angles = new float[15];
        for (int i = 0; i < bodies.length; i++) if (ANGLE[i] >= 0) {
            Transform relative = sample(i, partial);
            relative.mul(inverse, relative);
            Matrix3f m = relative.basis;
            int a = ANGLE[i];
            angles[a + 1] = (float) Math.asin(Math.max(-1, Math.min(1, -m.m20)));
            if (Math.abs(m.m20) < .9999F) {
                angles[a] = (float) Math.atan2(m.m21, m.m22);
                angles[a + 2] = (float) Math.atan2(m.m10, m.m00);
            } else {
                angles[a] = 0;
                angles[a + 2] = (float) Math.atan2(-m.m01, m.m11);
            }
        }
        Mat4 frame = new Mat4();
        uncenter(torso, Skeleton.BODY);
        write(torso, frame, .9375F);
        frame.moved(dx, dy, dz);
        Skeleton.Shape shape = Skeleton.snapshot(frame, angles);
        shape.physical = true;
        for (int i = 0; i < bodies.length; i++) {
            Transform at = sample(i, partial);
            uncenter(at, i);
            write(at, shape.parts[i], PIXEL);
            shape.parts[i].moved(dx, dy, dz);
        }
        return shape;
    }

    public void pose(float partial, Mat4 frame, float[] angles, double dx, double dy, double dz) {
        Skeleton.Shape shape = shape(partial, dx, dy, dz);
        frame.set(shape.frame);
        System.arraycopy(shape.angles, 0, angles, 0, angles.length);
    }

    private static Vector3f center(int part) {
        float[] lo = Skeleton.min(part), hi = Skeleton.max(part);
        return new Vector3f((lo[0] + hi[0]) * PIXEL / 2, (lo[1] + hi[1]) * PIXEL / 2, (lo[2] + hi[2]) * PIXEL / 2);
    }

    private static void uncenter(Transform at, int part) {
        Vector3f center = center(part);
        at.basis.transform(center);
        at.origin.sub(center);
    }

    private Transform sample(int i, float partial) {
        Transform out = new Transform();
        Quat4f a = new Quat4f(), b = new Quat4f();
        before[i].getRotation(a);
        after[i].getRotation(b);
        a.interpolate(b, partial);
        out.setRotation(a);
        out.origin.interpolate(before[i].origin, after[i].origin, partial);
        return out;
    }

    private static Transform decode(float[] state, int i) {
        int j = i * STRIDE;
        Transform out = new Transform();
        Quat4f q = new Quat4f(state[j + 3], state[j + 4], state[j + 5], state[j + 6]);
        q.normalize();
        out.setRotation(q);
        out.origin.set(state[j], state[j + 1], state[j + 2]);
        return out;
    }

    private static void put(Transform at, float[] state, int j) {
        state[j] = at.origin.x;
        state[j + 1] = at.origin.y;
        state[j + 2] = at.origin.z;
        Quat4f q = new Quat4f();
        at.getRotation(q);
        q.normalize();
        state[j + 3] = q.x;
        state[j + 4] = q.y;
        state[j + 5] = q.z;
        state[j + 6] = q.w;
    }

    private static Transform transform(Mat4 m) {
        float scale = (float) Math.sqrt(m.m[0] * m.m[0] + m.m[4] * m.m[4] + m.m[8] * m.m[8]);
        Transform out = new Transform();
        out.basis.set(
            new float[] { (float) m.m[0] / scale, (float) m.m[1] / scale, (float) m.m[2] / scale,
                (float) m.m[4] / scale, (float) m.m[5] / scale, (float) m.m[6] / scale, (float) m.m[8] / scale,
                (float) m.m[9] / scale, (float) m.m[10] / scale });
        out.origin.set((float) m.m[3], (float) m.m[7], (float) m.m[11]);
        return out;
    }

    private static void write(Transform t, Mat4 out, float scale) {
        out.identity();
        Matrix3f b = t.basis;
        out.m[0] = b.m00 * scale;
        out.m[1] = b.m01 * scale;
        out.m[2] = b.m02 * scale;
        out.m[4] = b.m10 * scale;
        out.m[5] = b.m11 * scale;
        out.m[6] = b.m12 * scale;
        out.m[8] = b.m20 * scale;
        out.m[9] = b.m21 * scale;
        out.m[10] = b.m22 * scale;
        out.m[3] = t.origin.x;
        out.m[7] = t.origin.y;
        out.m[11] = t.origin.z;
    }
}
