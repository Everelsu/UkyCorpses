package com.uky.corpses.port.physics.jbullet;

import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;

final class JBulletConstraint implements PhysicsConstraint {

    final TypedConstraint constraint;
    private final JBulletBody bodyA;
    private final JBulletBody bodyB;
    private boolean keepsCollision;
    boolean inWorld;

    JBulletConstraint(TypedConstraint constraint, JBulletBody bodyA, JBulletBody bodyB) {
        this.constraint = constraint;
        this.bodyA = bodyA;
        this.bodyB = bodyB;
    }

    @Override
    public PhysicsBody bodyA() {
        return this.bodyA;
    }

    @Override
    public PhysicsBody bodyB() {
        return this.bodyB;
    }

    @Override
    public void driveToOrientation(float qx, float qy, float qz, float qw, float frequency, float damping,
        float maxTorque) {}

    @Override
    public boolean keepsCollision() {
        return this.keepsCollision;
    }

    @Override
    public void setKeepsCollision(boolean keepsCollision) {
        this.keepsCollision = keepsCollision;
    }
}
