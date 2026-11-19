package com.uky.corpses;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.config.Configuration;

import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.relauncher.Side;

/** Installed Corpse storage and Revival settings, using the monolith's Forge configuration. */
public final class OriginalGameplay {

    public static boolean corpseStoreXp = true, enableCorpseCompass = true, removeCompassWhenLooted = true,
        ownerOnlyLooting, othersLootWhenSkeletal;
    public static int corpseExpiryTicks = 24000, corpseSkeletonTicks = 24000;
    public static boolean disableInSingleplayer = true, blockActionsWhileKnocked = true, totemPreventsDowned = true;
    public static int downedSeconds = 60, downedInvulnerableTicks = 10, reviveSeconds = 10, reviveDecaySeconds = 30,
        secondChanceCooldownSeconds = 120;
    public static float knockedHealth = 12, reviveHearts = 2.5F;
    public static boolean hideHudWhenDowned = true, cameraAvoidBlocks = true, showDownedPrompt = true,
        showRevivePrompt = true, showBeingRevivedPrompt = true, showDownedHealth = true, showDownedMarker = true;
    public static float downedMarkerHeight = .7F;
    public static final int GIVE_UP_TICKS = 50, GIVE_UP_TIMEOUT = 10, REVIVE_TIMEOUT = 7;
    public static final Map<Integer, ClientState> seen = new ConcurrentHashMap<>();
    public static volatile Boolean serverBlockActions, serverCrawlMode;

    private OriginalGameplay() {}

    public static boolean crawlPresentation() {
        Boolean server = serverCrawlMode;
        return server == null ? CorpseConfig.crawlDowned : server;
    }

    public static void preInit() {
        UkyCorpses.net.registerMessage(MsgDragInput.Handler.class, MsgDragInput.class, 64, Side.SERVER);
        UkyCorpses.net.registerMessage(MsgReviveInput.Handler.class, MsgReviveInput.class, 62, Side.SERVER);
        UkyCorpses.net.registerMessage(MsgOriginalState.Handler.class, MsgOriginalState.class, 63, Side.CLIENT);
    }

