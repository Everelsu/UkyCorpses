package com.uky.corpses.port.physics.jolt;

import javax.vecmath.Matrix3f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsShape;

final class JoltBody implements PhysicsBody {

    private final JoltWorld world;
    final Body body;
    final int bodyId;
    private final JoltShape shape;
    private final boolean bornStatic;
    private boolean isStatic;
    private float mass;
    private Object userPointer;
    private boolean respondsToContacts = true;
    boolean inWorld;
    int collisionGroupId = -1;
    int collisionSubGroupId = -1;
    private float ccdMotionThreshold;
    private float ccdSweptSphereRadius;
    private final RVec3 scratchLocation = new RVec3();
    private final Quat scratchOrientation = new Quat();
    private final Vec3 scratchVector = new Vec3();
    private final Quat4f quatOut = new Quat4f();
    private final Matrix3f aabbBasis = new Matrix3f();

    JoltBody(JoltWorld world, Body body, JoltShape shape, boolean isStatic, float mass) {
        this.world = world;
        this.body = body;
        this.bodyId = body.getId();
        this.shape = shape;
        this.bornStatic = isStatic;
        this.isStatic = isStatic;
        this.mass = mass;
    }

    @Override
    public void getWorldTransform(PhysTransform out) {
        this.body.getPositionAndRotation(this.scratchLocation, this.scratchOrientation);
        out.origin.set(
            (float) this.scratchLocation.xx(),
            (float) this.scratchLocation.yy(),
            (float) this.scratchLocation.zz());
        out.basis.set(toVecmath(this.scratchOrientation, this.quatOut));
    }

    @Override
    public void setWorldTransform(PhysTransform transform) {
        transform.getRotation(this.quatOut);
        this.scratchLocation.set((double) transform.origin.x, (double) transform.origin.y, (double) transform.origin.z);
        this.scratchOrientation.set(this.quatOut.x, this.quatOut.y, this.quatOut.z, this.quatOut.w);
        this.setPositionAndRotation();
    }

    private void setPositionAndRotation() {
        if (this.inWorld) {
            this.world.bodyInterface()
                .setPositionAndRotation(
                    this.bodyId,
                    this.scratchLocation,
                    this.scratchOrientation,
                    EActivation.Activate);
        } else {
            this.body.setPositionAndRotationInternal(this.scratchLocation, this.scratchOrientation);
        }
    }

    @Override
    public void getPosition(Vector3f out) {
        this.body.getPositionAndRotation(this.scratchLocation, this.scratchOrientation);
        out.set(
            (float) this.scratchLocation.xx(),
            (float) this.scratchLocation.yy(),
            (float) this.scratchLocation.zz());
    }

    @Override
    public void getRotation(Quat4f out) {
        this.body.getPositionAndRotation(this.scratchLocation, this.scratchOrientation);
        toVecmath(this.scratchOrientation, out);
    }

    @Override
    public void getCenterOfMassPosition(Vector3f out) {
        this.body.getCenterOfMassPosition(this.scratchLocation);
        out.set(
            (float) this.scratchLocation.xx(),
            (float) this.scratchLocation.yy(),
            (float) this.scratchLocation.zz());
    }

    @Override
    public void translate(Vector3f delta) {
        this.body.getPositionAndRotation(this.scratchLocation, this.scratchOrientation);
        this.scratchLocation.set(
            this.scratchLocation.xx() + (double) delta.x,
            this.scratchLocation.yy() + (double) delta.y,
            this.scratchLocation.zz() + (double) delta.z);
        this.setPositionAndRotation();
    }

    @Override
    public void getLinearVelocity(Vector3f out) {
        if (this.isStatic) {
            out.set(0.0F, 0.0F, 0.0F);
        } else {
            this.world.bodyInterface()
                .getLinearVelocity(this.bodyId, this.scratchVector);
            out.set(this.scratchVector.getX(), this.scratchVector.getY(), this.scratchVector.getZ());
        }
    }

    @Override
    public void setLinearVelocity(Vector3f velocity) {
        if (!this.isStatic) {
            this.scratchVector.set(velocity.x, velocity.y, velocity.z);
            this.body.setLinearVelocity(this.scratchVector);
        }
    }

    @Override
    public void getAngularVelocity(Vector3f out) {
        if (this.isStatic) {
            out.set(0.0F, 0.0F, 0.0F);
        } else {
            this.world.bodyInterface()
                .getAngularVelocity(this.bodyId, this.scratchVector);
            out.set(this.scratchVector.getX(), this.scratchVector.getY(), this.scratchVector.getZ());
        }
    }

    @Override
    public void setAngularVelocity(Vector3f velocity) {
        if (!this.isStatic) {
            this.scratchVector.set(velocity.x, velocity.y, velocity.z);
            this.body.setAngularVelocity(this.scratchVector);
        }
    }

    @Override
    public void applyCentralImpulse(Vector3f impulse) {
        if (!this.isStatic) {
            this.scratchVector.set(impulse.x, impulse.y, impulse.z);
            this.body.addImpulse(this.scratchVector);
        }
    }

    @Override
    public void applyImpulse(Vector3f impulse, Vector3f relativePosition) {
        if (!this.isStatic) {
            this.body.getCenterOfMassPosition(this.scratchLocation);
            this.scratchVector.set(impulse.x, impulse.y, impulse.z);
            this.scratchLocation.set(
                this.scratchLocation.xx() + (double) relativePosition.x,
                this.scratchLocation.yy() + (double) relativePosition.y,
                this.scratchLocation.zz() + (double) relativePosition.z);
            this.body.addImpulse(this.scratchVector, this.scratchLocation);
        }
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
        MotionProperties motion = this.motion();
        if (motion != null) {
            motion.setLinearDamping(linear);
            motion.setAngularDamping(angular);
        }
    }

