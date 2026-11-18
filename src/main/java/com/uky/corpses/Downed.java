package com.uky.corpses;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.uky.corpses.medicine.Medicine;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Revival server rules: independent knocked health, finite bleedout paused by held-use revival,
 * resumable progress, and held-jump surrender. Reactions owns the physical body and movement.
 */
public final class Downed {

    /** Lying down they're this tall: they crawl under anything a block high. */
    public static final float LOW = 0.6F, TALL = 1.8F;
    static final int CRAWL_POSE = EntityCorpse.PRONE;

    private static final class State {

        DamageSource source;
        int left, pose, alone = -1;
        float yaw;
        EntityPlayerMP reviver;
        boolean blast;
        int downAt;
        float knocked, maxKnocked;
        double reviveProgress;
        int reviveInputAt = -1000, giveUpInputAt = -1000, giveUpHeld;
        boolean finishPending, timerPaused;
        net.minecraft.item.ItemStack selfItem;
        int selfSlot = -1, selfTicks, selfTotal;
    }

    /** Up again where there's no room to stand: they crawl until there is. */
    private static final Set<UUID> crawling = new HashSet<>();

    private static final Map<UUID, State> downed = new HashMap<>();
    /** {x, y, z, yaw, pose} of the last down: where the body goes if they die. */
    private static final Map<UUID, double[]> lastSpot = new HashMap<>();
    /** Dying for real right now: let the death event through. */
    private static final Set<UUID> dying = new HashSet<>();
    /** Client: entity id -> last state the server broadcast. */
    public static final Map<Integer, MsgDowned> seen = new ConcurrentHashMap<>();
    private static final Random rand = new Random();
    private static int now;
    private static final Map<UUID, Integer> cooldown = new HashMap<>(), respawnProtection = new HashMap<>();

    /** Both sides. */
    public static boolean is(Entity p) {
        if (!(p instanceof EntityPlayer)) return false;
        if (!p.worldObj.isRemote) return downed.containsKey(p.getUniqueID());
        MsgDowned d = seen.get(p.getEntityId());
        return d != null && d.diedAt < 0 && !d.up; // died: only drawn as they lay until the body shows
    }

    /**
     * Where and how the body of p lies, {x, y, z, yaw, pose}, plus a sixth element if they were downed first;
     * consumed. Not downed first: thrown the way the blow went (fallPose).
     */
    static double[] takeSpot(EntityPlayer p, DamageSource source) {
        double[] s = lastSpot.remove(p.getUniqueID());
        if (s != null) return s;
        return new double[] { p.posX, p.boundingBox.minY, p.posZ, p.renderYawOffset, fallPose(p, source) };
    }

