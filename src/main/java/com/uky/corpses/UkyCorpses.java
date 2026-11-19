package com.uky.corpses;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.uky.corpses.blood.Blood;
import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Ragdoll;
import com.uky.corpses.corpse.Situation;
import com.uky.corpses.corpse.Skeleton;
import com.uky.corpses.corpse.Throw;
import com.uky.corpses.deathcam.DeathCam;
import com.uky.corpses.medicine.Medicine;
import com.uky.corpses.movement.Crouch;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

@Mod(
    modid = UkyCorpses.MODID,
    version = Tags.VERSION,
    name = "UkyCorpses",
    acceptedMinecraftVersions = "[1.7.10]",
    guiFactory = "com.uky.corpses.client.ConfigGui$Factory")
public class UkyCorpses {

    public static final String MODID = "ukycorpses";
    public static final String COMMAND = "ukycorpses";

    @Mod.Instance
    public static UkyCorpses instance;
    public static final Logger LOG = LogManager.getLogger(MODID);

    public static BlockCorpse block;
    public static ItemTape tape;
    public static SimpleNetworkWrapper net;

    private final Random rand = new Random();
    /** Work handed over from netty threads (1.7.10 runs mod messages there): run at the end of the next server tick. */
    public static final Queue<Runnable> serverTasks = new ConcurrentLinkedQueue<>();
    /** Inventory as it was at the moment of death, before drops lose their slots. */
    private final Map<UUID, Snapshot> pending = new HashMap<>();
    // ponytail: tapes waiting for respawn live in memory; a server restart between death and respawn loses the tape
    // (never the items)
    private final Map<UUID, ItemStack> tapes = new HashMap<>();

    private static final class Snapshot {

