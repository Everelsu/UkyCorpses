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
        return named(entity instanceof EntityPlayer ? "player" : EntityList.getEntityString(entity));
    }

    public static synchronized Type named(String name) {
        if (!loaded) {
            for (String file : new String[] { "red", "none", "rotten", "ichor", "creeper", "ender", "iron", "magma",
                "phantom", "sculk", "slime", "snow", "squid" }) {
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
        return entities.getOrDefault(id.contains(":") ? id : "minecraft:" + id, types.get("red"));
    }

    private BloodTypes() {}
}
