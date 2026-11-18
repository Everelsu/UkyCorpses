package com.uky.corpses.amp;

import com.uky.corpses.corpse.Skeleton;

/** Installed AMP names mapped to this port's persisted body indices. */
public enum Part {

    TORSO(Skeleton.BODY),
    HEAD(Skeleton.HEAD),
    LEFT_LEG(Skeleton.LLEG),
    RIGHT_LEG(Skeleton.RLEG),
    LEFT_ARM(Skeleton.LARM),
    RIGHT_ARM(Skeleton.RARM);

    public final int index;

    Part(int index) {
        this.index = index;
    }

    public boolean isSeverable() {
        return this != TORSO;
    }

    public static Part byIndex(int index) {
        for (Part p : values()) if (p.index == index) return p;
        return null;
    }
}
