package com.uky.corpses.mobs;

import com.uky.corpses.port.physics.PhysTransform;

/** Original oriented-part picking, with distances measured along the normalized view ray. */
public final class MobRay {

    public static int part(PhysTransform[] parts, float[] half, double[] eye, double[] direction, double reach,
        double[] hit) {
        if (parts == null || half == null || half.length != parts.length * 3) return -1;
        int found = -1;
        double nearest = reach;
        for (int i = 0; i < parts.length; i++) {
            PhysTransform t = parts[i];
            if (t == null) continue;
            double x = eye[0] - t.origin.x, y = eye[1] - t.origin.y, z = eye[2] - t.origin.z;
            double[] p = { t.basis.m00 * x + t.basis.m10 * y + t.basis.m20 * z,
                t.basis.m01 * x + t.basis.m11 * y + t.basis.m21 * z,
                t.basis.m02 * x + t.basis.m12 * y + t.basis.m22 * z };
            double[] d = { t.basis.m00 * direction[0] + t.basis.m10 * direction[1] + t.basis.m20 * direction[2],
                t.basis.m01 * direction[0] + t.basis.m11 * direction[1] + t.basis.m21 * direction[2],
                t.basis.m02 * direction[0] + t.basis.m12 * direction[1] + t.basis.m22 * direction[2] };
            double low = 0, high = nearest;
            for (int axis = 0; axis < 3; axis++) {
                double h = half[i * 3 + axis];
                if (Math.abs(d[axis]) < 1e-10) {
                    if (Math.abs(p[axis]) > h) {
                        high = -1;
                        break;
                    }
                } else {
                    double a = (-h - p[axis]) / d[axis], b = (h - p[axis]) / d[axis];
                    if (a > b) {
                        double v = a;
                        a = b;
                        b = v;
                    }
                    low = Math.max(low, a);
                    high = Math.min(high, b);
                    if (low > high) break;
                }
            }
            if (high >= 0 && low <= high && low <= nearest) {
                nearest = low;
                found = i;
            }
        }
        if (found >= 0 && hit != null) for (int i = 0; i < 3; i++) hit[i] = eye[i] + direction[i] * nearest;
        return found;
    }

    private MobRay() {}
}