    /**
     * Which way someone standing goes down: away from what hit them (struck from ahead: onto the back; from behind: on
     * the face; from the side: on the side); with nothing to tell, the way they were moving (running: forward); else
     * any.
     */
    static int fallPose(EntityPlayer p, DamageSource source) {
        double dx = 0, dz = 0;
        Entity hit = source == null ? null : source.getSourceOfDamage();
        if (hit != null && hit != p) {
            if (hit != source.getEntity() && hit.motionX * hit.motionX + hit.motionZ * hit.motionZ > 0.01) {
                dx = hit.motionX; // a projectile pushes the way it flew
                dz = hit.motionZ;
            } else {
                dx = p.posX - hit.posX; // a blow pushes away from who struck it
                dz = p.posZ - hit.posZ;
            }
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toRadians(p.renderYawOffset), fx = -Math.sin(yaw), fz = Math.cos(yaw);
        if (len > 1e-3) {
            double ahead = (dx * fx + dz * fz) / len;
            return ahead > 0.35 ? EntityCorpse.PRONE : ahead < -0.35 ? EntityCorpse.LIE : EntityCorpse.SIDE;
        }
        double mx = p.posX - p.lastTickPosX, mz = p.posZ - p.lastTickPosZ, speed = Math.sqrt(mx * mx + mz * mz);
        if (speed > 0.08) {
            double ahead = (mx * fx + mz * fz) / speed;
            if (ahead > 0.5) return EntityCorpse.PRONE;
            if (ahead < -0.5) return EntityCorpse.LIE;
            return EntityCorpse.SIDE;
        }
        float r = rand.nextFloat();
        return r < 0.4F ? EntityCorpse.LIE : r < 0.8F ? EntityCorpse.PRONE : EntityCorpse.SIDE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.entityLiving;
        UUID id = p.getUniqueID();
        if (dying.contains(id)) return;
        State st = downed.remove(id);
        if (st != null) { // finished by a player, or the void: the body stays as it lay
            clearPresentation(p, st);
            cooldown.remove(id);
            remember(p, st);
            died(p);
            return;
        }
        now = MinecraftServer.getServer()
            .getTickCounter();
        if (!CorpseConfig.downed || p.capabilities.isCreativeMode
            || OriginalGameplay.knockingDisabled(
                MinecraftServer.getServer()
                    .isSinglePlayer(),
                MinecraftServer.getServer()
                    .getCurrentPlayerCount(),
                canSelfRescue(p))
            || !OriginalGameplay.canBeDowned(now, cooldown.getOrDefault(id, 0), respawnProtection.getOrDefault(id, 0)))
            return;
        if (OriginalGameplay.tryTotem(p, e.source)) {
            e.setCanceled(true);
            return;
        }
        e.setCanceled(true);
        down(p, e.source);
    }

    public static boolean canSelfRescue(EntityPlayer player) {
        return Medicine.selfAdrenaline && Medicine.adrenalineSlot(player) >= 0;
    }

    /** Knocked down by source: also /corpses down. */
    static void down(EntityPlayerMP p, DamageSource source) {
        now = MinecraftServer.getServer()
            .getTickCounter();
        p.setHealth(Math.max(1, Math.min(p.getMaxHealth(), p.getHealth())));
        p.hurtResistantTime = 20;
        if (p.ridingEntity != null) p.mountEntity(null);
        State st = new State();
        st.source = source;
        st.left = (CorpseConfig.crawlDowned ? CorpseConfig.bleedSeconds : OriginalGameplay.downedSeconds) * 20;
        st.knocked = st.maxKnocked = OriginalGameplay.knockedHealth;
        st.pose = fallPose(p, source);
        if (CorpseConfig.crawlDowned) st.pose = CRAWL_POSE;
        st.yaw = p.renderYawOffset;
        st.blast = EntityCorpse.isExplosion(source);
        st.downAt = now;
        crawling.remove(p.getUniqueID());
        downed.put(p.getUniqueID(), st);
        Reactions.startDowned(p);
        height(p, LOW);
        send(p, st);
    }

    /**
     * Back to a wall right next to them (behind first, never the one they face), else face down where they stand. Not
     * in water: nobody sits back against a wall on the bottom.
     */
    static double[] spot(EntityPlayer p) {
        World w = p.worldObj;
        double y = floor(p);
        double[] on = stood(p, y);
        int bx = MathHelper.floor_double(on[0]), by = MathHelper.floor_double(y + 0.6),
            bz = MathHelper.floor_double(on[1]);
        int f = UkyCorpses.facing(p); // yaw = f * 90; forward of f is (-sin, cos)
        boolean wet = w.getBlock(bx, by, bz)
            .getMaterial()
            .isLiquid();
        for (int turn : wet ? new int[0] : new int[] { 2, 1, 3 }) {
            int d = (f + turn) & 3;
            int dx = d == 1 ? -1 : d == 3 ? 1 : 0, dz = d == 0 ? 1 : d == 2 ? -1 : 0;
            if (!w.getBlock(bx + dx, by, bz + dz)
                .isNormalCube()) continue;
            // and room for the legs across from it: in a corridor one block wide they'd go into the other wall
            if (w.getBlock(bx - dx, by, bz - dz)
                .isNormalCube()) continue;
            // 0.3 off the wall, facing away from it
            return new double[] { bx + 0.5 + dx * 0.25, y, bz + 0.5 + dz * 0.25, ((d + 2) & 3) * 90, EntityCorpse.SIT };
        }
        return new double[] { on[0], y, on[1], p.renderYawOffset, EntityCorpse.PRONE };
    }

    /**
     * Where on the floor they stand, {x, z}. Their middle, unless that's over the drop: on the very edge of a block a
     * player stands on a corner of their box with the middle in the air, and a body laid there would lie over the
     * next block down. Then the nearest point of the block they stand on, just inside its edge; the ledge does the
     * rest (Situation: what's over the drop hangs down).
     */
    private static double[] stood(EntityPlayer p, double y) {
        World w = p.worldObj;
        int by = MathHelper.floor_double(y - 0.01);
        if (solid(w, MathHelper.floor_double(p.posX), by, MathHelper.floor_double(p.posZ)))
            return new double[] { p.posX, p.posZ };
        double[] best = { p.posX, p.posZ };
        double far = Double.MAX_VALUE;
        AxisAlignedBB b = p.boundingBox;
        for (double ex : new double[] { b.minX + 0.001, b.maxX - 0.001 })
            for (double ez : new double[] { b.minZ + 0.001, b.maxZ - 0.001 }) {
                int x = MathHelper.floor_double(ex), z = MathHelper.floor_double(ez);
                if (!solid(w, x, by, z)) continue;
                double nx = Math.max(x + 0.05, Math.min(x + 0.95, p.posX)),
                    nz = Math.max(z + 0.05, Math.min(z + 0.95, p.posZ));
                double d = (nx - p.posX) * (nx - p.posX) + (nz - p.posZ) * (nz - p.posZ);
                if (d < far) {
                    far = d;
                    best = new double[] { nx, nz };
                }
            }
        return best;
    }

    private static boolean solid(World w, int x, int y, int z) {
        return w.getBlock(x, y, z)
            .getCollisionBoundingBoxFromPool(w, x, y, z) != null;
    }

    /** What they'd land on if hit mid-jump; void deaths stay at y=1. */
    private static double floor(EntityPlayer p) {
        if (p.onGround) return p.boundingBox.minY;
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        int y = MathHelper.floor_double(p.boundingBox.minY);
        for (int i = 0; i < 8 && y > 1; i++, y--) if (p.worldObj.getBlock(x, y - 1, z)
            .getCollisionBoundingBoxFromPool(p.worldObj, x, y - 1, z) != null) break;
        return Math.max(1, y);
    }

    /**
     * As tall as h, keeping the feet where they are, eyes down near the floor when low (no suffocating under a
     * one-block gap, rays start there). Both sides set it: the client moves the player.
     */
    public static void height(EntityPlayer p, float h) {
        p.eyeHeight = h < TALL ? p.getDefaultEyeHeight() - (1.62F - 0.35F) : p.getDefaultEyeHeight();
        if (p.height == h) return;
        p.height = h;
        p.boundingBox.maxY = p.boundingBox.minY + h;
    }

    /** Room to stand up here. */
    public static boolean room(EntityPlayer p) {
        AxisAlignedBB b = p.boundingBox;
        return p.worldObj
            .func_147461_a(
                AxisAlignedBB.getBoundingBox(
                    b.minX + 0.01,
                    b.minY + 0.01,
                    b.minZ + 0.01,
                    b.maxX - 0.01,
                    b.minY + TALL,
                    b.maxZ - 0.01))
            .isEmpty();
    }

    /** A recovered player can crawl out of a low gap instead of remaining locked in physics. */
    static void crawlOut(EntityPlayerMP p) {
        crawling.add(p.getUniqueID());
        height(p, LOW);
        UkyCorpses.net.sendToDimension(MsgDowned.crawling(p.getEntityId()), p.dimension);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        now = MinecraftServer.getServer()
            .getTickCounter();
        cooldown.entrySet()
            .removeIf(entry -> entry.getValue() <= now);
        respawnProtection.entrySet()
            .removeIf(entry -> entry.getValue() <= now);
        if (!crawling.isEmpty()) {
            Map<UUID, EntityPlayerMP> online = online();
            for (Iterator<UUID> it = crawling.iterator(); it.hasNext();) {
                EntityPlayerMP p = online.get(it.next());
                if (p == null || p.isDead) it.remove();
                else if (room(p)) { // stands up
                    it.remove();
                    height(p, TALL);
                    finish(p);
                } else {
                    height(p, LOW);
                    if (now % 10 == 0) UkyCorpses.net.sendToDimension(MsgDowned.crawling(p.getEntityId()), p.dimension);
                }
            }
        }
        if (downed.isEmpty()) return;
        java.util.List<EntityPlayerMP> selfReady = new java.util.ArrayList<>();
        Map<UUID, EntityPlayerMP> online = online();
        for (Iterator<Map.Entry<UUID, State>> it = downed.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<UUID, State> en = it.next();
            EntityPlayerMP p = online.get(en.getKey());
            State st = en.getValue();
            if (p == null || p.isDead) { // gone without the logout event
                it.remove();
                if (p != null) {
                    clearPresentation(p, st);
                    finish(p);
                }
                continue;
            }
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            p.fallDistance = 0;
            height(p, LOW);

            if (st.finishPending) {
                it.remove();
                die(p, st, "bleed", 4);
                continue;
            }
            if (st.selfItem != null) {
                if (!Medicine.selfAdrenaline || p.inventory.getStackInSlot(st.selfSlot) != st.selfItem
                    || st.selfItem.stackSize <= 0) {
                    st.selfItem = null;
                } else if (++st.selfTicks >= st.selfTotal) {
                    selfReady.add(p);
                    continue;
                }
            }
            st.giveUpHeld = OriginalGameplay.advanceGiveUp(st.giveUpHeld, now - st.giveUpInputAt);
            if (st.giveUpHeld >= OriginalGameplay.GIVE_UP_TICKS) {
                it.remove();
                die(p, st, "surrender", 2);
                continue;
            }
            boolean activeRevive = st.reviver != null && now - st.reviveInputAt <= OriginalGameplay.REVIVE_TIMEOUT
                && online.get(st.reviver.getUniqueID()) == st.reviver
                && canRevive(st.reviver, p)
                && !Reactions.isDragged(p);
            st.timerPaused = activeRevive;
            if (st.reviveProgress > 0 || activeRevive) st.reviveProgress = OriginalGameplay.advanceRevive(
                st.reviveProgress,
                activeRevive,
                OriginalGameplay.reviveSeconds,
                OriginalGameplay.reviveDecaySeconds);
            if (st.reviveProgress >= 1) {
                it.remove();
                clearPresentation(p, st);
                revive(p, false);
                continue;
            }
            if (st.reviveProgress <= 0 && !activeRevive) st.reviver = null;
            if (!activeRevive && CorpseConfig.crawlDowned) {
                boolean nearby = canSelfRescue(p);
                for (EntityPlayerMP other : online.values()) if (other != p && other.worldObj == p.worldObj
                    && other.isEntityAlive()
                    && !is(other)
                    && other.getDistanceSqToEntity(p) <= (double) CorpseConfig.friendRadius * CorpseConfig.friendRadius)
                    nearby = true;
                if (nearby) st.alone = -1;
                else if (st.alone < 0) st.alone = CorpseConfig.aloneSeconds * 20;
                else if (--st.alone <= 0) {
                    it.remove();
                    die(p, st, "bleed", 4);
                    continue;
                }
            }
            if (!activeRevive) st.left--;
            if (st.left <= 0) {
                it.remove();
                die(p, st, "bleed", 4);
                continue;
            }
            if (now % 4 == 0 || st.left <= 20) send(p, st);
        }
        for (EntityPlayerMP player : selfReady) {
            State st = downed.get(player.getUniqueID());
            if (st == null || st.selfItem == null) continue;
            net.minecraft.item.ItemStack item = st.selfItem;
            item.getItem()
                .onEaten(item, player.worldObj, player);
            if (item.stackSize <= 0) player.inventory.setInventorySlotContents(st.selfSlot, null);
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }
    }

    public static void selfAdrenaline(EntityPlayerMP player, int preferredSlot) {
        State state = downed.get(player.getUniqueID());
        if (state == null || !Medicine.selfAdrenaline || state.selfItem != null) return;
        int slot = preferredSlot >= 0 && preferredSlot < player.inventory.mainInventory.length
            && Medicine.isAdrenaline(player.inventory.mainInventory[preferredSlot]) ? preferredSlot
                : Medicine.adrenalineSlot(player);
        if (slot < 0) return;
        state.selfSlot = slot;
        state.selfItem = player.inventory.mainInventory[slot];
        state.selfTicks = 0;
        state.selfTotal = Math.max(1, state.selfItem.getMaxItemUseDuration());
        send(player, state);
    }

    private static Map<UUID, EntityPlayerMP> online() {
        Map<UUID, EntityPlayerMP> online = new HashMap<>();
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            online.put(p.getUniqueID(), p);
        }
        return online;
    }

