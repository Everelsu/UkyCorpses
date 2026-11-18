package com.uky.corpses.corpse;

/**
 * A 4x4 affine transform, built the way GL builds the modelview: each call multiplies on the right, so the last call
 * is the first applied to a point. Skeleton runs the very calls RenderCorpse makes, so hitboxes, the death camera and
 * the drawing share one body.
 */
public final class Mat4 implements Skeleton.Xf {

    /** Row-major: m[r * 4 + c]. */
    final double[] m = new double[16];
    private final double[] tmp = new double[16];

    public Mat4() {
        identity();
    }

    public Mat4(Mat4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
    }

    public Mat4 identity() {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    public Mat4 set(Mat4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
        return this;
    }

    /** this = this * o */
    public Mat4 mul(double[] o) {
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) {
            double s = 0;
            for (int k = 0; k < 4; k++) s += m[r * 4 + k] * o[k * 4 + c];
            tmp[r * 4 + c] = s;
        }
        System.arraycopy(tmp, 0, m, 0, 16);
        return this;
    }

    @Override
    public void translate(double x, double y, double z) {
        for (int r = 0; r < 4; r++) m[r * 4 + 3] += m[r * 4] * x + m[r * 4 + 1] * y + m[r * 4 + 2] * z;
    }

    /** this = T(x, y, z) * this: moves the result, not the input. */
    public Mat4 preTranslate(double x, double y, double z) {
        m[3] += x;
        m[7] += y;
        m[11] += z;
        return this;
    }

    /** As glRotatef: degrees about a unit axis. */
    @Override
    public void rotate(double deg, double x, double y, double z) {
        if (deg == 0) return;
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        mul(
            new double[] { t * x * x + c, t * x * y - s * z, t * x * z + s * y, 0, t * x * y + s * z, t * y * y + c,
                t * y * z - s * x, 0, t * x * z - s * y, t * y * z + s * x, t * z * z + c, 0, 0, 0, 0, 1 });
    }

    @Override
    public void scale(double x, double y, double z) {
        for (int r = 0; r < 4; r++) {
            m[r * 4] *= x;
            m[r * 4 + 1] *= y;
            m[r * 4 + 2] *= z;
        }
    }

    /** The point p (length 3) moved by this, into out. */
    public double[] apply(double x, double y, double z, double[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z + m[3];
        out[1] = m[4] * x + m[5] * y + m[6] * z + m[7];
        out[2] = m[8] * x + m[9] * y + m[10] * z + m[11];
        return out;
    }

    /** A direction (no translation) moved by this. */
    public double[] dir(double x, double y, double z, double[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z;
        out[1] = m[4] * x + m[5] * y + m[6] * z;
        out[2] = m[8] * x + m[9] * y + m[10] * z;
        return out;
    }

    /** The inverse of this affine transform, into out; false if it can't be inverted. */
    public boolean invert(Mat4 out) {
        double a = m[0], b = m[1], c = m[2], d = m[4], e = m[5], f = m[6], g = m[8], h = m[9], i = m[10];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12) return false;
        double k = 1 / det;
        double[] o = out.m;
        o[0] = A * k;
        o[1] = -(b * i - c * h) * k;
        o[2] = (b * f - c * e) * k;
        o[4] = B * k;
        o[5] = (a * i - c * g) * k;
        o[6] = -(a * f - c * d) * k;
        o[8] = C * k;
        o[9] = -(a * h - b * g) * k;
        o[10] = (a * e - b * d) * k;
        double tx = m[3], ty = m[7], tz = m[11];
        o[3] = -(o[0] * tx + o[1] * ty + o[2] * tz);
        o[7] = -(o[4] * tx + o[5] * ty + o[6] * tz);
        o[11] = -(o[8] * tx + o[9] * ty + o[10] * tz);
        o[12] = o[13] = o[14] = 0;
        o[15] = 1;
        return true;
    }

    /** Column-major copy for glMultMatrix. */
    public void toColumns(float[] out) {
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) out[c * 4 + r] = (float) m[r * 4 + c];
    }
}
