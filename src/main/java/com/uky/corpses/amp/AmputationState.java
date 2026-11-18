package com.uky.corpses.amp;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

/** Original persistent AMP state, with indices mapped to the port's six-body skeleton. */
public final class AmputationState {

    public static final String KEY = com.uky.corpses.compat.LegacyIds.PART_MASK,
        DYING = com.uky.corpses.compat.LegacyIds.HEADLESS_DEADLINE;

    public static int mask(Entity entity) {
        return entity == null ? 0 : read(entity.getEntityData());
    }

    public static int read(NBTTagCompound data) {
        return data.getByte(KEY) & 61;
    }

    public static boolean hasLost(Entity entity, Part part) {
        return part != null && (mask(entity) & (1 << part.index)) != 0;
    }

    public static boolean markLost(EntityLivingBase entity, Part part) {
        return entity != null && mark(entity.getEntityData(), part);
    }

    public static boolean mark(NBTTagCompound data, Part part) {
        if (part == null || !part.isSeverable()) return false;
        int bit = 1 << part.index, mask = read(data);
        if ((mask & bit) != 0) return false;
        data.setByte(KEY, (byte) (mask | bit));
        return true;
    }

    public static void clear(EntityLivingBase entity) {
        entity.getEntityData()
            .removeTag(KEY);
        entity.getEntityData()
            .removeTag(DYING);
    }

    public static boolean allowedHead(float health, float healthLoss, float maximum, double fraction) {
        return fraction > 0 && (fraction >= 1 || maximum > 0 && health - healthLoss <= maximum * fraction);
    }

    public static float overkill(float healthLoss, float healthBefore) {
        return Math.max(0, healthLoss - healthBefore);
    }

    public static void schedule(EntityLivingBase entity) {
        entity.getEntityData()
            .setLong(
                DYING,
                entity.worldObj.getTotalWorldTime() + Math.max(0, AmputationConfig.DECAPITATION_DEATH_DELAY.get()));
    }

    private AmputationState() {}
}
