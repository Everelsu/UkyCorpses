package com.uky.corpses.corpse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.Transform;
import com.uky.corpses.EntityCorpse;
import com.uky.corpses.port.physics.BodyProperties;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;
import com.uky.corpses.port.physics.PhysicsShape;
import com.uky.corpses.port.physics.PhysicsWorld;
import com.uky.corpses.port.physics.StepQuality;
import com.uky.corpses.port.physics.jolt.JoltNatives;
import com.uky.corpses.port.physics.jolt.JoltWorld;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Shared native world: bodies collide with one another and every block collider has one reference-counted owner. */
final class JoltBridge {

    private static final Map<World, Group> SHARED = new IdentityHashMap<>();
    private static boolean registered;
    private final Group group;
    private final RigidBody[] math;
    private final PhysicsBody[] bodies = new PhysicsBody[6];
    private final PhysicsConstraint[] constraints = new PhysicsConstraint[6];
    private final Set<String> terrain = new HashSet<>();
    private List<double[]> boxes = new ArrayList<>();
    private final double dx, dy, dz;
    private final PhysTransform transform = new PhysTransform();
    private final Vector3f velocity = new Vector3f();
    private boolean closed, pending, passThrough;
    private int hidden;
    private float impact, wallSpeed;

    float takeWallSpeed() {
        float speed = wallSpeed;
        wallSpeed = 0;
        return speed;
    }

    static void init() {
        if (registered) return;
        registered = true;
        Events events = new Events();
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        MinecraftForge.EVENT_BUS.register(events);
    }

    JoltBridge(RigidBody[] math, Generic6DofConstraint[] joints) {
        this(math, joints, null, 0, 0, 0);
    }

    JoltBridge(RigidBody[] math, Generic6DofConstraint[] joints, World context, double ox, double oy, double oz) {
        if (!JoltNatives.load())
            throw new IllegalStateException("Jolt 6.0.0 is required: " + JoltNatives.failureReason());
        this.math = math;
        group = group(context, ox, oy, oz);
        dx = ox - group.x;
        dy = oy - group.y;
        dz = oz - group.z;
        group.members.add(this);
        try {
            for (int i = 0; i < 6; i++) {
                Vector3f half = ((BoxShape) math[i].getCollisionShape()).getHalfExtentsWithMargin(new Vector3f());
                PhysicsShape shape = group.world.createBoxShape(half.x, half.y, half.z);
                Transform at = math[i].getWorldTransform(new Transform());
                Vector3f place = new Vector3f(
                    (float) (at.origin.x + dx),
                    (float) (at.origin.y + dy),
                    (float) (at.origin.z + dz));
                BodyProperties properties = new BodyProperties();
                properties.mass = math[i].getInvMass() > 0 ? 1 / math[i].getInvMass() : 1;
                properties.linearDamping = math[i].getLinearDamping();
                properties.angularDamping = math[i].getAngularDamping();
                properties.friction = PhysicsConfig.friction;
                properties.restitution = PhysicsConfig.restitution;
                properties.allowSleeping = false;
                properties.ccdMotionThreshold = .15F;
                properties.ccdSweptSphereRadius = Math.max(.005F, Math.min(half.x, Math.min(half.y, half.z)) * .8F);
                math[i].getLinearVelocity(properties.linearVelocity);
                bodies[i] = group.world.createDynamicBody(shape, place, at.getRotation(new Quat4f()), properties);
                if (bodies[i] == null) throw new IllegalStateException("Jolt ragdoll body budget exhausted");
                bodies[i].setUserPointer(this);
                bodies[i].setGravityFactor(PhysicsConfig.partGravity[i]);
                math[i].getAngularVelocity(velocity);
                bodies[i].setAngularVelocity(velocity);
                group.world.addBody(bodies[i]);
            }
            for (int i = 0; i < 6; i++) if (joints[i] != null) {
                Generic6DofConstraint j = joints[i];
                Transform a = j.getFrameOffsetA(new Transform()), b = j.getFrameOffsetB(new Transform());
                PhysTransform pa = new PhysTransform(), pb = new PhysTransform();
                pa.basis.set(a.basis);
                pa.origin.set(a.origin);
                pb.basis.set(b.basis);
                pb.origin.set(b.origin);
                Vector3f low = new Vector3f(
                    j.getRotationalLimitMotor(0).loLimit,
                    j.getRotationalLimitMotor(1).loLimit,
                    j.getRotationalLimitMotor(2).loLimit);
                Vector3f high = new Vector3f(
                    j.getRotationalLimitMotor(0).hiLimit,
                    j.getRotationalLimitMotor(1).hiLimit,
                    j.getRotationalLimitMotor(2).hiLimit);
                constraints[i] = group.world.createSixDofConstraint(
                    bodies[Skeleton.BODY],
                    bodies[i],
                    pa,
                    pb,
                    j.getTranslationalLimitMotor().lowerLimit,
                    j.getTranslationalLimitMotor().upperLimit,
                    low,
                    high);
                constraints[i].setKeepsCollision(i == Skeleton.RARM || i == Skeleton.LARM);
                group.world.addConstraint(constraints[i]);
            }
        } catch (Throwable error) {
            close();
            throw error;
        }
    }