    public static void config(Configuration ignored) {
        CorpseConfig.Section pc = CorpseConfig.section("corpse.rules", "Тела игроков.");
        corpseExpiryTicks = pc.num(
            "corpseExpiryTicks",
            24000,
            1200,
            2400000,
            "Загруженных тиков до выпадения вещей и исчезновения тела. 24000 — игровой день.");
        corpseStoreXp = pc.on("corpseStoreXp", true, "Сохранять в теле накопленный опыт с учётом штрафа за смерть.");
        enableCorpseCompass = pc.on("enableCorpseCompass", true, "Выдавать после возрождения компас последнего тела.");
        removeCompassWhenLooted = pc.on(
            "removeCompassWhenLooted",
            true,
            "Удалять компас опустошённого или исчезнувшего тела, в том числе при возвращении из контейнера.");
        corpseSkeletonTicks = pc.num(
            "corpseSkeletonTicks",
            24000,
            0,
            2400000,
            "Загруженных тиков до скелетизации; 0 — сразу. Для видимой стадии нужно большее corpseExpiryTicks.");
        ownerOnlyLooting = pc
            .on("ownerOnlyLooting", false, "Обыскивает только владелец. Перетаскивание не ограничивается.");
        othersLootWhenSkeletal = pc
            .on("othersLootWhenSkeletal", false, "При ownerOnlyLooting разрешать всем обыск после скелетизации.");

        CorpseConfig.Section pr = CorpseConfig.section("downed.rules", "Нокдаун и помощь.");
        pr.num("gameplayDefaultsVersion", 1, 1, 1, "Версия однократного обновления стандартных настроек; не менять.");
        disableInSingleplayer = pr.on(
            "disableInSingleplayer",
            true,
            "Без адреналина не переходить в нокдаун одному в локальном мире. Препарат в инвентаре или второй участник LAN разрешают нокдаун.");
        blockActionsWhileKnocked = pr.on(
            "blockActionsWhileKnocked",
            true,
            "В нокдауне запрещены предметы, взаимодействие, установка и разрушение блоков; атака запрещена всегда.");
        totemPreventsDowned = pr.on(
            "totemPreventsDowned",
            true,
            "Совместимый тотем бессмертия спасает от смертельного удара перед нокдауном. В ванильной 1.7.10 тотема нет.");
        downedSeconds = pr.num(
            "downedSeconds",
            60,
            5,
            3600,
            "Секунд до смерти в нокдауне; таймер приостанавливается во время подъёма.");
        knockedHealth = pr
            .num("knockedHealth", 12F, 1F, 10000F, "Отдельный запас урона, который выдерживает тело в нокдауне.");
        downedInvulnerableTicks = pr.num(
            "downedInvulnerableTicks",
            10,
            0,
            1200,
            "Первые тики нокдауна без потери knockedHealth. 0 отключает защиту.");
        reviveSeconds = pr.num("reviveSeconds", 10, 1, 300, "Секунд удержания использования для подъёма.");
        reviveDecaySeconds = pr.num(
            "reviveDecaySeconds",
            30,
            1,
            3600,
            "Секунд до исчезновения полной полосы незавершённого подъёма после отпускания кнопки.");
        reviveHearts = pr
            .num("reviveHearts", 2.5F, .5F, 100F, "Сердец после подъёма, не выше максимального здоровья игрока.");
        secondChanceCooldownSeconds = pr.num(
            "secondChanceCooldownSeconds",
            120,
            0,
            3600,
            "Секунд после подъёма, когда следующий смертельный удар убивает сразу. 0 отключает.");

        CorpseConfig.Section view = CorpseConfig.section("downed.display", "Вид и подсказки в нокдауне.");
        hideHudWhenDowned = view.on("hideHudWhenDowned", true, "Скрывать HUD, чат и руку, оставляя веки.");
        cameraAvoidBlocks = view
            .on("cameraAvoidBlocks", true, "Выталкивать камеру из блоков при падении и перетаскивании.");
        showDownedPrompt = view.on("showDownedPrompt", true, "Показывать удержание прыжка для сдачи.");
        showRevivePrompt = view.on("showRevivePrompt", true, "Показывать процент подъёма тому, кто помогает.");
        showBeingRevivedPrompt = view.on("showBeingRevivedPrompt", true, "Показывать полосу подъёма упавшему.");
        showDownedHealth = view.on("showDownedHealth", true, "Показывать оставшийся запас knockedHealth.");
        showDownedMarker = view
            .on("showDownedMarker", true, "Разрешить метку упавшего игрока при наличии поддержки взаимодействий.");
        downedMarkerHeight = view.num("downedMarkerHeight", .7F, 0F, 4F, "Высота метки над физическим туловищем.");

        // Corpse presentation rules; the penalty section owns XP retention, item loss and durability.
        Corpses.asBlock = false;
        Corpses.dropOnDespawn = true;
        Corpses.keepEmpty = true;
        Corpses.despawnTicks = 0;
        CorpseConfig.ownerLockMinutes = ownerOnlyLooting ? -1 : 0;
        CorpseConfig.searchSeconds = 0;
        CorpseConfig.restoreSlots = true;
        CorpseConfig.drag = true;
        if (!CorpseConfig.crawlDowned) {
            CorpseConfig.downed = CorpseConfig.friendRevive = true;
            CorpseConfig.downedHealth = 1;
            CorpseConfig.crawlSpeed = 0;
            CorpseConfig.downedBlindness = CorpseConfig.mobsIgnoreDowned = false;
            CorpseConfig.bleedSeconds = downedSeconds;
            CorpseConfig.aloneSeconds = 0;
            CorpseConfig.reviveSeconds = reviveSeconds;
            CorpseConfig.reviveHealth = Math.round(reviveHearts * 2);
        }
    }

    public static boolean clientBlocksActions() {
        Boolean server = serverBlockActions;
        return server == null ? blockActionsWhileKnocked : server;
    }

