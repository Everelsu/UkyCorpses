package com.uky.corpses.blood;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.uky.corpses.CorpseConfig;
import com.uky.corpses.Downed;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.medicine.Medicine;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * Blood on the ground (surface effects). The server only decides where a splat goes and tells the players near
 * it; the client keeps and draws them. A hit leaves a trail while the victim moves, bleeding drips, a crawling downed
 * player smears the floor.
 */
public final class Blood {

    /** Bleeding as a wound at all: set from wounds.bleeding (Medicine.config). */
    public static boolean bleeding = true;
    private static boolean bloodOn = true, damageBlood = true, charredSmoke = true, drops = true, stains = false;
    /** How long a stain lies before it's gone, seconds; pools under bodies three times as long. */
    private static int stainSeconds = 60;
    private static boolean registered;
    private static final Random rand = new Random();
    /** Ticks of trail left after a hit, per player. */
    private static final Map<UUID, Integer> hurtLeft = new HashMap<>();
    private static final Map<UUID, double[]> lastSpot = new HashMap<>();
    private static final Map<UUID, int[]> idle = new HashMap<>(); // {ticks since last splat, drip cooldown}

    public static void config(Configuration c) {
        CorpseConfig.Section s = CorpseConfig
            .section("blood", "Как выглядит кровь. Только визуал; урон — в wounds.bleeding.");
        bloodOn = s.on("enabled", true, "Главный выключатель крови.");
        damageBlood = s.on("onHit", true, "Кровь от ударов и след за раненым или ползущим.");
        drops = s.on("drops", true, "Брызги: капли разлетаются частицами.");
        stains = s.on("stains", false, "Пятна и лужи на земле. Каждый клиент решает сам.");
        stainSeconds = s
            .num("stainSeconds", 60, 5, 3600, "Сколько секунд сохнет пятно. Лужа под телом — втрое дольше.");
        charredSmoke = s.on("charredSmoke", true, "Дым над обгоревшим телом.");
    }

    /** Bleeding is on at all (effects.bleeding). */
    public static boolean bleedingOn() {
        return bleeding;
    }

    public static boolean charredSmoke() {
        return charredSmoke;
    }

    public static boolean stains() {
        return stains;
    }

    public static boolean drops() {
        return drops;
    }

    /** Ticks a stain lies before it's gone. */
    public static int stainTicks() {
        return stainSeconds * 20;
    }

    public static void preInit() {
        UkyCorpses.net.registerMessage(MsgBlood.Handler.class, MsgBlood.class, 20, Side.CLIENT);
        init();
    }

    /** Idempotent: whichever of preInit/init runs first subscribes. */
    public static void init() {
        if (registered) return;
        registered = true;
        Blood h = new Blood();
        MinecraftForge.EVENT_BUS.register(h);
        FMLCommonHandler.instance()
            .bus()
            .register(h);
    }

    /** Server: a splat of blood around x y z, size ~0.5..2. No-op when blood is off. */
    public static void spawn(World w, double x, double y, double z, float size) {
        if (w.isRemote || !bloodOn || com.uky.corpses.stains.SurfaceBlood.enabled) return;
        double top = floorAt(w, x, y, z);
        if (Double.isNaN(top)) return;
        UkyCorpses.net.sendToAllAround(
            new MsgBlood((float) x, (float) (top + 0.02), (float) z, size, rand.nextInt()),
            new NetworkRegistry.TargetPoint(w.provider.dimensionId, x, top, z, 64));
    }

    /** Server: a pool spreading under a body, starting after delay ticks, full size after grow more. */
    public static void pool(World w, double x, double y, double z, float size, int delay, int grow) {
        if (w.isRemote || !bloodOn || com.uky.corpses.stains.SurfaceBlood.enabled) return;
        double top = floorAt(w, x, y, z);
        if (Double.isNaN(top)) return;
        UkyCorpses.net.sendToAllAround(
            new MsgBlood((float) x, (float) (top + 0.02), (float) z, size, rand.nextInt(), delay, grow),
            new NetworkRegistry.TargetPoint(w.provider.dimensionId, x, top, z, 64));
    }

    /** Client: blood is on at all (each side reads its own config). */
    public static boolean on() {
        return bloodOn;
    }

    /** True if a splat was laid (and damage blood is on). */
    private static boolean hitBlood(World w, double x, double y, double z) {
        if (!damageBlood || w.isRemote || !bloodOn) return false;
        double top = floorAt(w, x, y, z);
        if (Double.isNaN(top)) return false;
        spawn(w, x, y, z, 1F);
        return true;
    }

