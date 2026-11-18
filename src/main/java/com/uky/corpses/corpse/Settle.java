package com.uky.corpses.corpse;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side coming down of a fresh body, one step per game tick: springs, not a physics engine; with a Probe a limb
 * that would swing into a block stops against it and stays leaning there.
 *
 * It starts exactly where and how the player was (from: the spot, the yaw, the limbs at the moment of death), so the
 * living player turns into the body without a cut. Then, by mode: it topples over (FALL), hits the floor nearly flat
 * from a height (SPLAT), goes slack where it lay downed (DYING), or is thrown through the air by a blast, turning,
 * and lands (THROWN). Limbs swing on springs into the resting pose. A last movement may follow: one hand rises,
 * trembles and drops (the death camera sees it from the eyes).
 */
public final class Settle {

    /** head, right arm, left arm, right leg, left leg; x y z each. */
    public static final int N = 15;
    public static final int FALL = 1, THROWN = 2, DYING = 3, SPLAT = 4, FLOAT = 5;
    private static final int MAX = 220;
    /** Ticks the slide from where they lay and the turn to the body's squared yaw take while dying. */
    private static final int TURN = 40;
    /** Ticks a falling body takes to come round from the death spot. */
    private static final int COME = 16;

    /** A downed player's pose at rest, as Settle angles: PRONE (face down, reaching ahead), SIT (slumped). */
    private static final float[] ALIVE_PRONE = { -1.1F, 0, 0, -3.2F, 0, .25F, -3.2F, 0, -.25F, 0, 0, .1F, 0, 0, -.1F },
        ALIVE_SIT = { .3F, 0, 0, -.25F, 0, .15F, -.35F, 0, -.1F, -1.4F, .3F, 0, -1.4F, -.25F, 0 };
    /** Thrown: limbs flung out while flying. */
    private static final float[] FLUNG = { -.3F, 0, 0, -2.4F, 0, 1.1F, -.6F, 0, -1.3F, -.5F, 0, .45F, .3F, 0, -.4F };

    public static float[] alive(int pose) {
        return pose == Poses.SIT ? ALIVE_SIT : ALIVE_PRONE;
    }

    /** Client: how each player stood the moment they died (by name), for their body to start from. */
    private static final Map<String, float[]> stances = new ConcurrentHashMap<>();
    private static final Map<String, Long> stancesAt = new ConcurrentHashMap<>();

    public static void remember(String player, float[] stance) {
        stances.put(player, stance);
        stancesAt.put(player, System.currentTimeMillis());
    }

    /** The stance player died in, if within the last few seconds; consumed. Null: straight. */
    public static float[] stance(String player) {
        float[] s = stances.remove(player);
        Long at = stancesAt.remove(player);
        return s != null && at != null && System.currentTimeMillis() - at < 5000 ? s : null;
    }

    /**
     * Asks the world whether a limb (0 head, 1 right arm, 2 left arm, 3 right leg, 4 left leg) is inside a block as the
     * body is drawn at partial 1 (where this step put it) or 0 (where it was).
     */
    public interface Probe {

        boolean blocked(int limb, float partial);
    }

    /** Ticks a stopped limb counts as resting on what stopped it. */
    private static final int HOLD = 12;

    private boolean on, began;
    /** Last tick each limb ran into a block; limbs that stay leaning on one when it's over. */
    private final int[] stopped = new int[5];
    private int held;
    private int mode, t, last, pose;
    private float roll;
    private float flail, k, kv, pk;
    private final float[] p = new float[N], v = new float[N], pp = new float[N], goal = new float[N];
    private Random rnd;
    /** Where it starts, relative to where it ends up; and the yaw it starts with. */
    private float fx, fy, fz, fromYaw;
    /** Thrown: ticks in the air, extra height at the top of the arc, degrees turned over. */
    private int flight;
    private float apex, spinEnd;
    /** The last movement: which arm (3 right, 6 left, -1 none), its goal, when it starts. */
    private int arm = -1, reachAt;
    private final float[] reach = new float[3];
    /** Hits on the floor not yet seen by the client effects, strongest first. */
    private float impact;
    private boolean landedNow;

    public boolean active() {
        return on;
    }

    public boolean began() {
        return began;
    }

    public int mode() {
        return mode;
    }

    /** Ticks a throw is in the air, or a drowned body takes to come up. */
    public int flight() {
        return flight;
    }

