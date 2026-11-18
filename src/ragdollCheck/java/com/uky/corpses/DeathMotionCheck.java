package com.uky.corpses;

import javax.vecmath.Vector3f;

public final class DeathMotionCheck {

    public static void run() {
        Vector3f velocity = DeathMotion.linearVelocity(new Vector3f(.1F, 0, 0), new Vector3f(.2F, 0, 0));
        assert Math.abs(velocity.x - 4) < .0001 : "pre-hit displacement lost";
        velocity = DeathMotion.linearVelocity(new Vector3f(.1F, 0, 0), new Vector3f(20, 0, 0));
        assert Math.abs(velocity.x - 2) < .0001 : "teleport became death velocity";
        velocity = DeathMotion.attackerFallback(new Vector3f(), new Vector3f(0, 0, 1));
        assert Math.abs(velocity.z - .75F) < .0001 && Math.abs(velocity.y - .108F) < .0001;
        Vector3f hit = DeathMotion.mappedImpulse(new Vector3f(1, 0, 0), true);
        assert hit.x == 10 && Math.abs(hit.y - 5.8F) < .0001;
        hit = DeathMotion.mappedImpulse(new Vector3f(0, 0, 1), false);
        assert hit.z == 8 && Math.abs(hit.y - 4.7F) < .0001;
    }

    private DeathMotionCheck() {}
}
