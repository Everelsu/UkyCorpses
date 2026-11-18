package com.uky.corpses.port.physics.jolt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.GroupFilterTable;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSettings;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.TempAllocatorImpl;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.port.physics.BodyProperties;
import com.uky.corpses.port.physics.ContactPair;
import com.uky.corpses.port.physics.ContactVisitor;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;
import com.uky.corpses.port.physics.PhysicsShape;
import com.uky.corpses.port.physics.PhysicsWorld;
import com.uky.corpses.port.physics.StepQuality;

public final class JoltWorld implements PhysicsWorld {

    static final int OBJECT_LAYER_STATIC = 0;
    static final int OBJECT_LAYER_MOVING = 1;
    static final int OBJECT_LAYER_PHANTOM = 2;
    private static final int BROADPHASE_LAYER_STATIC = 0;
    private static final int BROADPHASE_LAYER_MOVING = 1;
    private static final int NUM_OBJECT_LAYERS = 3;
    private static final int NUM_BROADPHASE_LAYERS = 2;
    private static final int MAX_BODIES = 32768;
    private static final int MAX_BODY_PAIRS = 32768;
    private static final int MAX_CONTACT_CONSTRAINTS = 10240;
    private static final int TEMP_ALLOCATOR_BYTES = 16777216;
    private static final int OPTIMIZE_BROADPHASE_AFTER_STATIC_ADDS = 256;
    private final BroadPhaseLayerInterfaceTable layerMap;
    private final ObjectLayerPairFilterTable objectPairFilter;
    private final ObjectVsBroadPhaseLayerFilterTable objectVsBroadPhaseFilter;
    private final TempAllocatorImpl tempAllocator;
    private final JobSystemThreadPool jobSystem;
    private final JoltContactCollector contacts = new JoltContactCollector();
    private final PhysicsSystem system;
    private final BodyInterface bodyInterface;
    private final PhysicsSettings settings;
    private final HashMap<Long, JoltBody> bodiesByAddress = new HashMap<>();
    private final RVec3 scratchLocation = new RVec3();
    private final Quat scratchOrientation = new Quat();
    private final Vec3 scratchVector = new Vec3();
    private final Quat4f scratchQuat = new Quat4f();
    private final JoltWorld.CollectedContact cursor = new JoltWorld.CollectedContact();
    private final Vector3f aabbMinA = new Vector3f();
    private final Vector3f aabbMaxA = new Vector3f();
    private final Vector3f aabbMinB = new Vector3f();
    private final Vector3f aabbMaxB = new Vector3f();
    private static final int MAX_BODIES_PER_GROUP = 16;
    private final HashMap<Integer, JoltWorld.NoCollideGroup> noCollideGroups = new HashMap<>();
    private int nextCollisionGroupId = 1;
    private final ArrayList<JoltBody> pendingDestroy = new ArrayList<>();
    private final HashMap<Long, Integer> pairSlots = new HashMap<>();
    private int[] collapsed = new int[256];
    private int collapsedCount;
    private volatile boolean destroyed;
    private int dynamicBodiesInWorld;
    private int staticAddsSinceOptimize;
    private StepQuality lastQuality;
    private float staticFriction = 0.2F;

    public JoltWorld(int workerThreads) {
        this.layerMap = new BroadPhaseLayerInterfaceTable(3, 2);
        this.layerMap.mapObjectToBroadPhaseLayer(0, 0);
        this.layerMap.mapObjectToBroadPhaseLayer(1, 1);
        this.layerMap.mapObjectToBroadPhaseLayer(2, 1);
        this.objectPairFilter = new ObjectLayerPairFilterTable(3);
        this.objectPairFilter.enableCollision(1, 1);
        this.objectPairFilter.enableCollision(1, 0);
        this.objectVsBroadPhaseFilter = new ObjectVsBroadPhaseLayerFilterTable(
            this.layerMap,
            2,
            this.objectPairFilter,
            3);
        this.system = new PhysicsSystem();
        this.system.init(32768, 0, 32768, 10240, this.layerMap, this.objectVsBroadPhaseFilter, this.objectPairFilter);
        this.bodyInterface = this.system.getBodyInterfaceNoLock();
        this.system.setContactListener(this.contacts);
        this.settings = this.system.getPhysicsSettings();
        this.settings.setAllowSleeping(false);
        this.settings.setPenetrationSlop(0.01F);
        this.system.setPhysicsSettings(this.settings);
        this.tempAllocator = new TempAllocatorImpl(16777216);
        int threads = Math.max(1, workerThreads);
        this.jobSystem = new JobSystemThreadPool(2048, 8, threads);
        UkyCorpses.LOG.info("Jolt physics world created with {} solver thread(s)", threads);
    }

