package com.uky.corpses;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
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
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.uky.corpses.medicine.Medicine;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Death Engine's downed state, ported. A lethal hit drops the player instead: face down on the floor (or slumped
 * against a wall right next to them; never in water), crawling slowly. They bleed out unless someone
 * right-clicks them with an empty hand and stays close until they're up. With nobody around they go fast. X gives up.
 *
 * While down: mobs lose interest and can't hurt them; players, lava and drowning can (and finish them). They can't
 * fight, use or break anything. Logging out while down is dying.
 */
public final class Downed {

    /** Lying down they're this tall: they crawl under anything a block high. */
    public static final float LOW = 0.6F, TALL = 1.8F;
    static final int CRAWL_POSE = EntityCorpse.PRONE;
    /** Ticks after a blast knocked them down during which it may still throw them up. */
    private static final int THROWN = 15;

    private static final class State {

        DamageSource source;
        int left, alone, pose;
        float yaw;
        double sx, sz;
        EntityPlayerMP reviver;
        int revive;
        boolean blast;
        int downAt;
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
    private static final Queue<EntityPlayerMP> givingUp = new ConcurrentLinkedQueue<>();
    private static final Random rand = new Random();
    private static int now;

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
        s = spot(p);
        if (s[4] == EntityCorpse.PRONE) s[4] = fallPose(p, source);
        return s;
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
            remember(p, st);
            died(p);
            return;
        }
        if (!CorpseConfig.downed || p.capabilities.isCreativeMode) return;
        e.setCanceled(true);
        down(p, e.source);
    }

    /** Knocked down by source: also /corpses down. */
    static void down(EntityPlayerMP p, DamageSource source) {
        p.setHealth(CorpseConfig.downedHealth);
        p.setAbsorptionAmount(0);
        p.extinguish();
        p.removePotionEffect(Potion.wither.id);
        p.removePotionEffect(Potion.poison.id);
        p.hurtResistantTime = 20;
        if (p.ridingEntity != null) p.mountEntity(null);
        double[] s = spot(p);
        State st = new State();
        st.source = source;
        st.left = CorpseConfig.bleedSeconds * 20;
        // counted from the first moment: the first packet must already say "alone", or the HUD flashes up
        st.alone = CorpseConfig.aloneSeconds * 20 - (friendNear(p, online()) ? 0 : 1);
        st.pose = (int) s[4];
        st.yaw = (float) s[3];
        if (st.pose == EntityCorpse.SIT)
            p.playerNetServerHandler.setPlayerLocation(s[0], s[1], s[2], st.yaw, p.rotationPitch);
        st.sx = p.posX;
        st.sz = p.posZ;
        st.blast = EntityCorpse.isExplosion(source);
        st.downAt = now;
        crawling.remove(p.getUniqueID());
        downed.put(p.getUniqueID(), st);
        height(p, LOW);
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(32, 16, 32))) {
            EntityLiving m = (EntityLiving) o;
            if (m.getAttackTarget() == p) m.setAttackTarget(null);
        }
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

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (EntityPlayerMP p; (p = givingUp.poll()) != null;) {
            State st = downed.remove(p.getUniqueID());
            if (st != null) die(p, st, "surrender", 2);
        }
        now++;
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
        Map<UUID, EntityPlayerMP> online = online();
        for (Iterator<Map.Entry<UUID, State>> it = downed.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<UUID, State> en = it.next();
            EntityPlayerMP p = online.get(en.getKey());
            State st = en.getValue();
            if (p == null || p.isDead) { // gone without the logout event
                it.remove();
                if (p != null) finish(p);
                continue;
            }
            double dx = p.posX - st.sx, dz = p.posZ - st.sz;
            if (st.pose == EntityCorpse.SIT && dx * dx + dz * dz > 0.25) st.pose = EntityCorpse.PRONE; // knocked off
            if (now % 20 == 1) {
                effect(p, Potion.moveSlowdown, crawl(st) ? 1 : 6);
                effect(p, Potion.weakness, 10);
                effect(p, Potion.digSlowdown, 4);
                if (CorpseConfig.downedBlindness) effect(p, Potion.blindness, 0);
            }
            if (p.getHealth() > CorpseConfig.downedHealth) p.setHealth(CorpseConfig.downedHealth); // no healing out
            if (p.motionY > 0 && !(st.blast && now - st.downAt < THROWN)) p.motionY = 0; // a blast may still throw them
            p.fallDistance = 0;
            height(p, LOW);

            if (st.reviver != null && !canRevive(st.reviver, p)) stopRevive(st);
            if (st.reviver != null && ++st.revive >= CorpseConfig.reviveSeconds * 20) {
                it.remove();
                revive(p, false);
                continue;
            }
            st.left--;
            st.alone = friendNear(p, online) ? CorpseConfig.aloneSeconds * 20 : st.alone - 1;
            if (st.left <= 0 || st.alone <= 0) {
                it.remove();
                die(p, st, "bleed", 4);
                continue;
            }
            if (now % 4 == 0 || st.left <= 20) send(p, st);
        }
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

    private static void effect(EntityPlayerMP p, Potion potion, int amp) {
        p.addPotionEffect(new PotionEffect(potion.id, 40, amp, true));
    }

    private static boolean friendNear(EntityPlayerMP p, Map<UUID, EntityPlayerMP> online) {
        double r = CorpseConfig.friendRadius;
        for (EntityPlayerMP o : online.values()) if (o != p && !o.isDead
            && o.worldObj == p.worldObj
            && !downed.containsKey(o.getUniqueID())
            && o.getDistanceSqToEntity(p) <= r * r) return true;
        return false;
    }

    private static boolean canRevive(EntityPlayerMP r, EntityPlayerMP p) {
        return !r.isDead && r.worldObj == p.worldObj
            && !downed.containsKey(r.getUniqueID())
            && r.getDistanceSqToEntity(p) <= 3.5 * 3.5;
    }

    private static void stopRevive(State st) {
        st.reviver = null;
        st.revive = 0;
    }

    /** Syringe, injector, defibrillator: up right now. False if p wasn't down. */
    public static boolean reviveNow(EntityPlayerMP p, boolean adrenaline) {
        if (downed.remove(p.getUniqueID()) == null) return false;
        revive(p, adrenaline);
        return true;
    }

    private static void revive(EntityPlayerMP p, boolean adrenaline) {
        if (room(p)) {
            height(p, TALL);
            finish(p);
        } else { // under something low: up, but crawling out
            crawling.add(p.getUniqueID());
            UkyCorpses.net.sendToDimension(MsgDowned.crawling(p.getEntityId()), p.dimension);
        }
        p.setHealth(CorpseConfig.reviveHealth);
        for (Potion potion : new Potion[] { Potion.moveSlowdown, Potion.weakness, Potion.digSlowdown,
            Potion.blindness }) p.removePotionEffect(potion.id);
        p.worldObj.playSoundAtEntity(p, "random.levelup", 0.6F, 1.4F);
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
        double[] on = stood(p, p.boundingBox.minY); // crawled to the edge: on it, not over the drop
        lastSpot.put(
            p.getUniqueID(),
            new double[] { on[0], p.boundingBox.minY, on[1], st.pose == EntityCorpse.SIT ? st.yaw : p.renderYawOffset,
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

    /** Mobs and the world can't touch them. Players, the void, lava and drowning still can. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent e) {
        DamageSource s = e.source;
        if (state(e.entityLiving) == null || !CorpseConfig.mobsIgnoreDowned
            || s == DamageSource.outOfWorld
            || s == DamageSource.lava
            || s == DamageSource.drown) return;
        if (!(s.getEntity() instanceof EntityPlayer || s.getSourceOfDamage() instanceof EntityPlayer))
            e.setCanceled(true);
    }

    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (CorpseConfig.mobsIgnoreDowned && is(e.target) && e.entityLiving instanceof EntityLiving)
            ((EntityLiving) e.entityLiving).setAttackTarget(null);
    }

    /** Bare hand on a downed player starts getting them up; it carries on by itself while you stay close. */
    @SubscribeEvent
    public void onInteract(EntityInteractEvent e) {
        if (is(e.entityPlayer)) {
            e.setCanceled(true);
            return;
        }
        if (!(e.entityPlayer instanceof EntityPlayerMP)) return;
        State st = state(e.target);
        if (st == null || e.entityPlayer.getHeldItem() != null || !CorpseConfig.friendRevive) return;
        e.setCanceled(true);
        EntityPlayerMP r = (EntityPlayerMP) e.entityPlayer;
        if (st.reviver != null) return; // already on it, or someone else is
        for (State other : downed.values()) if (other.reviver == r) stopRevive(other); // one at a time
        st.reviver = r;
    }

    @SubscribeEvent
    public void onUse(PlayerInteractEvent e) {
        if (is(e.entityPlayer)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onHit(AttackEntityEvent e) {
        if (is(e.entityPlayer)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        crawling.remove(e.player.getUniqueID());
        height(e.player, TALL);
        State st = downed.remove(e.player.getUniqueID());
        if (st != null) die((EntityPlayerMP) e.player, st, "bleed", 4);
    }

    /** MsgGiveUp, netty thread: X while down. Done on the next server tick. */
    static void giveUp(EntityPlayerMP p) {
        givingUp.add(p);
    }

    private static void send(EntityPlayerMP p, State st) {
        MsgDowned m = new MsgDowned(
            p.getEntityId(),
            st.pose,
            st.yaw,
            crawl(st),
            st.left,
            st.alone < CorpseConfig.aloneSeconds * 20 ? st.alone : -1,
            st.reviver == null ? -1 : st.revive * 100 / (CorpseConfig.reviveSeconds * 20),
            st.reviver == null ? -1 : st.reviver.getEntityId());
        m.blast = st.blast && now - st.downAt < 40;
        UkyCorpses.net.sendToDimension(m, p.dimension);
    }
}
