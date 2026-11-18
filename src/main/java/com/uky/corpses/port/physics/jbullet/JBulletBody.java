package com.uky.corpses.port.physics.jbullet;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsShape;

final class JBulletBody implements PhysicsBody {

    final RigidBody body;
    private final JBulletShape shape;
    private final boolean isStatic;
    private boolean parked;
    private float parkedMass;
    private Object userPointer;
    private final Transform scratch = new Transform();
    private final Vector3f scratchInertia = new Vector3f();
    private final Vector3f baseGravity = new Vector3f();
    private final Vector3f scratchGravity = new Vector3f();
    private boolean baseGravityKnown;
    private float gravityFactor = 1.0F;

    JBulletBody(RigidBody body, JBulletShape shape, boolean isStatic) {
        this.body = body;
        this.shape = shape;
        this.isStatic = isStatic;
        body.setUserPointer(this);
    }

    static JBulletBody of(CollisionObject object) {
        return object.getUserPointer() instanceof JBulletBody wrapper ? wrapper : null;
    }

    @Override
    public void getWorldTransform(PhysTransform out) {
        this.body.getWorldTransform(this.scratch);
        this.copy(this.scratch, out);
    }

    @Override
    public void setWorldTransform(PhysTransform transform) {
        this.copy(transform, this.scratch);
        this.body.setCenterOfMassTransform(this.scratch);
        if (this.body.getMotionState() != null) {
            this.body.getMotionState()
                .setWorldTransform(this.scratch);
        }
    }

    @Override
    public void getPosition(Vector3f out) {
        this.body.getWorldTransform(this.scratch);
        out.set(this.scratch.origin);
    }

    @Override
    public void getRotation(Quat4f out) {
        this.body.getWorldTransform(this.scratch);
        this.scratch.getRotation(out);
    }

    @Override
    public void getCenterOfMassPosition(Vector3f out) {
        this.body.getCenterOfMassPosition(out);
    }

    @Override
    public void translate(Vector3f delta) {
        this.body.translate(delta);
        if (this.body.getMotionState() != null) {
            this.body.getWorldTransform(this.scratch);
            this.body.getMotionState()
                .setWorldTransform(this.scratch);
        }
    }

    @Override
    public void getLinearVelocity(Vector3f out) {
        this.body.getLinearVelocity(out);
    }

    @Override
    public void setLinearVelocity(Vector3f velocity) {
        this.body.setLinearVelocity(velocity);
    }

    @Override
    public void getAngularVelocity(Vector3f out) {
        this.body.getAngularVelocity(out);
    }

    @Override
    public void setAngularVelocity(Vector3f velocity) {
        this.body.setAngularVelocity(velocity);
    }

    @Override
    public void applyCentralImpulse(Vector3f impulse) {
        this.body.applyCentralImpulse(impulse);
    }

    @Override
    public void applyImpulse(Vector3f impulse, Vector3f relativePosition) {
        this.body.applyImpulse(impulse, relativePosition);
    }

    @Override
    public float getFriction() {
        return this.body.getFriction();
    }

    @Override
    public void setFriction(float friction) {
        this.body.setFriction(friction);
    }

    @Override
    public void setRestitution(float restitution) {
        this.body.setRestitution(restitution);
    }

    @Override
    public void setDamping(float linear, float angular) {
        this.body.setDamping(linear, angular);
    }

    @Override
    public float getCcdMotionThreshold() {
        return this.body.getCcdMotionThreshold();
    }

    @Override
    public void setCcdMotionThreshold(float threshold) {
        this.body.setCcdMotionThreshold(threshold);
    }

    @Override
    public float getCcdSweptSphereRadius() {
        return this.body.getCcdSweptSphereRadius();
    }

    @Override
    public void setCcdSweptSphereRadius(float radius) {
        this.body.setCcdSweptSphereRadius(radius);
    }

    @Override
    public void activate() {
        this.body.activate(true);
    }

    @Override
    public void setSleepingAllowed(boolean allowed) {
        this.body.forceActivationState(allowed ? 1 : 4);
    }

    @Override
    public void setSleepingThresholds(float linear, float angular) {
        this.body.setSleepingThresholds(linear, angular);
    }

    @Override
    public void setMass(float mass) {
        this.scratchInertia.set(0.0F, 0.0F, 0.0F);
        this.shape.shape.calculateLocalInertia(mass, this.scratchInertia);
        this.body.setMassProps(mass, this.scratchInertia);
        this.body.updateInertiaTensor();
    }

    @Override
    public void setGravityFactor(float factor) {
        if (!this.isStatic) {
            if (!this.baseGravityKnown) {
                this.body.getGravity(this.baseGravity);
                this.baseGravityKnown = true;
            }

            this.scratchGravity.scale(factor, this.baseGravity);
            this.body.setGravity(this.scratchGravity);
            this.gravityFactor = factor;
        }
    }

    @Override
    public float getGravityFactor() {
        return this.gravityFactor;
    }

    @Override
    public void setContactResponse(boolean respond) {
        int flags = this.body.getCollisionFlags();
        this.body.setCollisionFlags(respond ? flags & -5 : flags | 4);
    }

    @Override
    public void setStatic(boolean parked) {
        if (!this.isStatic && parked != this.parked) {
            this.parked = parked;
            int flags = this.body.getCollisionFlags();
            if (parked) {
                this.parkedMass = this.body.getInvMass() > 0.0F ? 1.0F / this.body.getInvMass() : 0.0F;
                this.scratchInertia.set(0.0F, 0.0F, 0.0F);
                this.body.setMassProps(0.0F, this.scratchInertia);
                this.body.setCollisionFlags(flags | 1);
            } else {
                this.body.setCollisionFlags(flags & -2);
                if (this.parkedMass > 0.0F) {
                    this.setMass(this.parkedMass);
                }
            }

            this.body.updateInertiaTensor();
        }
    }

    @Override
    public float getInvMass() {
        return this.body.getInvMass();
    }

    @Override
    public boolean isStatic() {
        return this.isStatic;
    }

    @Override
    public PhysicsShape getShape() {
        return this.shape;
    }

    @Override
    public void getWorldAabb(Vector3f min, Vector3f max) {
        this.body.getWorldTransform(this.scratch);
        this.shape.shape.getAabb(this.scratch, min, max);
    }

    @Override
    public Object getUserPointer() {
        return this.userPointer;
    }

    @Override
    public void setUserPointer(Object userPointer) {
        this.userPointer = userPointer;
    }

    @Override
    public boolean isInWorld() {
        return this.body.isInWorld();
    }

    private void copy(Transform from, PhysTransform to) {
        to.basis.set(from.basis);
        to.origin.set(from.origin);
    }

    private void copy(PhysTransform from, Transform to) {
        to.basis.set(from.basis);
        to.origin.set(from.origin);
    }
}
