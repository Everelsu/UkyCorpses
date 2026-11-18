package com.uky.corpses.port.physics;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

public interface PhysicsWorld {

    String engineName();

    PhysicsShape createBoxShape(float var1, float var2, float var3);

    PhysicsBody createDynamicBody(PhysicsShape var1, Vector3f var2, Quat4f var3, BodyProperties var4);

    PhysicsBody createStaticBody(PhysicsShape var1, PhysTransform var2);

    PhysicsConstraint createSixDofConstraint(PhysicsBody var1, PhysicsBody var2, PhysTransform var3, PhysTransform var4,
        Vector3f var5, Vector3f var6, Vector3f var7, Vector3f var8);

    void addBody(PhysicsBody var1);

    void removeBody(PhysicsBody var1);

    void destroyBody(PhysicsBody var1);

    void addConstraint(PhysicsConstraint var1);

    void removeConstraint(PhysicsConstraint var1);

    void destroyConstraint(PhysicsConstraint var1);

    void setGravity(float var1, float var2, float var3);

    void setStaticFriction(float var1);

    void step(float var1, StepQuality var2);

    void skipStep();

    void forEachContactPair(ContactVisitor var1);

    int manifoldCount();

    int contactPointCount();

    int activeBodyCount();

    void destroy();
}
