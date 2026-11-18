package com.uky.corpses.stains;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public final class SurfaceBlood {

    public static boolean enabled = true;

    public static void config(Configuration c) {
        enabled = com.uky.corpses.CorpseConfig.section(com.uky.corpses.compat.LegacyIds.BLOOD_NAMESPACE, "Кровь.")
            .on("enabled", true, "Включить кровь SurfaceBlood.");
        SurfaceBloodConfig.SPEC.load();
        SurfaceBloodClientConfig.SPEC.load();
    }

    public static void init() {
        UkyCorpses.net.registerMessage(Hit.Handler.class, Hit.class, 90, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(new SurfaceBlood());
    }

    public static void initClient() {
        com.uky.corpses.stains.client.SurfaceBloodClient.init();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || !enabled
            || !SurfaceBloodConfig.SEND_HITS.get()
            || e.ammount < SurfaceBloodConfig.MIN_DAMAGE.get()) return;
        DamageCategory category = DamageCategory.of(e.source);
        if (category != DamageCategory.NONE)
            send(e.entityLiving, e.source.getSourceOfDamage(), e.source.getEntity(), e.ammount, false, category);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent e) {
        if (!e.entityLiving.worldObj.isRemote && enabled && SurfaceBloodClientConfig.DEATH_POOLS.get()) send(
            e.entityLiving,
            e.source.getSourceOfDamage(),
            e.source.getEntity(),
            0,
            true,
            DamageCategory.of(e.source));
    }

    private static void send(EntityLivingBase target, Entity source, Entity attacker, float amount, boolean dead,
        DamageCategory category) {
        Hit hit = new Hit();
        hit.entity = target.getEntityId();
        hit.attacker = attacker == null ? -1 : attacker.getEntityId();
        hit.dimension = target.dimension;
        hit.amount = amount;
        hit.dead = dead;
        hit.fatal = dead || amount >= target.getHealth();
        hit.melee = source == attacker && attacker instanceof EntityLivingBase
            && !dead
            && (category == DamageCategory.SHARP || category == DamageCategory.BLUNT)
            && attacker.getDistanceSqToEntity(target) <= 36;
        hit.category = category.ordinal();
        hit.seed = target.getRNG()
            .nextInt();
        HitResolver.resolve(hit, target, source, attacker, category);
        UkyCorpses.net.sendToAllAround(
            hit,
            new NetworkRegistry.TargetPoint(
                target.dimension,
                hit.x,
                hit.y,
                hit.z,
                SurfaceBloodConfig.SEND_RANGE.get()));
    }

    public static final class Hit implements IMessage {

        public int entity, dimension, attacker = -1, category = DamageCategory.BLUNT.ordinal();
        public double x, y, z;
        public float dx, dy, dz, amount, speed;
        public int seed;
        public boolean dead, fatal, melee, valid;

        public void toBytes(ByteBuf b) {
            b.writeInt(entity)
                .writeInt(attacker)
                .writeInt(dimension)
                .writeDouble(x)
                .writeDouble(y)
                .writeDouble(z)
                .writeFloat(dx)
                .writeFloat(dy)
                .writeFloat(dz)
                .writeFloat(amount)
                .writeByte((dead ? 1 : 0) | (fatal ? 2 : 0) | (melee ? 4 : 0))
                .writeByte(category)
                .writeFloat(speed)
                .writeInt(seed);
        }

        public void fromBytes(ByteBuf b) {
            if (b.readableBytes() != 62) return;
            entity = b.readInt();
            attacker = b.readInt();
            dimension = b.readInt();
            x = b.readDouble();
            y = b.readDouble();
            z = b.readDouble();
            dx = b.readFloat();
            dy = b.readFloat();
            dz = b.readFloat();
            amount = b.readFloat();
            int flags = b.readUnsignedByte();
            dead = (flags & 1) != 0;
            fatal = (flags & 2) != 0;
            melee = (flags & 4) != 0;
            category = b.readUnsignedByte();
            speed = b.readFloat();
            seed = b.readInt();
            valid = flags <= 7 && entity >= 0
                && Double.isFinite(x)
                && category < DamageCategory.values().length
                && Float.isFinite(speed)
                && speed >= 0
                && speed <= 1000000
                && Double.isFinite(y)
                && Double.isFinite(z)
                && Math.abs(x) < 30000001
                && Math.abs(z) < 30000001
                && Math.abs(y) < 4096
                && Float.isFinite(amount)
                && amount >= 0
                && amount <= 1000000
                && Float.isFinite(dx)
                && Float.isFinite(dy)
                && Float.isFinite(dz)
                && Math.abs(dx) <= 1
                && Math.abs(dy) <= 1
                && Math.abs(dz) <= 1;
        }

        public static final class Handler implements IMessageHandler<Hit, IMessage> {

            public IMessage onMessage(Hit h, MessageContext c) {
                net.minecraft.world.World world = com.uky.corpses.stains.client.SurfaceBloodClient.world();
                Entity victim = world == null ? null : world.getEntityByID(h.entity);
                if (h.valid) com.uky.corpses.client.CorpseClient.tasks.add(() -> {
                    if (world == com.uky.corpses.stains.client.SurfaceBloodClient.world() && world != null
                        && victim != null
                        && world.getEntityByID(h.entity) == victim
                        && world.provider.dimensionId == h.dimension)
                        com.uky.corpses.stains.client.SurfaceBloodClient.hit(h);
                });
                return null;
            }
        }
    }
}
