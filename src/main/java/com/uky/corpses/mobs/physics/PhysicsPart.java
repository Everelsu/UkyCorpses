package com.uky.corpses.mobs.physics;

/** Original physical ordering; corpse.Skeleton uses a different mesh ordering. */
public enum PhysicsPart {

    TORSO(0, 1),
    HEAD(1, 0),
    LEFT_LEG(2, 5),
    RIGHT_LEG(3, 4),
    LEFT_ARM(4, 3),
    RIGHT_ARM(5, 2);

    public final int index, skeletonIndex;

    PhysicsPart(int index, int skeletonIndex) {
        this.index = index;
        this.skeletonIndex = skeletonIndex;
    }

    public static PhysicsPart byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : null;
    }

    public boolean isSeverable() {
        return this != TORSO;
    }
}
