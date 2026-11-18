package com.uky.corpses.port.physics.jbullet;

import java.util.List;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.narrowphase.ManifoldPoint;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.uky.corpses.port.physics.BodyProperties;
import com.uky.corpses.port.physics.ContactPair;
import com.uky.corpses.port.physics.ContactVisitor;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;
import com.uky.corpses.port.physics.PhysicsShape;
import com.uky.corpses.port.physics.PhysicsWorld;
import com.uky.corpses.port.physics.StepQuality;

public final class JBulletWorld implements PhysicsWorld {

    private final CollisionConfiguration collisionConfig;
    private final CollisionDispatcher dispatcher;
    private final BroadphaseInterface broadphase;
    private final ConstraintSolver solver;
    private final DiscreteDynamicsWorld world;
    private final Transform scratchTransform = new Transform();
    private final Vector3f scratchInertia = new Vector3f();
    private final JBulletWorld.ManifoldCursor cursor = new JBulletWorld.ManifoldCursor();
    private float staticFriction = 0.5F;
    private static final int MAX_SUB_STEPS = 10;
    private int dynamicBodiesInWorld;

    public JBulletWorld() {
        this.collisionConfig = new DefaultCollisionConfiguration();
        this.dispatcher = new CollisionDispatcher(this.collisionConfig);
        this.broadphase = new DbvtBroadphase();
        this.solver = new SequentialImpulseConstraintSolver();
        this.world = new DiscreteDynamicsWorld(this.dispatcher, this.broadphase, this.solver, this.collisionConfig);
    }

    @Override
    public String engineName() {
        return "jbullet";
    }

    @Override
    public PhysicsShape createBoxShape(float halfX, float halfY, float halfZ) {
        return new JBulletShape(new BoxShape(new Vector3f(halfX, halfY, halfZ)));
    }

    @Override
    public PhysicsBody createDynamicBody(PhysicsShape shape, Vector3f position, Quat4f rotation,
        BodyProperties properties) {
        JBulletShape jShape = (JBulletShape) shape;
        this.scratchTransform.setIdentity();
        this.scratchTransform.origin.set(position);
        this.scratchTransform.setRotation(rotation);
        this.scratchInertia.set(0.0F, 0.0F, 0.0F);
        jShape.shape.calculateLocalInertia(properties.mass, this.scratchInertia);
        RigidBodyConstructionInfo info = new RigidBodyConstructionInfo(
            properties.mass,
            new DefaultMotionState(this.scratchTransform),
            jShape.shape,
            this.scratchInertia);
        info.linearDamping = properties.linearDamping;
        info.angularDamping = properties.angularDamping;
        info.restitution = properties.restitution;
        info.friction = properties.friction;
        info.additionalDamping = true;
        RigidBody body = new RigidBody(info);
        body.setLinearVelocity(properties.linearVelocity);
        body.setDamping(properties.linearDamping, properties.angularDamping);
        body.setSleepingThresholds(properties.sleepingLinearThreshold, properties.sleepingAngularThreshold);
        if (properties.ccdMotionThreshold > 0.0F) {
            body.setCcdSweptSphereRadius(properties.ccdSweptSphereRadius);
            body.setCcdMotionThreshold(properties.ccdMotionThreshold);
        }

        JBulletBody wrapper = new JBulletBody(body, jShape, false);
        wrapper.setSleepingAllowed(properties.allowSleeping);
        return wrapper;
    }

    @Override
    public PhysicsBody createStaticBody(PhysicsShape shape, PhysTransform transform) {
        JBulletShape jShape = (JBulletShape) shape;
        this.scratchTransform.basis.set(transform.basis);
        this.scratchTransform.origin.set(transform.origin);
        RigidBody body = new RigidBody(
            new RigidBodyConstructionInfo(
                0.0F,
                new DefaultMotionState(this.scratchTransform),
                jShape.shape,
                new Vector3f()));
        body.setCollisionFlags(body.getCollisionFlags() | 1);
        body.setFriction(this.staticFriction);
        return new JBulletBody(body, jShape, true);
    }

    @Override
    public PhysicsConstraint createSixDofConstraint(PhysicsBody bodyA, PhysicsBody bodyB, PhysTransform frameInA,
        PhysTransform frameInB, Vector3f linearLower, Vector3f linearUpper, Vector3f angularLower,
        Vector3f angularUpper) {
        JBulletBody a = (JBulletBody) bodyA;
        JBulletBody b = (JBulletBody) bodyB;
        Transform localA = new Transform();
        localA.basis.set(frameInA.basis);
        localA.origin.set(frameInA.origin);
        Transform localB = new Transform();
        localB.basis.set(frameInB.basis);
        localB.origin.set(frameInB.origin);
        Generic6DofConstraint constraint = new Generic6DofConstraint(a.body, b.body, localA, localB, true);
        constraint.setLinearLowerLimit(linearLower);
        constraint.setLinearUpperLimit(linearUpper);
        constraint.setAngularLowerLimit(angularLower);
        constraint.setAngularUpperLimit(angularUpper);
        return new JBulletConstraint(constraint, a, b);
    }