    void terrain(List<double[]> next) {
        boolean same = next.size() == boxes.size();
        for (int i = 0; same && i < next.size(); i++) same = Arrays.equals(next.get(i), boxes.get(i));
        if (same) return;
        group.updateTerrain(terrain, next, dx, dy, dz);
        boxes = next;
        for (PhysicsBody body : bodies) body.activate();
    }

    float step(int mask, boolean grounded) {
        if (closed || group.closed) throw new IllegalStateException("Closed Jolt body");
        for (int i = 0; i < 6; i++) if (constraints[i] != null) {
            if ((mask & 1 << i) != 0) {
                group.world.removeConstraint(constraints[i]);
                group.world.destroyConstraint(constraints[i]);
                constraints[i] = null;
            } else {
                float scale = (i == Skeleton.HEAD ? 16 : 1) * (grounded ? 1 : PhysicsConfig.airborneScale);
                constraints[i].driveToOrientation(
                    0,
                    0,
                    0,
                    1,
                    PhysicsConfig.relaxFrequency * scale,
                    PhysicsConfig.relaxDamping,
                    PhysicsConfig.relaxTorque);
            }
        }
        pending = true;
        if (group.context == null) group.step();
        float result = impact;
        impact = 0;
        return result > .12F ? result : 0;
    }

    void hideParts(int mask) {
        hidden = mask;
        for (int i = 0; i < 6; i++) if ((mask & (1 << i)) != 0) {
            bodies[i].setContactResponse(false);
            bodies[i].setStatic(true);
            bodies[i].setGravityFactor(0);
        }
    }

    void passThrough(boolean next) {
        if (passThrough == next) return;
        passThrough = next;
        for (int i = 0; i < 6; i++) bodies[i].setContactResponse(!next && (hidden & 1 << i) == 0);
    }

    private void push() {
        for (int i = 0; i < 6; i++) {
            if (math[i].getInvMass() <= 0) continue;
            bodies[i].setFriction(math[i].getFriction());
            bodies[i].setCcdMotionThreshold(math[i].getCcdMotionThreshold());
            bodies[i].setCcdSweptSphereRadius(math[i].getCcdSweptSphereRadius());
            math[i].getLinearVelocity(velocity);
            bodies[i].setLinearVelocity(velocity);
            math[i].getAngularVelocity(velocity);
            bodies[i].setAngularVelocity(velocity);
        }
    }

    private void pull() {
        for (int i = 0; i < 6; i++) {
            if (math[i].getInvMass() <= 0) continue;
            bodies[i].getWorldTransform(transform);
            Transform out = new Transform();
            out.basis.set(transform.basis);
            out.origin.set(
                (float) (transform.origin.x - dx),
                (float) (transform.origin.y - dy),
                (float) (transform.origin.z - dz));
            math[i].setWorldTransform(out);
            bodies[i].getLinearVelocity(velocity);
            math[i].setLinearVelocity(velocity);
            bodies[i].getAngularVelocity(velocity);
            math[i].setAngularVelocity(velocity);
        }
        pending = false;
    }

