package com.uky.corpses.pc;

/** Original CorpseCompassAngle spring and turn wrapping; independent of client rendering. */
public final class CompassAngle {

    private final Wobble direction = new Wobble(), random = new Wobble();

    public float towards(long ticks, double yaw, double dx, double dz) {
        double angle = Math.atan2(dz, dx) / (Math.PI * 2), facing = mod(yaw / 360);
        direction.update(ticks, .5 - (facing - .25));
        return (float) mod(angle + direction.rotation);
    }

    public float spinning(long ticks, int seed) {
        random.update(ticks, Math.random());
        return (float) mod(random.rotation + (float) (seed * 1327217883) / 2.1474836E9F);
    }

    static double mod(double value) {
        return (value % 1 + 1) % 1;
    }

    static final class Wobble {

        double rotation, deltaRotation;
        long lastUpdateTick = Long.MIN_VALUE;

        void update(long ticks, double target) {
            if (lastUpdateTick == ticks) return;
            lastUpdateTick = ticks;
            double d = mod(target - rotation + .5) - .5;
            deltaRotation += d * .1;
            deltaRotation *= .8;
            rotation = mod(rotation + deltaRotation);
        }
    }
}
