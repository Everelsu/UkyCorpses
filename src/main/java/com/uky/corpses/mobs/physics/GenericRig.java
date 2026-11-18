package com.uky.corpses.mobs.physics;

import java.util.List;

public final class GenericRig {

    public final List<GenericRig.Part> parts;
    public final float spawnYOffset;
    public final float renderScale;

    public GenericRig(List<GenericRig.Part> parts, float spawnYOffset, float renderScale) {
        this.parts = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parts));
        this.spawnYOffset = spawnYOffset;
        this.renderScale = renderScale;
    }

    public int size() {
        return this.parts.size();
    }

    public static final class Part {

        private final String name;
        private final int parentIndex;
        private final float ox;
        private final float oy;
        private final float oz;
        private final float hx;
        private final float hy;
        private final float hz;
        private final float jx;
        private final float jy;
        private final float jz;
        private final float qx;
        private final float qy;
        private final float qz;
        private final float qw;

        public Part(String name, int parentIndex, float ox, float oy, float oz, float hx, float hy, float hz, float jx,
            float jy, float jz, float qx, float qy, float qz, float qw) {
            this.name = name;
            this.parentIndex = parentIndex;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.hx = hx;
            this.hy = hy;
            this.hz = hz;
            this.jx = jx;
            this.jy = jy;
            this.jz = jz;
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
        }

        public String name() {
            return name;
        }

        public int parentIndex() {
            return parentIndex;
        }

        public float ox() {
            return ox;
        }

        public float oy() {
            return oy;
        }

        public float oz() {
            return oz;
        }

        public float hx() {
            return hx;
        }

        public float hy() {
            return hy;
        }

        public float hz() {
            return hz;
        }

        public float jx() {
            return jx;
        }

        public float jy() {
            return jy;
        }

        public float jz() {
            return jz;
        }

        public float qx() {
            return qx;
        }

        public float qy() {
            return qy;
        }

        public float qz() {
            return qz;
        }

        public float qw() {
            return qw;
        }

        public float volume() {
            return this.hx * this.hy * this.hz;
        }
    }
}
