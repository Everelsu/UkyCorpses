package com.uky.corpses.corpse;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.AxisAlignedBB;

/**
 * The geometry of a body: where ModelBiped's six boxes end up for a pose, its angles and how far it has gone down.
 * RenderCorpse makes the same calls on GL (Xf), so what's drawn, what's hit and where the death camera sits agree.
 *
 * A frame is built feet-on-the-floor at the body's position: place() turns it into the pose, model() applies the
 * player renderer's flip and 15/16 scale, limb() goes to one box (pixel units after scale(1/16)). lift() is how far
 * the whole thing must rise so nothing sinks into the floor deeper than a part may (the round back of a blocky head
 * can, a torso can't).
 */
public final class Skeleton {

    /** The handful of matrix calls a frame needs, made on GL or on a Mat4. */
    public interface Xf {

        void translate(double x, double y, double z);

        void rotate(double deg, double x, double y, double z);

        void scale(double x, double y, double z);
    }

    public static final int HEAD = 0, BODY = 1, RARM = 2, LARM = 3, RLEG = 4, LLEG = 5, PARTS = 6;
    /** ModelBiped at expand 0, pixels: pivot, box min, box max; which angles move it (-1: none). */
    private static final float[][] PIVOT = { { 0, 0, 0 }, { 0, 0, 0 }, { -5, 2, 0 }, { 5, 2, 0 }, { -1.9F, 12, 0 },
        { 1.9F, 12, 0 } };
    static final float[][] MIN = { { -4, -8, -4 }, { -4, 0, -2 }, { -3, -2, -2 }, { -1, -2, -2 }, { -2, 0, -2 },
        { -2, 0, -2 } };
    static final float[][] MAX = { { 4, 0, 4 }, { 4, 12, 2 }, { 1, 10, 2 }, { 3, 10, 2 }, { 2, 12, 2 }, { 2, 12, 2 } };
    private static final int[] ANGLE = { 0, -1, 3, 6, 9, 12 };
    /** How deep a part may sink into the floor: the head's back, a rolled-on shoulder, a heel. */
    private static final double[] SINK = { 0.25, 0, 0.07, 0.07, 0.09, 0.09 },
        SINK_SIDE = { 0.25, 0, 0.26, 0.26, 0.09, 0.09 };
    /** Sitting, a raised leg's back corner goes under the seat, where nothing shows it. */
    private static final double[] SINK_SIT = { 0.25, 0, 0.07, 0.07, 0.2, 0.2 };
    /** The hips, from the feet, standing: a thrown body turns about them. */
    public static final float HIPS = 0.9F;

    private Skeleton() {}

    /** A part's box corners, pixels. */
    public static float[] min(int part) {
        return MIN[part];
    }

    public static float[] max(int part) {
        return MAX[part];
    }

    /**
     * Into the pose: k 0 standing .. 1 down, roll degrees onto the side (Poses.roll). In the air (spin not NaN) the
     * standing body turns spin degrees about its hips, held `hips` above the feet' spot; at spin ±90 and hips 0 that is
     * exactly lying (on the back / face down).
     */
    public static void place(Xf m, int pose, float yaw, float k, float roll, float spin, float hips) {
        m.rotate(180F - yaw, 0, 1, 0); // local -z is now forward
        body(m, pose, k, roll, spin, hips);
    }

    /** place() without the turn to the yaw. */
    private static void body(Xf m, int pose, float k, float roll, float spin, float hips) {
        if (!Float.isNaN(spin)) {
            m.translate(0, hips, 0);
            m.rotate(spin, 1, 0, 0);
            if (roll != 0) m.rotate(-roll * k, 0, 1, 0);
            m.translate(0, -HIPS, 0);
        } else if (pose == Poses.SIT) {
            m.rotate(10F * k, 1, 0, 0); // leans back against the wall, pivoting on the hips
            m.translate(0, -0.6F * k, 0); // slides down the wall until the hips are on the floor
        } else {
            boolean face = pose == Poses.PRONE;
            m.translate(0, 0, (face ? 0.9F : -0.9F) * k);
            m.rotate((face ? -90F : 90F) * k, 1, 0, 0);
            if (roll != 0) m.rotate(-roll * k, 0, 1, 0); // on its side: rolled about the spine
        }
        model(m);
    }

    /** RendererLivingEntity's flip plus RenderPlayer's 15/16 scale. */
    public static void model(Xf m) {
        m.scale(-0.9375F, -0.9375F, 0.9375F);
        m.translate(0, -24 * 0.0625F - 0.0078125F, 0);
    }

