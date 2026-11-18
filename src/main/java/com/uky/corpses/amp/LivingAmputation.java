package com.uky.corpses.amp;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.uky.corpses.Reactions;
import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.relauncher.Side;

/** Installed AMP's live/death rolls, persistent losses, headless delay and tracker sync. */
public final class LivingAmputation {

    private static final Map<EntityLivingBase, Last> lastHits = new WeakHashMap<>();
    private static boolean registered;

    private static final class Last {

        final float blow, healthLoss, healthBefore;
        final long at;
        final boolean sharp;

        Last(float blow, float loss, float before, boolean sharp) {
            this.blow = blow;
            healthLoss = loss;
            healthBefore = before;
            this.sharp = sharp;
            at = System.currentTimeMillis();
        }
    }

    public static void init() {
        if (registered) return;
        registered = true;
        UkyCorpses.net.registerMessage(MsgMask.Handler.class, MsgMask.class, 95, Side.CLIENT);
        EntityRegistry
            .registerModEntity(EntityDetachedPart.class, "detached_part", 1, UkyCorpses.instance, 80, 1, false);
        LivingAmputation events = new LivingAmputation();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        LoosePhysics physics = new LoosePhysics();
        MinecraftForge.EVENT_BUS.register(physics);
        FMLCommonHandler.instance()
            .bus()
            .register(physics);
    }

    public static boolean eligible(EntityLivingBase victim) {
        if (victim == null || victim.worldObj == null || victim.worldObj.isRemote) return false;
        if (victim instanceof EntityPlayer) return AmputationConfig.ENABLE_FOR_PLAYERS.get();
        // shortcut: the 1.7 adapters currently provide standard adult biped stump geometry; add other rigs before
        // enabling them.
        return AmputationConfig.ENABLE_FOR_MOBS.get()
            && (victim instanceof EntityZombie || victim instanceof EntitySkeleton)
            && !victim.isChild();
    }

    /** Called immediately before vanilla subtracts health, after armor, effects and absorption. */
    public static void damage(EntityLivingBase victim, DamageSource source, float blow, float healthLoss) {
        if (!eligible(victim) || !Float.isFinite(blow) || !Float.isFinite(healthLoss) || blow <= 0 || healthLoss < 0)
            return;
        HitResolver.Hit hit = HitResolver.resolve(victim, source);
        boolean sharp = hit != null && hit.sharp;
        lastHits.put(victim, new Last(blow, healthLoss, victim.getHealth(), sharp));
        if (!AmputationConfig.ENABLE_ALIVE.get() || healthLoss >= victim.getHealth()
            || hit == null
            || !canLose(victim, hit.part, AmputationConfig.ALIVE_MAX_LIMBS_PER_ENTITY.get())) return;
        if (hit.part == Part.HEAD && !AmputationState.allowedHead(
            victim.getHealth(),
            healthLoss,
            victim.getMaxHealth(),
            AmputationConfig.ALIVE_DECAPITATION_MAX_HEALTH.get())) return;
        double chance = AmputationRoll.aliveChance(hit.part, blow, sharp, armor(victim, hit.part));
        if (AmputationRoll.roll(victim.getRNG(), chance)
            && detach(victim, hit, AmputationConfig.ALIVE_LIMB_LIFETIME.get())) {
            AmputationState.markLost(victim, hit.part);
            if (hit.part == Part.HEAD) AmputationState.schedule(victim);
            MsgMask.send(victim);
        }
    }

    private static boolean canLose(EntityLivingBase victim, Part part, int maximum) {
        return part != null && part.isSeverable()
            && !AmputationState.hasLost(victim, part)
            && Integer.bitCount(AmputationState.mask(victim)) < maximum;
    }