    BodyInterface bodyInterface() {
        return this.bodyInterface;
    }

    @Override
    public String engineName() {
        return "jolt";
    }

    boolean isDestroyed() {
        return this.destroyed;
    }

    @Override
    public PhysicsShape createBoxShape(float halfX, float halfY, float halfZ) {
        return new JoltShape(halfX, halfY, halfZ);
    }

    @Override
    public PhysicsBody createDynamicBody(PhysicsShape shape, Vector3f position, Quat4f rotation,
        BodyProperties properties) {
        if (this.destroyed) {
            return null;
        } else {
            JoltShape joltShape = (JoltShape) shape;
            this.scratchLocation.set((double) position.x, (double) position.y, (double) position.z);
            this.scratchOrientation.set(rotation.x, rotation.y, rotation.z, rotation.w);
            BodyCreationSettings creation = new BodyCreationSettings(
                joltShape.shape,
                this.scratchLocation,
                this.scratchOrientation,
                EMotionType.Dynamic,
                1);
            MassProperties mass = new MassProperties();
            mass.setMass(properties.mass);
            creation.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
            creation.setMassPropertiesOverride(mass);
            creation.setLinearDamping(properties.linearDamping);
            creation.setAngularDamping(properties.angularDamping);
            creation.setFriction(properties.friction);
            creation.setRestitution(properties.restitution);
            creation.setAllowSleeping(properties.allowSleeping);
            creation.setLinearVelocity(
                new Vec3(properties.linearVelocity.x, properties.linearVelocity.y, properties.linearVelocity.z));
            Body body = this.bodyInterface.createBody(creation);
            if (body == null) {
                UkyCorpses.LOG.error("Jolt refused a body: the {}-body budget is exhausted", 32768);
                return null;
            } else {
                JoltBody wrapper = new JoltBody(this, body, joltShape, false, properties.mass);
                wrapper.setCcdSweptSphereRadius(properties.ccdSweptSphereRadius);
                wrapper.setCcdMotionThreshold(properties.ccdMotionThreshold);
                this.bodiesByAddress.put(body.va(), wrapper);
                return wrapper;
            }
        }
    }

    @Override
    public PhysicsBody createStaticBody(PhysicsShape shape, PhysTransform transform) {
        if (this.destroyed) {
            return null;
        } else {
            JoltShape joltShape = (JoltShape) shape;
            transform.getRotation(this.scratchQuat);
            this.scratchLocation
                .set((double) transform.origin.x, (double) transform.origin.y, (double) transform.origin.z);
            this.scratchOrientation.set(this.scratchQuat.x, this.scratchQuat.y, this.scratchQuat.z, this.scratchQuat.w);
            BodyCreationSettings creation = new BodyCreationSettings(
                joltShape.shape,
                this.scratchLocation,
                this.scratchOrientation,
                EMotionType.Static,
                0);
            creation.setFriction(this.staticFriction);
            Body body = this.bodyInterface.createBody(creation);
            if (body == null) {
                UkyCorpses.LOG.error("Jolt refused a static body: the {}-body budget is exhausted", 32768);
                return null;
            } else {
                JoltBody wrapper = new JoltBody(this, body, joltShape, true, 0.0F);
                this.bodiesByAddress.put(body.va(), wrapper);
                return wrapper;
            }
        }
    }

    @Override
    public PhysicsConstraint createSixDofConstraint(PhysicsBody bodyA, PhysicsBody bodyB, PhysTransform frameInA,
        PhysTransform frameInB, Vector3f linearLower, Vector3f linearUpper, Vector3f angularLower,
        Vector3f angularUpper) {
        JoltBody a = (JoltBody) bodyA;
        JoltBody b = (JoltBody) bodyB;
        SixDofConstraintSettings creation = new SixDofConstraintSettings();
        creation.setSpace(EConstraintSpace.LocalToBodyCom);
        creation.setPosition1(
            new RVec3((double) frameInA.origin.x, (double) frameInA.origin.y, (double) frameInA.origin.z));
        creation.setPosition2(
            new RVec3((double) frameInB.origin.x, (double) frameInB.origin.y, (double) frameInB.origin.z));
        creation.setAxisX1(new Vec3(frameInA.basis.m00, frameInA.basis.m10, frameInA.basis.m20));
        creation.setAxisY1(new Vec3(frameInA.basis.m01, frameInA.basis.m11, frameInA.basis.m21));
        creation.setAxisX2(new Vec3(frameInB.basis.m00, frameInB.basis.m10, frameInB.basis.m20));
        creation.setAxisY2(new Vec3(frameInB.basis.m01, frameInB.basis.m11, frameInB.basis.m21));
        creation.setSwingType(ESwingType.Pyramid);
        applyLimit(creation, EAxis.TranslationX, linearLower.x, linearUpper.x);
        applyLimit(creation, EAxis.TranslationY, linearLower.y, linearUpper.y);
        applyLimit(creation, EAxis.TranslationZ, linearLower.z, linearUpper.z);
        applyLimit(creation, EAxis.RotationX, angularLower.x, angularUpper.x);
        applyLimit(creation, EAxis.RotationY, angularLower.y, angularUpper.y);
        applyLimit(creation, EAxis.RotationZ, angularLower.z, angularUpper.z);
        TwoBodyConstraint constraint = creation.create(a.body, b.body);
        return new JoltConstraint(constraint, a, b);
    }

