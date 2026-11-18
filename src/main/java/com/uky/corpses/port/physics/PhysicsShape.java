package com.uky.corpses.port.physics;

import javax.vecmath.Vector3f;

public interface PhysicsShape {

    void getHalfExtents(Vector3f var1);

    boolean isBox();
}
