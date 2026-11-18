package com.uky.corpses;

import javax.vecmath.Vector3f;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.ExplosionEvent;

import com.uky.corpses.blood.Blood;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Severing changes the physical joint; all equipment and loot remain in the original corpse. */
public final class Amputation {

    public static boolean enabled = true;
    private static boolean registered;
    private static final java.util.Map<EntityCorpse, Integer> lastSever = new java.util.WeakHashMap<>();

    public static void config(Configuration c) {
        enabled = CorpseConfig.section("dismemberment", "Расчленение.")
            .on("enabled", true, "Enable amputation.");
        com.uky.corpses.amp.AmputationConfig.SPEC.load();
    }

    public static void init() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new Amputation());
    }

    /** The original dead-body roll depends on impulse and per-slot armor, independently of loot rights. */
    public static boolean hit(EntityCorpse body, EntityPlayer player, int part) {
        if (!enabled || body.worldObj.isRemote || body.isDead) return false;
        float damage = (float) player.getEntityAttribute(SharedMonsterAttributes.attackDamage)
            .getAttributeValue();
        double strength = Math.min(50, 75 * Math.sqrt(Math.max(1, damage) / 5));
        Vec3 direction = player.getLookVec();
        return sever(body, part, strength, direction);
    }

    private static boolean sever(EntityCorpse body, int index, double strength, Vec3 direction) {
        com.uky.corpses.amp.Part part = com.uky.corpses.amp.Part.byIndex(index);
        if (!enabled || !com.uky.corpses.amp.AmputationConfig.ENABLE_DEAD.get()
            || !Double.isFinite(strength)
            || !com.uky.corpses.amp.AmputationConfig.ENABLE_FOR_PLAYERS.get()) return false;
        Integer last = lastSever.get(body);
        if (last != null && body.ticksExisted - last < com.uky.corpses.amp.AmputationConfig.DEAD_COOLDOWN_TICKS.get())
            return false;
        double chance = com.uky.corpses.amp.AmputationRoll.deadChance(part, strength, armorCover(body, index));
        if (!com.uky.corpses.amp.AmputationRoll.roll(body.worldObj.rand, chance)) return false;
        Skeleton.Shape shape = body.physicalShape(1);
        if (shape == null || !body.severPart(index, launch(body.worldObj.rand, part, direction))) return false;
        lastSever.put(body, body.ticksExisted);
        bleed(body, shape, index);
        return true;
    }

    static Vector3f launch(java.util.Random random, com.uky.corpses.amp.Part part, Vec3 direction) {
        double hop = com.uky.corpses.amp.AmputationConfig.LIMB_HOP_SPEED.get() * (.6 + .8 * random.nextDouble());
        double force = com.uky.corpses.amp.AmputationConfig.LIMB_DETACH_FORCE.get() * (.7 + .6 * random.nextDouble());
        Vector3f push = new Vector3f();
        if (direction != null && direction.lengthVector() > .001) {
            Vec3 normal = direction.normalize();
            push.set(
                (float) (normal.xCoord * force),
                (float) (normal.yCoord * force * .3),
                (float) (normal.zCoord * force));
        }
        double scatter = hop * .35;
        push.x += (random.nextDouble() - .5) * 2 * scatter;
        push.y += hop;
        push.z += (random.nextDouble() - .5) * 2 * scatter;
        if (part == com.uky.corpses.amp.Part.HEAD) push.scale(.55F);
        float max = com.uky.corpses.amp.AmputationConfig.LIMB_MAX_LAUNCH_SPEED.get()
            .floatValue();
        if (push.length() > max) push.scale(max / push.length());
        return push;
    }

    private static double armorCover(EntityCorpse body, int part) {
        if (part == Skeleton.HEAD)
            return fraction(armor(body.armor(3)), com.uky.corpses.amp.AmputationConfig.FULL_HELMET_ARMOR.get());
        if (part == Skeleton.RLEG || part == Skeleton.LLEG) return fraction(
            armor(body.armor(1)) + armor(body.armor(0)),
            com.uky.corpses.amp.AmputationConfig.FULL_LEG_ARMOR.get());
        return fraction(armor(body.armor(2)), com.uky.corpses.amp.AmputationConfig.FULL_CHESTPLATE_ARMOR.get());
    }

    private static double fraction(double points, double full) {
        return full <= 0 ? 0 : Math.max(0, Math.min(1, points / full));
    }

    private static double armor(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmor ? ((ItemArmor) stack.getItem()).damageReduceAmount
            : 0;
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate event) {
        if (event.world.isRemote) return;
        float power = event.explosion.explosionSize;
        if (!Float.isFinite(power) || power <= 0) return;
        Vec3 origin = Vec3
            .createVectorHelper(event.explosion.explosionX, event.explosion.explosionY, event.explosion.explosionZ);
        float radius = power * 2;
        for (Entity entity : event.getAffectedEntities()) {
            if (!(entity instanceof EntityCorpse) || entity.isDead) continue;
            EntityCorpse body = (EntityCorpse) entity;
            Skeleton.Shape shape = body.physicalShape(1);
            if (shape == null) continue;
            double[] at = shape.parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
            double dx = body.posX + at[0] - origin.xCoord, dy = body.posY + at[1] - origin.yCoord,
                dz = body.posZ + at[2] - origin.zCoord;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance >= radius) continue;
            double[] b = shape.bounds(Skeleton.BODY);
            float exposure = event.world.getBlockDensity(
                origin,
                AxisAlignedBB.getBoundingBox(
                    body.posX + b[0],
                    body.posY + b[1],
                    body.posZ + b[2],
                    body.posX + b[3],
                    body.posY + b[4],
                    body.posZ + b[5]));
            float strength = (1 - (float) distance / radius) * exposure;
            if (strength <= 0) continue;
            double scale = Math.min(120, (power * 18 + 10) * strength) / Math.max(.01, distance);
            Vector3f impulse = new Vector3f(
                (float) (dx * scale),
                (float) (dy * scale + strength * 5),
                (float) (dz * scale));
            body.impulsePart(Skeleton.BODY, impulse);
            for (int part = 0; part < Skeleton.PARTS; part++)
                sever(body, part, impulse.length(), Vec3.createVectorHelper(impulse.x, impulse.y, impulse.z));
        }
    }

    private static void bleed(EntityCorpse body, Skeleton.Shape shape, int part) {
        double[] point = shape.parts[part].apply(0, 0, 0, new double[3]);
        double x = body.posX + point[0], y = body.posY + point[1], z = body.posZ + point[2];
        body.worldObj.playSoundEffect(x, y, z, "mob.slime.attack", .7F, .6F + body.worldObj.rand.nextFloat() * .2F);
        body.worldObj.playSoundEffect(x, y, z, "dig.cloth", .7F, .7F);
        if (body.burn() == EntityCorpse.BURNT) return;
        Blood.spawn(body.worldObj, x, y, z, 1.2F);
        Blood.pool(body.worldObj, x, y, z, .9F, 0, 25);
        float[] pivot = Skeleton.pivot(part);
        shape.parts[Skeleton.BODY].apply(pivot[0], pivot[1], pivot[2], point);
        Blood.spawn(body.worldObj, body.posX + point[0], body.posY + point[1], body.posZ + point[2], .6F);
    }
}