    void close() {
        if (closed) return;
        closed = true;
        if (!group.closed) {
            for (PhysicsConstraint constraint : constraints) if (constraint != null) {
                group.world.removeConstraint(constraint);
                group.world.destroyConstraint(constraint);
            }
            for (PhysicsBody body : bodies) if (body != null) {
                group.world.removeBody(body);
                group.world.destroyBody(body);
            }
            for (String id : terrain) group.releaseTerrain(id);
        }
        terrain.clear();
        group.members.remove(this);
        if (group.context == null && group.empty()) group.close();
    }

    private static Group group(World context, double x, double y, double z) {
        if (!JoltNatives.load())
            throw new IllegalStateException("Jolt 6.0.0 is required: " + JoltNatives.failureReason());
        Group result = context == null ? null : SHARED.get(context);
        if (result == null) {
            result = new Group(context, x, y, z);
            if (context != null) SHARED.put(context, result);
        } else if (result.empty()) {
            // Rebase only after every old body is gone; keep the expensive native allocator and solver warm.
            result.x = x;
            result.y = y;
            result.z = z;
        }
        return result;
    }

    /** One loose limb in precisely the same native scene as the world's corpses and physical players. */
    static final class Detached {

        final Group group;
        final PhysicsBody body;
        final Vector3f previousVelocity = new Vector3f();
        final Set<String> terrain = new HashSet<>();
        final Vector3f position = new Vector3f(), velocity = new Vector3f(), angular = new Vector3f();
        final Quat4f rotation = new Quat4f();
        boolean closed;

        Detached(World context, double x, double y, double z, Vector3f half, Quat4f rotation, BodyProperties properties,
            Vector3f angular, float gravity) {
            this(group(context, x, y, z), x, y, z, half, rotation, properties, angular, gravity);
        }

        Detached(JoltBridge parent, double x, double y, double z, Vector3f half, Quat4f rotation,
            BodyProperties properties, Vector3f angular, float gravity) {
            this(parent.group, x, y, z, half, rotation, properties, angular, gravity);
        }

        private Detached(Group group, double x, double y, double z, Vector3f half, Quat4f rotation,
            BodyProperties properties, Vector3f angular, float gravity) {
            this.group = group;
            body = group.world.createDynamicBody(
                group.world.createBoxShape(half.x, half.y, half.z),
                new Vector3f((float) (x - group.x), (float) (y - group.y), (float) (z - group.z)),
                rotation,
                properties);
            if (body == null) {
                if (group.context == null && group.empty()) group.close();
                throw new IllegalStateException("Jolt loose limb body budget exhausted");
            }
            body.setAngularVelocity(angular);
            body.setGravityFactor(gravity);
            body.setUserPointer(this);
            group.world.addBody(body);
            group.detached.add(this);
            pull();
        }

        void terrain(List<double[]> global) {
            if (!closed && !group.closed) group.updateTerrain(terrain, global, -group.x, -group.y, -group.z);
        }

        private void pull() {
            body.getPosition(position);
            body.getRotation(rotation);
            body.getLinearVelocity(velocity);
            body.getAngularVelocity(angular);
        }

        void snapshot(double[] out, Quat4f rotation, Vector3f velocity, Vector3f angular) {
            out[0] = group.x + position.x;
            out[1] = group.y + position.y;
            out[2] = group.z + position.z;
            rotation.set(this.rotation);
            velocity.set(this.velocity);
            angular.set(this.angular);
        }

        void close() {
            if (closed) return;
            closed = true;
            if (!group.closed) {
                group.world.removeBody(body);
                group.world.destroyBody(body);
                for (String id : terrain) group.releaseTerrain(id);
            }
            terrain.clear();
            group.detached.remove(this);
            if (group.context == null && group.empty()) group.close();
        }
    }

    /** Native articulated mob bodies share collision filtering and terrain with players and detached limbs. */
    static final class Rig {

        final Group group;
        final Vector3f offset;
        List<PhysicsBody> parts = new ArrayList<>();
        List<PhysicsConstraint> joints = new ArrayList<>();
        final Set<String> terrain = new HashSet<>();
        boolean closed;
        float gravityScale = 1, buoyancy = Float.NaN;
        float[] cached = new float[0];

