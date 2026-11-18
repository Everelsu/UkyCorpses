package com.uky.corpses.port.physics;

public interface PhysicsConstraint {

    PhysicsBody bodyA();

    PhysicsBody bodyB();

    boolean keepsCollision();

    void setKeepsCollision(boolean var1);

    default void relaxToRest(float frequency, float damping, float maxTorque) {
        this.driveToOrientation(0.0F, 0.0F, 0.0F, 1.0F, frequency, damping, maxTorque);
    }

    void driveToOrientation(float var1, float var2, float var3, float var4, float var5, float var6, float var7);
}