    /** As ModelRenderer.render: to the pivot, then z, y, x. Still in model units (1/16 of a pixel box). */
    public static void limb(Xf m, int part, float[] a) {
        float[] p = PIVOT[part];
        m.translate(p[0] * 0.0625F, p[1] * 0.0625F, p[2] * 0.0625F);
        int i = ANGLE[part];
        if (i < 0) return;
        m.rotate(Math.toDegrees(a[i + 2]), 0, 0, 1);
        m.rotate(Math.toDegrees(a[i + 1]), 0, 1, 0);
        m.rotate(Math.toDegrees(a[i]), 1, 0, 0);
    }

    /** Frame, then into the part, then pixels: corners of MIN..MAX go straight through it. */
    public static Mat4 part(Mat4 frame, int part, float[] a, Mat4 out) {
        out.set(frame);
        limb(out, part, a);
        out.scale(0.0625, 0.0625, 0.0625);
        return out;
    }

    private static final ThreadLocal<Mat4> TMP = ThreadLocal.withInitial(Mat4::new);

    /**
     * How far to raise a frame (built at floor height 0) so nothing sinks deeper than it may. skip: bit per part left
     * out (what hangs over an edge).
     */
    public static double lift(Mat4 frame, int pose, float[] a, int skip) {
        Mat4 m = TMP.get();
        double[] p = new double[3];
        double[] sink = pose == Poses.SIDE ? SINK_SIDE : pose == Poses.SIT ? SINK_SIT : SINK;
        double need = 0;
        for (int i = 0; i < PARTS; i++) {
            if ((skip & 1 << i) != 0) continue;
            part(frame, i, a, m);
            double low = Double.MAX_VALUE;
            for (int c = 0; c < 8; c++) {
                m.apply(
                    (c & 1) == 0 ? MIN[i][0] : MAX[i][0],
                    (c & 2) == 0 ? MIN[i][1] : MAX[i][1],
                    (c & 4) == 0 ? MIN[i][2] : MAX[i][2],
                    p);
                low = Math.min(low, p[1]);
            }
            need = Math.max(need, -low - sink[i]);
        }
        return need;
    }

    /** Parts that hang over an edge and may go below the floor. */
    public static int skip(int situation) {
        switch (situation) {
            case Situation.FEET_OVER:
                return 1 << RLEG | 1 << LLEG;
            case Situation.HEAD_OVER:
                return 1 << HEAD | 1 << RARM | 1 << LARM;
            default:
                return 0;
        }
    }

    /**
     * A body at rest, relative to its position (feet spot on the floor): the frame (with lift) and its six parts in
     * pixels. Shared by the hitboxes on both sides and the client's outline and picking.
     */
    public static final class Shape {

        public final Mat4 frame = new Mat4();
        public final Mat4[] parts = new Mat4[PARTS];
        public final float[] angles = new float[15];

        Shape() {
            for (int i = 0; i < PARTS; i++) parts[i] = new Mat4();
        }

        /** A part's whole box, relative to the body's position: {minX, minY, minZ, maxX, maxY, maxZ}. */
        public double[] bounds(int i) {
            double[] p = new double[3], r = { 1e9, 1e9, 1e9, -1e9, -1e9, -1e9 };
            for (int c = 0; c < 8; c++) {
                parts[i].apply(
                    (c & 1) == 0 ? MIN[i][0] : MAX[i][0],
                    (c & 2) == 0 ? MIN[i][1] : MAX[i][1],
                    (c & 4) == 0 ? MIN[i][2] : MAX[i][2],
                    p);
                for (int k = 0; k < 3; k++) {
                    r[k] = Math.min(r[k], p[k]);
                    r[k + 3] = Math.max(r[k + 3], p[k]);
                }
            }
            return r;
        }

