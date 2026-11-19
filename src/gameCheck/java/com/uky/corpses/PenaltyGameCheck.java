package com.uky.corpses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.config.Property;

import com.uky.corpses.medicine.Medicine;
import com.uky.corpses.pc.PcCorpses;

import cpw.mods.fml.common.Loader;

public final class PenaltyGameCheck {

    private static final Map<Property, String> saved = new LinkedHashMap<>();
    private static int xp, respawnCycle;
    private static ItemStack battery, gun;
    private static long charge;
    private static int capacity;
    private static boolean checkedTabs;

    public static void clientTick(int phase) {
        Minecraft mc = Minecraft.getMinecraft();
        if (phase >= 2 && !checkedTabs) {
            checkedTabs = true;
            List<ItemStack> items = new ArrayList<>();
            Medicine.tab.displayAllReleventItems(items);
            check(items.size() == 17, "Creative tab has all 17 medicine items");
            check(
                items.get(0)
                    .getItem() == Medicine.BANDAGE
                    && items.get(2)
                        .getItem() == Medicine.RAG
                    && items.get(7)
                        .getItem() == Medicine.CLUB
                    && items.get(8)
                        .getItem() == Medicine.TOXILIN,
                "Creative groups are first aid, equipment, then pills");
            if (Loader.isModLoaded("NotEnoughItems")) checkNei(items);
        }
        int cycle = phase >= 180 ? 2 : phase >= 80 ? 1 : 0;
        if (cycle > respawnCycle && mc.thePlayer != null && !mc.thePlayer.isEntityAlive()) {
            mc.thePlayer.respawnPlayer();
            mc.displayGuiScreen(null);
            respawnCycle = cycle;
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkNei(List<ItemStack> creative) {
        try {
            Object entry = Class.forName("codechicken.nei.ItemSorter")
                .getMethod("find", String.class)
                .invoke(null, "ukycorpses.nei.itemsort.creative");
            check(entry != null, "NEI registers our creative sort option");
            Comparator<ItemStack> sort = (Comparator<ItemStack>) entry.getClass()
                .getField("comparator")
                .get(entry);
            List<ItemStack> items = new ArrayList<>(creative);
            Collections.reverse(items);
            items.sort(sort);
            check(
                items.equals(creative) && sort.compare(new ItemStack(Items.apple), new ItemStack(Items.stick)) == 0,
                "NEI restores creative order and leaves other mods to existing sort options");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not check NEI order", failure);
        }
    }

    private static void set(String category, String key, String value) {
        Property p = CorpseConfig.config()
            .getCategory(category)
            .get(key);
        saved.putIfAbsent(p, p.getString());
        p.set(value);
    }

    public static boolean tick(EntityPlayerMP p, int phase) {
        if (phase == 1) {
            if (Loader.isModLoaded("hbm")) prepareHbm();
            p.worldObj.getGameRules()
                .setOrCreateGameRule("doMobSpawning", "false");
            p.worldObj.getGameRules()
                .setOrCreateGameRule("keepInventory", "false");
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                p.worldObj.setBlock(x, 65, z, Blocks.bedrock);
                for (int y = 66; y <= 73; y++) p.worldObj.setBlockToAir(x, y, z);
            }
        }
        if (phase == 60 || phase == 160 || phase == 260) {
            check(p.isEntityAlive(), "Real player is alive before each death penalty scenario");
            set("penalty", "enabled", phase == 260 ? "false" : "true");
            set("penalty", "mode", "hardcore");
            set("penalty.hardcore", "keepXpPercent", "50");
            set("penalty.hardcore", "durabilityLossPercent", "25");
            set("penalty.hardcore", "itemLossPercent", phase == 60 ? "0" : "100");
            set("corpse.rules", "corpseStoreXp", "true");
            CorpseConfig.sync();
            check(
                CorpseConfig.keepXpPercent == (phase == 260 ? 100 : 50)
                    && CorpseConfig.durabilityLossPercent == (phase == 260 ? 0 : 25)
                    && CorpseConfig.itemLossPercent == (phase == 60 || phase == 260 ? 0 : 100),
                "Selected penalties survive the complete config pipeline, scenario " + phase);
            CorpseConfig.downed = false;
            Reactions.teleported(p);
            p.playerNetServerHandler.setPlayerLocation(.5, 66, .5, 0, 0);
            p.setHealth(20);
            p.hurtResistantTime = 0;
            for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = null;
            for (int i = 0; i < 4; i++) p.inventory.armorInventory[i] = null;
            ItemStack sword = new ItemStack(Items.diamond_sword);
            sword.setTagCompound(new NBTTagCompound());
            sword.getTagCompound()
                .setString("PenaltyQA", "kept");
            p.inventory.mainInventory[0] = sword;
            p.inventory.mainInventory[1] = new ItemStack(Items.diamond, 3);
            if (battery != null) {
                p.inventory.mainInventory[2] = battery.copy();
                p.inventory.mainInventory[3] = gun.copy();
            }
            p.inventory.armorInventory[3] = new ItemStack(Items.diamond_helmet);
            p.experience = 0;
            p.experienceLevel = 0;
            p.experienceTotal = 0;
            p.addExperience(500);
            xp = UkyCorpses.totalXp(p);
            p.attackEntityFrom(DamageSource.outOfWorld, 1000);
        }
        if (phase == 70 || phase == 170 || phase == 270) {
            EntityCorpse body = null;
            for (Object e : p.worldObj.loadedEntityList) if (e instanceof EntityCorpse) {
                EntityCorpse b = (EntityCorpse) e;
                if (!b.isDead && p.getUniqueID()
                    .equals(b.physicalOwner()) && PcCorpses.isLastDeath(b)) body = b;
            }
            check(body != null, "Real fatal damage creates the indexed corpse, scenario " + phase);
            boolean disabled = phase == 270;
            int retainedXp = body.xp;
            if (phase == 170) for (Object entity : p.worldObj.loadedEntityList)
                if (entity instanceof net.minecraft.entity.item.EntityXPOrb
                    && !((net.minecraft.entity.Entity) entity).isDead)
                    retainedXp += ((net.minecraft.entity.item.EntityXPOrb) entity).xpValue;
            check(
                retainedXp == (disabled ? xp : xp / 2),
                "Death stores the configured fraction of actual XP, scenario " + phase);
            int stacks = 0;
            for (int i = 0; i < body.getSizeInventory(); i++) {
                ItemStack stack = body.getStackInSlot(i);
                if (stack == null) continue;
                stacks++;
                if (battery != null && stack.getItem() == battery.getItem()) check(
                    stack.getTagCompound()
                        .getLong("charge") == (disabled ? charge : charge - charge / 4),
                    "Powered HBM armor loses charge after death");
                else if (gun != null && stack.getItem() == gun.getItem()) check(
                    stack.getTagCompound()
                        .getInteger("magcount0") == (disabled ? capacity : capacity - capacity / 4),
                    "HBM gun loses loaded rounds after death");
                else if (stack.isItemStackDamageable()) check(
                    stack.getItemDamage() == (disabled ? 0 : stack.getMaxDamage() / 4),
                    "Corpse tool and armor durability follows the configured penalty, scenario " + phase);
                if (stack.getItem() == Items.diamond_sword) check(
                    "kept".equals(
                        stack.getTagCompound()
                            .getString("PenaltyQA")),
                    "Penalty preserves unrelated item NBT");
            }
            check(
                stacks == (phase == 170 ? 0 : battery == null ? 3 : 5),
                "Item loss applies on real death and is completely bypassed when disabled, scenario " + phase);
            check(p.getHealth() <= 0, "Penalty checks use an actual dead player");
        }
        if (phase == 300) {
            for (Map.Entry<Property, String> entry : saved.entrySet()) entry.getKey()
                .set(entry.getValue());
            CorpseConfig.sync();
            return true;
        }
        return false;
    }

    private static void prepareHbm() {
        try {
            Class<?> api = Class.forName("api.hbm.energymk2.IBatteryItem");
            for (Object value : Item.itemRegistry) {
                if (!(value instanceof ItemArmor) || !api.isInstance(value)) continue;
                ItemStack candidate = new ItemStack((Item) value);
                long max = (long) api.getMethod("getMaxCharge", ItemStack.class)
                    .invoke(value, candidate);
                if (max < 4) continue;
                api.getMethod("setCharge", ItemStack.class, long.class)
                    .invoke(value, candidate, max);
                if (candidate.hasTagCompound() && candidate.getTagCompound()
                    .getLong("charge") == max) {
                    charge = max;
                    battery = candidate;
                    break;
                }
            }
            Object weapon = Class.forName("com.hbm.items.ModItems")
                .getField("gun_greasegun")
                .get(null);
            gun = new ItemStack((Item) weapon);
            Object config = weapon.getClass()
                .getMethod("getConfig", ItemStack.class, int.class)
                .invoke(weapon, gun, 0);
            Object receiver = ((Object[]) config.getClass()
                .getMethod("getReceivers", ItemStack.class)
                .invoke(config, gun))[0];
            Object magazine = receiver.getClass()
                .getMethod("getMagazine", ItemStack.class)
                .invoke(receiver, gun);
            capacity = (int) magazine.getClass()
                .getMethod("getCapacity", ItemStack.class)
                .invoke(magazine, gun);
            magazine.getClass()
                .getMethod("setAmount", ItemStack.class, int.class)
                .invoke(magazine, gun, capacity);
            check(
                battery != null && capacity >= 4
                    && gun.getTagCompound()
                        .getInteger("magcount0") == capacity,
                "Found a charged HBM armor and a loaded gun for the death scenarios");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not prepare HBM equipment", failure);
        }
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("PENALTYQA {}", text);
    }
}
