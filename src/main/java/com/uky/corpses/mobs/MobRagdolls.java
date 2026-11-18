package com.uky.corpses.mobs;

import java.util.Map;
import java.util.WeakHashMap;

import javax.vecmath.Vector3f;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.corpse.Ragdoll;
import com.uky.corpses.mobs.physics.MobRigType;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.relauncher.Side;

/** Server-owned visual ragdolls; cancelling a death or changing loot is never part of this bridge. */
public final class MobRagdolls {

    private static boolean registered;
    private static final Map<EntityLivingBase, Hit> hits = new WeakHashMap<>();
    private static final Map<EntityLivingBase, Blast> blasts = new WeakHashMap<>();

    private static final class Hit {

        final World world;
        final long tick;
        final Vector3f motion;
        Vector3f direction;
        boolean melee;

        Hit(EntityLivingBase entity) {
            world = entity.worldObj;
            tick = world.getTotalWorldTime();
            motion = new Vector3f((float) entity.motionX, (float) entity.motionY, (float) entity.motionZ);
        }
    }

    private static final class Blast {

        final World world;
        final long tick;
        final double[] center;

        Blast(World world, double x, double y, double z) {
            this.world = world;
            tick = world.getTotalWorldTime();
            center = new double[] { x, y, z };
        }
    }

