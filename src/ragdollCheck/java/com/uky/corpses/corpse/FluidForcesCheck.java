package com.uky.corpses.corpse;

import javax.vecmath.Vector3f;

public final class FluidForcesCheck {

    public static void run() {
        Vector3f v = new Vector3f(10, -2, 0), a = new Vector3f(1, 2, 3);
        FluidForces.advance(v, a, 2, 1, 15, 1, 2.2F, 0, 0);
        assert Math.abs(v.x - 7) < 1e-5 && Math.abs(v.y - (-2 + 1.65F) * .78F) < 1e-5;
        assert Math.abs(a.x - .7F) < 1e-6;
        v.set(20, 0, 0);
        a.set(1, 1, 1);
        FluidForces.advance(v, a, 0, 1, 15, 1, 2.2F, 1, 0);
        assert Math.abs(v.x - 19.2F) < 1e-5 && Math.abs(v.y - (.825F + 3.75F) * .78F) < 1e-5;
        v.set(1, 2, 3);
        FluidForces.advance(v, a, -2, 1, 15, 1, 2.2F, 1, 0);
        assert v.equals(new Vector3f(1, 2, 3));
        v.set(0, 0, 0);
        FluidForces.advance(v, a, 2, 0, 15, 0, 2.2F, 0, 0);
        assert v.y == 0;
        System.out.println("Original fluid lift, surface skidding, current and angular drag passed");
    }
}
