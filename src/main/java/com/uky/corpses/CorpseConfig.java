package com.uky.corpses;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** config/uky/corpses.cfg — next to ukyui's own config. Also edited in game (client.ConfigGui), then read again. */
public final class CorpseConfig {

    public static final String[] MODES = { "easy", "standard", "hardcore" };
    // keepXpPercent, durabilityLossPercent, itemLossPercent per mode
    private static final int[][] DEFAULTS = { { 100, 0, 0 }, { 50, 10, 0 }, { 0, 25, 25 } };

    public static int ownerLockMinutes;
    public static boolean restoreSlots;
    public static boolean tape, tapeGone;
    /** Seconds anyone but the owner holds right-click to search a body; blows that break one apart. */
    public static int searchSeconds, breakBlows;
    /** Sneak-right-click drags an entity body; a broken body leaves bones (and flesh). */
    public static boolean drag, bones, flesh;

    public static boolean downed;
    /** A friend gets the downed up with an empty hand; mobs leave the downed alone; the downed can't see. */
    public static boolean friendRevive, mobsIgnoreDowned, downedBlindness;
    /** Health while down (half hearts); how fast the downed crawl, percent of walking. */
    public static int downedHealth, crawlSpeed;
    public static int bleedSeconds;
    public static int aloneSeconds;
    public static int friendRadius;
    public static int reviveSeconds;
    public static int reviveHealth;

    public static String mode;
    public static int keepXpPercent;
    public static int durabilityLossPercent;
    public static int itemLossPercent;

    private static Configuration cfg;

    static void load(File file) {
        cfg = new Configuration(file);
        sync();
    }

    /** The file as loaded; the settings screen edits its properties in place. */
    public static Configuration config() {
        return cfg;
    }

    /** The file read from disk again (/corpses reload): edited by hand while the game runs. */
    public static void reload() {
        cfg.load();
        sync();
    }

    /** Reads every setting again: at start, and after the settings screen saved. */
    public static void sync() {
        Configuration c = cfg;
        order.clear();

        Corpses.config(c); // corpse: enabled, asBlock, despawn

        Section access = section("corpse.access", "Кто и как открывает тело.");
        ownerLockMinutes = access.num(
            "ownerLockMinutes",
            15,
            -1,
            100000,
            "Сколько минут тело открывает только владелец (и креатив). 0 — сразу всем, -1 — только владелец, навсегда.");
        searchSeconds = access.num(
            "searchSeconds",
            4,
            0,
            60,
            "Сколько секунд идёт обыск чужого тела после клика (не отходи). 0 — сразу.");
        restoreSlots = access
            .on("restoreSlots", true, "Владелец получает вещи в те же слоты: броня надета, хотбар как был.");

        Section handling = section("corpse.handling", "Что можно делать с телом руками.");
        drag = handling
            .on("drag", true, "Shift+ПКМ по телу — тащишь, пока держишь Shift. Тело-блок становится сущностью.");
        breakBlows = handling.num("breakBlows", 6, 1, 100, "Сколько ударов разбивают тело (меч или топор — за два).");
        bones = handling.on("bones", true, "Разбитое тело оставляет кости.");
        flesh = handling.on("flesh", true, "Разбитое тело оставляет гнилую плоть (у обгоревшего её нет).");

        Section tapes = section("corpse.tape", "Кассета: запись смерти с причиной, координатами и списком вещей.");
        tape = tapes.on("enabled", true, "Выдавать кассету после возрождения.");
        tapeGone = tapes.on("goneWhenTaken", true, "Кассета исчезает, когда владелец забрал из тела всё.");

        Section penalty = section(
            "penalty",
            "Штраф за смерть. mode выбирает подкатегорию ниже, остальные лежат про запас.");
        mode = penalty.pick("mode", "standard", MODES, "easy, standard или hardcore.");
        for (int i = 0; i < MODES.length; i++) {
            Section m = section("penalty." + MODES[i], "Режим " + MODES[i] + ".");
            int xp = m.num("keepXpPercent", DEFAULTS[i][0], 0, 100, "Сколько процентов опыта остаётся в теле.");
            int dur = m.num(
                "durabilityLossPercent",
                DEFAULTS[i][1],
                0,
                100,
                "Сколько % прочности теряют инструменты и броня.");
            int loss = m.num("itemLossPercent", DEFAULTS[i][2], 0, 100, "Шанс каждого стака сгореть при смерти, %.");
            if (MODES[i].equals(mode)) {
                keepXpPercent = xp;
                durabilityLossPercent = dur;
                itemLossPercent = loss;
            }
        }

        Section d = section("downed", "Нокдаун: смертельный удар не убивает сразу — игрок падает и ждёт помощи.");
        downed = d.on("enabled", true, "false — умирать сразу, как в ванили.");
        downedHealth = d.num("health", 4, 1, 20, "Здоровье в нокдауне, в половинках сердца.");
        crawlSpeed = d.num("crawlSpeedPercent", 30, 0, 100, "Скорость ползком, % от ходьбы. 0 — не двигается.");
        downedBlindness = d.on("blindness", true, "Упавший почти ничего не видит.");
        mobsIgnoreDowned = d.on("mobsIgnore", true, "Мобы не трогают упавшего. false — добивают.");

        Section timer = section("downed.timer", "Сколько можно пролежать.");
        bleedSeconds = timer.num("bleedSeconds", 35, 5, 3600, "Если рядом есть кто-то живой.");
        aloneSeconds = timer.num("aloneSeconds", 5, 1, 3600, "Если рядом никого. Пришёл друг — отсчёт сбрасывается.");
        friendRadius = timer.num("friendRadius", 32, 1, 256, "Радиус, в котором другой игрок считается рядом, блоков.");

        Section revive = section("downed.revive", "Подъём упавшего.");
        friendRevive = revive
            .on("byHand", true, "Друг поднимает ПКМ пустой рукой. false — только шприц и дефибриллятор.");
        reviveSeconds = revive.num("seconds", 18, 1, 120, "Сколько секунд поднимать.");
        reviveHealth = revive.num("health", 6, 1, 20, "Здоровье после подъёма, в половинках сердца.");
        com.uky.corpses.medicine.Medicine.shockAfterRevive = revive.on("shockAfter", true, "После подъёма — шок.");

        com.uky.corpses.deathcam.DeathCam.config(c);
        com.uky.corpses.medicine.Medicine.config(c);
        com.uky.corpses.blood.Blood.config(c);
        com.uky.corpses.movement.Crouch.config(c);

        dress(c);

        if (c.hasChanged()) c.save();
    }