    private static void applyLimit(SixDofConstraintSettings settings, EAxis axis, float lower, float upper) {
        if (lower > upper) {
            settings.makeFreeAxis(axis);
        } else if (lower == upper) {
            settings.makeFixedAxis(axis);
        } else {
            settings.setLimitedAxis(axis, lower, upper);
        }
    }

    @Override
    public void addBody(PhysicsBody body) {
        if (!this.destroyed) {
            JoltBody joltBody = (JoltBody) body;
            if (!joltBody.inWorld) {
                this.bodyInterface
                    .addBody(joltBody.bodyId, joltBody.isStatic() ? EActivation.DontActivate : EActivation.Activate);
                joltBody.inWorld = true;
                joltBody.applyMotionTypeOnAdd();
                if (joltBody.isStatic()) {
                    this.staticAddsSinceOptimize++;
                } else {
                    this.dynamicBodiesInWorld++;
                    joltBody.applyMotionQuality();
                }
            }
        }
    }

    void onParkedChanged(boolean parked) {
        if (parked) {
            this.dynamicBodiesInWorld--;
            this.staticAddsSinceOptimize++;
        } else {
            this.dynamicBodiesInWorld++;
        }
    }

    @Override
    public void removeBody(PhysicsBody body) {
        if (!this.destroyed) {
            JoltBody joltBody = (JoltBody) body;
            if (joltBody.inWorld) {
                this.bodyInterface.removeBody(joltBody.bodyId);
                joltBody.inWorld = false;
                if (!joltBody.isStatic()) {
                    this.dynamicBodiesInWorld--;
                }
            }
        }
    }

    @Override
    public void destroyBody(PhysicsBody body) {
        if (!this.destroyed) {
            JoltBody joltBody = (JoltBody) body;
            this.removeBody(joltBody);
            this.leaveNoCollideGroup(joltBody);
            this.pendingDestroy.add(joltBody);
        }
    }

    private void flushPendingDestroys() {
        for (int i = 0; i < this.pendingDestroy.size(); i++) {
            JoltBody body = this.pendingDestroy.get(i);
            this.bodiesByAddress.remove(body.body.va());
            this.bodyInterface.destroyBody(body.bodyId);
        }

        this.pendingDestroy.clear();
    }

    @Override
    public void addConstraint(PhysicsConstraint constraint) {
        if (!this.destroyed) {
            JoltConstraint joltConstraint = (JoltConstraint) constraint;
            if (!joltConstraint.inWorld) {
                if (!joltConstraint.keepsCollision()) {
                    this.stopCollidingWithEachOther(
                        (JoltBody) joltConstraint.bodyA(),
                        (JoltBody) joltConstraint.bodyB());
                }

                this.system.addConstraint(joltConstraint.constraint);
                joltConstraint.inWorld = true;
            }
        }
    }

    private void stopCollidingWithEachOther(JoltBody a, JoltBody b) {
        if (a != null && b != null && a != b) {
            if (a.collisionGroupId >= 0 && b.collisionGroupId >= 0 && a.collisionGroupId != b.collisionGroupId) {
                UkyCorpses.LOG.warn(
                    "Cannot disable collisions between bodies from two separate constraint groups ({} and {}); the joint will fight its own contacts.",
                    a.collisionGroupId,
                    b.collisionGroupId);
            } else {
                int groupId = a.collisionGroupId >= 0 ? a.collisionGroupId : b.collisionGroupId;
                JoltWorld.NoCollideGroup group;
                if (groupId < 0) {
                    groupId = this.nextCollisionGroupId++;
                    group = new JoltWorld.NoCollideGroup();
                    this.noCollideGroups.put(groupId, group);
                } else {
                    group = (JoltWorld.NoCollideGroup) this.noCollideGroups.get(groupId);
                    if (group == null) {
                        return;
                    }
                }

                if (this.join(a, groupId, group) && this.join(b, groupId, group)) {
                    group.filter.disableCollision(a.collisionSubGroupId, b.collisionSubGroupId);
                }
            }
        }
    }

