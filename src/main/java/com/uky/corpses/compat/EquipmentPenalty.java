package com.uky.corpses.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.uky.corpses.UkyCorpses;

public final class EquipmentPenalty {

    private static final Class<?> BATTERY = optional("api.hbm.energymk2.IBatteryItem");
    private static final Class<?> RF = optional("cofh.api.energy.IEnergyContainerItem");
    private static final Class<?> GUN = optional("com.hbm.items.weapon.sedna.ItemGunBaseNT");
    private static final Map<Class<?>, Boolean> failures = new HashMap<>();

    private EquipmentPenalty() {}

    public static ItemStack apply(ItemStack stack, int percent) {
        if (percent <= 0) return stack;
        Object item = stack.getItem();
        try {
            if (is(BATTERY, item)) {
                ItemStack copy = stack.copy();
                long capacity = read(BATTERY, "getMaxCharge", item, copy);
                long charge = read(BATTERY, "getCharge", item, copy);
                if (capacity > 0 && charge > 0) {
                    BATTERY.getMethod("setCharge", ItemStack.class, long.class)
                        .invoke(item, copy, remaining(charge, capacity, percent));
                    return copy;
                }
                return stack;
            }
            if (is(RF, item)) {
                ItemStack copy = stack.copy();
                long capacity = read(RF, "getMaxEnergyStored", item, copy);
                long charge = read(RF, "getEnergyStored", item, copy);
                if (capacity > 0 && charge > 0) {
                    RF.getMethod("extractEnergy", ItemStack.class, int.class, boolean.class)
                        .invoke(item, copy, (int) (charge - remaining(charge, capacity, percent)), false);
                    return copy;
                }
                return stack;
            }
            if (is(GUN, item)) return magazinePenalty(stack, percent);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (failures.putIfAbsent(item.getClass(), true) == null) UkyCorpses.LOG.warn(
                "Could not apply death penalty to {}",
                item.getClass()
                    .getName(),
                failure);
            return stack;
        }
        if (stack.isItemStackDamageable()) {
            int max = stack.getMaxDamage();
            long worn = Math.min(max - 1L, stack.getItemDamage() + (long) max * percent / 100);
            stack.setItemDamage((int) Math.max(stack.getItemDamage(), worn));
        }
        return stack;
    }

    private static ItemStack magazinePenalty(ItemStack stack, int percent) throws ReflectiveOperationException {
        if (!stack.hasTagCompound()) return stack;
        ItemStack copy = stack.copy();
        Object gun = stack.getItem();
        Method getConfig = GUN.getMethod("getConfig", ItemStack.class, int.class);
        int count = (int) GUN.getMethod("getConfigCount")
            .invoke(gun);
        Map<Integer, Integer> remainingBySlot = new HashMap<>();
        for (int mode = 0; mode < count; mode++) {
            Object config = getConfig.invoke(gun, copy, mode);
            if (config == null) continue;
            for (Object receiver : (Object[]) config.getClass()
                .getMethod("getReceivers", ItemStack.class)
                .invoke(config, copy)) {
                if (receiver == null) continue;
                Object magazine = receiver.getClass()
                    .getMethod("getMagazine", ItemStack.class)
                    .invoke(receiver, copy);
                if (magazine == null) continue;
                Field index;
                try {
                    index = magazine.getClass()
                        .getField("index");
                } catch (NoSuchFieldException noStoredMagazine) {
                    continue;
                }
                if (!"magcount".equals(
                    magazine.getClass()
                        .getField("KEY_MAG_COUNT")
                        .get(null)))
                    continue;
                int slot = index.getInt(magazine);
                int capacity = (int) read(magazine.getClass(), "getCapacity", magazine, stack);
                int ammo = (int) magazine.getClass()
                    .getMethod("getAmount", ItemStack.class, IInventory.class)
                    .invoke(magazine, stack, null);
                if (capacity <= 0 || ammo <= 0) continue;
                int left = (int) remaining(ammo, capacity, percent);
                Integer previous = remainingBySlot.get(slot);
                if (previous == null || left < previous) {
                    magazine.getClass()
                        .getMethod("setAmount", ItemStack.class, int.class)
                        .invoke(magazine, copy, left);
                    remainingBySlot.put(slot, left);
                }
            }
        }
        return remainingBySlot.isEmpty() ? stack : copy;
    }

    private static long remaining(long current, long max, int percent) {
        long loss = max / 100 * percent + max % 100 * percent / 100;
        return Math.max(0, current - Math.max(1, loss));
    }

    private static long read(Class<?> api, String method, Object item, ItemStack stack)
        throws ReflectiveOperationException {
        return ((Number) api.getMethod(method, ItemStack.class)
            .invoke(item, stack)).longValue();
    }

    private static boolean is(Class<?> api, Object item) {
        return api != null && api.isInstance(item);
    }

    private static Class<?> optional(String name) {
        try {
            return Class.forName(name, false, EquipmentPenalty.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }
}