    /**
     * Categories in the order they were read, each with its settings in that order: how the file and screen show them.
     */
    private static final Map<String, List<String>> order = new LinkedHashMap<>();

    public static Section section(String name, String comment) {
        cfg.setCategoryComment(name, comment);
        order.computeIfAbsent(name, k -> new ArrayList<>());
        return new Section(name);
    }

    /** One category being read. */
    public static final class Section {

        private final String name;

        private Section(String name) {
            this.name = name;
        }

        private void add(String key) {
            order.get(name)
                .add(key);
        }

        public boolean on(String key, boolean def, String comment) {
            add(key);
            return cfg.getBoolean(key, name, def, comment);
        }

        public int num(String key, int def, int min, int max, String comment) {
            add(key);
            return cfg.getInt(key, name, def, min, max, comment);
        }

        public float num(String key, float def, float min, float max, String comment) {
            add(key);
            return cfg.getFloat(key, name, def, min, max, comment);
        }

        public String pick(String key, String def, String[] values, String comment) {
            add(key);
            return cfg.getString(key, name, def, comment, values);
        }

        public Property prop(String key, boolean def, String comment) {
            add(key);
            return cfg.get(name, key, def, comment);
        }
    }

    /**
     * Names for the settings screen (ukycorpses.config.(category)[.(property)]; the three penalty modes share theirs),
     * leftovers from old versions hidden from it (whole categories nothing reads any more: gone), and what only takes
     * effect after a restart marked so.
     */
    private static void dress(Configuration c) {
        for (String name : new ArrayList<>(c.getCategoryNames())) {
            if (!c.hasCategory(name)) continue; // went with its parent
            ConfigCategory cat = c.getCategory(name);
            // asked for by this version (Property.wasRead only says it came from the file), or holds what was
            boolean read = order.containsKey(name);
            for (String o : order.keySet()) read |= o.startsWith(name + ".");
            if (!read) {
                c.removeCategory(cat);
                continue;
            }
            cat.setLanguageKey("ukycorpses.config." + name);
            String key = name.startsWith("penalty.") ? "penalty" : name; // the modes share their names
            if (order.containsKey(name)) c.setCategoryPropertyOrder(name, order.get(name));
            boolean ids = name.equals("medicine.potions");
            if (ids) cat.setShowInGui(false); // only for an id clash with another mod: the file is enough
            for (Property p : new ArrayList<>(cat.getOrderedValues())) {
                if (!order.getOrDefault(name, Collections.emptyList())
                    .contains(p.getName())) { // left from an older version
                    cat.remove(p.getName());
                    continue;
                }
                p.setLanguageKey("ukycorpses.config." + key + "." + p.getName());
                if (ids) p.setRequiresMcRestart(true)
                    .setShowInGui(false);
                if (p.getName()
                    .equals("healthRegen")) p.setRequiresWorldRestart(true);
                if (p.getName()
                    .equals("chestLoot")) p.setRequiresMcRestart(true);
            }
        }
    }
}
