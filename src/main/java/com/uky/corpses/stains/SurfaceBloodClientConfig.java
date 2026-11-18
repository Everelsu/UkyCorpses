package com.uky.corpses.stains;

import java.util.ArrayList;
import java.util.List;

import com.uky.corpses.stains.client.decal.StainSprites;
import com.uky.corpses.stains.config.ConfigSpec;
import com.uky.corpses.stains.config.ConfigSpec.BooleanValue;
import com.uky.corpses.stains.config.ConfigSpec.Builder;
import com.uky.corpses.stains.config.ConfigSpec.ConfigValue;
import com.uky.corpses.stains.config.ConfigSpec.DoubleValue;
import com.uky.corpses.stains.config.ConfigSpec.EnumValue;
import com.uky.corpses.stains.config.ConfigSpec.IntValue;

public final class SurfaceBloodClientConfig {

    public static final ConfigSpec SPEC;
    public static final EnumValue<StainSprites.Style> STYLE;
    public static final BooleanValue SPECULAR;
    public static final DoubleValue SHADER_PACK_BRIGHTNESS;
    public static final EnumValue<SurfaceBloodClientConfig.Palette> PALETTE;
    public static final ConfigValue<String> CUSTOM_COLOR;
    public static final ConfigValue<String> CUSTOM_DRIED;
    public static final ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final EnumValue<SurfaceBloodClientConfig.Quality> QUALITY;
    public static final EnumValue<SurfaceBloodClientConfig.Preset> PRESET;
    public static final DoubleValue AMOUNT, WEAK_HIT_DAMAGE, WEAK_HIT_MIN_SCALE;
    public static final IntValue MAX_DROPLETS;
    public static final IntValue MAX_STAINS;
    public static final IntValue STAIN_LIFETIME;
    public static final IntValue DRY_TIME;
    public static final IntValue RENDER_DISTANCE;
    public static final BooleanValue KEEP_STAINS;
    public static final BooleanValue RUNS;
    public static final BooleanValue DRIPS;
    public static final BooleanValue POOLS;
    public static final BooleanValue DEATH_POOLS;
    public static final BooleanValue SURFACES;
    public static final BooleanValue SOAK_THROUGH;
    public static final IntValue SOAK_TIME;
    public static final BooleanValue FOOTPRINTS;
    public static final BooleanValue WATER;
    public static final DoubleValue POOL_SIZE;
    public static final DoubleValue DEATH_POOL_SIZE;
    public static final DoubleValue FOOTPRINT_STEPS;
    public static final EnumValue<SurfaceBloodClientConfig.PrintShape> FOOTPRINT_SHAPE;
    public static final IntValue FOOTPRINT_FADE;
    public static final BooleanValue SPRAY;
    public static final BooleanValue BURST;
    public static final BooleanValue MIST;
    public static final BooleanValue GOUTS;
    public static final BooleanValue STREAM;
    public static final DoubleValue SPRAY_SPEED;
    public static final DoubleValue DROPLET_SIZE;
    public static final DoubleValue DROPLET_BRIGHTNESS;
    public static final BooleanValue BLEEDING;
    public static final DoubleValue BLEED_HEALTH;
    public static final DoubleValue BLEED_AMOUNT;
    public static final DoubleValue BLEED_TIME;
    public static final BooleanValue WOUNDS;
    public static final BooleanValue ARMOUR_WOUNDS;
    public static final DoubleValue WOUND_SIZE;
    public static final BooleanValue WOUND_DRIPS;
    public static final BooleanValue SPATTER;
    public static final IntValue SPATTER_LASTS;
    public static final BooleanValue GEAR;
    public static final BooleanValue GEAR_DROPPED;
    public static final DoubleValue GEAR_COVERAGE;
    public static final DoubleValue GEAR_OPACITY;
    public static final BooleanValue GEAR_HOTBAR;
    public static final BooleanValue GEAR_DRIPS;
    public static final IntValue GEAR_LASTS;
    public static final IntValue GEAR_FADE;
    public static final BooleanValue SOUNDS;
    public static final DoubleValue VOLUME;
    public static final DoubleValue HIT_VOLUME;
    public static final DoubleValue SPURT_VOLUME;
    public static final DoubleValue SPLAT_VOLUME;
    public static final DoubleValue DRIP_VOLUME;
    public static final DoubleValue STEP_VOLUME;
    public static final DoubleValue SIZZLE_VOLUME;
    public static final ConfigValue<List<? extends String>> HIT_SOUNDS;
    public static final ConfigValue<List<? extends String>> SPURT_SOUNDS;
    public static final ConfigValue<List<? extends String>> SPLAT_SOUNDS;
    public static final ConfigValue<List<? extends String>> DRIP_SOUNDS;
    public static final ConfigValue<List<? extends String>> SOFT_DRIP_SOUNDS;
    public static final ConfigValue<List<? extends String>> WATER_DRIP_SOUNDS;
    public static final ConfigValue<List<? extends String>> STEP_SOUNDS;
    public static final ConfigValue<List<? extends String>> SQUELCH_SOUNDS;
    public static final ConfigValue<List<? extends String>> SIZZLE_SOUNDS;
    public static final BooleanValue VIGNETTE;
    public static final DoubleValue VIGNETTE_STRENGTH;
    public static final BooleanValue GUESS_HITS;
    public static final BooleanValue STUMPS;
    public static final DoubleValue STUMP_AMOUNT;
    public static final DoubleValue STUMP_TIME;
    public static final DoubleValue STUMP_SPEED;
    public static final BooleanValue STUMP_LIMBS;