        Rig(World world, double x, double y, double z) {
            this(group(world, x, y, z), x, y, z);
        }

        Rig(JoltBridge parent, double x, double y, double z) {
            this(parent.group, x, y, z);
        }

        private Rig(Group scene, double x, double y, double z) {
            group = scene;
            offset = new Vector3f((float) (x - group.x), (float) (y - group.y), (float) (z - group.z));
            group.rigs.add(this);
        }

        PhysicsWorld physics() {
            return group.world;
        }

        void own(List<PhysicsBody> parts, List<PhysicsConstraint> joints) {
            this.parts = parts;
            this.joints = joints;
        }

        void terrain(List<double[]> boxes) {
            if (!closed && !group.closed) group.updateTerrain(terrain, boxes, -group.x, -group.y, -group.z);
        }

        void prepare() {
            if (closed) return;
            Vector3f velocity = new Vector3f();
            for (PhysicsBody body : parts) if (body != null && !body.isStatic()) {
                body.getLinearVelocity(velocity);
                if (velocity.length() > PhysicsConfig.maxLinearSpeed) {
                    velocity.scale(PhysicsConfig.maxLinearSpeed / velocity.length());
                    body.setLinearVelocity(velocity);
                }
                body.getAngularVelocity(velocity);
                if (velocity.length() > PhysicsConfig.maxAngularSpeed) {
                    velocity.scale(PhysicsConfig.maxAngularSpeed / velocity.length());
                    body.setAngularVelocity(velocity);
                }
            }
        }

        void hidePart(int index) {
            if (index < 0 || index >= parts.size() || closed || group.closed) return;
            PhysicsBody body = parts.get(index);
            if (body == null || body.isStatic()) return;
            for (java.util.Iterator<PhysicsConstraint> it = joints.iterator(); it.hasNext();) {
                PhysicsConstraint joint = it.next();
                if (joint.bodyA() == body || joint.bodyB() == body) {
                    group.world.removeConstraint(joint);
                    group.world.destroyConstraint(joint);
                    it.remove();
                }
            }
            body.setLinearVelocity(new Vector3f());
            body.setAngularVelocity(new Vector3f());
            body.setContactResponse(false);
            body.setGravityFactor(0);
            body.setStatic(true);
        }

        float[] snapshot() {
            if (closed || group.closed) return cached.clone();
            float[] state = new float[parts.size() * 13];
            Vector3f v = new Vector3f();
            Quat4f q = new Quat4f();
            for (int i = 0; i < parts.size(); i++) {
                PhysicsBody body = parts.get(i);
                int j = i * 13;
                if (body == null) {
                    state[j + 6] = 1;
                    continue;
                }
                body.getPosition(v);
                state[j] = v.x - offset.x;
                state[j + 1] = v.y - offset.y;
                state[j + 2] = v.z - offset.z;
                body.getRotation(q);
                state[j + 3] = q.x;
                state[j + 4] = q.y;
                state[j + 5] = q.z;
                state[j + 6] = q.w;
                body.getLinearVelocity(v);
                state[j + 7] = v.x;
                state[j + 8] = v.y;
                state[j + 9] = v.z;
                body.getAngularVelocity(v);
                state[j + 10] = v.x;
                state[j + 11] = v.y;
                state[j + 12] = v.z;
            }
            cached = state;
            return state;
        }

        boolean restore(float[] state) {
            if (closed || group.closed || !RagdollWorld.RigScene.validState(state) || state.length != parts.size() * 13)
                return false;
            PhysTransform transform = new PhysTransform();
            Vector3f v = new Vector3f();
            Quat4f q = new Quat4f();
            for (int i = 0; i < parts.size(); i++) {
                int j = i * 13;
                PhysicsBody body = parts.get(i);
                v.set(state[j] + offset.x, state[j + 1] + offset.y, state[j + 2] + offset.z);
                q.set(state[j + 3], state[j + 4], state[j + 5], state[j + 6]);
                q.normalize();
                transform.set(v, q);
                body.setWorldTransform(transform);
                if (!body.isStatic()) {
                    v.set(state[j + 7], state[j + 8], state[j + 9]);
                    body.setLinearVelocity(v);
                    v.set(state[j + 10], state[j + 11], state[j + 12]);
                    body.setAngularVelocity(v);
                }
            }
            return true;
        }