    public static void init() {
        if (registered) return;
        registered = true;
        UkyCorpses.net.registerMessage(MsgMobRagdoll.Handler.class, MsgMobRagdoll.class, 96, Side.CLIENT);
        UkyCorpses.net.registerMessage(MsgMobHit.Handler.class, MsgMobHit.class, 97, Side.SERVER);
        EntityRegistry.registerModEntity(EntityMobRagdoll.class, "mob_ragdoll", 11, UkyCorpses.instance, 128, 2, false);
        MinecraftForge.EVENT_BUS.register(new MobRagdolls());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeHit(LivingHurtEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if (victim.worldObj.isRemote || victim instanceof EntityPlayer
            || MobTypes.type(victim) == MobRigType.UNSUPPORTED) return;
        Hit hit = hits.get(victim);
        if (hit == null || hit.world != victim.worldObj || hit.tick != victim.worldObj.getTotalWorldTime()) {
            hit = new Hit(victim);
            hits.put(victim, hit);
        }
        Entity direct = event.source.getSourceOfDamage(), attacker = event.source.getEntity();
        Vector3f direction = null;
        if (direct != null && event.source.isProjectile())
            direction = new Vector3f((float) direct.motionX, (float) direct.motionY, (float) direct.motionZ);
        else if (attacker instanceof EntityLivingBase && attacker != victim) {
            Vec3 look = attacker.getLookVec();
            direction = look == null ? null
                : new Vector3f((float) look.xCoord, (float) look.yCoord, (float) look.zCoord);
            if (direction == null || direction.lengthSquared() < 1e-6F) direction = new Vector3f(
                (float) (victim.posX - attacker.posX),
                (float) (victim.posY - attacker.posY),
                (float) (victim.posZ - attacker.posZ));
            hit.melee = true;
        }
        if (direction != null && direction.lengthSquared() >= 1e-6F) {
            direction.normalize();
            hit.direction = direction;
        }
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate event) {
        if (event.world.isRemote) return;
        for (Object value : event.getAffectedEntities())
            if (value instanceof EntityLivingBase && !(value instanceof EntityPlayer)) blasts.put(
                (EntityLivingBase) value,
                new Blast(
                    event.world,
                    event.explosion.explosionX,
                    event.explosion.explosionY,
                    event.explosion.explosionZ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if (victim.worldObj.isRemote || victim instanceof EntityPlayer
            || !Ragdoll.enabled
            || !com.uky.corpses.corpse.PhysicsConfig.mobRagdolls
            || MobTypes.type(victim) == MobRigType.UNSUPPORTED) return;
        Hit hit = hits.remove(victim);
        if (hit != null && (hit.world != victim.worldObj || victim.worldObj.getTotalWorldTime() - hit.tick > 2))
            hit = null;
        Vector3f motion = hit == null
            ? new Vector3f((float) victim.motionX, (float) victim.motionY, (float) victim.motionZ)
            : hit.motion;
        Vector3f travelled = new Vector3f(
            (float) (victim.posX - victim.lastTickPosX),
            (float) (victim.posY - victim.lastTickPosY),
            (float) (victim.posZ - victim.lastTickPosZ));
        Vector3f velocity = inherited(motion, travelled);
        if (!event.source.isExplosion() && event.source.getEntity() instanceof EntityLivingBase) {
            Entity attacker = event.source.getEntity();
            Vec3 look = attacker.getLookVec();
            Vector3f heading = look == null
                ? new Vector3f((float) (victim.posX - attacker.posX), 0, (float) (victim.posZ - attacker.posZ))
                : new Vector3f((float) look.xCoord, 0, (float) look.zCoord);
            velocity = attackerFallback(velocity, heading);
        }
        EntityMobRagdoll body = EntityMobRagdoll.from(victim, velocity);
        if (body == null) return;
        Vector3f impulse = hit == null || hit.direction == null || event.source.isExplosion() ? null
            : killingImpulse(hit.direction, hit.melee);
        Blast blast = blasts.remove(victim);
        if (blast != null && (blast.world != victim.worldObj || victim.worldObj.getTotalWorldTime() - blast.tick > 2))
            blast = null;
        double[] center = event.source.isExplosion() && blast != null ? blast.center : null;
        Vector3f kick = center == null ? null
            : explosionKick(victim.posX, victim.posY + victim.height * .5, victim.posZ, victim.rotationYaw, center);
        body.applyBirthMotion(impulse, kick, center, victim.onGround);
        if (!victim.worldObj.spawnEntityInWorld(body)) body.setDead();
    }

    public static boolean hasBody(EntityLivingBase source) {
        if (source.deathTime <= 0 && source.getHealth() > 0) return false;
        String type = net.minecraft.entity.EntityList.getEntityString(source);
        for (Object value : source.worldObj.loadedEntityList) if (value instanceof EntityMobRagdoll) {
            EntityMobRagdoll body = (EntityMobRagdoll) value;
            if (!body.isDead && body.sourceEntityId() == source.getEntityId()
                && body.sourceType()
                    .equals(type)
                && body.visualEntity() != null) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void unloadChunk(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        for (java.util.List<?> slice : event.getChunk().entityLists) for (Object value : slice)
            if (value instanceof EntityMobRagdoll) ((EntityMobRagdoll) value).disposePhysics();
    }

    @SubscribeEvent
    public void unloadWorld(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        hits.entrySet()
            .removeIf(e -> e.getKey().worldObj == event.world);
        blasts.entrySet()
            .removeIf(e -> e.getKey().worldObj == event.world);
        if (!event.world.isRemote) for (Object value : event.world.loadedEntityList)
            if (value instanceof EntityMobRagdoll) ((EntityMobRagdoll) value).disposePhysics();
    }

    public static Vector3f inherited(Vector3f motion, Vector3f travelled) {
        Vector3f value = new Vector3f(motion);
        if (travelled.lengthSquared() > motion.lengthSquared() && travelled.lengthSquared() < 25) value.set(travelled);
        value.scale(20);
        return value;
    }

    public static Vector3f attackerFallback(Vector3f velocity, Vector3f direction) {
        if (velocity.x * velocity.x + velocity.z * velocity.z >= .75F * .75F || direction.lengthSquared() < 1e-6F)
            return velocity;
        Vector3f heading = new Vector3f(direction);
        heading.y = 0;
        if (heading.lengthSquared() < 1e-6F) return velocity;
        heading.normalize();
        return new Vector3f(heading.x * .75F, velocity.y + .108F, heading.z * .75F);
    }

    public static Vector3f killingImpulse(Vector3f direction, boolean melee) {
        float base = melee ? 10 : 8;
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        Vector3f impulse = new Vector3f(
            direction.x * base,
            (float) (direction.y * base + .3 + .55 * base * horizontal),
            direction.z * base);
        impulse.scale(2.5F);
        return impulse;
    }

    public static Vector3f explosionKick(double x, double y, double z, float yaw, double[] center) {
        double dx = x - center[0], dy = y - center[1], dz = z - center[2], horizontal = Math.sqrt(dx * dx + dz * dz),
            distance = Math.sqrt(horizontal * horizontal + dy * dy);
        double hx = horizontal > .001 ? dx / horizontal : Math.sin(Math.toRadians(yaw)),
            hz = horizontal > .001 ? dz / horizontal : -Math.cos(Math.toRadians(yaw));
        double elevation = Math.max(Math.toRadians(30), Math.min(Math.toRadians(70), Math.atan2(dy, horizontal))),
            speed = 35 * (1 - .4 * Math.min(distance / 8, 1));
        return new Vector3f(
            (float) (hx * Math.cos(elevation) * speed),
            (float) (Math.sin(elevation) * speed),
            (float) (hz * Math.cos(elevation) * speed));
    }
}