    public static float hitScale(float damage, boolean fatal) {
        if (fatal) return 1;
        float fraction = Math.max(
            0,
            Math.min(
                1,
                damage / WEAK_HIT_DAMAGE.get()
                    .floatValue()));
        float minimum = WEAK_HIT_MIN_SCALE.get()
            .floatValue();
        return minimum + (1 - minimum) * fraction * fraction;
    }

    private SurfaceBloodClientConfig() {}

    private static ConfigValue<List<? extends String>> sounds(Builder b, String name, String comment,
        List<String> defaults) {
        return b.comment(comment)
            .defineListAllowEmpty(java.util.Arrays.asList(name), () -> defaults, id -> {
                if (id instanceof String text && ResourceIds.valid(text.trim())) {
                    return true;
                }

                return false;
            });
    }

    private static List<String> range(String name, int count) {
        List<String> ids = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            ids.add("ukycorpses:" + name + "_" + i);
        }

        return ids;
    }

    public static SurfaceBloodClientConfig.Preset preset() {
        return (SurfaceBloodClientConfig.Preset) PRESET.get();
    }

    public static float volume(DoubleValue sound) {
        return SOUNDS.get() ? (float) ((Double) VOLUME.get() * (Double) sound.get()) : 0.0F;
    }

    static {
        Builder b = new Builder();
        b.push("look");
        STYLE = b.comment(
            "PIXEL: stains at vanilla texel density, snapped to the block's pixel grid. SMOOTH: soft, detailed stains at any angle.")
            .defineEnum("style", StainSprites.Style.PIXEL);
        PALETTE = b.comment("Recolours red blood. NATURAL keeps it red; CUSTOM uses custom_color and custom_dried.")
            .defineEnum("palette", SurfaceBloodClientConfig.Palette.NATURAL);
        CUSTOM_COLOR = b.comment("Fresh blood colour for the CUSTOM palette, as #RRGGBB.")
            .define("custom_color", "#2A6FB0");
        CUSTOM_DRIED = b.comment("What CUSTOM blood dries to, as #RRGGBB.")
            .define("custom_dried", "#16324D");
        ENTITY_BLACKLIST = b.comment(
            "Creatures that never bleed or take wounds, by id (\"minecraft:zombie\") or tag (\"#minecraft:raiders\").")
            .defineListAllowEmpty(java.util.Arrays.asList("entity_blacklist"), java.util.Collections::emptyList, id -> {
                if (id instanceof String text && ResourceIds.valid(
                    text.trim()
                        .replaceFirst("^#", ""))) {
                    return true;
                }

                return false;
            });
        SPECULAR = b.comment(
            new String[] {
                "Wet blood reflects light: the sky, and a glint of the sun or moon, on fresh pools and splats,",
                "fading as it dries. Under a shader pack, blood's textures get specular maps (LabPBR) for packs that read them." })
            .define("specular", false);
        SHADER_PACK_BRIGHTNESS = b
            .comment(
                new String[] {
                    "Brightens blood in the world (stains, drops, mist) while a shader pack is on. Packs light its",
                    "dark red as they would a dark block, which out of the sun leaves it near black. 1 for none." })
            .defineInRange("shader_pack_brightness", 1.6, 1.0, 2.5);
        b.pop();
        b.push("performance");
        QUALITY = b.comment("How many droplets each hit throws. LOW suits weak machines and big fights.")
            .defineEnum("quality", SurfaceBloodClientConfig.Quality.HIGH);
        MAX_DROPLETS = b.comment("Droplets in the air at once. The oldest land early once this is reached.")
            .defineInRange("max_droplets", 2048, 64, 16384);
        MAX_STAINS = b.comment("Stains kept in the world at once. The oldest go first once this is reached.")
            .defineInRange("max_stains", 12000, 256, 100000);
        RENDER_DISTANCE = b.comment("Stains and droplets further than this many blocks away aren't drawn.")
            .defineInRange("render_distance", 64, 16, 256);
        b.pop();
        b.push("blood");
        PRESET = b.comment("MILD, REALISTIC or EXTREME: scales spray, bleeding, pools, wounds and footprints together.")
            .defineEnum("preset", SurfaceBloodClientConfig.Preset.REALISTIC);
        AMOUNT = b.comment("Scales how much blood every hit throws, on top of the preset.")
            .defineInRange("amount", 1.0, 0.0, 10.0);
        STAIN_LIFETIME = b.comment(
            "Полное время жизни пятна, секунды игрового времени. В последней трети срока оно плавно исчезает. Вода, дождь и лимит max_stains удаляют раньше.")
            .defineInRange("stain_lifetime", 600, 10, 86400);
        WEAK_HIT_DAMAGE = b.comment("Урон, начиная с которого объём крови полный. 1 единица = половина сердца.")
            .defineInRange("weak_hit_damage", 4.0, 0.1, 100.0);
        WEAK_HIT_MIN_SCALE = b
            .comment("Минимальная доля крови при слабом ударе: 0.1 = 10%. Смертельные удары не уменьшаются.")
            .defineInRange("weak_hit_min_scale", 0.1, 0.0, 1.0);
        DRY_TIME = b
            .comment("За сколько секунд свежая кровь высыхает и темнеет. Высыхание само по себе не удаляет пятно.")
            .defineInRange("dry_time", 180, 5, 3600);
        KEEP_STAINS = b.comment(
            "Сохранять пятна между посещениями мира. Пауза и выключенный сервер не расходуют таймер; ограничение stain_lifetime продолжает действовать.")
            .define("keep_stains", true);
        RUNS = b.comment("Heavy stains on walls run downwards.")
            .define("runs", true);
        DRIPS = b.comment("Drops gather and drip from wet ceilings and from the bottom edges of runs.")
            .define("drips", true);
        POOLS = b.comment("Blood that keeps dripping onto one spot collects into a spreading pool.")
            .define("pools", true);
        DEATH_POOLS = b.comment("A pool spreads under creatures that die of a bleeding wound.")
            .define("death_pools", false);
        POOL_SIZE = b.comment("Scales how far pools spread.")
            .defineInRange("pool_size", 1.0, 0.1, 4.0);
        DEATH_POOL_SIZE = b.comment("Scales the pool a body leaves, when death_pools is on.")
            .defineInRange("death_pool_size", 1.0, 0.1, 4.0);
        SURFACES = b.comment(
            "Surfaces take blood differently: wool, wood, dirt and sand soak it up, glass and ice make it bead and run, snow turns pink.")
            .define("surfaces", true);
        SOAK_THROUGH = b.comment(
            "Blood on wool, planks, logs, hay and other absorbent blocks soaks through to the far side of the block over time, and drips from it if that's a ceiling. Needs surfaces.")
            .define("soak_through", true);
        SOAK_TIME = b.comment(
            "Seconds before soaked-in blood starts showing on the far side of the block. It takes about as long again to come all the way through.")
            .defineInRange("soak_time", 20, 1, 600);
        FOOTPRINTS = b.comment(
            "Walking through wet blood leaves bloody footprints for a few steps: many from a pool, fewer from a splatter.")
            .define("footprints", true);
        FOOTPRINT_STEPS = b.comment(
            "Множитель количества следов после наступания в свежую кровь. Лужа даёт длиннее цепочку, отдельное пятно — короче.")
            .defineInRange("footprint_steps", 4.0, 0.1, 10.0);
        FOOTPRINT_SHAPE = b.comment(
            "SHOE: shoe prints with sole and heel, paws and hooves. SQUARE: square prints the shape of a player's or mob's blocky leg.")
            .defineEnum("footprint_shape", SurfaceBloodClientConfig.PrintShape.SQUARE);
        FOOTPRINT_FADE = b.comment(
            "Время жизни следов, секунды. В последней трети они плавно исчезают. 0 — использовать общий stain_lifetime.")
            .defineInRange("footprint_fade", 30, 0, 3600);
        WATER = b.comment("Blood in water spreads into red clouds instead of vanishing, and hisses on lava.")
            .define("water", true);
        b.pop();
        b.push("hit");
        SPRAY = b.comment("Создавать летящие брызги при попадании; их направление и форма зависят от удара.")
            .define("spray", true);
        BURST = b.comment("The splash sprite drawn right at the wound for the blink after a hit, on top of the spray.")
            .define("burst", false);
        MIST = b.comment("A puff of fine mist that hangs at the wound.")
            .define("mist", true);
        GOUTS = b.comment("Big hits throw a few heavy gouts that land as large splats.")
            .define("gouts", true);
        STREAM = b.comment("Blood keeps pumping out of the wound for a few ticks after the hit.")
            .define("stream", true);
        SPRAY_SPEED = b.comment("Множитель скорости и дальности брызг.")
            .defineInRange("spray_speed", 1.0, 0.1, 3.0);
        DROPLET_SIZE = b.comment("Множитель размера летящих капель.")
            .defineInRange("droplet_size", 1.0, 0.25, 3.0);
        DROPLET_BRIGHTNESS = b
            .comment("Brightness of blood in the air (spray, drips, falling drops) next to blood that has landed.")
            .defineInRange("droplet_brightness", 0.8, 0.3, 1.5);
        b.pop();
        b.push("bleeding");
        BLEEDING = b
            .comment("Wounded creatures bleed for a while after each hit, leaving a trail of drops as they move.")
            .define("bleeding", true);
        BLEED_HEALTH = b.comment("Below this fraction of max health creatures keep bleeding slowly. 0 turns it off.")
            .defineInRange("bleed_health", 0.35, 0.0, 1.0);
        BLEED_AMOUNT = b.comment("Scales how much a wound bleeds.")
            .defineInRange("bleed_amount", 1.0, 0.0, 4.0);
        BLEED_TIME = b.comment("Scales how long a wound keeps bleeding.")
            .defineInRange("bleed_time", 1.0, 0.1, 5.0);
        b.pop();
        b.push("wounds");
        WOUNDS = b.comment("Раны и кровь на коже игрока и мобов в месте попадания.")
            .define("wounds", true);
        ARMOUR_WOUNDS = b.comment("Hits that land on armour mark the armour instead.")
            .define("armour", true);
        WOUND_SIZE = b.comment("Scales the size of wounds.")
            .defineInRange("size", 1.0, 0.25, 3.0);
        WOUND_DRIPS = b.comment("Blood runs down the body from wounds.")
            .define("drips", true);
        SPATTER = b.comment("Летящие капли пачкают кожу и броню тех, на кого попали, включая атакующего игрока.")
            .define("spatter", true);
        SPATTER_LASTS = b
            .comment(
                new String[] { "Seconds blood that's landed on someone stays before wearing off, once they're unhurt.",
                    "Water rinses it at once." })
            .defineInRange("spatter_lasts", 120, 5, 86400);
        b.pop();
        b.push("weapons");
        GEAR = b
            .comment(
                "Кровь на оружии и инструментах после попадания. Только внешний вид: урон и прочность не меняются.")
            .define("blood", true);
        GEAR_DROPPED = b
            .comment("A bloody weapon that's dropped keeps its blood on the ground, and when picked up again.")
            .define("dropped", true);
        GEAR_COVERAGE = b.comment("Множитель площади загрязнения оружия за один удар.")
            .defineInRange("coverage", 1.0, 0.1, 4.0);
        GEAR_OPACITY = b.comment("Непрозрачность крови на оружии: 0 — невидима, 1 — полностью видима.")
            .defineInRange("opacity", 1.0, 0.0, 1.0);
        GEAR_HOTBAR = b.comment("Показывать кровь также на иконке предмета в инвентаре и панели быстрого доступа.")
            .define("hotbar", true);
        GEAR_DRIPS = b.comment("После попадания с кончика оружия некоторое время капает кровь.")
            .define("drips", true);
        GEAR_FADE = b
            .comment("Сколько секунд кровь на оружии плавно стирается после lasts. Вода и дождь смывают раньше.")
            .defineInRange("fade", 30, 1, 3600);
        GEAR_LASTS = b.comment("Секунды до начала плавного исчезновения крови с оружия после последнего попадания.")
            .defineInRange("lasts", 300, 5, 86400);
        b.pop();
        b.push("sounds");
        SOUNDS = b.comment("Blood makes sounds at all.")
            .define("sounds", true);
        VOLUME = b.comment("Volume of all blood sounds.")
            .defineInRange("volume", 1.0, 0.0, 2.0);
        HIT_VOLUME = b.comment("Volume of the hit itself.")
            .defineInRange("hit", 0.0, 0.0, 2.0);
        SPURT_VOLUME = b.comment("Volume of blood forced out of deep wounds.")
            .defineInRange("spurt", 0.0, 0.0, 2.0);
        SPLAT_VOLUME = b.comment("Volume of drops landing.")
            .defineInRange("splat", 1.0, 0.0, 2.0);
        DRIP_VOLUME = b.comment("Volume of drips from ceilings, runs and weapons.")
            .defineInRange("drip", 0.5, 0.0, 2.0);
        STEP_VOLUME = b.comment("Volume of stepping in pools and of each bloody footprint.")
            .defineInRange("step", 0.0, 0.0, 2.0);
        SIZZLE_VOLUME = b.comment("Volume of blood hissing on lava.")
            .defineInRange("sizzle", 1.0, 0.0, 2.0);
        b.comment(
            new String[] {
                "Which sounds each thing plays, one picked at random. Any sound id works, from SurfaceBlood (ukycorpses_blood:hit_1 to",
                "hit_8, spurt_1 to 5, splat_1 to 6, drip_1 to 4, drip_1_muffled, drip_2_muffled, squelch_1 to 5), vanilla or other mods.",
                "An empty list is silent. In game, /ukycorpses_blood sound <id> plays one and /ukycorpses_blood sounds <thing> a pick." });
        b.push("pick");
        HIT_SOUNDS = sounds(b, "hit", "A hit landing.", range("hit", 8));
        SPURT_SOUNDS = sounds(b, "spurt", "Blood forced out of a deep wound.", range("spurt", 5));
        SPLAT_SOUNDS = sounds(b, "splat", "A drop splatting onto something.", range("splat", 6));
        DRIP_SOUNDS = sounds(
            b,
            "drip",
            "A drop falling onto a floor or into a pool.",
            java.util.Arrays.asList("ukycorpses:drip_1", "ukycorpses:drip_2"));
        SOFT_DRIP_SOUNDS = sounds(
            b,
            "soft_drip",
            "A drop that only fell a little way, muffled and quieter.",
            java.util.Arrays.asList("ukycorpses:drip_1_muffled", "ukycorpses:drip_2_muffled"));
        WATER_DRIP_SOUNDS = sounds(
            b,
            "water_drip",
            "A drop falling into water.",
            java.util.Arrays.asList("ukycorpses:drip_3", "ukycorpses:drip_4"));
        STEP_SOUNDS = sounds(
            b,
            "step",
            "Stepping in a pool.",
            java.util.Arrays.asList("ukycorpses:drip_1_muffled", "ukycorpses:drip_2_muffled"));
        SQUELCH_SOUNDS = sounds(
            b,
            "squelch",
            "Each step that leaves a bloody footprint, quieter as the blood wears off.",
            range("squelch", 5));
        SIZZLE_SOUNDS = sounds(
            b,
            "sizzle",
            "Blood hissing on lava.",
            java.util.Arrays.asList("minecraft:block.lava.extinguish"));
        b.pop();
        b.pop();
        b.push("screen");
        VIGNETTE = b.comment("Blood creeps in from the screen edges when you're hurt.")
            .define("vignette", true);
        VIGNETTE_STRENGTH = b.comment("How strong that is.")
            .defineInRange("vignette_strength", 1.0, 0.0, 2.0);
        b.pop();
        b.push("compat");
        GUESS_HITS = b
            .comment("On servers without SurfaceBlood, work out hits from vanilla damage packets so blood still shows.")
            .define("guess_hits", true);
        STUMPS = b.comment(
            new String[] { "Blood spurts from severed parts: in time with the heart on the living,",
                "a last beat or two and an ebbing gush on a body that just died, and a slow ooze from an old corpse." })
            .define("stumps", true);
        STUMP_AMOUNT = b.comment("Scales how much blood a stump throws, on the body and on the limb.")
            .defineInRange("stump_amount", 1.0, 0.0, 4.0);
        STUMP_TIME = b.comment("Scales how long a stump keeps bleeding.")
            .defineInRange("stump_time", 1.0, 0.1, 5.0);
        STUMP_SPEED = b.comment("Scales how fast, and so how far, blood sprays out of a stump.")
            .defineInRange("stump_speed", 1.0, 0.1, 3.0);
        STUMP_LIMBS = b.comment("The limb that came off bleeds from its cut end too, not just the body it came off.")
            .define("stump_limbs", true);
        b.pop();
        SPEC = b.build();
    }

    public static enum Palette {

        NATURAL(0, 0),
        GREEN(4164114, 1980940),
        BLUE(2052008, 1189194),
        PURPLE(6954892, 3019328),
        BLACK(1971993, 854539),
        CUSTOM(0, 0);

        public final int color;
        public final int dried;

        private Palette(int color, int dried) {
            this.color = color;
            this.dried = dried;
        }
    }

    public static enum Preset {

        MILD(0.45F, 0.4F, 0.6F, 0.75F, 0.5F),
        REALISTIC(1.0F, 1.0F, 1.0F, 1.0F, 1.0F),
        EXTREME(2.0F, 2.2F, 1.6F, 1.3F, 1.6F);

        public final float spray;
        public final float bleed;
        public final float pool;
        public final float wound;
        public final float prints;

        private Preset(float spray, float bleed, float pool, float wound, float prints) {
            this.spray = spray;
            this.bleed = bleed;
            this.pool = pool;
            this.wound = wound;
            this.prints = prints;
        }
    }

    public static enum PrintShape {
        SHOE,
        SQUARE;
    }

    public static enum Quality {

        LOW(0.4F),
        MEDIUM(0.7F),
        HIGH(1.0F);

        public final float droplets;

        private Quality(float droplets) {
            this.droplets = droplets;
        }
    }
}
