package com.uky.corpses.port.physics.jolt;

import com.github.stephengold.joltjni.ContactManifold;
import com.github.stephengold.joltjni.CustomContactListener;
import com.github.stephengold.joltjni.Vec3;

final class JoltContactCollector extends CustomContactListener {

    private static final int INITIAL_CAPACITY = 4096;
    private long[] bodyVa = new long[8192];
    private float[] data = new float[16384];
    private boolean[] isNew = new boolean[4096];
    private int count;

    synchronized void reset() {
        this.count = 0;
    }

    synchronized int count() {
        return this.count;
    }

    long bodyVa1(int index) {
        return this.bodyVa[index * 2];
    }

    long bodyVa2(int index) {
        return this.bodyVa[index * 2 + 1];
    }

    float distance(int index) {
        return this.data[index * 4];
    }

    float normalX(int index) {
        return this.data[index * 4 + 1];
    }

    float normalY(int index) {
        return this.data[index * 4 + 2];
    }

    float normalZ(int index) {
        return this.data[index * 4 + 3];
    }

    boolean isNew(int index) {
        return this.isNew[index];
    }

    public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        this.record(body1Va, body2Va, manifoldVa, true);
    }

    public void onContactPersisted(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        this.record(body1Va, body2Va, manifoldVa, false);
    }

    private void record(long body1Va, long body2Va, long manifoldVa, boolean added) {
        ContactManifold manifold = new ContactManifold(manifoldVa);
        float depth = manifold.getPenetrationDepth();
        Vec3 normal = manifold.getWorldSpaceNormal();
        float nx = -normal.getX();
        float ny = -normal.getY();
        float nz = -normal.getZ();
        synchronized (this) {
            this.ensureCapacity(this.count + 1);
            int index = this.count++;
            this.bodyVa[index * 2] = body1Va;
            this.bodyVa[index * 2 + 1] = body2Va;
            int base = index * 4;
            this.data[base] = -depth;
            this.data[base + 1] = nx;
            this.data[base + 2] = ny;
            this.data[base + 3] = nz;
            this.isNew[index] = added;
        }
    }

    private void ensureCapacity(int required) {
        if (required > this.isNew.length) {
            int capacity = Math.max(required, this.isNew.length * 2);
            long[] grownVa = new long[capacity * 2];
            float[] grownData = new float[capacity * 4];
            boolean[] grownNew = new boolean[capacity];
            System.arraycopy(this.bodyVa, 0, grownVa, 0, this.count * 2);
            System.arraycopy(this.data, 0, grownData, 0, this.count * 4);
            System.arraycopy(this.isNew, 0, grownNew, 0, this.count);
            this.bodyVa = grownVa;
            this.data = grownData;
            this.isNew = grownNew;
        }
    }
}
