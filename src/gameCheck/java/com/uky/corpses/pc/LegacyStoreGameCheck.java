package com.uky.corpses.pc;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.compat.LegacyIds;

public final class LegacyStoreGameCheck {

    public static void run(EntityPlayerMP player) {
        UUID corpse = UUID.randomUUID(), owner = UUID.randomUUID();
        PcStore previous = new PcStore(LegacyIds.CORPSE_DATA);
        NBTTagCompound record = new NBTTagCompound();
        record.setString("CorpseId", corpse.toString());
        record.setString("Owner", owner.toString());
        record.setInteger("Dim", 1);
        record.setDouble("X", 12.5);
        record.setDouble("Y", 66);
        record.setDouble("Z", 4.5);
        NBTTagCompound body = new NBTTagCompound();
        body.setInteger("xp", 237);
        record.setTag("Body", body);
        previous.records.put(corpse, record);
        previous.targets.put(owner, record);
        previous.lastDeaths.put(owner, corpse);
        player.worldObj.mapStorage.setData(LegacyIds.CORPSE_DATA, previous);
        PcStore migrated = PcStore.get(player.worldObj);
        if (migrated == previous || !migrated.mapName.equals("ukycorpses_pending_corpses")
            || !record.equals(migrated.records.get(corpse))
            || !corpse.equals(migrated.lastDeaths.get(owner))
            || !record.equals(migrated.targets.get(owner)))
            throw new AssertionError("Legacy corpse index migration lost identity, pending target or inventory NBT");
        NBTTagCompound saved = new NBTTagCompound();
        migrated.writeToNBT(saved);
        PcStore restored = new PcStore();
        restored.readFromNBT(saved);
        if (!record.equals(restored.records.get(corpse))) throw new AssertionError("Canonical index lost migrated NBT");
        migrated.records.remove(corpse);
        migrated.targets.remove(owner);
        migrated.lastDeaths.remove(owner);
        migrated.markDirty();
        UkyCorpses.LOG.info("COMPATQA Corpse index migration preserves UUID, compass target and body NBT");
    }
}
