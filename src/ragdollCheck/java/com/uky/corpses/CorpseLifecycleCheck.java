package com.uky.corpses;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

public final class CorpseLifecycleCheck {

    public static void main(String[] args) {
        int cleanupMinutes = com.uky.corpses.corpse.PhysicsConfig.corpseLifetimeMinutes;
        com.uky.corpses.corpse.PhysicsConfig.corpseLifetimeMinutes = 20;
        assert !com.uky.corpses.corpse.PhysicsConfig.expired(24099, 100);
        assert com.uky.corpses.corpse.PhysicsConfig.expired(24100, 100);
        assert !com.uky.corpses.corpse.PhysicsConfig.expired(50, 100);
        com.uky.corpses.corpse.PhysicsConfig.corpseLifetimeMinutes = 0;
        assert !com.uky.corpses.corpse.PhysicsConfig.expired(Long.MAX_VALUE, 0);
        com.uky.corpses.corpse.PhysicsConfig.corpseLifetimeMinutes = cleanupMinutes;

        CorpseLifecycle body = new CorpseLifecycle();
        for (int i = 0; i < 10; i++) assert !body.tick(false, 10, 30, 6);
        assert body.ageTicks == 10 && !body.skeletal : "skeleton is checked before age increment, matching PC";
        NBTTagCompound save = new NBTTagCompound();
        body.save(save);
        CorpseLifecycle loaded = new CorpseLifecycle();
        loaded.load(save);
        assert loaded.ageTicks == 10 && loaded.emptyTicks == 0;
        // Nothing ticks while the chunk is unloaded, regardless of an unrelated world's clock.
        assert !loaded.tick(false, 10, 30, 6) && loaded.skeletal;
        for (int i = 0; i < 5; i++) assert !loaded.tick(true, 10, 30, 6);
        assert loaded.emptyTicks == 5;
        assert !loaded.tick(false, 10, 30, 6)
            && loaded.emptyTicks == 0 : "refilling resets the continuous empty lifetime";
        for (int i = 0; i < 5; i++) assert !loaded.tick(true, 10, 30, 6);
        assert loaded.tick(true, 10, 30, 6) : "empty body must expire after exact loaded empty lifetime";
        CorpseLifecycle full = new CorpseLifecycle();
        for (int i = 0; i < 29; i++) assert !full.tick(false, 50, 30, 6);
        assert full.tick(false, 50, 30, 6) : "expiry still drops a full inventory";
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        assert CorpseLifecycle.mayLoot(owner, owner, true, false, false);
        assert CorpseLifecycle.mayLoot(null, other, true, false, false);
        assert !CorpseLifecycle.mayLoot(owner, other, true, false, true);
        assert !CorpseLifecycle.mayLoot(owner, other, true, true, false);
        assert CorpseLifecycle.mayLoot(owner, other, true, true, true);
        assert CorpseLifecycle.mayLoot(owner, other, false, false, false);
        CorpseLifecycle immediate = new CorpseLifecycle();
        assert !immediate.tick(false, 0, 30, 6) && immediate.skeletal;
        save.setInteger("AgeTicks", -10);
        save.setInteger("EmptyTicks", -1);
        immediate.load(save);
        assert immediate.ageTicks == 0
            && immediate.emptyTicks == 0 : "invalid negative counters cannot extend lifecycle";
        System.out.println(
            "PC: loaded lifetime/save-resume, exact skeleton/expiry/empty thresholds, refill and owner access passed");
    }
}
