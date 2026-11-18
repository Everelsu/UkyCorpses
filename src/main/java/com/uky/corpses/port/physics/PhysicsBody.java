package com.uky.corpses.port.physics;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

public interface PhysicsBody {

    void getWorldTransform(PhysTransform var1);

    void setWorldTransform(PhysTransform var1);

    void getPosition(Vector3f var1);

    void getRotation(Quat4f var1);

    void getCenterOfMassPosition(Vector3f var1);

    void translate(Vector3f var1);

    void getLinearVelocity(Vector3f var1);

    void setLinearVelocity(Vector3f var1);

    void getAngularVelocity(Vector3f var1);

    void setAngularVelocity(Vector3f var1);

    void applyCentralImpulse(Vector3f var1);

    void applyImpulse(Vector3f var1, Vector3f var2);

    float getFriction();

    void setFriction(float var1);

    void setRestitution(float var1);

    void setDamping(float var1, float var2);

    float getCcdMotionThreshold();

    void setCcdMotionThreshold(float var1);

    float getCcdSweptSphereRadius();

    void setCcdSweptSphereRadius(float var1);

    void setStatic(boolean var1);

    void activate();

    void setSleepingAllowed(boolean var1);

    void setSleepingThresholds(float var1, float var2);

    void setMass(float var1);

    void setGravityFactor(float var1);

    float getGravityFactor();

    void setContactResponse(boolean var1);

    float getInvMass();

    boolean isStatic();

    PhysicsShape getShape();

    void getWorldAabb(Vector3f var1, Vector3f var2);

    Object getUserPointer();

    void setUserPointer(Object var1);

    boolean isInWorld();
}
