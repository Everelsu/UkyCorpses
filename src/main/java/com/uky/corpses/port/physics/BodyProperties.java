package com.uky.corpses.port.physics;

import javax.vecmath.Vector3f;

public final class BodyProperties {

    public float mass = 1.0F;
    public float linearDamping;
    public float angularDamping;
    public float friction = 0.5F;
    public float restitution;
    public boolean allowSleeping;
    public float sleepingLinearThreshold = 0.3F;
    public float sleepingAngularThreshold = 0.3F;
    public float ccdMotionThreshold;
    public float ccdSweptSphereRadius;
    public final Vector3f linearVelocity = new Vector3f();

    public BodyProperties reset() {
        this.mass = 1.0F;
        this.linearDamping = 0.0F;
        this.angularDamping = 0.0F;
        this.friction = 0.5F;
        this.restitution = 0.0F;
        this.allowSleeping = false;
        this.sleepingLinearThreshold = 0.3F;
        this.sleepingAngularThreshold = 0.3F;
        this.ccdMotionThreshold = 0.0F;
        this.ccdSweptSphereRadius = 0.0F;
        this.linearVelocity.set(0.0F, 0.0F, 0.0F);
        return this;
    }
}
