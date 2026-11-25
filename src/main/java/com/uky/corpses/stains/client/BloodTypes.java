package com.uky.corpses.stains.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Original SurfaceBlood blood definitions, mapped to legacy registry names. */
public final class BloodTypes {

    private static final Map<String, Type> entities = new HashMap<>();
    private static final Map<String, Type> types = new HashMap<>();
    private static final Map<String, Type> resolved = new HashMap<>();
    private static boolean loaded;

    public static final class Type {

        public final String name;
        public final int color, dried;
        public final float amount;
        public final boolean flows, bleeds;

        public Type(String name, int color, int dried, float amount, boolean bleeds, boolean flows) {
            this.name = name;
            this.color = color;
            this.dried = dried;
            this.amount = amount;
            this.bleeds = bleeds;
            this.flows = flows;
        }

        Type(JsonObject json) {
            name = json.get("name")
                .getAsString();
            bleeds = !json.has("bleeds") || json.get("bleeds")
                .getAsBoolean();
            color = json.has("color") ? Integer.parseInt(
                json.get("color")
                    .getAsString()
                    .replace("#", ""),
                16) : 0;
            dried = json.has("dried") ? Integer.parseInt(
                json.get("dried")
                    .getAsString()
                    .replace("#", ""),
                16) : color;
            amount = json.has("amount") ? json.get("amount")
                .getAsFloat() : 1;
            flows = bleeds && (!json.has("flows") || json.get("flows")
                .getAsBoolean());
        }
    }

    public static Type of(Entity entity) {
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) if (type.getName()
            .startsWith("com.hbm.entity.mob.glyphid.EntityGlyphid")) return named("hbm:glyphid");
        return named(entity instanceof EntityPlayer ? "player" : EntityList.getEntityString(entity));
    }

    public static synchronized Type named(String name) {
        if (!loaded) {
            for (String file : new String[] { "red", "none", "rotten", "ichor", "creeper", "ender", "iron", "magma",
                "phantom", "sculk", "slime", "snow", "squid", "glyphid" }) {
                try (InputStream in = BloodTypes.class
                    .getResourceAsStream("/assets/ukycorpses_blood/blood_types/" + file + ".json")) {
                    if (in == null) throw new IllegalStateException("Missing SurfaceBlood blood type " + file);
                    JsonObject json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                    Type type = new Type(json);
                    types.put(type.name, type);
                    if (json.has("entities")) for (JsonElement entry : json.getAsJsonArray("entities"))
                        entities.put(entry.getAsString(), type);
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }
            loaded = true;
        }
        return resolved.computeIfAbsent(name, BloodTypes::resolve);
    }

    private static Type resolve(String name) {
        String id = name == null ? "player"
            : name.replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
        if (id.equals("pig_zombie")) id = "zombified_piglin";
        if (id.equals("lava_slime")) id = "magma_cube";
        if (id.equals("villager_golem")) id = "iron_golem";
        if (id.equals("snow_man")) id = "snow_golem";
        if (id.equals("mushroom_cow")) id = "mooshroom";
        if (id.equals("ozelot")) id = "ocelot";
        if (id.equals("entity_horse")) id = "horse";
        if (id.startsWith("hbm:glyphid") || id.startsWith("hbm.glyphid")) return types.get("glyphid");
        String key = id.contains(":") ? id : "minecraft:" + id;
        if (id.startsWith("special_mobs.") || id.startsWith("specialmobs.")
            || id.startsWith("special_mobs:")
            || id.startsWith("specialmobs:")) {
            String variant = id.substring(Math.max(id.indexOf('.'), id.indexOf(':')) + 1);
            Type special = specialMob(variant);
            if (special != null) return special;
        }
        return entities.getOrDefault(key, types.get("red"));
    }

    private static Type specialMob(String variant) {
        switch (variant) {
            case "lava_monster":
            case "ghost_spider":
            case "mirage_enderman":
                return types.get("none");
            case "undead_witch":
                return types.get("rotten");
            case "blackberry_slime":
                return coloredSlime(0x653D83, 0x2B1939);
            case "blueberry_slime":
                return coloredSlime(0x426CC1, 0x1B2B51);
            case "caramel_slime":
                return coloredSlime(0xBC742A, 0x583014);
            case "grape_slime":
                return coloredSlime(0x934BAC, 0x411E50);
            case "lemon_slime":
                return coloredSlime(0xC4B43D, 0x5A4D1A);
            case "strawberry_slime":
                return coloredSlime(0xCA4957, 0x60212D);
            case "watermelon_slime":
                return coloredSlime(0x64A647, 0x2D4D24);
            default:
                break;
        }
        if (variant.endsWith("_blaze") || variant.endsWith("_skeleton") || variant.endsWith("_ghast"))
            return types.get("none");
        if (variant.endsWith("_zombie")) return types.get("rotten");
        if (variant.endsWith("_spider") || variant.endsWith("_silverfish")) return types.get("ichor");
        if (variant.endsWith("_creeper")) return types.get("creeper");
        if (variant.endsWith("_enderman")) return types.get("ender");
        if (variant.endsWith("_slime")) return types.get("slime");
        if (variant.endsWith("_witch")) return types.get("red");
        return null;
    }

    private static Type coloredSlime(int color, int dried) {
        Type slime = types.get("slime");
        return new Type(slime.name, color, dried, slime.amount, slime.bleeds, slime.flows);
    }

    private BloodTypes() {}
}
