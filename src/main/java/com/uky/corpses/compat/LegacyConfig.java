package com.uky.corpses.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Public setting names are independent of the internal port packages. */
public final class LegacyConfig {

    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("cruor.blood", "blood.stains");
        NAMES.put("cruor", "blood");
        NAMES.put("ragdollifiedreactions", "reactions");
        NAMES.put("ragdollifiedamp", "dismemberment");
        NAMES.put("ragdollifiedpc.corpse", "corpse.rules");
        NAMES.put("ragdollifiedpr.revive", "downed.rules");
        NAMES.put("ragdollifiedpr.downedView", "downed.display");
        NAMES.put("ragdollified.performance", "corpse.performance");
        NAMES.put("ragdollified.physics", "physics.solver");
        NAMES.put("ragdollified.Ragdoll Settings", "corpse.lifetime");
        NAMES.put("ragdollified.humanoidJointLimits", "physics.joints");
        NAMES.put("ragdollified.partWeightMultipliers", "physics.partMass");
        NAMES.put("ragdollified.partGravityMultipliers", "physics.partGravity");
        NAMES.put("ragdollified", "physics");
        NAMES.put("ragdoll", "physics");
        NAMES.put("wounds", "effects");
        NAMES.put("blood", "legacy.blood");
    }

    public static String path(String old) {
        for (Map.Entry<String, String> entry : NAMES.entrySet())
            if (old.equalsIgnoreCase(entry.getKey()) || old.regionMatches(
                true,
                0,
                entry.getKey() + ".",
                0,
                entry.getKey()
                    .length() + 1))
                return entry.getValue() + old.substring(
                    entry.getKey()
                        .length());
        return old;
    }

    public static String comment(String text) {
        return value(text).replaceAll("(?i)ragdollified reactions(?: [0-9.]+)?", "Реакции")
            .replaceAll("(?i)ragdollified player corpse(?: [0-9.]+)?", "Тела игроков")
            .replaceAll("(?i)ragdollified player revive(?: [0-9.]+)?", "Помощь игрокам")
            .replaceAll("(?i)ragdollified", "Физика")
            .replaceAll("(?i)cruor", "Кровь");
    }

    public static String value(String text) {
        return text == null ? "" : text.replace("cruor:", "ukycorpses:");
    }

    public static void discardUnused(Configuration config) {
        for (String name : new String[] { "leaning", "leaningornot", "corpse.tape", "corpse.access", "deathcam" })
            if (config.hasCategory(name)) config.removeCategory(config.getCategory(name));
        discard(config, "corpse", "enabled", "asBlock", "despawnSeconds", "dropItemsOnDespawn", "keepEmpty");
        discard(config, "corpse.handling", "drag");
        discard(config, "corpse.rules", "corpseSettleTimeoutTicks");
    }

    private static void discard(Configuration config, String category, String... keys) {
        if (!config.hasCategory(category)) return;
        for (String key : keys) if (config.hasKey(category, key)) config.getCategory(category)
            .remove(key);
    }

    public static void gameplayDefaults(Configuration config) {
        if (config.hasKey("downed.rules", "gameplayDefaultsVersion")) return;
        Path file = config.getConfigFile()
            .toPath();
        Path backup = file.resolveSibling(file.getFileName() + ".before-gameplay-defaults.bak");
        try {
            if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot back up gameplay settings: " + file, failure);
        }
        replaceDefault(config, "downed.rules", "knockedHealth", "50.0", "12.0");
        replaceDefault(config, "downed.rules", "knockedHealth", "50", "12.0");
        replaceDefault(config, "downed.rules", "downedInvulnerableTicks", "40", "10");
        replaceDefault(config, "gameplay", "healthRegen", "false", "true");
        replaceDefault(config, "dismemberment.general", "enableWhileAlive", "true", "false");
        config.get("downed.rules", "gameplayDefaultsVersion", 1)
            .set(1);
    }

    private static void replaceDefault(Configuration config, String category, String name, String old, String value) {
        if (config.hasKey(category, name)) {
            Property property = config.getCategory(category)
                .get(name);
            if (property.getString()
                .equals(old)) property.set(value);
        }
    }

    public static boolean migrate(Configuration config) {
        boolean old = false;
        for (String name : config.getCategoryNames())
            old |= name.equals("cruor") || name.startsWith("ragdollified") || name.equals("ragdoll");
        if (!old) return false;
        Path file = config.getConfigFile()
            .toPath();
        Path backup = file.resolveSibling(file.getFileName() + ".before-layout.bak");
        try {
            if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot back up configuration before migration: " + file, failure);
        }
        Map<String, Map<String, Property>> moved = new LinkedHashMap<>();
        ArrayList<String> removed = new ArrayList<>();
        for (String name : new ArrayList<>(config.getCategoryNames())) {
            String target = path(name);
            if (target.equals(name)) continue;
            Map<String, Property> values = moved.computeIfAbsent(target, ignored -> new LinkedHashMap<>());
            for (Map.Entry<String, Property> entry : config.getCategory(name)
                .entrySet()) values.putIfAbsent(entry.getKey(), entry.getValue());
            removed.add(name);
        }
        removed.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String name : removed) if (config.hasCategory(name)) config.removeCategory(config.getCategory(name));
        for (Map.Entry<String, Map<String, Property>> category : moved.entrySet())
            for (Map.Entry<String, Property> setting : category.getValue()
                .entrySet())
                config.getCategory(category.getKey())
                    .putIfAbsent(setting.getKey(), setting.getValue());
        return true;
    }

    private LegacyConfig() {}
}
