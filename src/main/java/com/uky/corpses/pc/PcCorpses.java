package com.uky.corpses.pc;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.ChunkEvent;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.OriginalGameplay;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/** Corpse storage compass issuance and recovery; inventory records are never spawned a second time. */
public final class PcCorpses {

    public static CorpseCompass compass;
    private static boolean restoring;

    public static void preInit() {
        compass = new CorpseCompass();
        GameRegistry.registerItem(compass, "corpse_compass");
    }

    public static void init() {
        PcCorpses events = new PcCorpses();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
    }

    public static void created(EntityCorpse body) {
        if (body.worldObj.isRemote || body.physicalOwner() == null) return;
        PcStore store = PcStore.get(body.worldObj);
        sync(body);
        store.lastDeaths.put(body.physicalOwner(), body.getUniqueID());
        if (OriginalGameplay.enableCorpseCompass) store.targets.put(body.physicalOwner(), target(body));
        store.markDirty();
    }

    public static void sync(EntityCorpse body) {
        if (restoring || body.worldObj == null || body.worldObj.isRemote || body.isDead || body.physicalOwner() == null)
            return;
        PcStore store = PcStore.get(body.worldObj);
        UUID id = body.getUniqueID();
        if (store.removed.contains(id)) return;
        NBTTagCompound record = target(body), nbt = new NBTTagCompound();
        body.writeToNBT(nbt);
        record.setTag("Body", nbt);
        store.records.put(id, record);
        store.markDirty();
    }

    public static boolean isLastDeath(EntityCorpse body) {
        return body.getUniqueID()
            .equals(PcStore.get(body.worldObj).lastDeaths.get(body.physicalOwner()));
    }

    public static void moved(EntityCorpse body) {
        sync(body);
        PcStore store = PcStore.get(body.worldObj);
        NBTTagCompound pending = store.targets.get(body.physicalOwner());
        if (pending != null && body.getUniqueID()
            .equals(PcStore.uuid(pending, "CorpseId"))) {
            store.targets.put(body.physicalOwner(), target(body));
            store.markDirty();
        }
    }

    static void refreshCompass(ItemStack stack, net.minecraft.world.World world) {
        if (world.isRemote) return;
        NBTTagCompound record = PcStore.get(world).records.get(CorpseCompass.id(stack));
        if (record == null) return;
        NBTTagCompound current = CorpseCompass.target(stack);
        if (current == null) return;
        for (String key : new String[] { "X", "Y", "Z" }) current.setDouble(key, record.getDouble(key));
        current.setInteger("Dim", record.getInteger("Dim"));
    }

    public static void removed(EntityCorpse body) {
        if (!restoring && body.worldObj != null && !body.worldObj.isRemote) PcStore.get(body.worldObj)
            .remove(body.getUniqueID());
    }

    static NBTTagCompound target(EntityCorpse body) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString(
            "CorpseId",
            body.getUniqueID()
                .toString());
        if (body.physicalOwner() != null) t.setString(
            "Owner",
            body.physicalOwner()
                .toString());
        t.setLong("Life", body.physicalLife());
        t.setInteger("DeathEntityId", body.getEntityId());
        t.setLong("DeathTime", body.created);
        t.setDouble("X", body.posX);
        t.setDouble("Y", body.posY);
        t.setDouble("Z", body.posZ);
        t.setInteger("Dim", body.worldObj.provider.dimensionId);
        t.setString("Name", body.owner());
        String[] armor = { "Boots", "Legs", "Chest", "Helmet" };
        for (int i = 0; i < 4; i++) {
            ItemStack stack = body.armor(i);
            if (stack != null) {
                NBTTagCompound s = new NBTTagCompound();
                stack.writeToNBT(s);
                t.setTag(armor[i], s);
            }
        }
        return t;
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PcStore store = PcStore.get(player.worldObj);
        NBTTagCompound target = store.targets.remove(player.getUniqueID());
        if (target == null) return;
        store.markDirty();
        UUID id = PcStore.uuid(target, "CorpseId");
        if (!OriginalGameplay.enableCorpseCompass
            || (OriginalGameplay.removeCompassWhenLooted && id != null && store.spent(id))) return;
        ItemStack stack = CorpseCompass.create(target);
        if (!player.inventory.addItemStackToInventory(stack)) player.entityDropItem(stack, 0);
        player.inventory.markDirty();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void join(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !(event.entity instanceof EntityCorpse)) return;
        EntityCorpse body = (EntityCorpse) event.entity;
        PcStore store = PcStore.get(event.world);
        if (store.removed.contains(body.getUniqueID())) {
            event.setCanceled(true);
            clear(body);
            body.setDead();
        } else sync(body);
    }

    @SubscribeEvent
    public void unload(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        for (Object section : event.getChunk().entityLists) for (Object candidate : (java.util.List<?>) section)
            if (candidate instanceof EntityCorpse) sync((EntityCorpse) candidate);
    }

    static void clear(EntityCorpse body) {
        boolean before = restoring;
        restoring = true;
        try {
            for (int i = 0; i < body.getSizeInventory(); i++) body.setInventorySlotContents(i, null);
            body.xp = 0;
        } finally {
            restoring = before;
        }
    }

    public static boolean retrieve(UUID corpseId, EntityPlayerMP target) {
        if (corpseId == null || target == null) return false;
        PcStore store = PcStore.get(target.worldObj);
        if (store.removed.contains(corpseId)) return false;
        for (WorldServer world : MinecraftServer.getServer().worldServers)
            if (world != null) for (Object e : new ArrayList<>(world.loadedEntityList))
                if (e instanceof EntityCorpse && corpseId.equals(((Entity) e).getUniqueID())) {
                    EntityCorpse body = (EntityCorpse) e;
                    if (body.isDead) return false;
                    if (body.cleanupExpired()) {
                        clear(body);
                        body.setDead();
                        return false;
                    }
                    give(body, target);
                    store.remove(corpseId);
                    body.setDead();
                    return true;
                }
        NBTTagCompound record = store.records.get(corpseId);
        if (record == null) return false;
        // Tombstone first: a later chunk load may contain the old on-disk inventory.
        store.remove(corpseId);
        EntityCorpse body = new EntityCorpse(target.worldObj);
        boolean before = restoring;
        restoring = true;
        try {
            body.readFromNBT(record.getCompoundTag("Body"));
            if (body.cleanupExpired()) return false;
            give(body, target);
        } finally {
            restoring = before;
            body.disposePhysics();
        }
        return true;
    }

    private static void give(EntityCorpse body, EntityPlayerMP target) {
        boolean before = restoring;
        restoring = true;
        try {
            for (int i = 0; i < body.getSizeInventory(); i++) {
                ItemStack s = body.getStackInSlot(i);
                if (s == null) continue;
                body.setInventorySlotContents(i, null);
                if (!target.inventory.addItemStackToInventory(s)) target.entityDropItem(s, 0);
            }
            if (body.xp > 0) {
                target.addExperience(body.xp);
                body.xp = 0;
            }
            target.inventory.markDirty();
            target.inventoryContainer.detectAndSendChanges();
        } finally {
            restoring = before;
        }
    }

    public static boolean retrieveLast(EntityPlayerMP owner) {
        PcStore store = PcStore.get(owner.worldObj);
        return retrieve(store.lastDeaths.get(owner.getUniqueID()), owner);
    }

    static boolean spent(ItemStack stack, net.minecraft.world.World world) {
        UUID id = CorpseCompass.id(stack);
        return !world.isRemote && OriginalGameplay.removeCompassWhenLooted
            && id != null
            && PcStore.get(world)
                .spent(id);
    }
}
