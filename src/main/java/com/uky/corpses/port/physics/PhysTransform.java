package com.uky.corpses.port.physics;

import javax.vecmath.Matrix3f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

public final class PhysTransform {

    public final Matrix3f basis = new Matrix3f();
    public final Vector3f origin = new Vector3f();
    private static final ThreadLocal<float[]> QUAT_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);

    public PhysTransform() {
        this.basis.setIdentity();
    }

    public PhysTransform(PhysTransform other) {
        this.set(other);
    }

    public void setIdentity() {
        this.basis.setIdentity();
        this.origin.set(0.0F, 0.0F, 0.0F);
    }

    public void set(PhysTransform other) {
        this.basis.set(other.basis);
        this.origin.set(other.origin);
    }

    public void set(Vector3f position, Quat4f rotation) {
        this.origin.set(position);
        this.setRotation(rotation);
    }

    public void setRotation(Quat4f q) {
        this.basis.set(q);
    }

    public Quat4f getRotation(Quat4f out) {
        float trace = this.basis.m00 + this.basis.m11 + this.basis.m22;
        if (trace > 0.0F) {
            float s = (float) Math.sqrt((double) (trace + 1.0F));
            float t = 0.5F / s;
            out.set(
                (this.basis.m21 - this.basis.m12) * t,
                (this.basis.m02 - this.basis.m20) * t,
                (this.basis.m10 - this.basis.m01) * t,
                s * 0.5F);
            return out;
        } else {
            int i = this.basis.m00 < this.basis.m11 ? (this.basis.m11 < this.basis.m22 ? 2 : 1)
                : (this.basis.m00 < this.basis.m22 ? 2 : 0);
            int j = (i + 1) % 3;
            int k = (i + 2) % 3;
            float s = (float) Math.sqrt(
                (double) (this.basis.getElement(i, i) - this.basis.getElement(j, j)
                    - this.basis.getElement(k, k)
                    + 1.0F));
            float t = 0.5F / s;
            float[] q = QUAT_SCRATCH.get();
            q[i] = s * 0.5F;
            q[3] = (this.basis.getElement(k, j) - this.basis.getElement(j, k)) * t;
            q[j] = (this.basis.getElement(j, i) + this.basis.getElement(i, j)) * t;
            q[k] = (this.basis.getElement(k, i) + this.basis.getElement(i, k)) * t;
            out.set(q[0], q[1], q[2], q[3]);
            return out;
        }
    }

    public void transform(Vector3f v) {
        this.basis.transform(v);
        v.add(this.origin);
    }

    public void inverseRotate(Vector3f v) {
        float x = v.x;
        float y = v.y;
        float z = v.z;
        v.set(
            this.basis.m00 * x + this.basis.m10 * y + this.basis.m20 * z,
            this.basis.m01 * x + this.basis.m11 * y + this.basis.m21 * z,
            this.basis.m02 * x + this.basis.m12 * y + this.basis.m22 * z);
    }

    @Override
    public String toString() {
        return "PhysTransform[origin=" + this.origin + "]";
    }
}
