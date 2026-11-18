package com.uky.corpses.stains.client.decal;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uky.corpses.stains.SurfaceBloodClientConfig;

public final class StainSprites {

    public enum Style {
        PIXEL,
        SMOOTH
    }

    private static final Random selector = new Random(0);
    private static final Map<Style, Map<String, List<Sprite>>> cache = new EnumMap<>(Style.class);

    public static final class Sprite {

        public final float u, v, w, h, pixels, size, length, ax, ay, texelWidth, texelHeight;
        public final int frames;

        Sprite(float u, float v, float w, float h, float pixels) {
            this(u, v, w, h, pixels, 1);
        }

        public Sprite(float u, float v, float w, float h, float pixels, int frames) {
            this(u, v, w, h, pixels, pixels, pixels, 0, pixels / 2, pixels / 2, frames);
        }

        private Sprite(float u, float v, float w, float h, float width, float height, float size, float length,
            float ax, float ay, int frames) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.pixels = width;
            texelWidth = width;
            texelHeight = height;
            this.size = size;
            this.length = length;
            this.ax = ax;
            this.ay = ay;
            this.frames = frames;
        }
    }

    public static ResourceLocation texture() {
        return new ResourceLocation(
            "ukycorpses_blood",
            "textures/stain/" + SurfaceBloodClientConfig.STYLE.get()
                .name()
                .toLowerCase(java.util.Locale.ROOT) + ".png");
    }

    /** Reads the original size, length, anchor and animation metadata without changing the source atlas. */
    public static void read(Style style, Reader reader) {
        JsonObject root = new JsonParser().parse(reader)
            .getAsJsonObject();
        float width = root.get("width")
            .getAsFloat(),
            height = root.get("height")
                .getAsFloat();
        if (!(width > 0 && height > 0 && width <= 8192 && height <= 8192))
            throw new IllegalArgumentException("Invalid SurfaceBlood atlas dimensions");
        Map<String, List<Sprite>> families = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("sprites")
            .entrySet()) {
            List<Sprite> sprites = new ArrayList<>();
            for (JsonElement element : entry.getValue()
                .getAsJsonArray()) {
                JsonObject s = element.getAsJsonObject();
                float w = s.get("w")
                    .getAsFloat(),
                    h = s.get("h")
                        .getAsFloat();
                if (!(w > 0 && h > 0 && w <= width && h <= height))
                    throw new IllegalArgumentException("Invalid SurfaceBlood sprite dimensions");
                sprites.add(
                    new Sprite(
                        s.get("x")
                            .getAsFloat() / width,
                        s.get("y")
                            .getAsFloat() / height,
                        w / width,
                        h / height,
                        w,
                        h,
                        value(s, "size", Math.max(w, h)),
                        value(s, "length", 0),
                        value(s, "ax", w / 2),
                        value(s, "ay", h / 2),
                        Math.max(1, (int) value(s, "frames", 1))));
            }
            sprites.sort(Comparator.comparingDouble(sprite -> sprite.size));
            families.put(entry.getKey(), sprites);
        }
        cache.put(style, families);
    }

    private static float value(JsonObject object, String name, float fallback) {
        return object.has(name) ? object.get(name)
            .getAsFloat() : fallback;
    }

    private static List<Sprite> family(String family) {
        Style style = SurfaceBloodClientConfig.STYLE.get();
        if (!cache.containsKey(style)) {
            ResourceLocation json = new ResourceLocation(
                "ukycorpses_blood",
                "textures/stain/" + style.name()
                    .toLowerCase(java.util.Locale.ROOT) + ".json");
            try (Reader reader = new InputStreamReader(
                net.minecraft.client.Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(json)
                    .getInputStream(),
                StandardCharsets.UTF_8)) {
                read(style, reader);
            } catch (java.io.IOException | RuntimeException failure) {
                com.uky.corpses.UkyCorpses.LOG.warn("Cannot load SurfaceBlood stain sprites {}", json, failure);
                cache.put(style, new HashMap<>());
            }
        }
        Map<String, List<Sprite>> families = cache.get(style);
        List<Sprite> list = families.get(family);
        if (list == null || list.isEmpty()) list = families.get("splat");
        return list == null || list.isEmpty() ? java.util.Collections.singletonList(new Sprite(0, 0, 1, 1, 1)) : list;
    }

    public static Sprite choose(String family, Random random) {
        List<Sprite> list = family(family);
        return list.get(random.nextInt(list.size()));
    }

    public static Sprite largest(String family) {
        List<Sprite> list = family(family);
        return list.get(list.size() - 1);
    }

    public static Sprite nearest(String family, float size) {
        return nearest(family, size, selector);
    }

    public static Sprite nearest(String family, float size, Random random) {
        List<Sprite> list = family(family);
        float best = Float.MAX_VALUE;
        for (Sprite sprite : list) best = Math.min(best, Math.abs(sprite.size - size));
        int ties = 0;
        for (Sprite sprite : list) if (Math.abs(sprite.size - size) <= best + .01F) ties++;
        int choice = random.nextInt(ties);
        for (Sprite sprite : list) if (Math.abs(sprite.size - size) <= best + .01F && choice-- == 0) return sprite;
        return list.get(0);
    }

    public static Sprite nearestStreak(String family, float width, float length, Random random) {
        Sprite best = null;
        float score = Float.MAX_VALUE;
        for (Sprite sprite : family(family)) {
            float value = Math.abs(sprite.size - width) * 2 + Math.abs(sprite.length - length)
                + random.nextFloat() * .5F;
            if (value < score) {
                best = sprite;
                score = value;
            }
        }
        return best;
    }

    public static Sprite nearestRatio(String family, float elongation, Random random) {
        Sprite best = null;
        float score = Float.MAX_VALUE;
        for (Sprite sprite : family(family)) {
            float value = Math.abs(sprite.length / Math.max(sprite.size, .01F) - elongation) + random.nextFloat() * .3F;
            if (value < score) {
                best = sprite;
                score = value;
            }
        }
        return best;
    }

    public static void clear() {
        cache.clear();
    }

    public static int variant(String family, Sprite sprite) {
        List<Sprite> sprites = family(family);
        for (int i = 0; i < sprites.size(); i++) {
            Sprite candidate = sprites.get(i);
            if (candidate == sprite || sprite != null && candidate.u == sprite.u
                && candidate.v == sprite.v
                && candidate.w == sprite.w
                && candidate.h == sprite.h) return i;
        }
        return -1;
    }

    public static Sprite variant(String family, int index) {
        List<Sprite> list = family(family);
        return list.get(Math.max(0, Math.min(list.size() - 1, index)));
    }

    private StainSprites() {}
}