    private static boolean crawl(State st) {
        return st.pose != EntityCorpse.SIT;
    }

    private static boolean canRevive(EntityPlayerMP r, EntityPlayerMP p) {
        return r != p && r.isEntityAlive()
            && r.worldObj == p.worldObj
            && !downed.containsKey(r.getUniqueID())
            && !Reactions.isDragging(r)
            && distanceToBody(r, p) <= 2.5 * 2.5;
    }

    private static double distanceToBody(EntityPlayer r, EntityPlayer p) {
        com.uky.corpses.corpse.Skeleton.Shape physical = Reactions.physicalShape(p, 1);
        if (physical == null) return r.getDistanceSqToEntity(p);
        double[] torso = physical.parts[com.uky.corpses.corpse.Skeleton.BODY].apply(0, 6, 0, new double[3]);
        return r.getDistanceSq(p.posX + torso[0], p.posY + torso[1], p.posZ + torso[2]);
    }

    /** Syringe, injector, defibrillator: up right now. False if p wasn't down. */
    public static boolean reviveNow(EntityPlayerMP p, boolean adrenaline) {
        State state = downed.remove(p.getUniqueID());
        if (state == null) return false;
        clearPresentation(p, state);
        revive(p, adrenaline);
        return true;
    }

    private static void revive(EntityPlayerMP p, boolean adrenaline) {
        Reactions.revived(p);
        if (room(p)) {
            height(p, TALL);
            finish(p);
        } else { // under something low: up, but crawling out
            crawling.add(p.getUniqueID());
            UkyCorpses.net.sendToDimension(MsgDowned.crawling(p.getEntityId()), p.dimension);
        }
        p.setHealth(Math.min(p.getMaxHealth(), OriginalGameplay.reviveHearts * 2));
        cooldown.put(p.getUniqueID(), now + OriginalGameplay.secondChanceCooldownSeconds * 20);
        Medicine.onRevived(p, adrenaline);
    }

