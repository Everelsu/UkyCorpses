package com.uky.corpses.mobs;

import com.uky.corpses.mobs.physics.MobRigType;
import com.uky.corpses.mobs.physics.RagdollBodyFactory.BodyProfile;

/** Original ClientRagdoll buildPhysics torso height, separated from entity/world lifetime. */
public final class MobBirth {

    public static float torsoHeight(MobRigType type, BodyProfile profile, boolean baby, float scale, float meshScale) {
        float y = type == MobRigType.HUMANOID_STANDARD || type == MobRigType.HUMANOID_SKELETON
            || type == MobRigType.HUMANOID_DROWNED
            || type == MobRigType.ILLAGER
            || type == MobRigType.WITCH ? (baby ? .6F : 1.2F) : 0;
        if (type == MobRigType.QUADRUPED) y = .7F * scale;
        if (type == MobRigType.CHICKEN) y = .4F * scale;
        if (type == MobRigType.BAT) y = .28125F;
        if (type == MobRigType.CREEPER) y = .75F;
        switch (profile) {
            case COW:
                y = baby ? .54F : 1.07F;
                break;
            case PIG:
                y = baby ? .32F : .63F;
                break;
            case SHEEP:
                y = baby ? .47F : .94F;
                break;
            case CAT:
                y = baby ? .2F : .36F;
                break;
            case WOLF:
                y = baby ? .3125F : .625F;
                break;
            case SLIME:
            case MAGMA_CUBE:
                y = .25F;
                break;
            case SILVERFISH:
                y = .125F;
                break;
            case SNOW_GOLEM:
                y = 1;
                break;
            case BLAZE:
                y = 1.2F;
                break;
            case SPIDER:
                y = .5625F;
                break;
            case CAVE_SPIDER:
                y = .39375F;
                break;
            case GHAST:
                y = 2.25F;
                break;
            case SQUID:
                y = 1.6F;
                break;
            case WITHER:
                y = 1.702F;
                break;
            case ENDER_DRAGON:
                y = 4.5F;
                break;
            case CHICKEN:
                y = baby ? .27F : .54F;
                break;
            default:
                break;
        }
        if (type == MobRigType.EQUINE) y = (baby ? .63125F : 1) * (profile == BodyProfile.HORSE ? 1.1F
            : profile == BodyProfile.DONKEY ? .87F : profile == BodyProfile.MULE ? .92F : 1);
        if (type == MobRigType.IRON_GOLEM) y = 1.515625F;
        if (type == MobRigType.ENDERMAN) y = 2;
        return y * meshScale;
    }

    public static int missingParts(int skeletonMask) {
        int mask = 0;
        if ((skeletonMask & 1) != 0) mask |= 1 << 1;
        if ((skeletonMask & 4) != 0) mask |= 1 << 5;
        if ((skeletonMask & 8) != 0) mask |= 1 << 4;
        if ((skeletonMask & 16) != 0) mask |= 1 << 3;
        if ((skeletonMask & 32) != 0) mask |= 1 << 2;
        return mask;
    }

    private MobBirth() {}
}
