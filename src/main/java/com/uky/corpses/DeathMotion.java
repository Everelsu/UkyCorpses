package com.uky.corpses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.vecmath.Vector3f;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public final class DeathMotion {

    private static final Map<UUID, Sample> beforeHit = new HashMap<>();
    private static final Map<UUID, Hit> hits = new HashMap<>();
    private static boolean registered;
    private static int captured;

    private static final class Sample {

        final Vector3f velocity;
        final long tick, at;

        Sample(EntityPlayer player) {
            velocity = new Vector3f((float) player.motionX, (float) player.motionY, (float) player.motionZ);
            tick = player.worldObj.getTotalWorldTime();
            at = System.currentTimeMillis();
        }
    }

    private static final class Hit {

        final Vector3f direction;
        final boolean melee;
        final long at = System.currentTimeMillis();

        Hit(Vector3f direction, boolean melee) {
            this.direction = direction;
            this.melee = melee;
        }
    }

    public static void init() {
        if (registered) return;
        registered = true;
        DeathMotion handler = new DeathMotion();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer) || event.entityLiving.worldObj.isRemote) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        UUID id = player.getUniqueID();
        Sample earlier = beforeHit.get(id);
        if (earlier == null || earlier.tick != player.worldObj.getTotalWorldTime())
            beforeHit.put(id, new Sample(player));
        Entity direct = event.source.getSourceOfDamage();
        Entity attacker = event.source.getEntity();
        Vector3f direction = null;
        boolean melee = false;
        if (direct != null && event.source.isProjectile()) {
            direction = new Vector3f((float) direct.motionX, (float) direct.motionY, (float) direct.motionZ);
        } else if (attacker instanceof EntityLivingBase && attacker != player) {
            Vec3 look = attacker.getLookVec();
            direction = look == null ? null
                : new Vector3f((float) look.xCoord, (float) look.yCoord, (float) look.zCoord);
            if (direction == null || direction.lengthSquared() < 1e-6F) direction = new Vector3f(
                (float) (player.posX - attacker.posX),
                (float) (player.posY - attacker.posY),
                (float) (player.posZ - attacker.posZ));
            melee = true;
        }
        if (direction != null && direction.lengthSquared() >= 1e-6F) {
            direction.normalize();
            hits.put(id, new Hit(direction, melee));
        }
        if (++captured >= 256) {
            captured = 0;
            long cutoff = System.currentTimeMillis() - 10000;
            beforeHit.entrySet()
                .removeIf(e -> e.getValue().at < cutoff);
            hits.entrySet()
                .removeIf(e -> e.getValue().at < cutoff);
        }
    }

    /** Consume the pre-impact movement before knockback/death bookkeeping can overwrite it. */
    public static Vector3f capture(EntityPlayer player, DamageSource source) {
        Sample sample = beforeHit.remove(player.getUniqueID());
        if (sample != null && player.worldObj.getTotalWorldTime() - sample.tick > 2) sample = null;
        Vector3f motion = sample == null
            ? new Vector3f((float) player.motionX, (float) player.motionY, (float) player.motionZ)
            : new Vector3f(sample.velocity);
        Vector3f travelled = new Vector3f(
            (float) (player.posX - player.lastTickPosX),
            (float) (player.posY - player.lastTickPosY),
            (float) (player.posZ - player.lastTickPosZ));
        Vector3f velocity = linearVelocity(motion, travelled);
        if (source == null || source.isExplosion()) return velocity;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof EntityLivingBase) || attacker == player) return velocity;
        Vec3 look = attacker.getLookVec();
        Vector3f direction = look == null ? new Vector3f() : new Vector3f((float) look.xCoord, 0, (float) look.zCoord);
        if (direction.lengthSquared() < 1e-6F)
            direction.set((float) (player.posX - attacker.posX), 0, (float) (player.posZ - attacker.posZ));
        return attackerFallback(velocity, direction);
    }

    static Vector3f linearVelocity(Vector3f motion, Vector3f travelled) {
        Vector3f result = new Vector3f(motion);
        float moved = travelled.lengthSquared();
        if (moved > motion.lengthSquared() && moved < 25) result.set(travelled);
        result.scale(20);
        return result;
    }

    static Vector3f attackerFallback(Vector3f velocity, Vector3f direction) {
        if (velocity.x * velocity.x + velocity.z * velocity.z >= .75F * .75F || direction.lengthSquared() < 1e-6F)
            return velocity;
        Vector3f heading = new Vector3f(direction);
        heading.y = 0;
        if (heading.lengthSquared() < 1e-6F) return velocity;
        heading.normalize();
        return new Vector3f(heading.x * .75F, velocity.y + .108F, heading.z * .75F);
    }

    /** Default torso killing impulse; the rig distributes it through the whole body at the hit point. */
    public static Vector3f hitImpulse(EntityPlayer player, DamageSource source) {
        Hit hit = hits.remove(player.getUniqueID());
        if (source == null || source.isExplosion() || hit == null || System.currentTimeMillis() - hit.at > 10000)
            return null;
        Vector3f impulse = mappedImpulse(hit.direction, hit.melee);
        impulse.scale(2.5F);
        return impulse;
    }

    static Vector3f mappedImpulse(Vector3f direction, boolean melee) {
        float base = melee ? 10 : 8;
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return new Vector3f(
            direction.x * base,
            (float) (direction.y * base + .3 + .55 * base * horizontal),
            direction.z * base);
    }

    /** Original explosion launch is a velocity change, not an impulse or a predetermined flight arc. */
    public static Vector3f explosionKick(EntityPlayer player, double[] blast) {
        if (blast == null || blast.length < 3) return null;
        double dx = player.posX - blast[0], dy = player.posY + player.height * .5 - blast[1],
            dz = player.posZ - blast[2];
        double horizontal = Math.sqrt(dx * dx + dz * dz), distance = Math.sqrt(horizontal * horizontal + dy * dy);
        double headingX, headingZ;
        if (horizontal > .001) {
            headingX = dx / horizontal;
            headingZ = dz / horizontal;
        } else {
            double yaw = Math.toRadians(player.rotationYaw);
            headingX = Math.sin(yaw);
            headingZ = -Math.cos(yaw);
        }
        double elevation = Math.max(Math.toRadians(30), Math.min(Math.toRadians(70), Math.atan2(dy, horizontal)));
        double speed = 35 * (1 - .4 * Math.min(distance / 8, 1));
        double horizontalSpeed = Math.cos(elevation) * speed;
        return new Vector3f(
            (float) (headingX * horizontalSpeed),
            (float) (Math.sin(elevation) * speed),
            (float) (headingZ * horizontalSpeed));
    }

    private static void clear(EntityPlayer player) {
        beforeHit.remove(player.getUniqueID());
        hits.remove(player.getUniqueID());
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.player);
    }

    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.player);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        clear(event.player);
    }
}
