package com.uky.corpses.pc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/** Corpse storage materialized records, death targets and removal tombstones, shared by every dimension. */
public final class PcStore extends WorldSavedData {

    private static final String NAME = "ukycorpses_pending_corpses";
    final Map<UUID, NBTTagCompound> records = new HashMap<>(), targets = new HashMap<>();
    final Map<UUID, UUID> lastDeaths = new HashMap<>();
    final Set<UUID> removed = new HashSet<>();

    public PcStore() {
        super(NAME);
    }

    public PcStore(String name) {
        super(name);
    }

    public static PcStore get(World context) {
        World base = DimensionManager.getWorld(0);
        if (base == null) base = context;
        PcStore result = (PcStore) base.mapStorage.loadData(PcStore.class, NAME);
        if (result == null) {
            PcStore previous = (PcStore) base.mapStorage
                .loadData(PcStore.class, com.uky.corpses.compat.LegacyIds.CORPSE_DATA);
            result = new PcStore();
            if (previous != null) {
                NBTTagCompound tag = new NBTTagCompound();
                previous.writeToNBT(tag);
                result.readFromNBT(tag);
                result.markDirty();
            }
            base.mapStorage.setData(NAME, result);
        }
        return result;
    }

    static UUID uuid(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) return null;
        try {
            return UUID.fromString(tag.getString(key));
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        records.clear();
        targets.clear();
        lastDeaths.clear();
        removed.clear();
        readMap(tag.getTagList("Materialized", 10), records, "CorpseId");
        readMap(tag.getTagList("DeathTargets", 10), targets, "Owner");
        NBTTagList list = tag.getTagList("LastDeaths", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            UUID owner = uuid(e, "Owner"), id = uuid(e, "CorpseId");
            if (owner != null && id != null) lastDeaths.put(owner, id);
        }
        list = tag.getTagList("RemovedCorpseIds", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            UUID id = uuid(list.getCompoundTagAt(i), "CorpseId");
            if (id != null) removed.add(id);
        }
        for (UUID id : removed) records.remove(id);
    }

    private static void readMap(NBTTagList list, Map<UUID, NBTTagCompound> out, String key) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            UUID id = uuid(t, key);
            if (id != null) out.put(id, t);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setTag("Materialized", list(records));
        tag.setTag("DeathTargets", list(targets));
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, UUID> e : lastDeaths.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString(
                "Owner",
                e.getKey()
                    .toString());
            t.setString(
                "CorpseId",
                e.getValue()
                    .toString());
            list.appendTag(t);
        }
        tag.setTag("LastDeaths", list);
        list = new NBTTagList();
        for (UUID id : removed) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("CorpseId", id.toString());
            list.appendTag(t);
        }
        tag.setTag("RemovedCorpseIds", list);
    }

    private static NBTTagList list(Map<UUID, NBTTagCompound> map) {
        NBTTagList result = new NBTTagList();
        for (NBTTagCompound t : map.values()) result.appendTag(t.copy());
        return result;
    }

    public boolean spent(UUID id) {
        if (removed.contains(id)) return true;
        NBTTagCompound record = records.get(id);
        return record != null && record.getCompoundTag("Body")
            .getTagList("items", 10)
            .tagCount() == 0;
    }

    void remove(UUID id) {
        records.remove(id);
        removed.add(id);
        markDirty();
    }
}