    public boolean dying() {
        return mode == DYING;
    }

    /**
     * mode: how it comes down. init: the limbs it starts with (null: straight). from: start spot minus rest spot, and
     * the start yaw. flightTicks/apex: thrown only. lastMove: a hand rises before it goes still.
     */
    public void begin(int now, long seed, int mode, int pose, float roll, float[] init, float dx, float dy, float dz,
        float startYaw, int flightTicks, float apex, boolean lastMove) {
        on = began = true;
        this.mode = mode;
        this.pose = pose;
        this.roll = roll;
        last = now;
        t = 0;
        rnd = new Random(seed);
        fx = dx;
        fy = dy;
        fz = dz;
        fromYaw = startYaw;
        flight = Math.max(1, flightTicks);
        this.apex = apex;
        impact = 0;
        held = 0;
        Arrays.fill(stopped, -HOLD);
        Arrays.fill(v, 0);
        if (init != null) System.arraycopy(init, 0, p, 0, N);
        else Arrays.fill(p, 0);
        System.arraycopy(p, 0, pp, 0, N);
        flail = 1;
        k = pk = kv = 0;
        switch (mode) {
            case SPLAT: // from a height: slams down, hard, limbs flying
                flail = 2.5F;
                kv = 0.2F;
                break;
            case DYING: // already on the floor: a last shudder, then slack
                k = pk = 1;
                break;
            case THROWN: // a turn and a bit, more for a long flight; lands on the back, face or side
                flail = 2.5F;
                float end = pose == Poses.PRONE ? -90 : 90;
                spinEnd = end + (flight > 14 ? Math.signum(end) * 360 : 0);
                break;
            default:
        }
        arm = -1;
        if (lastMove && mode != THROWN && mode != SPLAT) {
            reachAt = (mode == DYING ? 30 : 34) + rnd.nextInt(12);
            arm = -2; // picked once the resting angles are known
        }
    }

    /** 0..1 over the first TURN ticks of dying, eased: how far the yaw has come round. */
    public float turn(float partial) {
        float x = Math
            .min(1F, (t + partial) / (mode == DYING ? TURN : mode == THROWN || mode == FLOAT ? flight : COME));
        return x * x * (3 - 2 * x);
    }

    /** The yaw it's drawn at, from the start yaw round to rest. */
    public float yaw(float partial, float rest) {
        float d = rest - fromYaw;
        d = d - 360F * (float) Math.floor((d + 180F) / 360F);
        return fromYaw + d * turn(partial);
    }

    /** Offset from the resting spot it's drawn at: the slide from the death spot, the arc of a throw. */
    public void offset(float partial, double[] out) {
        float u = turn(partial), left = 1 - u;
        out[0] = fx * left;
        out[1] = fy * left;
        out[2] = fz * left;
        if (mode == THROWN && flying(partial)) {
            float s = Math.min(1F, (t + partial) / flight);
            out[0] = fx * (1 - s);
            out[2] = fz * (1 - s);
            out[1] = fy * (1 - s) + 4 * apex * s * (1 - s);
        }
    }

    public boolean flying(float partial) {
        return mode == THROWN && t + partial < flight;
    }

    /** Degrees turned in the air (NaN when not flying): about the hips, which come down from standing height. */
    public float spin(float partial) {
        if (!flying(partial)) return Float.NaN;
        float s = (t + partial) / flight;
        return spinEnd * (1 - (1 - s) * (1 - s));
    }

    /** 0..1 through the flight. */
    public float progress(float partial) {
        return Math.min(1F, (t + partial) / flight);
    }

    public float hips(float partial) {
        if (!flying(partial)) return 0;
        return Skeleton.HIPS * (1 - (t + partial) / flight);
    }

    /** 0 just died .. 1 a while ago: the skin pales over the first second and a half. */
    public float pale(float partial) {
        return Math.min(1F, (t + partial) / 30F);
    }

    /** The floor was hit since the last call: how hard (0 none). */
    public float takeImpact() {
        float i = impact;
        impact = 0;
        return i;
    }

    /** Thrown body just came down (once). */
    public boolean takeLanding() {
        boolean l = landedNow;
        landedNow = false;
        return l;
    }

    /** Catches up with the entity's tick counter; target = the resting angles. */
    public void advance(int now, float[] target) {
        advance(now, target, null);
    }