    /** Height of the walkable top below feetY, NaN where a splat can't lie (water, air, stairs, fences). */
    private static double floorAt(World w, double x, double feetY, double z) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z), by = MathHelper.floor_double(feetY);
        if (w.getBlock(bx, by, bz)
            .getMaterial()
            .isLiquid()
            || w.getBlock(bx, by - 1, bz)
                .getMaterial()
                .isLiquid())
            return Double.NaN;
        for (int y = by; y >= by - 4; y--) {
            Block b = w.getBlock(bx, y, bz);
            AxisAlignedBB box = b.getCollisionBoundingBoxFromPool(w, bx, y, bz);
            if (box == null) continue;
            if (b instanceof BlockStairs || b instanceof BlockFence
                || b instanceof BlockWall
                || b instanceof BlockFenceGate
                || box.maxY - y > 1.0001) return Double.NaN;
            double top = box.maxY;
            if (top > feetY + 0.5) return Double.NaN;
            return w.getBlock(bx, MathHelper.floor_double(top + 0.01), bz)
                .getMaterial()
                .isSolid() ? Double.NaN : top;
        }
        return Double.NaN;
    }

    /** Explosions, shots and arrows, or a player hitting hard (over 3.5 hearts), open a wound. */
    private static boolean shouldBleed(DamageSource s, float amount) {
        if (s.isExplosion() || s.isProjectile() || s.getSourceOfDamage() instanceof EntityArrow) return true;
        if (s.getEntity() instanceof EntityPlayer && s.getSourceOfDamage() instanceof EntityPlayer) return amount > 7F;
        String t = s.damageType;
        return t != null && (t.contains("bullet") || t.contains("explosion"));
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !bloodOn) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        float amount = e.ammount;
        // a lethal hit is Downed's: no wound on someone who goes down or dies
        if (amount <= 0F || p.getHealth() - amount <= 0F || Downed.is(p)) return;
        if (damageBlood) {
            hurtLeft.put(p.getUniqueID(), amount > 6F ? 140 : 40);
            lastSpot.remove(p.getUniqueID());
            idle.remove(p.getUniqueID());
        }
        Potion bl = Medicine.BLEEDING;
        if (bleeding && bl != null && shouldBleed(e.source, amount)) {
            PotionEffect old = p.getActivePotionEffect(bl);
            int ticks = Medicine.bleedSeconds * 20;
            if (old == null || old.getDuration() < ticks) p.addPotionEffect(new PotionEffect(bl.id, ticks, 0, false));
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || !bloodOn || !damageBlood) return;
        UUID id = p.getUniqueID();
        if (Downed.is(p)) {
            trail(p, id, false);
            return;
        }
        Integer left = hurtLeft.get(id);
        if (left != null) {
            if (left <= 0) forget(id);
            else {
                hurtLeft.put(id, left - 1);
                trail(p, id, true);
            }
        }
        Potion bl = Medicine.BLEEDING;
        if (bleeding && bl != null && p.isPotionActive(bl)) {
            int[] st = idle.get(id);
            if (st == null) idle.put(id, st = new int[2]);
            if (st[1]-- > 0) return;
            st[1] = hitBlood(p.worldObj, p.posX, p.posY, p.posZ) ? 18 : 4;
        }
    }

    /** A splat every ~0.35 blocks moved; a hurt one standing still keeps dripping every 8 ticks. */
    private static void trail(EntityPlayer p, UUID id, boolean standingDrips) {
        double[] prev = lastSpot.get(id);
        int[] st = idle.get(id);
        if (st == null) idle.put(id, st = new int[2]);
        double dx = prev == null ? 1 : p.posX - prev[0], dz = prev == null ? 0 : p.posZ - prev[1];
        if (dx * dx + dz * dz >= 0.12 || standingDrips && st[0] >= 8) {
            hitBlood(p.worldObj, p.posX, p.posY, p.posZ);
            lastSpot.put(id, new double[] { p.posX, p.posZ });
            st[0] = 0;
        } else st[0]++;
    }

    private static void forget(UUID id) {
        hurtLeft.remove(id);
        lastSpot.remove(id);
        idle.remove(id);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        forget(e.player.getUniqueID());
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        forget(e.player.getUniqueID());
    }
}