    @Override
    public void addBody(PhysicsBody body) {
        JBulletBody jBody = (JBulletBody) body;
        if (!jBody.body.isInWorld()) {
            this.world.addRigidBody(jBody.body);
            if (!jBody.isStatic()) {
                this.dynamicBodiesInWorld++;
            }
        }
    }

    @Override
    public void removeBody(PhysicsBody body) {
        JBulletBody jBody = (JBulletBody) body;
        if (jBody.body.isInWorld()) {
            this.world.removeRigidBody(jBody.body);
            if (!jBody.isStatic()) {
                this.dynamicBodiesInWorld--;
            }
        }
    }

    @Override
    public void destroyBody(PhysicsBody body) {
        this.removeBody(body);
    }

    @Override
    public void addConstraint(PhysicsConstraint constraint) {
        JBulletConstraint jConstraint = (JBulletConstraint) constraint;
        if (!jConstraint.inWorld) {
            this.world.addConstraint(jConstraint.constraint, !jConstraint.keepsCollision());
            jConstraint.inWorld = true;
        }
    }

    @Override
    public void removeConstraint(PhysicsConstraint constraint) {
        JBulletConstraint jConstraint = (JBulletConstraint) constraint;
        if (jConstraint.inWorld) {
            this.world.removeConstraint(jConstraint.constraint);
            jConstraint.inWorld = false;
        }
    }

    @Override
    public void destroyConstraint(PhysicsConstraint constraint) {
        this.removeConstraint(constraint);
    }

    @Override
    public void setStaticFriction(float friction) {
        this.staticFriction = friction;
    }

    @Override
    public void setGravity(float x, float y, float z) {
        this.world.setGravity(new Vector3f(x, y, z));
    }

    @Override
    public void step(float dt, StepQuality quality) {
        int iterations;
        float fixedSubStep;
        switch (quality) {
            case HIGH:
                iterations = 20;
                fixedSubStep = 0.008333334F;
                break;
            case BALANCED:
                iterations = 10;
                fixedSubStep = 0.008333334F;
                break;
            case ECONOMY:
                iterations = 10;
                fixedSubStep = 0.016666668F;
                break;
            default:
                iterations = 6;
                fixedSubStep = 0.025F;
        }

        this.world.getSolverInfo().numIterations = iterations;
        this.world.stepSimulation(dt, 10, fixedSubStep);
    }

    @Override
    public void skipStep() {}

    @Override
    public void forEachContactPair(ContactVisitor visitor) {
        List<PersistentManifold> manifolds = this.dispatcher.getInternalManifoldPointer();
        int i = 0;

        for (int n = manifolds.size(); i < n; i++) {
            PersistentManifold manifold = manifolds.get(i);
            if (manifold.getNumContacts() != 0) {
                JBulletBody a = JBulletBody.of((CollisionObject) manifold.getBody0());
                JBulletBody b = JBulletBody.of((CollisionObject) manifold.getBody1());
                if (a != null && b != null) {
                    this.cursor.bind(manifold, a, b);
                    visitor.visit(this.cursor);
                }
            }
        }
    }

    @Override
    public int manifoldCount() {
        return this.dispatcher.getNumManifolds();
    }

    @Override
    public int contactPointCount() {
        int total = 0;
        int count = this.dispatcher.getNumManifolds();

        for (int i = 0; i < count; i++) {
            total += this.dispatcher.getManifoldByIndexInternal(i)
                .getNumContacts();
        }

        return total;
    }

    @Override
    public int activeBodyCount() {
        return this.dynamicBodiesInWorld;
    }

    @Override
    public void destroy() {}

    private static final class ManifoldCursor implements ContactPair {

        private PersistentManifold manifold;
        private JBulletBody a;
        private JBulletBody b;
        private ManifoldPoint point;

        void bind(PersistentManifold manifold, JBulletBody a, JBulletBody b) {
            this.manifold = manifold;
            this.a = a;
            this.b = b;
            this.point = manifold.getContactPoint(0);
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
            return this.manifold.getNumContacts();
        }

        @Override
        public void selectContact(int index) {
            this.point = this.manifold.getContactPoint(index);
        }

        @Override
        public float distance() {
            return this.point.getDistance();
        }

        @Override
        public void getNormalOnB(Vector3f out) {
            out.set(this.point.normalWorldOnB);
        }

        @Override
        public void getPositionOnB(Vector3f out) {
            out.set(this.point.positionWorldOnB);
        }

        @Override
        public float appliedImpulse() {
            return this.point.appliedImpulse;
        }

        @Override
        public boolean isNewContact() {
            return this.point.getLifeTime() <= 1;
        }
    }
}