    private boolean join(JoltBody body, int groupId, JoltWorld.NoCollideGroup group) {
        if (body.collisionGroupId == groupId) {
            return true;
        } else if (group.nextSubGroup >= 16) {
            UkyCorpses.LOG.warn(
                "Constraint group {} is full at {} bodies; further joints in this assembly will collide with each other.",
                groupId,
                16);
            return false;
        } else {
            body.collisionGroupId = groupId;
            body.collisionSubGroupId = group.nextSubGroup++;
            group.members++;
            body.body.setCollisionGroup(new CollisionGroup(group.filter, groupId, body.collisionSubGroupId));
            return true;
        }
    }

    private void leaveNoCollideGroup(JoltBody body) {
        if (body.collisionGroupId >= 0) {
            JoltWorld.NoCollideGroup group = (JoltWorld.NoCollideGroup) this.noCollideGroups.get(body.collisionGroupId);
            if (group != null && --group.members <= 0) {
                this.noCollideGroups.remove(body.collisionGroupId);
            }

            body.collisionGroupId = -1;
            body.collisionSubGroupId = -1;
        }
    }

    @Override
    public void removeConstraint(PhysicsConstraint constraint) {
        if (!this.destroyed) {
            JoltConstraint joltConstraint = (JoltConstraint) constraint;
            if (joltConstraint.inWorld) {
                this.system.removeConstraint(joltConstraint.constraint);
                joltConstraint.inWorld = false;
            }
        }
    }

    @Override
    public void destroyConstraint(PhysicsConstraint constraint) {
        this.removeConstraint(constraint);
    }

    @Override
    public void setGravity(float x, float y, float z) {
        this.system.setGravity(x, y, z);
    }

    @Override
    public void setStaticFriction(float friction) {
        this.staticFriction = friction;
    }

    @Override
    public synchronized void step(float dt, StepQuality quality) {
        if (!this.destroyed) {
            if (this.staticAddsSinceOptimize >= 256) {
                this.system.optimizeBroadPhase();
                this.staticAddsSinceOptimize = 0;
            }

            int collisionSteps;
            int velocitySteps;
            int positionSteps;
            switch (quality) {
                case HIGH:
                    collisionSteps = 3;
                    velocitySteps = 10;
                    positionSteps = 2;
                    break;
                case BALANCED:
                    collisionSteps = 2;
                    velocitySteps = 10;
                    positionSteps = 2;
                    break;
                case ECONOMY:
                    collisionSteps = 2;
                    velocitySteps = 8;
                    positionSteps = 2;
                    break;
                default:
                    collisionSteps = 2;
                    velocitySteps = 6;
                    positionSteps = 1;
            }

            if (quality != this.lastQuality) {
                this.settings.setNumVelocitySteps(velocitySteps);
                this.settings.setNumPositionSteps(positionSteps);
                this.system.setPhysicsSettings(this.settings);
                this.lastQuality = quality;
            }

            this.flushPendingDestroys();
            this.contacts.reset();
            this.system.update(dt, collisionSteps, this.tempAllocator, this.jobSystem);
            this.collapseContacts();
        }
    }

    private void collapseContacts() {
        this.pairSlots.clear();
        this.collapsedCount = 0;
        int reported = this.contacts.count();

        for (int i = 0; i < reported; i++) {
            JoltBody a = (JoltBody) this.bodiesByAddress.get(this.contacts.bodyVa1(i));
            JoltBody b = (JoltBody) this.bodiesByAddress.get(this.contacts.bodyVa2(i));
            if (a != null && b != null) {
                int low = Math.min(a.bodyId, b.bodyId);
                int high = Math.max(a.bodyId, b.bodyId);
                long key = (long) low << 32 | (long) high & 4294967295L;
                int slot = this.pairSlots.getOrDefault(key, -1);
                if (slot < 0) {
                    if (this.collapsedCount == this.collapsed.length) {
                        this.collapsed = Arrays.copyOf(this.collapsed, this.collapsedCount * 2);
                    }

                    this.collapsed[this.collapsedCount] = i;
                    this.pairSlots.put(key, this.collapsedCount);
                    this.collapsedCount++;
                } else if (this.contacts.distance(i) < this.contacts.distance(this.collapsed[slot])) {
                    this.collapsed[slot] = i;
                }
            }
        }
    }