    private static boolean detach(EntityLivingBase victim, HitResolver.Hit hit, int lifetime) {
        if (victim instanceof EntityPlayerMP && Reactions.is(victim)) {
            Vec3 push = push(victim.getRNG(), hit.part, Vec3.createVectorHelper(0, 0, 0), hit.direction);
            return Reactions.severPart(
                (EntityPlayerMP) victim,
                hit.part.index,
                new javax.vecmath.Vector3f((float) push.xCoord, (float) push.yCoord, (float) push.zCoord));
        }
        return EntityDetachedPart.launch(victim, hit.part, hit.direction, lifetime);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void death(LivingDeathEvent event) {
        EntityLivingBase victim = event.entityLiving;
        Last last = lastHits.remove(victim);
        if (!eligible(victim) || !AmputationConfig.ENABLE_ON_DEATH.get()) return;
        HitResolver.Hit hit = HitResolver.resolve(victim, event.source);
        if (hit == null || !canLose(victim, hit.part, AmputationConfig.DEATH_MAX_LIMBS.get())) return;
        boolean fresh = last != null && System.currentTimeMillis() - last.at <= 250;
        double chance = AmputationRoll.deathChance(
            hit.part,
            fresh ? last.blow : 0,
            fresh ? AmputationState.overkill(last.healthLoss, last.healthBefore) : 0,
            fresh ? last.sharp : hit.sharp,
            armor(victim, hit.part));
        if (AmputationRoll.roll(victim.getRNG(), chance)
            && detach(victim, hit, AmputationConfig.DEATH_LIMB_LIFETIME.get())) {
            AmputationState.markLost(victim, hit.part);
            MsgMask.send(victim);
        }
    }

    @SubscribeEvent
    public void tick(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if (victim.worldObj.isRemote) return;
        if (victim.getEntityData()
            .hasKey(AmputationState.DYING, 4)
            && victim.worldObj.getTotalWorldTime() >= victim.getEntityData()
                .getLong(AmputationState.DYING)) {
            victim.getEntityData()
                .removeTag(AmputationState.DYING);
            victim.attackEntityFrom(DamageSource.outOfWorld, victim.getHealth() + 1);
        }
    }

    @SubscribeEvent
    public void tracking(PlayerEvent.StartTracking event) {
        if (event.entityPlayer instanceof EntityPlayerMP && event.target instanceof EntityLivingBase)
            UkyCorpses.net.sendTo(new MsgMask((EntityLivingBase) event.target), (EntityPlayerMP) event.entityPlayer);
    }

    @SubscribeEvent
    public void respawn(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
        if (event.player.worldObj.isRemote) return;
        AmputationState.clear(event.player);
        MsgMask.send(event.player);
    }

    static double armor(EntityLivingBase entity, Part part) {
        double points, full;
        if (part == Part.HEAD) {
            points = points(entity.getEquipmentInSlot(4));
            full = AmputationConfig.FULL_HELMET_ARMOR.get();
        } else if (part == Part.LEFT_LEG || part == Part.RIGHT_LEG) {
            points = points(entity.getEquipmentInSlot(1)) + points(entity.getEquipmentInSlot(2));
            full = AmputationConfig.FULL_LEG_ARMOR.get();
        } else {
            points = points(entity.getEquipmentInSlot(3));
            full = AmputationConfig.FULL_CHESTPLATE_ARMOR.get();
        }
        return full <= 0 ? 0 : Math.max(0, Math.min(1, points / full));
    }

    private static int points(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmor ? ((ItemArmor) stack.getItem()).damageReduceAmount
            : 0;
    }

    static Vec3 push(java.util.Random random, Part part, Vec3 outward, Vec3 direction) {
        double hop = AmputationConfig.LIMB_HOP_SPEED.get() * (.6 + .8 * random.nextDouble()),
            force = AmputationConfig.LIMB_DETACH_FORCE.get() * (.7 + .6 * random.nextDouble());
        double x = outward.xCoord * force * .5, y = outward.yCoord * force * .5, z = outward.zCoord * force * .5;
        if (direction != null && direction.lengthVector() > .001) {
            Vec3 blow = direction.normalize();
            x += blow.xCoord * force;
            y += blow.yCoord * force * .3;
            z += blow.zCoord * force;
        }
        double scatter = hop * .35;
        x += (random.nextDouble() - .5) * 2 * scatter;
        y += hop;
        z += (random.nextDouble() - .5) * 2 * scatter;
        double scale = part == Part.HEAD ? .55 : 1;
        x *= scale;
        y *= scale;
        z *= scale;
        double length = Math.sqrt(x * x + y * y + z * z), max = AmputationConfig.LIMB_MAX_LAUNCH_SPEED.get();
        if (length > max) {
            x *= max / length;
            y *= max / length;
            z *= max / length;
        }
        return Vec3.createVectorHelper(x, y, z);
    }

    private LivingAmputation() {}
}
