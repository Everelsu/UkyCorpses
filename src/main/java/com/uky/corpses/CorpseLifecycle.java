package com.uky.corpses;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/** Corpse storage counts loaded ticks; unloaded world time never consumes the body's lifetime. */
public final class CorpseLifecycle {

    public int ageTicks, emptyTicks;
    public boolean skeletal;

    public boolean tick(boolean empty, int skeletonTicks, int expiryTicks, int emptyLifetime) {
        if (ageTicks >= skeletonTicks) skeletal = true;
        if (ageTicks < Integer.MAX_VALUE) ageTicks++;
        if (ageTicks >= expiryTicks) return true;
        if (!empty) {
            emptyTicks = 0;
            return false;
        }
        if (emptyTicks < Integer.MAX_VALUE) emptyTicks++;
        return emptyTicks >= emptyLifetime;
    }

    public void save(NBTTagCompound tag) {
        tag.setInteger("AgeTicks", ageTicks);
        tag.setInteger("EmptyTicks", emptyTicks);
        tag.setBoolean("Skeletal", skeletal);
    }

    public void load(NBTTagCompound tag) {
        ageTicks = Math.max(0, tag.getInteger("AgeTicks"));
        emptyTicks = Math.max(0, tag.getInteger("EmptyTicks"));
        skeletal = tag.getBoolean("Skeletal");
    }

    public static boolean mayLoot(UUID owner, UUID actor, boolean ownerOnly, boolean skeletal,
        boolean othersWhenSkeletal) {
        return !ownerOnly || owner == null || owner.equals(actor) || skeletal && othersWhenSkeletal;
    }
}