        void close() {
            if (closed) return;
            if (!group.closed) snapshot();
            closed = true;
            if (!group.closed) {
                for (PhysicsConstraint joint : new ArrayList<>(joints)) if (joint != null) {
                    group.world.removeConstraint(joint);
                    group.world.destroyConstraint(joint);
                }
                for (PhysicsBody body : new ArrayList<>(parts)) if (body != null) {
                    group.world.removeBody(body);
                    group.world.destroyBody(body);
                }
                for (String id : terrain) group.releaseTerrain(id);
            }
            parts.clear();
            joints.clear();
            terrain.clear();
            group.rigs.remove(this);
            if (group.context == null && group.empty()) group.close();
        }
    }

    private static final class StaticBox {

        final PhysicsBody body;
        int references;

        StaticBox(PhysicsBody body) {
            this.body = body;
        }
    }

    private static final class Group {

        final PhysicsWorld world;
        final World context;
        double x, y, z;
        final List<JoltBridge> members = new ArrayList<>();
        final List<Detached> detached = new ArrayList<>();
        final List<Rig> rigs = new ArrayList<>();
        final Map<String, StaticBox> terrain = new HashMap<>();
        boolean closed;

        Group(World context, double x, double y, double z) {
            this.context = context;
            this.x = x;
            this.y = y;
            this.z = z;
            world = new JoltWorld(PhysicsConfig.solverThreads);
            world.setGravity(0, -PhysicsConfig.gravity, 0);
            world.setStaticFriction(PhysicsConfig.friction);
        }

        boolean empty() {
            return members.isEmpty() && detached.isEmpty() && rigs.isEmpty();
        }

        void updateTerrain(Set<String> held, List<double[]> next, double dx, double dy, double dz) {
            Set<String> wanted = new HashSet<>();
            for (double[] local : next) {
                double[] b = { local[0] + dx, local[1] + dy, local[2] + dz, local[3] + dx, local[4] + dy,
                    local[5] + dz };
                StringBuilder key = new StringBuilder();
                for (int i = 0; i < b.length; i++) {
                    long coordinate = Math.round(b[i] * 1000000);
                    key.append(coordinate)
                        .append(':');
                    b[i] = coordinate / 1000000.0;
                }
                String id = key.toString();
                if (!wanted.add(id) || held.contains(id)) continue;
                StaticBox shared = terrain.get(id);
                if (shared == null) {
                    PhysicsShape shape = world.createBoxShape(
                        (float) (b[3] - b[0]) * .5F,
                        (float) (b[4] - b[1]) * .5F,
                        (float) (b[5] - b[2]) * .5F);
                    PhysTransform at = new PhysTransform();
                    at.origin
                        .set((float) ((b[0] + b[3]) * .5), (float) ((b[1] + b[4]) * .5), (float) ((b[2] + b[5]) * .5));
                    PhysicsBody body = world.createStaticBody(shape, at);
                    if (body == null) throw new IllegalStateException("Jolt terrain body budget exhausted");
                    world.addBody(body);
                    shared = new StaticBox(body);
                    terrain.put(id, shared);
                }
                shared.references++;
            }
            for (String id : held) if (!wanted.contains(id)) releaseTerrain(id);
            held.clear();
            held.addAll(wanted);
        }

        void releaseTerrain(String id) {
            StaticBox box = terrain.get(id);
            if (box != null && --box.references == 0) {
                world.removeBody(box.body);
                world.destroyBody(box.body);
                terrain.remove(id);
            }
        }