    /**
     * Same, and a limb that would swing into a block stops against it and bounces back a little instead; one still
     * leaning on a block when the body goes still stays where it stopped (held()).
     */
    public void advance(int now, float[] target, Probe probe) {
        while (on && last < now) {
            last++;
            step(target);
            if (probe != null) collide(probe);
            if (!on) held = holding();
        }
    }

    /** Limbs that came to rest against a block: bit per limb (0 head .. 4 left leg). */
    public int held() {
        return held;
    }

    /** The angles the held limbs stopped at, over out's. */
    public void heldLimbs(float[] out) {
        for (int g = 0; g < 5; g++) if ((held & 1 << g) != 0) System.arraycopy(p, g * 3, out, g * 3, 3);
    }

    private int holding() {
        int m = 0;
        for (int g = 0; g < 5; g++) if (t - stopped[g] < HOLD) m |= 1 << g;
        return m;
    }

    /** Undoes this step for each limb that went into a block from outside one. */
    private void collide(Probe probe) {
        if (mode == THROWN && t < flight || mode == FLOAT) return; // in the air or the water: nothing to hit
        for (int g = 0; g < 5; g++) {
            int i = g * 3;
            if (Math.abs(p[i] - pp[i]) + Math.abs(p[i + 1] - pp[i + 1]) + Math.abs(p[i + 2] - pp[i + 2]) < 1e-4F
                && t - stopped[g] >= HOLD) continue;
            if (!probe.blocked(g, 1) || probe.blocked(g, 0)) continue; // free, or was already in (rest the way it lies)
            for (int j = i; j < i + 3; j++) {
                p[j] = pp[j];
                v[j] *= -0.3F;
            }
            stopped[g] = t;
        }
    }

    private void step(float[] target) {
        pk = k;
        System.arraycopy(p, 0, pp, 0, N);
        t++;
        System.arraycopy(target, 0, goal, 0, N);
        if (arm == -2) pickArm(target);
        float loose = lastMove(); // 0, or how floppy the dropping arm is
        if (mode == THROWN && t < flight) { // in the air: limbs flung out, flailing
            for (int i = 0; i < N; i++) {
                float want = FLUNG[i] + (rnd.nextFloat() - 0.5F) * 0.6F;
                v[i] = (v[i] + (want - p[i]) * 0.12F) * 0.85F;
                p[i] += v[i];
            }
            return;
        }
        if (mode == THROWN && t == flight) { // landed: flat, hard, a bounce
            k = pk = 1;
            kv = -0.05F;
            impact = 1.5F;
            landedNow = true;
            for (int i = 0; i < N; i++) v[i] += (rnd.nextFloat() - 0.5F) * 1.4F * (i < 3 ? 0.35F : 1);
        }
        float err = 0, speed = 0;
        if (mode == FLOAT) { // drifting up limp, turning over to lie on the water; no floor to hit
            float u = Math.min(1F, t / (float) flight);
            k = u * u * (3 - 2 * u);
            for (int i = 0; i < N; i++) {
                float drift = t < flight ? 0.006F : 0.001F; // the water stirs it while it rises
                v[i] = (v[i] + (goal[i] - p[i]) * 0.025F + (rnd.nextFloat() - 0.5F) * drift) * 0.93F;
                p[i] += v[i];
                err = Math.max(err, off(i, target));
                if (t - stopped[i / 3] >= HOLD) speed = Math.max(speed, Math.abs(v[i]));
            }
            if (t >= MAX || t > flight && err < 0.03F && speed < 0.004F) on = false;
            return;
        }
        if (mode == DYING) {
            for (int i = 0; i < N; i++) {
                if (t < 10) v[i] += (rnd.nextFloat() - 0.5F) * 0.05F; // the last twitch
                float stiff = i >= arm && i < arm + 3 && arm >= 0 ? 0.06F : 0.03F;
                v[i] = (v[i] + (goal[i] - p[i]) * stiff) * (0.9F + loose * 0.05F); // nothing holds the limbs up
                p[i] += v[i];
                err = Math.max(err, off(i, target));
                if (t - stopped[i / 3] >= HOLD) speed = Math.max(speed, Math.abs(v[i]));
            }
            if (t >= MAX || t > TURN && arm < 0 && err < 0.02F && speed < 0.004F) on = false;
            return;
        }
        if (k < 1 || kv != 0) {
            kv += 0.014F; // gravity pulling the body over
            k += kv;
        }
        float hit = 0;
        if (k >= 1) {
            if (kv > 0.05F) hit = kv;
            k = 1;
            kv = -kv * (mode == SPLAT ? 0.12F : 0.1F); // a dead weight hardly bounces
            if (Math.abs(kv) < 0.012F) kv = 0;
        }
        if (hit > 0) impact = Math.max(impact, hit * flail);
        boolean rest = k == 1 && kv == 0;
        for (int i = 0; i < N; i++) {
            if (hit > 0) v[i] += (rnd.nextFloat() - 0.5F) * 2.4F * hit * flail * (i < 3 ? 0.35F : 1); // the neck holds
            else if (k < 1) v[i] += (rnd.nextFloat() - 0.5F) * 0.04F * flail;
            boolean moving = arm >= 0 && i >= arm && i < arm + 3;
            float stiff = moving ? 0.07F : 0.1F, damp = moving ? 0.86F + loose * 0.06F : 0.86F;
            v[i] = (v[i] + (goal[i] * (mode == THROWN ? 1 : k) - p[i]) * stiff) * damp;
            p[i] += v[i];
            err = Math.max(err, off(i, target));
            if (t - stopped[i / 3] >= HOLD) speed = Math.max(speed, Math.abs(v[i]));
        }
        if (t >= MAX || rest && arm < 0 && err < 0.02F && speed < 0.004F) on = false;
    }