        final Map<ItemStack, Integer> slots = new IdentityHashMap<>();
        int xp;
        IChatComponent cause;
        double[] spot;
        boolean burnt;
        DamageSource source;
        int held;
        boolean fell;
        Reactions.DeathPose physical;
        javax.vecmath.Vector3f velocity, impulse, kick;
        /** The blast that killed them ({x, y, z, size}), null if none. */
        double[] blast;
        /** Which way their body faced when they died. */
        float yaw;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        com.uky.corpses.port.physics.jolt.JoltLibrary.directory = event.getModConfigurationDirectory()
            .getParentFile()
            .toPath();
        if (!com.uky.corpses.port.physics.jolt.JoltNatives.load())
            throw new IllegalStateException(com.uky.corpses.port.physics.jolt.JoltNatives.failureReason());
        CorpseConfig.load(new File(event.getModConfigurationDirectory(), "uky/corpses.cfg"));
        block = new BlockCorpse();
        GameRegistry.registerBlock(block, "corpse");
        GameRegistry.registerTileEntity(TileCorpse.class, MODID + ":corpse");
        tape = new ItemTape();
        GameRegistry.registerItem(tape, "tape");
        com.uky.corpses.pc.PcCorpses.preInit();
        EntityRegistry.registerModEntity(EntityCorpse.class, "corpse", 0, this, 80, 3, false);
        net = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        net.registerMessage(MsgCorpse.Handler.class, MsgCorpse.class, 0, Side.CLIENT);
        net.registerMessage(MsgDowned.Handler.class, MsgDowned.class, 1, Side.CLIENT);
        net.registerMessage(MsgGiveUp.Handler.class, MsgGiveUp.class, 2, Side.SERVER);
        net.registerMessage(MsgSelfAdrenaline.Handler.class, MsgSelfAdrenaline.class, 8, Side.SERVER);
        net.registerMessage(MsgTakeAll.Handler.class, MsgTakeAll.class, 3, Side.SERVER);
        net.registerMessage(MsgDeath.Handler.class, MsgDeath.class, 4, Side.CLIENT);
        net.registerMessage(MsgUse.Handler.class, MsgUse.class, 5, Side.SERVER);
        net.registerMessage(MsgVoidReturn.Handler.class, MsgVoidReturn.class, 9, Side.CLIENT);
        net.registerMessage(MsgBodyLink.Handler.class, MsgBodyLink.class, 7, Side.CLIENT);
        net.registerMessage(MsgRagdoll.Handler.class, MsgRagdoll.class, 6, Side.CLIENT);
        // ports of UkyCorpses other parts; each owns its own registrations and message ids from 10/20/30 up
        Medicine.preInit();
        Blood.preInit();
        DeathCam.preInit();
        Reactions.preInit();
        OriginalGameplay.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new Throw());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new CorpseGui());
        FMLCommonHandler.instance()
            .bus()
            .register(new VoidReturn());
        Downed downed = new Downed();
        MinecraftForge.EVENT_BUS.register(downed);
        FMLCommonHandler.instance()
            .bus()
            .register(downed);
        if (Loader.isModLoaded("Waila"))
            FMLInterModComms.sendMessage("Waila", "register", "com.uky.corpses.compat.WailaCompat.register");
        Medicine.init();
        Blood.init();
        Corpses.init();
        Crouch.init();
        Reactions.init();
        DeathMotion.init();
        com.uky.corpses.stains.SurfaceBlood.init();
        com.uky.corpses.corpse.RagdollWorld.init();
        com.uky.corpses.core.Hooks.init();
        com.uky.corpses.pc.PcCorpses.init();
        Amputation.init();
        com.uky.corpses.amp.LivingAmputation.init();
        com.uky.corpses.mobs.MobRagdolls.init();
        if (event.getSide()
            .isClient()) CorpseClient.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        Corpses.serverStarting(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) event.entityLiving;
        double[] spot = Downed.takeSpot(p, event.source); // taken either way, a stale one would misplace the next body
        Reactions.DeathPose physical = Reactions.takeDeath(p);
        boolean keep = p.worldObj.getGameRules()
            .getGameRuleBooleanValue("keepInventory");
        tell(p, event.source, !Corpses.enabled ? MsgDeath.DROPPED : keep ? MsgDeath.KEPT : MsgDeath.BODY);
        if (!Corpses.enabled) return; // vanilla drops
        if (keep) return;

        Snapshot s = new Snapshot();
        s.physical = physical;
        ItemStack[] main = p.inventory.mainInventory; // Backhand appends its offhand slot to this array
        for (int i = 0; i < main.length; i++) if (main[i] != null) s.slots.put(main[i], i);
        ItemStack[] armor = p.inventory.armorInventory;
        for (int i = 0; i < armor.length; i++) if (armor[i] != null) s.slots.put(armor[i], EntityCorpse.ARMOR + i);
        s.xp = OriginalGameplay.corpseStoreXp ? totalXp(p) : 0;
        s.spot = spot;
        s.burnt = event.source.isFireDamage();
        s.source = event.source;
        s.velocity = DeathMotion.capture(p, event.source);
        s.impulse = DeathMotion.hitImpulse(p, event.source);
        s.fell = event.source == DamageSource.fall || p.fallDistance > 5;
        s.blast = EntityCorpse.isExplosion(event.source) ? Throw.take(p) : null;
        s.kick = DeathMotion.explosionKick(p, s.blast);
        s.yaw = p.renderYawOffset;
        s.held = p.inventory.currentItem; // the inventory is wiped by the time the drops event comes
        s.cause = event.source.func_151519_b(p);
        // XP goes into the body instead of orbs: at level 0 vanilla's getExperiencePoints() drops nothing
        if (OriginalGameplay.corpseStoreXp) {
            p.experienceLevel = 0;
            p.experience = 0;
            p.experienceTotal = 0;
        }
        pending.put(p.getUniqueID(), s);
    }

    /**
     * The dead player's own client learns what killed them first thing. Bled out or gave up lying downed: the blow
     * that knocked them down (the combat log has long forgotten it), and how it ended.
     */
    private static void tell(EntityPlayerMP p, DamageSource source, int kind) {
        try {
            String why = Downed.takeWhy(p);
            IChatComponent cause = why != null ? source.func_151519_b(p)
                : p.func_110142_aN()
                    .func_151521_b();
            net.sendTo(new MsgDeath(IChatComponent.Serializer.func_150696_a(cause), why == null ? "" : why, kind), p);
        } catch (RuntimeException e) { // a modded damage source with a broken message: no cause line, no harm
            LOG.warn("Death cause for {} not sent", p.getCommandSenderName(), e);
        }
    }

    // LOW: after Baubles-style mods add their items, after EnderIO-style soulbound takes its own
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlayerDrops(PlayerDropsEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        Snapshot snap = pending.remove(player.getUniqueID());
        if (snap == null) return;
        World world = player.worldObj;

        int xp = (int) ((long) snap.xp * CorpseConfig.keepXpPercent / 100);
        List<EntityCorpse.Entry> entries = new ArrayList<>();
        List<ItemStack> lost = new ArrayList<>();
        for (EntityItem ei : event.drops) {
            ItemStack s = ei.getEntityItem();
            Integer slot = takeSlot(snap.slots, s);
            if (rand.nextInt(100) < CorpseConfig.itemLossPercent) {
                lost.add(s);
                continue;
            }
            s = com.uky.corpses.compat.EquipmentPenalty.apply(s, CorpseConfig.durabilityLossPercent);
            entries.add(new EntityCorpse.Entry(CorpseConfig.restoreSlots && slot != null ? slot : -1, s));
        }
        if (!Ragdoll.enabled && entries.isEmpty() && xp == 0 && lost.isEmpty()) return;

        int cause = Poses.cause(snap.source, snap.fell, snap.burnt);
        long lock = CorpseConfig.ownerLockMinutes < 0 ? -1 : CorpseConfig.ownerLockMinutes * 1200L;
        EntityCorpse body = layOut(
            player,
            snap.spot,
            cause,
            snap.burnt,
            snap.fell,
            snap.blast,
            snap.yaw,
            snap.held,
            entries,
            xp,
            lock,
            snap.physical,
            snap.velocity,
            snap.impulse,
            snap.kick);
        if (!body.isDead) com.uky.corpses.pc.PcCorpses.created(body);
        event.drops.clear();

        // deathsStat is bumped after this event, hence +1
        int number = player.func_147099_x()
            .writeStat(StatList.deathsStat) + 1;
        int dim = world.provider.dimensionId;
        int x = MathHelper.floor_double(body.posX), y = MathHelper.floor_double(body.posY),
            z = MathHelper.floor_double(body.posZ);
        player.addChatMessage(new ChatComponentTranslation("ukycorpses.msg.placed", x, y, z));
        net.sendTo(
            new MsgCorpse(x, y, z, dim, number, entries.size(), levels(xp), lost.size(), CorpseConfig.ownerLockMinutes),
            player);
        if (CorpseConfig.tape) {
            List<ItemStack> kept = new ArrayList<>();
            for (EntityCorpse.Entry e : entries) kept.add(e.stack);
            tapes.put(player.getUniqueID(), ItemTape.create(body, dim, number, snap.cause, kept, lost));
        }
        LOG.info(
            "Body #{} of {} ({} stacks, {} lost, {} xp) at {} {} {} dim {}",
            number,
            body.owner(),
            entries.size(),
            lost.size(),
            xp,
            x,
            y,
            z,
            dim);
    }

    /**
     * The body of player, laid out and put in the world (as blocks where they fit), a pool of blood spreading under
     * it. at: {x, y, z, yaw, pose} and a sixth element if they lay downed; blast: {x, y, z, size} of the blast that
     * killed them, or null; lock: ticks only the owner may open it, -1 for ever.
     */
    EntityCorpse layOut(EntityPlayer player, double[] at, int cause, boolean burnt, boolean fell, double[] blast,
        float yaw, int held, List<EntityCorpse.Entry> entries, int xp, long lock) {
        return layOut(
            player,
            at,
            cause,
            burnt,
            fell,
            blast,
            yaw,
            held,
            entries,
            xp,
            lock,
            null,
            DeathMotion.capture(player, null),
            null,
            DeathMotion.explosionKick(player, blast));
    }

    private EntityCorpse layOut(EntityPlayer player, double[] at, int cause, boolean burnt, boolean fell,
        double[] blast, float yaw, int held, List<EntityCorpse.Entry> entries, int xp, long lock,
        Reactions.DeathPose physical, javax.vecmath.Vector3f velocity, javax.vecmath.Vector3f impulse,
        javax.vecmath.Vector3f kick) {
        World world = player.worldObj;
        boolean downed = at.length > 5;
        // a blast throws them (not someone already lying downed): the arc is worked out against the blocks
        double[] flight = !Ragdoll.enabled && !downed && blast != null ? Throw.fly(world, at[0], at[1], at[2], blast)
            : null;
        // drowned, or died in lava: comes up and floats at the surface
        double[] liquid = surface(world, at);
        if (liquid != null) flight = null;
        EntityCorpse body = new EntityCorpse(world);
        if (Ragdoll.enabled) {
            body.setLocationAndAngles(at[0], VoidReturn.birthY(at[1]), at[2], yaw, 0);
            body.setPose((int) at[4]);
            body.setGrid(false);
        } else if (liquid != null) afloat(body, at, liquid, cause);
        else lay(body, at, flight, cause, fell);
        if (flight != null && body.pose() == EntityCorpse.SIT) { // landed in a hole: no flight after all
            flight = null;
            lay(body, at, null, cause, fell);
        }
        boolean lava = liquid != null && liquid[1] > 0;
        body.setBurn(burnt || lava ? EntityCorpse.BURNT : cause == Poses.BLAST ? EntityCorpse.SCORCHED : 0);
        body.setHeldSlot(held);
        // clients start it where and how the player was: standing (or lying downed), at their yaw
        body.from(at[0], VoidReturn.birthY(at[1]), at[2], downed ? (float) at[3] : yaw);
        if (liquid != null) body.floats((int) liquid[2]);
        else if (downed) body.dying(); // was lying there downed: goes slack, no fall
        else if (flight != null) body.thrown((int) flight[3], (float) flight[4]);
        else if (body.situation() == Situation.SPLAT) body.splat();
        body.setOwner(player.getCommandSenderName());
        body.ownerId = player.getUniqueID();
        VoidReturn.attach(body, player);
        body.fill(entries);
        body.xp = xp;
        body.created = world.getTotalWorldTime();
        body.lockTicks = lock;
        body.sync();
        if (Ragdoll.enabled && physical != null) body
            .inheritPhysical(physical.state, physical.severed, physical.originX, physical.originY, physical.originZ);
        if (Ragdoll.enabled && !body.ragdollActive()) {
            if (downed) body.beginPhysical();
            else body.startPhysical(
                velocity == null ? new javax.vecmath.Vector3f() : velocity,
                yaw,
                player.rotationPitch,
                player.rotationYawHead);
            body.applyDeathMotion(impulse, kick, blast);
        }
        body.ampHiddenMask(
            com.uky.corpses.amp.AmputationState.mask(player) & ~(physical == null ? 0 : physical.severed));
        if (physical != null) body.physicalLife(physical.generation);
        if (!world.spawnEntityInWorld(body)) {
            body.setDead();
            return body;
        }
        if (player instanceof EntityPlayerMP)
            net.sendTo(new MsgBodyLink(player.getUniqueID(), body.physicalLife()), (EntityPlayerMP) player);
        if (liquid == null) pool(body, cause, downed ? 5 : flight != null ? (int) flight[3] + 8 : 16);
        return body;
    }

    /**
     * Where and how the body lies: at the death spot (or where a blast threw it), quarter turns only so the hitbox
     * and the grid can follow it, a pose and a variant of it that fits the death, then the ground has its say.
     */
    private void lay(EntityCorpse body, double[] at, double[] flight, int cause, boolean fell) {
        int pose = (int) at[4];
        double yaw = at[3];
        if (flight != null) { // on the back mostly, else face down or on the side; head or feet first
            float r = rand.nextFloat();
            pose = r < 0.5F ? EntityCorpse.LIE : r < 0.8F ? EntityCorpse.PRONE : EntityCorpse.SIDE;
            yaw = flight[5] + (rand.nextBoolean() ? 180 : 0);
        }
        double x = flight != null ? flight[0] : at[0], y = Math.max(1, flight != null ? flight[1] : at[1]),
            z = flight != null ? flight[2] : at[2];
        body.setLocationAndAngles(x, y, z, Math.round(yaw / 90) * 90F, 0);
        body.setSituation(Situation.NONE, 0);
        body.setGrid(false);
        body.setPose(pose);
        body.setLook(Poses.choose(rand, pose, cause));
        Situation.situate(body, fell && flight == null); // ledges, holes, falls: may turn, slide or re-pose it
    }

    /**
     * Water or lava where they died, with open air over its surface: {surface height, 1 if lava, ticks to come up}.
     * Null on dry land, or under a ceiling (a flooded cave: the body stays down there).
     */
    private static double[] surface(World w, double[] at) {
        int x = MathHelper.floor_double(at[0]), z = MathHelper.floor_double(at[2]),
            y0 = MathHelper.floor_double(at[1] + 0.3);
        Material m = w.getBlock(x, y0, z)
            .getMaterial();
        if (!m.isLiquid()) {
            m = w.getBlock(x, ++y0, z)
                .getMaterial();
            if (!m.isLiquid()) return null;
        }
        int y = y0;
        while (y < y0 + 48 && w.getBlock(x, y, z)
            .getMaterial()
            .isLiquid()) y++;
        if (w.getBlock(x, y, z)
            .getMaterial()
            .isSolid()) return null;
        double top = y - 0.12;
        return new double[] { top, m == Material.lava ? 1 : 0, MathHelper.clamp_double((top - at[1]) * 14, 16, 70) };
    }

    /** Afloat, face down mostly (the drowned float that way), half under: no blocks, no grid, just the surface. */
    private void afloat(EntityCorpse body, double[] at, double[] liquid, int cause) {
        int pose = rand.nextFloat() < 0.65F ? EntityCorpse.PRONE : EntityCorpse.LIE;
        body.setLocationAndAngles(at[0], liquid[0] - 0.32, at[2], Math.round(at[3] / 90) * 90F, 0);
        body.setSituation(Situation.NONE, 0);
        body.setGrid(false);
        body.setPose(pose);
        body.setLook(Poses.choose(rand, pose, liquid[1] > 0 ? Poses.FIRE : Poses.DROWN));
        Situation.tuck(body);
    }

    /**
     * A pool of blood spreads under the body once it has come down (not for the burnt or the drowned). Under the
     * chest: where the trunk is as it lies.
     */
    private static void pool(EntityCorpse body, int cause, int delay) {
        if (body.burn() == EntityCorpse.BURNT || cause == Poses.DROWN) return;
        float size = cause == Poses.BLAST ? 2.3F
            : cause == Poses.SHOT || cause == Poses.FALL ? 1.9F
                : cause == Poses.MELEE ? 1.7F : cause == Poses.SICK ? 0.9F : 1.4F;
        double[] c = body.shape().parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
        Blood.pool(body.worldObj, body.posX + c[0], body.posY + c[1] + 0.3, body.posZ + c[2], size, delay, 240);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Runnable r; (r = serverTasks.poll()) != null;) r.run();
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ItemStack t = tapes.remove(event.player.getUniqueID());
        if (t != null && !event.player.inventory.addItemStackToInventory(t))
            event.player.dropPlayerItemWithRandomChoice(t, false);
    }

    /** Yaw snapped to 0..3, a quarter turn each, 0 = facing +z. */
    static int facing(EntityLivingBase e) {
        return MathHelper.floor_double(e.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
    }

    /** Vanilla drops the very stack objects it took out of the inventory; equality is the fallback for mods. */
    private static Integer takeSlot(Map<ItemStack, Integer> slots, ItemStack s) {
        Integer slot = slots.remove(s);
        if (slot != null) return slot;
        for (Iterator<Map.Entry<ItemStack, Integer>> it = slots.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<ItemStack, Integer> e = it.next();
            if (ItemStack.areItemStacksEqual(e.getKey(), s)) {
                it.remove();
                return e.getValue();
            }
        }
        return null;
    }

    /** What level + bar are worth in points. experienceTotal can't be trusted: /xp and enchanting skew it. */
    static int totalXp(EntityPlayer p) {
        long pts = 0;
        for (int l = 0; l < p.experienceLevel; l++) pts += xpBarCap(l);
        pts += Math.round(p.experience * xpBarCap(p.experienceLevel));
        return (int) Math.min(pts, Integer.MAX_VALUE);
    }

    /** Whole levels experience points make, counted from level 0. */
    static int levels(int points) {
        int l = 0;
        while (points >= xpBarCap(l)) points -= xpBarCap(l++);
        return l;
    }

    static int xpBarCap(int level) {
        return level >= 30 ? 62 + (level - 30) * 7 : level >= 15 ? 17 + (level - 15) * 3 : 17;
    }

    static void dropXp(World w, double x, double y, double z, int xp) {
        while (xp > 0) {
            int orb = EntityXPOrb.getXPSplit(xp);
            xp -= orb;
            w.spawnEntityInWorld(new EntityXPOrb(w, x, y, z, orb));
        }
    }
}