    /** How the last downed death of each player ended ("bleed", "surrender"), for their death camera's card. */
    private static final Map<UUID, String> why = new HashMap<>();

    /** How p's death that is happening now ended if they were lying downed; null otherwise. Consumed. */
    static String takeWhy(EntityPlayer p) {
        return why.remove(p.getUniqueID());
    }

    private static final String[] TRACKER = { "_combatTracker", "field_94063_bt" };

    /**
     * Their own death, with one of ukycorpses.death.(why).1..variants instead of vanilla's line. 1.7.10 has no
     * showDeathMessages rule: vanilla always says what the combat log makes of the death, and the log has long
     * forgotten
     * the blow by now ("X died"). So for the length of the death the log is one that says our line.
     */
    private static void die(EntityPlayerMP p, State st, String why, int variants) {
        clearPresentation(p, st);
        cooldown.remove(p.getUniqueID());
        respawnProtection.remove(p.getUniqueID());
        remember(p, st);
        died(p);
        Downed.why.put(p.getUniqueID(), why);
        IChatComponent line = new ChatComponentTranslation(
            "ukycorpses.death." + why + "." + (rand.nextInt(variants) + 1),
            p.func_145748_c_());
        CombatTracker kept = null;
        try {
            kept = ObfuscationReflectionHelper.getPrivateValue(EntityLivingBase.class, p, TRACKER);
            ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, p, new Says(p, line), TRACKER);
        } catch (RuntimeException ex) {
            UkyCorpses.LOG.warn("Death line for {} left to vanilla", p.getCommandSenderName(), ex);
        }
        dying.add(p.getUniqueID());
        try {
            p.setHealth(0);
            p.onDeath(st.source);
        } finally {
            if (kept != null) ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, p, kept, TRACKER);
            dying.remove(p.getUniqueID());
            Downed.why.remove(p.getUniqueID());
        }
    }

    /** A combat log that has only one thing to say about the death. */
    private static final class Says extends CombatTracker {

        private final IChatComponent line;

        Says(EntityLivingBase e, IChatComponent line) {
            super(e);
            this.line = line;
        }

        @Override
        public IChatComponent func_151521_b() {
            return line;
        }
    }

    private static void remember(EntityPlayerMP p, State st) {
        lastSpot.put(
            p.getUniqueID(),
            new double[] { p.posX, p.boundingBox.minY, p.posZ, st.pose == EntityCorpse.SIT ? st.yaw : p.renderYawOffset,
                st.pose, 1 }); // sixth element: was down
    }

    /** Clients keep drawing them as they lay until the body that replaces them shows up. */
    private static void died(EntityPlayerMP p) {
        UkyCorpses.net.sendToAll(MsgDowned.died(p.getEntityId()));
    }

    private static void finish(EntityPlayerMP p) {
        UkyCorpses.net.sendToAll(MsgDowned.gone(p.getEntityId()));
    }

    private static State state(Entity p) {
        return p instanceof EntityPlayerMP ? downed.get(p.getUniqueID()) : null;
    }

    /** Short configurable grace period; subsequent damage follows vanilla rules. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent e) {
        State state = state(e.entityLiving);
        if (state != null && MinecraftServer.getServer()
            .getTickCounter() - state.downAt < OriginalGameplay.downedInvulnerableTicks) e.setCanceled(true);
    }

    /** Runs at vanilla's final health write, after armor, resistance, absorption and hit cooldown. */
    public static boolean damaged(EntityLivingBase target, DamageSource source, float healthAfter) {
        State state = state(target);
        if (state == null) return false;
        float damage = Math.max(0, target.getHealth() - healthAfter);
        if (damage > 0 && Float.isFinite(damage)) {
            state.knocked = Math.max(0, state.knocked - damage);
            state.selfItem = null;
            state.source = source;
            state.finishPending = state.knocked <= 0;
            send((EntityPlayerMP) target, state);
        }
        return true;
    }

    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (CorpseConfig.mobsIgnoreDowned && is(e.target) && e.entityLiving instanceof EntityLiving)
            ((EntityLiving) e.entityLiving).setAttackTarget(null);
    }

    /** Bare hand on a downed player starts getting them up; it carries on by itself while you stay close. */
    @SubscribeEvent
    public void onInteract(EntityInteractEvent e) {
        if (Reactions.isDragging(e.entityPlayer) || is(e.entityPlayer) && OriginalGameplay.blockActionsWhileKnocked) {
            e.setCanceled(true);
            return;
        }
        if (!(e.entityPlayer instanceof EntityPlayerMP)) return;
        State st = state(e.target);
        if (st == null || e.entityPlayer.isSneaking() || !CorpseConfig.friendRevive) return;
        e.setCanceled(true);
        EntityPlayerMP r = (EntityPlayerMP) e.entityPlayer;
        reviveInput(r, e.target.getEntityId());
    }

    @SubscribeEvent
    public void onUse(PlayerInteractEvent e) {
        if (is(e.entityPlayer) && OriginalGameplay.blockActionsWhileKnocked
            && !(e.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK && Medicine.maySelfAdrenaline(e.entityPlayer))
            || Reactions.isDragging(e.entityPlayer)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onHit(AttackEntityEvent e) {
        if (is(e.entityPlayer) || Reactions.isDragging(e.entityPlayer)) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHeal(LivingHealEvent event) {
        if (state(event.entityLiving) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        State old = downed.remove(player.getUniqueID());
        if (old != null) clearPresentation(player, old);
        crawling.remove(player.getUniqueID());
        cooldown.remove(player.getUniqueID());
        respawnProtection.put(
            player.getUniqueID(),
            MinecraftServer.getServer()
                .getTickCounter() + 40);
        height(player, TALL);
        finish(player);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote || event.world.provider.dimensionId != 0) return;
        downed.clear();
        crawling.clear();
        lastSpot.clear();
        dying.clear();
        why.clear();
        cooldown.clear();
        respawnProtection.clear();
        now = 0;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        crawling.remove(e.player.getUniqueID());
        height(e.player, TALL);
        State st = downed.remove(e.player.getUniqueID());
        if (st != null) die((EntityPlayerMP) e.player, st, "bleed", 4);
    }

    public static void giveUpInput(EntityPlayerMP player, boolean holding) {
        State state = downed.get(player.getUniqueID());
        if (state == null || player.isDead) return;
        if (holding) state.giveUpInputAt = MinecraftServer.getServer()
            .getTickCounter();
        else {
            state.giveUpHeld = 0;
            state.giveUpInputAt = -1000;
        }
    }

    public static void reviveInput(EntityPlayerMP player, int targetId) {
        if (!CorpseConfig.friendRevive) return;
        Entity target = player.worldObj.getEntityByID(targetId);
        if (!(target instanceof EntityPlayerMP)) return;
        State state = downed.get(target.getUniqueID());
        if (state == null || !canRevive(player, (EntityPlayerMP) target) || Reactions.isDragged((EntityPlayer) target))
            return;
        state.reviver = player;
        state.reviveInputAt = MinecraftServer.getServer()
            .getTickCounter();
    }

    private static void send(EntityPlayerMP p, State st) {
        MsgDowned m = new MsgDowned(
            p.getEntityId(),
            st.pose,
            st.yaw,
            crawl(st),
            st.left,
            st.alone,
            st.selfItem != null ? st.selfTicks * 100 / st.selfTotal
                : st.reviveProgress <= 0 ? -1 : (int) (st.reviveProgress * 100),
            st.selfItem != null ? p.getEntityId() : st.reviver == null ? -1 : st.reviver.getEntityId());
        m.blast = st.blast && now - st.downAt < 40;
        UkyCorpses.net.sendToDimension(m, p.dimension);
        MsgOriginalState detail = new MsgOriginalState(
            p,
            now,
            true,
            st.knocked,
            st.maxKnocked,
            (float) st.reviveProgress,
            st.timerPaused,
            st.giveUpHeld,
            st.reviver == null ? -1 : st.reviver.getEntityId());
        UkyCorpses.net.sendTo(detail, p);
        if (st.reviver != null && st.reviver != p) UkyCorpses.net.sendTo(detail, st.reviver);
    }

    private static void clearPresentation(EntityPlayerMP player, State state) {
        MsgOriginalState packet = new MsgOriginalState(player, now, false, 0, 0, 0, false, 0, -1);
        UkyCorpses.net.sendTo(packet, player);
        if (state.reviver != null && state.reviver != player) UkyCorpses.net.sendTo(packet, state.reviver);
    }
}