    /**
     * How far limb i still is from where it should end up; a limb leaning on a block is where it ends up (and its
     * pushing against it doesn't keep the body from going still).
     */
    private float off(int i, float[] target) {
        return t - stopped[i / 3] < HOLD ? 0 : Math.abs(target[i] - p[i]);
    }

    /** The arm on the side the face is turned to, or the upper one lying on the side. */
    private void pickArm(float[] target) {
        boolean right;
        if (pose == Poses.SIDE) right = roll < 0; // the upper arm: left when lying on the right side
        else right = target[1] > 0;
        arm = right ? 3 : 6;
        float in = right ? -1 : 1; // toward the middle of the body
        switch (pose) {
            case Poses.PRONE: // claws ahead along the floor
                reach[0] = -3.2F;
                reach[1] = 0;
                reach[2] = 0.15F * in;
                break;
            case Poses.SIT: // lifts toward the face
                reach[0] = -1.3F;
                reach[1] = 0;
                reach[2] = 0.25F * in;
                break;
            case Poses.SIDE:
                reach[0] = -1.5F;
                reach[1] = target[arm + 1];
                reach[2] = 0;
                break;
            default: // on the back: up toward the sky, over the face
                reach[0] = -2.0F;
                reach[1] = 0;
                reach[2] = 0.3F * in;
        }
    }

    /**
     * Bends goal for the rising hand: up over ~1.2 s, held trembling ~1.8 s, then let go. Returns how floppy the arm
     * is (1 just let go).
     */
    private float lastMove() {
        if (arm < 0) return 0;
        int s = t - reachAt;
        if (s < 0) return 0;
        final int UP = 24, HOLD = 36, DROP = 30;
        float w;
        if (s < UP) {
            float x = s / (float) UP;
            w = x * x * (3 - 2 * x);
        } else if (s < UP + HOLD) w = 1;
        else if (s < UP + HOLD + DROP) w = 0;
        else {
            arm = -1; // done: the body may go still
            return 0;
        }
        for (int j = 0; j < 3; j++) {
            float tremble = s >= UP && s < UP + HOLD ? (float) Math.sin(s * 1.7 + j * 2.1) * 0.035F : 0;
            if (pose == Poses.PRONE && j == 0 && s >= UP && s < UP + HOLD)
                tremble += (float) Math.sin((s - UP) * 0.35) * 0.12F; // clawing at the floor
            goal[arm + j] += (reach[j] - goal[arm + j]) * w + tremble * w;
        }
        return s >= UP + HOLD ? 1 : 0;
    }

    /** How far down the body is, 0 standing .. 1 flat. */
    public float tip(float partial) {
        return pk + (k - pk) * partial;
    }

    public void limbs(float partial, float[] out) {
        for (int i = 0; i < N; i++) out[i] = pp[i] + (p[i] - pp[i]) * partial;
    }
}