        void step() {
            boolean requested = !detached.isEmpty() || !rigs.isEmpty();
            for (Rig rig : rigs) rig.prepare();
            for (Detached loose : detached) loose.body.getLinearVelocity(loose.previousVelocity);
            for (JoltBridge bridge : members) if (!bridge.closed && bridge.pending) {
                bridge.push();
                requested = true;
            }
            if (!requested || closed) return;
            if (context != null) {
                for (JoltBridge bridge : members) if (!bridge.closed)
                    for (PhysicsBody body : bridge.bodies) FluidForces.apply(context, body, x, y, z, 1, Float.NaN);
                for (Detached loose : detached)
                    if (!loose.closed) FluidForces.apply(context, loose.body, x, y, z, 1, Float.NaN);
                for (Rig rig : rigs) if (!rig.closed) for (PhysicsBody body : rig.parts)
                    if (body != null) FluidForces.apply(context, body, x, y, z, rig.gravityScale, rig.buoyancy);
            }
            world.step(.05F, StepQuality.HIGH);
            world.forEachContactPair(pair -> {
                float strength = 0;
                for (int i = 0; i < pair.contactCount(); i++) {
                    pair.selectContact(i);
                    strength = Math.max(strength, pair.appliedImpulse() / 80);
                }
                Vector3f normal = new Vector3f(), va = previousVelocity(pair.bodyA()),
                    vb = previousVelocity(pair.bodyB());
                pair.getNormalOnB(normal);
                va.sub(vb);
                float approach = -va.dot(normal), inverseMass = pair.bodyA()
                    .getInvMass()
                    + pair.bodyB()
                        .getInvMass();
                if (approach > 1.5F && inverseMass > 0) strength = Math.max(strength, approach / inverseMass / 80);
                Object a = pair.bodyA()
                    .getUserPointer(),
                    b = pair.bodyB()
                        .getUserPointer();
                if (Math.abs(normal.y) < .5F && approach > 0) {
                    if (a instanceof JoltBridge)
                        ((JoltBridge) a).wallSpeed = Math.max(((JoltBridge) a).wallSpeed, approach);
                    if (b instanceof JoltBridge)
                        ((JoltBridge) b).wallSpeed = Math.max(((JoltBridge) b).wallSpeed, approach);
                }
                if (a instanceof JoltBridge) ((JoltBridge) a).impact = Math.max(((JoltBridge) a).impact, strength);
                if (b instanceof JoltBridge) ((JoltBridge) b).impact = Math.max(((JoltBridge) b).impact, strength);
            });
            for (JoltBridge bridge : members) if (!bridge.closed) bridge.pull();
            for (Detached loose : detached) if (!loose.closed) loose.pull();
            for (Rig rig : rigs) if (!rig.closed) rig.snapshot();
        }

        private Vector3f previousVelocity(PhysicsBody body) {
            Vector3f value = new Vector3f();
            if (body.getUserPointer() instanceof JoltBridge) {
                JoltBridge bridge = (JoltBridge) body.getUserPointer();
                for (int i = 0; i < bridge.bodies.length; i++) if (bridge.bodies[i] == body) {
                    bridge.math[i].getLinearVelocity(value);
                    break;
                }
            }
            if (body.getUserPointer() instanceof Detached)
                value.set(((Detached) body.getUserPointer()).previousVelocity);
            return value;
        }

        void close() {
            if (closed) return;
            closed = true;
            for (JoltBridge bridge : new ArrayList<>(members)) bridge.close();
            members.clear();
            for (Detached loose : new ArrayList<>(detached)) loose.close();
            detached.clear();
            for (Rig rig : new ArrayList<>(rigs)) rig.close();
            rigs.clear();
            terrain.clear();
            world.destroy();
            if (context != null && SHARED.get(context) == this) SHARED.remove(context);
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void joined(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
            if (!event.player.worldObj.isRemote && Ragdoll.enabled
                && (PhysicsConfig.playerRagdolls || PhysicsConfig.mobRagdolls))
                group(event.player.worldObj, event.player.posX, event.player.posY, event.player.posZ);
        }

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            for (Group group : new ArrayList<>(SHARED.values())) group.step();
        }

        @SubscribeEvent
        public void unload(WorldEvent.Unload event) {
            Group group = SHARED.get(event.world);
            if (group != null) group.close();
        }

        @SubscribeEvent
        public void chunk(ChunkEvent.Unload event) {
            if (event.world.isRemote) return;
            for (List<?> slice : event.getChunk().entityLists) for (Object object : slice)
                if (object instanceof EntityCorpse) ((EntityCorpse) object).disposePhysics();
        }
    }
}
