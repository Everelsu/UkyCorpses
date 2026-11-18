package com.uky.corpses.pc;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class PcCheck {

    public static void run() {
        UUID owner = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        PcStore source = new PcStore();
        NBTTagCompound record = new NBTTagCompound();
        record.setString("CorpseId", first.toString());
        record.setString("Owner", owner.toString());
        NBTTagCompound body = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        NBTTagCompound item = new NBTTagCompound();
        item.setInteger("Count", 1);
        items.appendTag(item);
        body.setTag("items", items);
        record.setTag("Body", body);
        source.records.put(first, record);
        source.lastDeaths.put(owner, first);
        source.targets.put(owner, record);
        assert !source.spent(first);
        assert !source.spent(second) : "unknown corpse is not known looted";
        NBTTagCompound saved = new NBTTagCompound();
        source.writeToNBT(saved);
        PcStore restored = new PcStore();
        restored.readFromNBT(saved);
        assert restored.lastDeaths.get(owner)
            .equals(first);
        assert restored.records.get(first)
            .getCompoundTag("Body")
            .getTagList("items", 10)
            .tagCount() == 1;
        restored.remove(first);
        saved = new NBTTagCompound();
        restored.writeToNBT(saved);
        PcStore reloaded = new PcStore();
        reloaded.readFromNBT(saved);
        assert reloaded.spent(first)
            && !reloaded.records.containsKey(first) : "retrieval tombstone must suppress old chunk inventory";
        assert !reloaded.spent(second) : "tombstone must match only exact corpse UUID";
        body.setTag("items", new NBTTagList());
        source.records.put(second, record);
        assert source.spent(second) : "empty corpse cleans compass independently of XP";
        assert PcStore.uuid(new NBTTagCompound(), "Owner") == null;
        CompassAngle.Wobble spring = new CompassAngle.Wobble();
        spring.update(1, .25);
        double once = spring.rotation;
        spring.update(1, .75);
        assert spring.rotation == once : "same frame cannot advance spring twice";
        for (int tick = 2; tick < 300; tick++) spring.update(tick, .25);
        assert Math.abs(spring.rotation - .25) < 1e-6;
        CompassAngle angle = new CompassAngle();
        for (int tick = 1; tick < 100; tick++) {
            float turn = angle.towards(tick, tick * 90, 3, -2);
            assert turn >= 0 && turn < 1;
            float spin = angle.spinning(tick, 17);
            assert spin >= 0 && spin < 1;
        }
        System.out.println(
            "Corpse storage: saved index, exact identity, retrieval tombstones, empty cleanup and compass spring passed");
    }

    private PcCheck() {}
}
