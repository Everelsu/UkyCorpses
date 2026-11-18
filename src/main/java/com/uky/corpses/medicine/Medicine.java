package com.uky.corpses.medicine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.FoodStats;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.uky.corpses.CorpseConfig;
import com.uky.corpses.Downed;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.blood.Blood;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import sun.misc.Unsafe;

/**
 * Medicine and first aid: bandages, a rag, a medkit, syringe and injector, a defibrillator, a club, an HP
 * scanner and nine pills, plus the six effects they and wounds cause. A hard hit sends adrenaline first and shock after
 * it; a very hard one (or the club) stuns: the player sits and can't move. Hunger and natural regeneration can be
 * turned off, as in UkyCorpses (regeneration is off by default there, and here).
 */
public final class Medicine {

    public static Potion BLEEDING, SHOCK, STUN, EUPHORIA, INVERSION, ADRENALINE;
    public static Item RAG, BANDAGE, MEDKIT, SCANNER, SYRINGE, INJECTOR, DEFIB, CLUB, TOXILIN, ICON;
    public static CreativeTabs tab;

    // config
    public static boolean shockAfterRevive, selfAdrenaline;

    public static boolean isAdrenaline(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof MedItem)) return false;
        MedItem.Kind kind = ((MedItem) stack.getItem()).kind;
        return kind == MedItem.Kind.SYRINGE || kind == MedItem.Kind.INJECTOR;
    }

    public static int adrenalineSlot(EntityPlayer player) {
        int best = -1;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            net.minecraft.item.ItemStack stack = player.inventory.mainInventory[i];
            if (isAdrenaline(stack) && (best < 0
                || stack.getMaxItemUseDuration() < player.inventory.mainInventory[best].getMaxItemUseDuration()))
                best = i;
        }
        return best;
    }

    public static boolean maySelfAdrenaline(EntityPlayer player) {
        return selfAdrenaline && Downed.is(player) && isAdrenaline(player.getCurrentEquippedItem());
    }

    static boolean stun, shockOnHit, laced, healthRegen, hunger;
    static int bleedInterval, stunDamage, shockDamage, clubStunChance;
    /** Seconds: bleeding from a wound, stun from a hard hit / from the bat, immune after one, shock, adrenaline. */
    public static int bleedSeconds, stunHitSeconds, clubStunSeconds, stunImmuneSeconds, shockSeconds, adrenalineSeconds;
    /** The effects themselves: how much shock slows and adrenaline speeds up (percent); what makes the head swim. */
    public static int shockSlow, adrenalineSpeed;
    /**
     * Whole effects switched off: nothing puts them on, and one put on anyway (a command, another mod) is taken off.
     */
    public static boolean shockOn, adrenalineOn, euphoriaOn;
    public static boolean shockNausea, stunNausea, stunSits, pillNausea, inversion, euphoriaShader;
    /** Per item name: ticks it's held to apply, half hearts it heals. Read by MedItem. */
    static final Map<String, Integer> useTicks = new HashMap<>();
    static final Map<String, Float> heals = new HashMap<>();
    static float bleedDamage;
    /** On someone else: treating them, feeding them a pill, and their fighting it off (a hit drops the pill). */
    public static boolean treatOthers, forceFeed, resist, resistDrops;
    /** Bat, syringe, injector and scanner in dungeon and other chests (read once, at start). */
    static boolean chestLoot;
    static int forceFeedTicks;
    static float othersRange;
    /** Blocks; the scanner label is drawn this far. */
    public static int scannerRange;
    private static final int[] ids = new int[6];

    private static final Random rand = new Random();
    /** Hit hard, adrenaline running: shock comes when it ends, unless protected by then. */
    private static final Set<UUID> pending = new HashSet<>();
    private static final Map<UUID, Integer> protection = new HashMap<>();
    private static final Map<UUID, Float> lastHp = new HashMap<>();
    private static final Map<UUID, EntitySeat> seats = new HashMap<>();
    /** {x, y, z, yaw, pitch} where a stunned player sat down. */
    private static final Map<UUID, double[]> anchors = new HashMap<>();
    /** Bleeding ticks wait here for the end of the server tick. */
    private static final List<EntityLivingBase> bleeders = new ArrayList<>();

    public static void config(Configuration c) {
        CorpseConfig.section("wounds", "Раны и состояния. У каждого свой выключатель enabled.");
        selfAdrenaline = CorpseConfig.section("medicine", "Лекарства и помощь.")
            .on(
                "selfAdrenaline",
                true,
                "Разрешить укол адреналина себе в нокдауне: клавиша G находит шприц или инжектор в инвентаре. Время применения — medicine.timing.");

        CorpseConfig.Section b = CorpseConfig
            .section("wounds.bleeding", "Кровотечение: сильный удар, взрыв или выстрел.");
        com.uky.corpses.blood.Blood.bleeding = b.on("enabled", true, "false — кровотечения нет вовсе.");
        bleedSeconds = b.num("seconds", 60, 5, 3600, "Сколько секунд длится, если не перевязать.");
        bleedInterval = b.num("intervalTicks", 70, 5, 1200, "Раз в сколько тиков бьёт (20 тиков = 1 с).");
        bleedDamage = b.num("damage", 1F, 0.5F, 20F, "Урон за раз, в половинках сердца.");

        CorpseConfig.Section st = CorpseConfig.section("wounds.stun", "Оглушение: очень сильный удар или бита.");
        stun = st.on("enabled", true, "false — оглушения нет вовсе.");
        stunDamage = st.num("damage", 12, 1, 100, "От какого урона за тик оглушает, в половинках сердца.");
        stunHitSeconds = st.num("hitSeconds", 5, 1, 60, "Сколько секунд оглушает сильный удар.");
        clubStunChance = st.num("clubChance", 25, 0, 100, "Шанс оглушить битой, %. В спину — вдвое выше.");
        clubStunSeconds = st.num("clubSeconds", 3, 1, 60, "Сколько секунд оглушает бита.");
        stunImmuneSeconds = st
            .num("immuneSeconds", 15, 0, 600, "Сколько секунд после оглушения нельзя оглушить снова.");
        stunSits = st.on("sits", true, "Оглушённый садится на землю. false — только сильно замедлен.");
        stunNausea = st.on("nausea", true, "Оглушённого мутит.");

        CorpseConfig.Section sh = CorpseConfig
            .section("wounds.shock", "Шок: когда кончается адреналин после удара или подъёма.");
        shockOn = sh.on("enabled", true, "false — шока нет вовсе.");
        shockOnHit = sh.on("onHit", true, "Сильный удар даёт адреналин, а за ним шок. Инжектор защищает.");
        shockDamage = sh.num("damage", 6, 1, 100, "От какого урона за тик начинается, в половинках сердца.");
        shockSeconds = sh.num("seconds", 10, 1, 300, "Сколько секунд длится.");
        shockSlow = sh.num("slowPercent", 30, 0, 90, "Насколько замедляет, %.");
        shockNausea = sh.on("nausea", true, "Шок мутит.");

        CorpseConfig.Section ad = CorpseConfig
            .section("wounds.adrenaline", "Адреналин: от сильного удара, шприца и инжектора.");
        adrenalineOn = ad.on("enabled", true, "false — адреналина нет вовсе.");
        adrenalineSeconds = ad
            .num("seconds", 7, 1, 300, "Сколько секунд после удара и шприца (у инжектора свои 27 с).");
        adrenalineSpeed = ad.num("speedPercent", 35, 0, 200, "Насколько ускоряет, %. 0 — без ускорения.");

        CorpseConfig.Section m = CorpseConfig.section("medicine", "Лекарства и медицинские предметы.");
        heals.put("bandage", (float) m.num("bandageHeal", 3, 1, 40, "Сколько лечит бинт, в половинках сердца."));
        heals.put("medkit", (float) m.num("medkitHeal", 10, 1, 40, "Сколько лечит аптечка, в половинках сердца."));
        chestLoot = m.on(
            "chestLoot",
            true,
            "Бита, шприц, инжектор и сканер попадаются в сундуках данжей, шахт, крепостей, храмов и кузниц. Нужен перезапуск.");
        scannerRange = m
            .num("scannerRange", 12, 2, 64, "Дальность подписей здоровья у сканера, блоков. Только клиент.");

        CorpseConfig.Section u = CorpseConfig
            .section("medicine.timing", "Сколько секунд держать ПКМ, чтобы применить.");
        useTicks.put("bandage", seconds(u, "bandage", 2.0, "Бинт."));
        useTicks.put("medkit", seconds(u, "medkit", 5.0, "Аптечка."));
        useTicks.put("rag", seconds(u, "rag", 3.5, "Тряпка к ране."));
        useTicks.put("adrenaline_syringe", seconds(u, "syringe", 4.0, "Шприц с адреналином упавшему."));
        useTicks.put("adrenaline_injector", seconds(u, "injector", 1.0, "Инжектор."));

        CorpseConfig.Section o = CorpseConfig.section("medicine.others", "Помощь другим: ПКМ предметом по игроку.");
        treatOthers = o.on("treat", true, "Бинт, аптечка, тряпка и инжектор работают на другом игроке.");
        forceFeed = o.on("forceFeed", true, "Таблетку можно скормить другому игроку, даже против воли.");
        forceFeedTicks = seconds(o, "forceFeedSeconds", 3.0, "Сколько секунд кормить таблеткой другого.");
        resist = o.on("resist", true, "Цель может отбиться: удар по кормящему срывает таблетку или укол.");
        resistDrops = o.on("resistDrops", true, "Сорванная таблетка (или инжектор) выпадает на землю.");
        othersRange = o
            .num("range", 3.5F, 1F, 8F, "Дальше скольких блоков цель уходит из рук: применение прерывается.");

        CorpseConfig.Section d = CorpseConfig.section("medicine.drugs", "Таблетки и их побочки.");
        euphoriaOn = d.on("euphoria", true, "Эйфория от антидепрессанта. false — её нет.");
        euphoriaShader = d.on("euphoriaShader", true, "Эйфория красит экран. Только клиент.");
        inversion = d.on("inversion", true, "Морфинол переворачивает управление.");
        pillNausea = d.on("pillNausea", true, "Побочная тошнота от таблеток.");
        laced = d.on("lacedFood", true, "Токсилин + еда = отравленная еда.");

        CorpseConfig.Section p = CorpseConfig
            .section("medicine.potions", "Номера эффектов. Только при конфликте с другим модом; нужен перезапуск.");
        String[] names = { "Bleeding", "Shock", "Stun", "Euphoria", "Inversion", "Adrenaline" };
        for (int i = 0; i < 6; i++)
            ids[i] = p.num(names[i], 40 + i, 32, 127, "Занят чужим — берётся следующий свободный.");

        CorpseConfig.Section g = CorpseConfig.section("gameplay", "Выживание в духе UkyCorpses.");
        healthRegen = g
            .on("healthRegen", true, "Регенерация от сытости. Выкл: лечат бинты и аптечки. При загрузке мира.");
        hunger = g.on("hunger", true, "Голод. false — сытость всегда полная.");
    }

    public static void preInit() {
        UkyCorpses.net.registerMessage(MsgFeed.Handler.class, MsgFeed.class, 10, Side.CLIENT);
        growPotions();
        BLEEDING = new Fx(freeId(ids[0]), "bleeding", true, 0xCC0000, true);
        SHOCK = new Fx(freeId(ids[1]), "shock", true, 0x66CCFF, true);
        STUN = new Fx(freeId(ids[2]), "stun", true, 0x666677, true);
        EUPHORIA = new Fx(freeId(ids[3]), "euphoria", false, 0xFFAAFF, true);
        INVERSION = new Fx(freeId(ids[4]), "inversion", true, 0x555577, true);
        ADRENALINE = new Fx(freeId(ids[5]), "adrenaline", false, 0xFFAA00, true);

        tab = new CreativeTabs("ukycorpses") {

            @Override
            @SideOnly(Side.CLIENT)
            public Item getTabIconItem() {
                return ICON;
            }
        };
        // stacks: cheap and small things stack high, what saves a life stays scarce
        BANDAGE = reg("bandage", MedItem.Kind.HEAL, 8, 40, 3);
        MEDKIT = reg("medkit", MedItem.Kind.HEAL, 4, 100, 10);
        SCANNER = reg("hpscanner", MedItem.Kind.SCANNER, 1, 0, 0);
        SYRINGE = reg("adrenaline_syringe", MedItem.Kind.SYRINGE, 2, 80, 0);
        INJECTOR = reg("adrenaline_injector", MedItem.Kind.INJECTOR, 4, 20, 0);
        RAG = reg("rag", MedItem.Kind.RAG, 16, 70, 0);
        DEFIB = reg("defibrillator", MedItem.Kind.DEFIB, 1, 0, 0);
        CLUB = reg("club", MedItem.Kind.CLUB, 1, 0, 0);
        TOXILIN = pill("toxilin");
        for (String n : new String[] { "morphinol", "flyuk", "nalgezin", "stimulin", "paracetol", "kserin", "placebo",
            "antidepressant" }) pill(n);
        ICON = reg("pills_icon", MedItem.Kind.PLAIN, 1, 0, 0);

        EntityRegistry.registerModEntity(EntitySeat.class, "seat", 10, UkyCorpses.instance, 64, 20, false);
    }

    public static void init() {
        recipes();
        if (chestLoot) loot();
        Events e = new Events();
        MinecraftForge.EVENT_BUS.register(e);
        FMLCommonHandler.instance()
            .bus()
            .register(e);
    }

    private static int seconds(CorpseConfig.Section s, String key, double def, String comment) {
        return (int) Math.round(s.num(key, (float) def, 0.05F, 60F, comment) * 20);
    }

    private static Item reg(String name, MedItem.Kind kind, int stack, int use, float heal) {
        MedItem i = new MedItem(name, kind, stack, use, heal);
        GameRegistry.registerItem(i, name);
        return i;
    }

    private static Item pill(String name) {
        return reg(name, MedItem.Kind.PILL, 16, 32, 0);
    }

    /** Potion.potionTypes is 32 long and final. Ours go past it, so it is swapped for a longer copy. */
    private static void growPotions() {
        if (Potion.potionTypes.length >= 64) return;
        try {
            Field f = ReflectionHelper.findField(Potion.class, "potionTypes", "field_76425_a");
            Field u = Unsafe.class.getDeclaredField("theUnsafe");
            u.setAccessible(true);
            Unsafe unsafe = (Unsafe) u.get(null);
            unsafe.putObjectVolatile(
                unsafe.staticFieldBase(f),
                unsafe.staticFieldOffset(f),
                Arrays.copyOf(Potion.potionTypes, 64));
        } catch (Exception ex) {
            UkyCorpses.LOG.error("Could not grow Potion.potionTypes; effects need free ids below 32", ex);
        }
    }

    private static int freeId(int want) {
        Potion[] t = Potion.potionTypes;
        for (int i = Math.min(want, t.length); i < t.length; i++) if (t[i] == null) return i;
        for (int i = 1; i < t.length; i++) if (t[i] == null) return i;
        throw new IllegalStateException("No free potion id for ukycorpses");
    }

    // ---- recipes: the shapes are UkyCorpses, with 1.7.10 stand-ins for what it doesn't have ----

    private static void recipes() {
        ItemStack wool = new ItemStack(Blocks.wool, 1, 0);
        add(new ShapelessOreRecipe(new ItemStack(RAG), wool));
        add(new ShapelessOreRecipe(new ItemStack(BANDAGE), wool, wool, RAG));
        add(new ShapelessOreRecipe(new ItemStack(MEDKIT), wool, wool, BANDAGE, BANDAGE));
        // copper where a mod brings it, gold where nothing does
        for (String c : new String[] { "ingotCopper", "ingotGold" }) add(
            new ShapedOreRecipe(
                new ItemStack(DEFIB),
                "IRI",
                "CMC",
                "IRI",
                'I',
                "ingotIron",
                'R',
                "dustRedstone",
                'C',
                c,
                'M',
                MEDKIT));
        add(
            new ShapedOreRecipe(
                new ItemStack(SCANNER),
                "IPI",
                "RGR",
                "III",
                'I',
                "ingotIron",
                'P',
                "paneGlass",
                'R',
                "dustRedstone",
                'G',
                "ingotGold"));
        pillRecipe("antidepressant", "dyePink", Items.cookie);
        pillRecipe("flyuk", "dyeYellow", Items.feather);
        pillRecipe("kserin", "dyePurple", Items.ender_pearl); // no chorus fruit in this version
        pillRecipe("morphinol", "dyeWhite", new ItemStack(Blocks.red_flower, 1, 0));
        pillRecipe("nalgezin", "dyeBlue", Blocks.brown_mushroom);
        pillRecipe("paracetol", "dyeLightBlue", Items.milk_bucket);
        pillRecipe("placebo", "dyeGray", Items.wheat);
        pillRecipe("stimulin", "dyeRed", Items.speckled_melon);
        pillRecipe("toxilin", "dyeGreen", Items.fermented_spider_eye);
        RecipeSorter.register(
            "ukycorpses:laced",
            LacedRecipe.class,
            RecipeSorter.Category.SHAPELESS,
            "after:minecraft:shapeless");
        add(new LacedRecipe());
    }

    private static void add(IRecipe r) {
        GameRegistry.addRecipe(r);
    }

    /**
     * What isn't crafted turns up in chests: the bat and the syringe in dungeons, mineshafts, strongholds, temples and
     * the smithy, the injector and the scanner (which also crafts) rarely. Vanilla's dungeon weights for scale: saddle,
     * iron, bread 10; golden apple 1.
     */
    private static void loot() {
        String[] where = { ChestGenHooks.DUNGEON_CHEST, ChestGenHooks.MINESHAFT_CORRIDOR,
            ChestGenHooks.STRONGHOLD_CORRIDOR, ChestGenHooks.STRONGHOLD_CROSSING, ChestGenHooks.PYRAMID_DESERT_CHEST,
            ChestGenHooks.PYRAMID_JUNGLE_CHEST, ChestGenHooks.VILLAGE_BLACKSMITH };
        Object[][] items = { { CLUB, 1, 1, 6 }, { SYRINGE, 1, 2, 6 }, { INJECTOR, 1, 1, 2 }, { SCANNER, 1, 1, 2 } };
        for (String c : where) for (Object[] i : items) ChestGenHooks.addItem(
            c,
            new WeightedRandomChestContent(new ItemStack((Item) i[0]), (Integer) i[1], (Integer) i[2], (Integer) i[3]));
    }

    private static void pillRecipe(String name, String dye, Object extra) {
        Item pill = GameRegistry.findItem(UkyCorpses.MODID, name);
        add(new ShapelessOreRecipe(new ItemStack(pill, 2), dye, extra, Items.sugar));
    }

    // ---- what the other subsystems call ----

    /** Downed.revive calls this after a player gets up. adrenaline: brought back with the syringe/injector. */
    public static void onRevived(EntityPlayerMP p, boolean adrenaline) {
        p.removePotionEffect(BLEEDING.id);
        p.removePotionEffect(STUN.id);
        lastHp.put(p.getUniqueID(), p.getHealth());
        if (adrenaline) p.addPotionEffect(new PotionEffect(ADRENALINE.id, adrenalineSeconds * 20, 0, true));
        if (shockAfterRevive) pending.add(p.getUniqueID());
    }

    public static void teleported(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        anchors.remove(id);
        EntitySeat old = seats.remove(id);
        if (old != null) old.setDead();
    }

    /** Fx: a bleeding tick is due. Applied at the end of the server tick. */
    static void bleed(EntityLivingBase e) {
        bleeders.add(e);
    }

    /** No shock for the next `ticks`. */
    static void protect(EntityPlayer p, int ticks) {
        protection.put(p.getUniqueID(), ticks);
    }

    /** World tick each player stops being immune to the next stun at: no stunning anyone over and over. */
    private static final Map<UUID, Long> stunFree = new HashMap<>();

    /**
     * Sat down for `ticks`, the head swimming a little (the client draws the rest: the flash of the blow, stars, how
     * long). Not while still immune from the last one.
     */
    static boolean stun(EntityLivingBase e, int ticks) {
        Long free = stunFree.get(e.getUniqueID());
        long now = e.worldObj.getTotalWorldTime();
        if (free != null && now < free || e.isPotionActive(STUN)) return false;
        stunFree.put(e.getUniqueID(), now + ticks + stunImmuneSeconds * 20L);
        e.addPotionEffect(new PotionEffect(STUN.id, ticks, 0, true));
        if (stunNausea) e.addPotionEffect(new PotionEffect(Potion.confusion.id, ticks + 40, 0, true));
        return true;
    }

    /**
     * A blow of the club: a chance to stun (clubStunChance, twice that from behind) for 3 seconds, with a thud. The
     * downed are out of it already.
     */
    static void clubbed(EntityPlayerMP t, EntityLivingBase by) {
        if (!stun || Downed.is(t)) return;
        double dx = by.posX - t.posX, dz = by.posZ - t.posZ, len = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toRadians(t.rotationYawHead);
        boolean behind = len > 1e-3 && (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / len < -0.3;
        float chance = clubStunChance / 100F * (behind ? 2 : 1);
        if (rand.nextFloat() >= chance || !stun(t, clubStunSeconds * 20)) return;
        t.worldObj.playSoundAtEntity(t, "mob.zombie.wood", 0.7F, 1.4F + rand.nextFloat() * 0.2F);
    }

    /** p loses x if x is switched off. */
    private static void off(EntityPlayer p, Potion x, boolean on) {
        if (!on && x != null && p.isPotionActive(x)) p.removePotionEffect(x.id);
    }

    /** A pill's nausea: only if pills may cause it (pillNausea). */
    private static void nausea(EntityPlayer p, int ticks, int amp) {
        if (pillNausea) fx(p, Potion.confusion, ticks, amp);
    }

    private static void fx(EntityPlayer p, Potion pot, int ticks, int amp) {
        p.addPotionEffect(new PotionEffect(pot.id, ticks, amp));
    }

    /** Vanilla harmful ids (Potion.isBadEffect is client-only here): slowness, fatigue, nausea, blindness... */
    private static final int[] HARMFUL = { 2, 4, 9, 15, 17, 18, 19, 20 };

    /**
     * Ends p's hold on the server and makes the client let go too: it holds on while the held stack is the same
     * object, so the slot is sent again.
     */
    static void stopHold(EntityPlayerMP p) {
        feedOver(p, MedItem.targets.remove(p));
        p.clearItemInUse();
        p.playerNetServerHandler
            .sendPacket(new S2FPacketSetSlot(0, 36 + p.inventory.currentItem, p.inventory.getCurrentItem()));
    }

    /** giver's hold on t (if any) is over: t's HUD lets go. */
    static void feedOver(EntityPlayer giver, EntityPlayer t) {
        if (t instanceof EntityPlayerMP)
            UkyCorpses.net.sendTo(new MsgFeed(giver.getEntityId(), null, 0, false), (EntityPlayerMP) t);
    }

    static void pill(EntityPlayer p, String name) {
        switch (name) {
            case "toxilin":
                fx(p, Potion.poison, 300, 1);
                nausea(p, 200, 0);
                break;
            case "morphinol":
                if (inversion) fx(p, INVERSION, 300, 0);
                fx(p, Potion.resistance, 400, 1);
                break;
            case "flyuk":
                fx(p, Potion.moveSpeed, 300, 1);
                fx(p, Potion.digSpeed, 300, 1);
                fx(p, Potion.hunger, 300, 0);
                if (rand.nextFloat() < 0.25F) nausea(p, 200, 1);
                break;
            case "nalgezin":
                fx(p, Potion.regeneration, 160, 0);
                fx(p, Potion.moveSlowdown, 300, 1);
                fx(p, Potion.weakness, 200, 0);
                break;
            case "stimulin":
                fx(p, Potion.regeneration, 200, 2);
                fx(p, Potion.hunger, 400, 2);
                nausea(p, 160, 0);
                break;
            case "paracetol":
                for (int id : HARMFUL) p.removePotionEffect(id);
                for (Potion x : new Potion[] { BLEEDING, SHOCK, STUN, INVERSION }) p.removePotionEffect(x.id);
                nausea(p, 240, 2);
                break;
            case "kserin":
                fx(p, Potion.moveSpeed, 200, 2);
                fx(p, Potion.blindness, 100, 0);
                nausea(p, 100, 0);
                break;
            case "placebo":
                int roll = rand.nextInt(4);
                if (roll == 0) fx(p, Potion.moveSpeed, 200, 0);
                else if (roll == 1) fx(p, Potion.jump, 200, 0);
                else if (roll == 2) fx(p, Potion.regeneration, 200, 0);
                nausea(p, 120, 4);
                break;
            case "antidepressant":
                nausea(p, 100, 2);
                fx(p, EUPHORIA, 500, 0);
                break;
            default:
        }
    }

    // ---- server events ----

    public static final class Events {

        /** Hit detection by health lost per tick, so it is the damage that got through armor. */
        private void hit(EntityPlayerMP p, UUID id, boolean down) {
            Float last = lastHp.put(id, p.getHealth());
            if (last == null || down || p.isDead || p.getHealth() <= 0 || p.capabilities.isCreativeMode) return;
            float drop = last - p.getHealth();
            if (stun && drop >= stunDamage) stun(p, stunHitSeconds * 20);
            if (shockOnHit && drop >= shockDamage) {
                p.addPotionEffect(new PotionEffect(ADRENALINE.id, adrenalineSeconds * 20, 0, true));
                pending.add(id);
            }
        }

        private void seat(EntityPlayerMP p, UUID id, boolean down) {
            EntitySeat s = seats.get(id);
            if (stun && stunSits && p.isPotionActive(STUN) && !down && !p.isDead && !com.uky.corpses.Reactions.is(p)) {
                if (s == null || s.isDead || s.worldObj != p.worldObj) {
                    s = new EntitySeat(p.worldObj);
                    s.setPosition(p.posX, p.boundingBox.minY, p.posZ);
                    p.worldObj.spawnEntityInWorld(s);
                    seats.put(id, s);
                    anchors.put(id, new double[] { p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch });
                }
                // a couple of ticks later: the client has to know the seat before it is told to sit on it
                if (p.ridingEntity != s && s.ticksExisted >= 3) p.mountEntity(s);
            } else if (s != null) release(p, id);
        }

        private void release(EntityPlayerMP p, UUID id) {
            EntitySeat s = seats.remove(id);
            double[] a = anchors.remove(id);
            if (s == null) return;
            if (p.ridingEntity == s) p.mountEntity(null);
            s.setDead();
            if (a != null && !p.isDead && s.worldObj == p.worldObj && !com.uky.corpses.Reactions.is(p) && !Downed.is(p))
                p.playerNetServerHandler.setPlayerLocation(a[0], a[1], a[2], (float) a[3], (float) a[4]);
        }

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote || !(e.player instanceof EntityPlayerMP))
                return;
            EntityPlayerMP p = (EntityPlayerMP) e.player;
            if (!p.isUsingItem() && MedItem.targets.containsKey(p)) feedOver(p, MedItem.targets.remove(p));
            UUID id = p.getUniqueID();
            boolean down = Downed.is(p);
            hit(p, id, down);
            off(p, BLEEDING, Blood.bleedingOn());
            off(p, SHOCK, shockOn);
            off(p, STUN, stun);
            off(p, ADRENALINE, adrenalineOn);
            off(p, INVERSION, inversion);
            off(p, EUPHORIA, euphoriaOn);
            if (pending.contains(id) && !p.isPotionActive(ADRENALINE)) {
                pending.remove(id);
                if (!protection.containsKey(id) && shockOn) {
                    p.addPotionEffect(new PotionEffect(SHOCK.id, shockSeconds * 20, 0, true));
                    if (shockNausea)
                        p.addPotionEffect(new PotionEffect(Potion.confusion.id, shockSeconds * 20, 2, true));
                }
            }
            Integer left = protection.get(id);
            if (left != null) {
                if (left <= 1) protection.remove(id);
                else protection.put(id, left - 1);
            }
            if (stun && p.isPotionActive(STUN)) {
                if (p.ticksExisted % 10 == 0) {
                    p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 6, true));
                    p.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 1, true));
                    p.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 40, 4, true));
                }
                p.fallDistance = 0;
            }
            seat(p, id, down);
            if (!hunger) {
                FoodStats f = p.getFoodStats();
                if (f.getFoodLevel() < 20 || f.getSaturationLevel() < 5F) {
                    NBTTagCompound t = new NBTTagCompound(); // setFoodLevel is client-only in this version
                    f.writeNBT(t);
                    t.setInteger("foodLevel", 20);
                    t.setFloat("foodSaturationLevel", 5F);
                    f.readNBT(t);
                }
            }
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || bleeders.isEmpty()) return;
            List<EntityLivingBase> due = new ArrayList<>(bleeders);
            bleeders.clear();
            for (EntityLivingBase b : due) {
                if (b.isDead || b.getHealth() <= 0) continue;
                b.hurtResistantTime = 0; // i-frames would swallow a 1 hp tick
                b.attackEntityFrom(Fx.BLEED, bleedDamage);
            }
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            UUID id = e.player.getUniqueID();
            if (e.player instanceof EntityPlayerMP) release((EntityPlayerMP) e.player, id);
            pending.remove(id);
            protection.remove(id);
            lastHp.remove(id);
        }

        @SubscribeEvent
        public void respawn(PlayerEvent.PlayerRespawnEvent event) {
            UUID id = event.player.getUniqueID();
            for (Potion effect : new Potion[] { BLEEDING, SHOCK, STUN, ADRENALINE, INVERSION, EUPHORIA })
                event.player.removePotionEffect(effect.id);
            pending.remove(id);
            protection.remove(id);
            lastHp.put(id, event.player.getHealth());
            anchors.remove(id);
            EntitySeat old = seats.remove(id);
            if (old != null) old.setDead();
            bleeders.removeIf(
                entity -> entity.getUniqueID()
                    .equals(id));
        }

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load e) {
            if (!e.world.isRemote) e.world.getGameRules()
                .setOrCreateGameRule("naturalRegeneration", String.valueOf(healthRegen));
        }

        /** Stunned hands do nothing. */
        /** Fighting off a pill or a shot: the one it's for hits the one giving it. */
        @SubscribeEvent
        public void onResist(LivingAttackEvent e) {
            if (!resist || !(e.entityLiving instanceof EntityPlayerMP) || e.source.getEntity() == null) return;
            EntityPlayerMP p = (EntityPlayerMP) e.entityLiving;
            ItemStack s = p.getItemInUse();
            if (s == null || !(s.getItem() instanceof MedItem)
                || !((MedItem) s.getItem()).forced()
                || MedItem.targets.get(p) != e.source.getEntity()) return;
            if (resistDrops && !p.capabilities.isCreativeMode) {
                p.dropPlayerItemWithRandomChoice(s.splitStack(1), false);
                if (s.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
            }
            stopHold(p);
            p.worldObj.playSoundAtEntity(p, "random.pop", 0.8F, 0.6F); // knocked out of the hand
        }

        @SubscribeEvent
        public void onAttack(AttackEntityEvent e) {
            if (e.entityPlayer.isPotionActive(STUN)) e.setCanceled(true);
        }

        @SubscribeEvent
        public void onEaten(PlayerUseItemEvent.Finish e) {
            if (!laced || e.entityPlayer.worldObj.isRemote || e.item == null || !e.item.hasTagCompound()) return;
            if (!e.item.getTagCompound()
                .getBoolean(LacedRecipe.TAG)) return;
            fx(e.entityPlayer, Potion.poison, 300, 1);
            fx(e.entityPlayer, Potion.confusion, 200, 0);
        }
    }
}