    @Override
    public float getCcdMotionThreshold() {
        return this.ccdMotionThreshold;
    }

    @Override
    public void setCcdMotionThreshold(float threshold) {
        this.ccdMotionThreshold = threshold;
        this.applyMotionQuality();
    }

    @Override
    public float getCcdSweptSphereRadius() {
        return this.ccdSweptSphereRadius;
    }

    @Override
    public void setCcdSweptSphereRadius(float radius) {
        this.ccdSweptSphereRadius = radius;
    }

    void applyMotionQuality() {
        if (!this.isStatic && this.inWorld) {
            this.world.bodyInterface()
                .setMotionQuality(
                    this.bodyId,
                    this.ccdMotionThreshold > 0.0F ? EMotionQuality.LinearCast : EMotionQuality.Discrete);
        }
    }

    @Override
    public void setStatic(boolean parked) {
        if (!this.bornStatic && this.isStatic != parked) {
            this.isStatic = parked;
            if (this.inWorld) {
                this.world.bodyInterface()
                    .setMotionType(
                        this.bodyId,
                        parked ? EMotionType.Static : EMotionType.Dynamic,
                        parked ? EActivation.DontActivate : EActivation.Activate);
                this.world.bodyInterface()
                    .setObjectLayer(this.bodyId, this.parkedObjectLayer(parked));
                this.world.onParkedChanged(parked);
                if (!parked) {
                    this.applyMotionQuality();
                }
            }
        }
    }

    @Override
    public void activate() {
        if (!this.isStatic && this.inWorld) {
            this.world.bodyInterface()
                .activateBody(this.bodyId);
        }
    }

    @Override
    public void setSleepingAllowed(boolean allowed) {
        if (!this.isStatic) {
            this.body.setAllowSleeping(allowed);
            if (!allowed && this.inWorld) {
                this.world.bodyInterface()
                    .activateBody(this.bodyId);
            }
        }
    }

    @Override
    public void setSleepingThresholds(float linear, float angular) {}

    @Override
    public void setMass(float newMass) {
        MotionProperties motion = this.motion();
        if (motion != null && !(newMass <= 0.0F)) {
            MassProperties massProperties = this.shape.shape.getMassProperties();
            massProperties.scaleToMass(newMass);
            motion.setMassProperties(63, massProperties);
            this.mass = newMass;
        }
    }

    @Override
    public void setGravityFactor(float factor) {
        MotionProperties motion = this.motion();
        if (motion != null) {
            motion.setGravityFactor(factor);
        }
    }

    @Override
    public float getGravityFactor() {
        MotionProperties motion = this.motion();
        return motion == null ? 1.0F : motion.getGravityFactor();
    }

    void applyMotionTypeOnAdd() {
        if (!this.bornStatic) {
            this.world.bodyInterface()
                .setMotionType(
                    this.bodyId,
                    this.isStatic ? EMotionType.Static : EMotionType.Dynamic,
                    this.isStatic ? EActivation.DontActivate : EActivation.Activate);
            this.world.bodyInterface()
                .setObjectLayer(this.bodyId, this.parkedObjectLayer(this.isStatic));
        }
    }

    private int parkedObjectLayer(boolean parked) {
        if (!this.respondsToContacts) {
            return 2;
        } else {
            return parked ? 0 : 1;
        }
    }

    @Override
    public void setContactResponse(boolean respond) {
        if (!this.bornStatic) {
            this.respondsToContacts = respond;
            this.world.bodyInterface()
                .setObjectLayer(this.bodyId, this.parkedObjectLayer(this.isStatic));
        }
    }

    boolean respondsToContacts() {
        return this.respondsToContacts;
    }

    @Override
    public float getInvMass() {
        return !this.isStatic && !(this.mass <= 0.0F) ? 1.0F / this.mass : 0.0F;
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
        this.body.getPositionAndRotation(this.scratchLocation, this.scratchOrientation);
        toVecmath(this.scratchOrientation, this.quatOut);
        this.aabbBasis.set(this.quatOut);
        float ex = Math.abs(this.aabbBasis.m00) * this.shape.halfX() + Math.abs(this.aabbBasis.m01) * this.shape.halfY()
            + Math.abs(this.aabbBasis.m02) * this.shape.halfZ();
        float ey = Math.abs(this.aabbBasis.m10) * this.shape.halfX() + Math.abs(this.aabbBasis.m11) * this.shape.halfY()
            + Math.abs(this.aabbBasis.m12) * this.shape.halfZ();
        float ez = Math.abs(this.aabbBasis.m20) * this.shape.halfX() + Math.abs(this.aabbBasis.m21) * this.shape.halfY()
            + Math.abs(this.aabbBasis.m22) * this.shape.halfZ();
        float px = (float) this.scratchLocation.xx();
        float py = (float) this.scratchLocation.yy();
        float pz = (float) this.scratchLocation.zz();
        min.set(px - ex, py - ey, pz - ez);
        max.set(px + ex, py + ey, pz + ez);
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
        return this.inWorld;
    }

    private MotionProperties motion() {
        return this.isStatic ? null : this.body.getMotionPropertiesUnchecked();
    }

    private static Quat4f toVecmath(Quat from, Quat4f out) {
        out.set(from.getX(), from.getY(), from.getZ(), from.getW());
        return out;
    }
}
