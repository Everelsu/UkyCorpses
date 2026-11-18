package com.uky.corpses;

/** Installed ReactionDetector math; velocities here are blocks/second. */
final class ReactionRules {

    static final int NONE = 0, IMPACT = 1, LANDING = 2, KNOCKBACK = 3;

    static int detect(double[] previous, double[] current, boolean horizontalCollision, boolean verticalCollision,
        boolean below, boolean wasGround, boolean bouncy, boolean blocking, boolean impacts, boolean landings,
        boolean knockback, double impactThreshold, double landingThreshold, double knockbackThreshold) {
        if (below && bouncy) return NONE;
        double speed = length(previous), x = 0, y = 0, z = 0;
        if (speed > .002) {
            x = previous[0] / speed;
            y = previous[1] / speed;
            z = previous[2] / speed;
        }
        double dx = current[0] - previous[0], dy = current[1] - previous[1], dz = current[2] - previous[2];
        double braking = Math.max(0, Math.min(speed, -(dx * x + dy * y + dz * z)));
        if ((horizontalCollision || verticalCollision) && braking > 0) {
            boolean landing = below && previous[1] < 0 && -previous[1] >= Math.hypot(previous[0], previous[2]);
            if (landing ? landings && braking >= landingThreshold : impacts && braking >= impactThreshold)
                return landing ? LANDING : IMPACT;
        }
        dx += x * braking;
        dy += y * braking;
        dz += z * braking;
        boolean jump = wasGround && dy > 0 && Math.hypot(dx, dz) < dy * .25;
        return knockback && !blocking && !jump && Math.sqrt(dx * dx + dy * dy + dz * dz) >= knockbackThreshold
            ? KNOCKBACK
            : NONE;
    }

    static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    static float wallDamage(float speed, float minimum, float scale) {
        if (!Float.isFinite(speed)) return 0;
        float damage = (Math.min(80, speed) - minimum) * scale;
        return damage >= .5F ? damage : 0;
    }
}