    /** Vanilla 1.7 has no totem; supported backports use the same registry item and vanilla resurrection effects. */
    public static boolean tryTotem(EntityPlayerMP player, DamageSource source) {
        if (!totemPreventsDowned || source == DamageSource.outOfWorld) return false;
        int main = player.inventory.currentItem,
            offhand = player.inventory.mainInventory.length > 36 ? player.inventory.mainInventory.length - 1 : -1;
        for (int slot : new int[] { main, offhand }) {
            if (slot < 0) continue;
            ItemStack held = player.inventory.mainInventory[slot];
            if (held == null) continue;
            String name = String.valueOf(Item.itemRegistry.getNameForObject(held.getItem()));
            if (!"minecraft:totem_of_undying".equals(name) && !"etfuturum:totem_of_undying".equals(name)) continue;
            if (--held.stackSize <= 0) player.inventory.mainInventory[slot] = null;
            player.inventory.markDirty();
            player.setHealth(1);
            player.extinguish();
            player.addPotionEffect(new PotionEffect(Potion.regeneration.id, 900, 1));
            player.addPotionEffect(new PotionEffect(Potion.field_76444_x.id, 100, 1));
            player.addPotionEffect(new PotionEffect(Potion.fireResistance.id, 800, 0));
            return true;
        }
        return false;
    }

    public static boolean draggablePart(int part) {
        return part == Skeleton.HEAD || part == Skeleton.RLEG || part == Skeleton.LLEG;
    }

    public static int choosePullPart(int hit, Skeleton.Shape shape, double eyeX, double eyeY, double eyeZ) {
        if (hit == Skeleton.HEAD || hit == Skeleton.RARM || hit == Skeleton.LARM) return Skeleton.HEAD;
        if (hit == Skeleton.RLEG || hit == Skeleton.LLEG) return hit;
        if (shape == null) return Skeleton.HEAD;
        int nearest = Skeleton.HEAD;
        double distance = Double.MAX_VALUE;
        for (int part : new int[] { Skeleton.HEAD, Skeleton.RLEG, Skeleton.LLEG }) {
            float[] min = Skeleton.min(part), max = Skeleton.max(part);
            double[] at = shape.parts[part]
                .apply((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2, new double[3]);
            double dx = at[0] - eyeX, dy = at[1] - eyeY, dz = at[2] - eyeZ;
            double next = dx * dx + dy * dy + dz * dz;
            if (next < distance) {
                distance = next;
                nearest = part;
            }
        }
        return nearest;
    }

    public static boolean knockingDisabled(boolean singleplayer, int players) {
        return knockingDisabled(singleplayer, players, false);
    }

    public static boolean knockingDisabled(boolean singleplayer, int players, boolean selfRescue) {
        return disableInSingleplayer && singleplayer && players <= 1 && !selfRescue;
    }

    public static double advanceRevive(double progress, boolean active, int seconds, int decaySeconds) {
        double step = 1D / (Math.max(1, active ? seconds : decaySeconds) * 20D);
        return Math.max(0, Math.min(1, progress + (active ? step : -step)));
    }

    public static float knockedHealthAfter(float health, float damage, int elapsedTicks, int invulnerableTicks) {
        if (elapsedTicks < invulnerableTicks || Float.isNaN(damage) || damage <= 0) return health;
        return Math.max(0, health - damage);
    }

    public static boolean canBeDowned(int tick, int cooldownUntil, int respawnUntil) {
        return tick >= cooldownUntil && tick >= respawnUntil;
    }

    public static int advanceGiveUp(int heldTicks, int elapsedSinceInput) {
        return elapsedSinceInput > GIVE_UP_TIMEOUT ? 0 : Math.min(GIVE_UP_TICKS, heldTicks + 1);
    }

    public static void clearClient() {
        seen.clear();
        serverBlockActions = null;
        serverCrawlMode = null;
    }

    public static final class ClientState {

        public final float health, maxHealth, progress;
        public final boolean paused;
        public final int giveUpTicks, reviver, serverTick;

        ClientState(float health, float maxHealth, float progress, boolean paused, int giveUpTicks, int reviver,
            int serverTick) {
            this.health = health;
            this.maxHealth = maxHealth;
            this.progress = progress;
            this.paused = paused;
            this.giveUpTicks = giveUpTicks;
            this.reviver = reviver;
            this.serverTick = serverTick;
        }
    }
}