    @Override
    public synchronized void skipStep() {
        if (!this.destroyed) {
            this.contacts.reset();
            this.pairSlots.clear();
            this.collapsedCount = 0;
            this.flushPendingDestroys();
        }
    }

    @Override
    public void forEachContactPair(ContactVisitor visitor) {
        if (!this.destroyed) {
            for (int slot = 0; slot < this.collapsedCount; slot++) {
                int i = this.collapsed[slot];
                JoltBody a = (JoltBody) this.bodiesByAddress.get(this.contacts.bodyVa1(i));
                JoltBody b = (JoltBody) this.bodiesByAddress.get(this.contacts.bodyVa2(i));
                if (a != null && b != null) {
                    this.cursor.bind(i, a, b);
                    visitor.visit(this.cursor);
                }
            }
        }
    }

    @Override
    public int manifoldCount() {
        return this.collapsedCount;
    }

    @Override
    public int contactPointCount() {
        return this.collapsedCount;
    }

    @Override
    public int activeBodyCount() {
        return this.dynamicBodiesInWorld;
    }

    @Override
    public synchronized void destroy() {
        if (!this.destroyed) {
            this.destroyed = true;
            this.pendingDestroy.clear();
            this.noCollideGroups.clear();
            this.system.removeAllConstraints();
            this.system.removeAllBodies();
            this.system.destroyAllBodies();
            this.bodiesByAddress.clear();
            this.contacts.reset();
            this.pairSlots.clear();
            this.collapsedCount = 0;
            this.dynamicBodiesInWorld = 0;
            this.jobSystem.close();
            this.tempAllocator.close();
            this.system.close();
            this.contacts.close();
            this.settings.close();
            this.objectVsBroadPhaseFilter.close();
            this.objectPairFilter.close();
            this.layerMap.close();
        }
    }

    private final class CollectedContact implements ContactPair {

        private int index;
        private JoltBody a;
        private JoltBody b;

        void bind(int index, JoltBody a, JoltBody b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }

        @Override
        public PhysicsBody bodyA() {
            return this.a;
        }

        @Override
        public PhysicsBody bodyB() {
            return this.b;
        }

        @Override
        public int contactCount() {
            return 1;
        }

        @Override
        public void selectContact(int contactIndex) {}

        @Override
        public float distance() {
            return JoltWorld.this.contacts.distance(this.index);
        }

        @Override
        public void getNormalOnB(Vector3f out) {
            out.set(
                JoltWorld.this.contacts.normalX(this.index),
                JoltWorld.this.contacts.normalY(this.index),
                JoltWorld.this.contacts.normalZ(this.index));
        }

        @Override
        public void getPositionOnB(Vector3f out) {
            this.a.getWorldAabb(JoltWorld.this.aabbMinA, JoltWorld.this.aabbMaxA);
            this.b.getWorldAabb(JoltWorld.this.aabbMinB, JoltWorld.this.aabbMaxB);
            out.set(
                this.overlapCentre(
                    JoltWorld.this.aabbMinA.x,
                    JoltWorld.this.aabbMaxA.x,
                    JoltWorld.this.aabbMinB.x,
                    JoltWorld.this.aabbMaxB.x),
                this.overlapCentre(
                    JoltWorld.this.aabbMinA.y,
                    JoltWorld.this.aabbMaxA.y,
                    JoltWorld.this.aabbMinB.y,
                    JoltWorld.this.aabbMaxB.y),
                this.overlapCentre(
                    JoltWorld.this.aabbMinA.z,
                    JoltWorld.this.aabbMaxA.z,
                    JoltWorld.this.aabbMinB.z,
                    JoltWorld.this.aabbMaxB.z));
        }

        private float overlapCentre(float minA, float maxA, float minB, float maxB) {
            float low = Math.max(minA, minB);
            float high = Math.min(maxA, maxB);
            return low <= high ? (low + high) * 0.5F : (Math.min(maxA, maxB) + Math.max(minA, minB)) * 0.5F;
        }

        @Override
        public float appliedImpulse() {
            return 0.0F;
        }

        @Override
        public boolean isNewContact() {
            return JoltWorld.this.contacts.isNew(this.index);
        }
    }

    private static final class NoCollideGroup {

        final GroupFilterTable filter = new GroupFilterTable(16);
        int nextSubGroup;
        int members;
    }
}