        /** World boxes around the parts, each long one cut in two so a bent limb doesn't swell its box. */
        public List<AxisAlignedBB> boxes(double x, double y, double z) {
            List<AxisAlignedBB> out = new ArrayList<>(11);
            double[] p = new double[3];
            for (int i = 0; i < PARTS; i++) {
                int cuts = i == HEAD ? 1 : 2;
                for (int s = 0; s < cuts; s++) {
                    double y0 = MIN[i][1] + (MAX[i][1] - MIN[i][1]) * s / cuts,
                        y1 = MIN[i][1] + (MAX[i][1] - MIN[i][1]) * (s + 1) / cuts;
                    double ax = 1e9, ay = 1e9, az = 1e9, bx = -1e9, by = -1e9, bz = -1e9;
                    for (int c = 0; c < 8; c++) {
                        parts[i].apply(
                            (c & 1) == 0 ? MIN[i][0] : MAX[i][0],
                            (c & 2) == 0 ? y0 : y1,
                            (c & 4) == 0 ? MIN[i][2] : MAX[i][2],
                            p);
                        ax = Math.min(ax, p[0]);
                        ay = Math.min(ay, p[1]);
                        az = Math.min(az, p[2]);
                        bx = Math.max(bx, p[0]);
                        by = Math.max(by, p[1]);
                        bz = Math.max(bz, p[2]);
                    }
                    out.add(
                        AxisAlignedBB.getBoundingBox(
                            x + ax - 0.01,
                            y + Math.max(0, ay),
                            z + az - 0.01,
                            x + bx + 0.01,
                            y + Math.max(by, 0.06),
                            z + bz + 0.01));
                }
            }
            return out;
        }

        /**
         * Ray from (relative to the body's position) start along d, as far as len: distance to the nearest part hit,
         * or -1. hitPart gets which part.
         */
        public double ray(double sx, double sy, double sz, double dx, double dy, double dz, double len, int[] hitPart) {
            Mat4 inv = TMP.get();
            double best = -1;
            double[] o = new double[3], v = new double[3];
            for (int i = 0; i < PARTS; i++) {
                if (!parts[i].invert(inv)) continue;
                inv.apply(sx, sy, sz, o);
                inv.dir(dx, dy, dz, v);
                double t0 = 0, t1 = len;
                boolean miss = false;
                for (int k = 0; k < 3 && !miss; k++) {
                    double lo = MIN[i][k] - 0.3, hi = MAX[i][k] + 0.3; // a little slack: limbs are thin
                    if (Math.abs(v[k]) < 1e-9) {
                        if (o[k] < lo || o[k] > hi) miss = true;
                        continue;
                    }
                    double a = (lo - o[k]) / v[k], b = (hi - o[k]) / v[k];
                    if (a > b) {
                        double t = a;
                        a = b;
                        b = t;
                    }
                    t0 = Math.max(t0, a);
                    t1 = Math.min(t1, b);
                    if (t0 > t1) miss = true;
                }
                if (miss) continue;
                if (best < 0 || t0 < best) {
                    best = t0;
                    if (hitPart != null) hitPart[0] = i;
                }
            }
            return best;
        }
    }

    /**
     * A body lying still: angles from its look, the ground and its situation, limbs tucked away from blocks (tuck: bit
     * per part); frame lifted to the floor and tilted `lean` degrees down a slope.
     */
    public static Shape rest(int pose, float yaw, int look, int situation, float bend, float lean, int tuck) {
        Shape s = new Shape();
        Poses.rest(pose, look, situation, bend, tuck, s.angles);
        posed(s.frame, pose, yaw, 1, Poses.roll(pose, look), Float.NaN, 0, s.angles, situation, lean);
        for (int i = 0; i < PARTS; i++) part(s.frame, i, s.angles, s.parts[i]);
        return s;
    }

    /**
     * A frame for these angles at the body's position: lifted so nothing sinks into the floor, then tilted lean
     * degrees about its middle (positive: the end behind its yaw goes down), as it lies along a step or a slope.
     */
    public static Mat4 posed(Mat4 out, int pose, float yaw, float k, float roll, float spin, float hips, float[] a,
        int situation, float lean) {
        return posed(out, pose, yaw, k, roll, spin, hips, a, situation, lean, a);
    }

    /**
     * Same, lifted by what liftBy would need instead of a: a limb swinging through the floor while the body settles
     * then dips into it (and is stopped there) instead of the whole body hopping up.
     */
    public static Mat4 posed(Mat4 out, int pose, float yaw, float k, float roll, float spin, float hips, float[] a,
        int situation, float lean, float[] liftBy) {
        out.identity();
        place(out, pose, yaw, k, roll, spin, hips);
        double up = lift(out, pose, liftBy, skip(situation));
        out.identity();
        out.rotate(180F - yaw, 0, 1, 0);
        if (lean != 0) out.rotate(lean * Math.min(1F, k), 1, 0, 0);
        out.translate(0, up, 0);
        body(out, pose, k, roll, spin, hips);
        return out;
    }
}
