package com.uky.corpses.port.physics;

import javax.vecmath.Vector3f;

public interface ContactPair {

    PhysicsBody bodyA();

    PhysicsBody bodyB();

    int contactCount();

    void selectContact(int var1);

    float distance();

    void getNormalOnB(Vector3f var1);

    void getPositionOnB(Vector3f var1);

    float appliedImpulse();

    boolean isNewContact();
}
