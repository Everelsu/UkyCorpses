package com.uky.corpses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.vecmath.Vector3f;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Ragdoll;
import com.uky.corpses.corpse.RagdollWorld;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/** Reversible live-player ragdolls, separate from Downed's health, bleedout and inventory rules. */
public final class Reactions {

    public static boolean enabled = true, manual = true, affectCreative, ignoreMobs = true, ignoreManualMobs;
    public static boolean impacts = true, landings = true, knockback = true, shieldsBlockKnockback = true,
        explosions = true, shieldsBlockExplosions = true, pistons = true, stickyPistonsGrab = true, heavyHits = true,
        lightning = true, fallingBlocks = true, buckedOff = true, wallDamage = true, autoGetUp = true,
        sneakToGetUp = true, showHint = true;
    public static String cameraMode = "HEAD";
    public static String[] heavyHitters = { "minecraft:iron_golem", "minecraft:warden", "minecraft:ender_dragon",
        "minecraft:ravager", "minecraft:hoglin", "minecraft:zoglin" };
    public static float sensitivity = 1, launchLimit = 40, impactSpeed = 16, landingSpeed = 24, shoveSpeed = 30,
        explosionSpeed = 4, explosionScale = 2, heavyDamage = 12, heavyHitLaunchScale = 1.5F, pistonLaunchSpeed = 6,
        wallDamageMinSpeed = 10, wallDamagePerSpeed = .25F;
    public static int minimumTicks = 60, stillTicks = 30, maximumTicks = 200, cooldownTicks = 40;
    private static final int INTERVAL = 2;
    private static final Map<UUID, Active> active = new HashMap<>();
    private static final Map<UUID, Motion> motions = new HashMap<>();
    private static final Map<UUID, Long> cooldown = new HashMap<>(), requested = new HashMap<>();
    private static final Map<UUID, Long> teleports = new HashMap<>();
    private static final Map<Integer, Replica> replicas = new HashMap<>();
    private static final Map<Integer, Long> closed = new HashMap<>();
    private static final Map<UUID, DeathPose> deaths = new HashMap<>();
    private static long clock;
    private static long serial;

    private static final class Active {

        EntityPlayerMP player;
        Ragdoll rig;
        World world;
        double x, y, z;
        boolean manual, lethal, noClip, flying;
        int age, still;
        long wallAt = -100;
        ReactionPistons.Contact piston;
        long generation;
        float height;
        double expectedX, expectedY, expectedZ;
        double[] lastTorso;
        EntityPlayerMP holder;
        int heldPart;
        long dragPing;
    }

    private static final class Replica {

        Ragdoll rig;
        World world;
        double x, y, z;
        long at;
        long generation;
        int age, holder, heldPart;
        UUID uuid;
        boolean manual, lethal;
    }

    private static final class Motion {

        double x, y, z, vx, vy, vz;
        boolean ground;
        Entity mover;
        World world;
    }

    public static void config(Configuration ignored) {
        CorpseConfig.Section c;
        c = CorpseConfig.section("reactions.general", "Общие настройки реакций.");
        enabled = c.on(
            "enabled",
            true,
            "Автоматические падения от внешних воздействий. Ручное падение на H включается отдельно; настоящий нокдаун задаётся downed.");
        manual = c.on("allowManualRagdoll", true, "Разрешить игроку вручную упасть и встать клавишей H.");
        affectCreative = c
            .on("affectCreative", false, "Применять автоматические реакции к игрокам в творческом режиме.");
        sensitivity = c.num(
            "sensitivity",
            1F,
            0.1F,
            10F,
            "Множитель чувствительности: больше — легче упасть; делит пороги скорости столкновения, приземления и отбрасывания.");
        launchLimit = c.num(
            "maxLaunchSpeed",
            40F,
            0F,
            200F,
            "Максимальная начальная скорость тела, блоков/секунду. Не относится к телепортации.");
        cooldownTicks = c
            .num("cooldownTicks", 40, 0, 1200, "Пауза между автоматическими реакциями в тиках; 20 тиков = 1 секунда.");
        c = CorpseConfig.section("reactions.triggers", "Причины падения.");
        impacts = c.on("impacts", true, "Падать от сильного столкновения со стеной или потолком.");
        landings = c.on("landings", true, "Падать после сильного приземления; обычный урон падения задаёт Minecraft.");
        knockback = c.on("knockback", true, "Падать при резком отбрасывании. Телепорт не считается отбрасыванием.");
        shieldsBlockKnockback = c
            .on("shieldsBlockKnockback", true, "Блокирование оружием защищает от реакции на отбрасывание.");
        explosions = c.on("explosions", true, "Падать и получать физический импульс от взрывов.");
        shieldsBlockExplosions = c
            .on("shieldsBlockExplosions", true, "Блокирование оружием защищает от физического падения при взрыве.");
        pistons = c.on("pistons", true, "Поршни могут толкать игрока в рэгдолл.");
        stickyPistonsGrab = c
            .on("stickyPistonsGrab", true, "Липкий поршень временно удерживает физическое тело; требует pistons.");
        heavyHits = c.on("heavyHits", true, "Падать после сильных ударов тяжёлых существ из heavyHitters.");
        lightning = c.on("lightning", true, "Молния вызывает физическое падение.");
        fallingBlocks = c.on("fallingBlocks", true, "Падающий блок или наковальня могут сбить игрока.");
        buckedOff = c.on("buckedOff", true, "Лошадь может сбросить игрока в рэгдолл.");
        impactSpeed = c.num(
            "impactThreshold",
            16F,
            0.5F,
            200F,
            "Потеря скорости при столкновении, блоков/секунду, начиная с которой игрок падает.");
        landingSpeed = c.num("landingThreshold", 24F, 0.5F, 200F, "Порог скорости приземления, блоков/секунду.");
        shoveSpeed = c.num(
            "knockbackThreshold",
            30F,
            0.5F,
            200F,
            "Порог резкого изменения скорости отбрасывания, блоков/секунду.");
        explosionSpeed = c.num(
            "explosionMinStrength",
            4F,
            0F,
            200F,
            "Минимальная сила взрыва для физического падения; TNT имеет силу 4.");
        explosionScale = c.num("explosionLaunchScale", 2F, 0F, 20F, "Множитель начального импульса от взрыва.");
        pistonLaunchSpeed = c.num("pistonLaunchSpeed", 6F, 0F, 40F, "Скорость запуска поршнем, блоков/секунду.");
        heavyDamage = c
            .num("heavyHitMinDamage", 12F, 0F, 1000F, "Минимальный урон тяжёлого удара в половинках сердца.");
        heavyHitLaunchScale = c.num("heavyHitLaunchScale", 1.5F, 0F, 20F, "Множитель импульса после тяжёлого удара.");
        heavyHitters = c.strings("heavyHitters", heavyHitters, "Registry IDs of heavy attackers.");
        c = CorpseConfig.section("reactions.mobs", "Реакции мобов.");
        ignoreMobs = c.on(
            "mobsIgnoreDownedPlayers",
            true,
            "Мобы теряют цель, если игрок упал от реакции. Настройка настоящего нокдауна: downed.mobsIgnore.");
        ignoreManualMobs = c.on("mobsIgnoreManualRagdoll", false, "Мобы теряют цель и при ручном падении на H.");
        c = CorpseConfig.section("reactions.damage", "Урон при физических реакциях.");
        wallDamage = c.on(
            "wallDamage",
            true,
            "Физическое тело может получать дополнительный урон от сильных ударов о стены. Телепорт урон не наносит.");
        wallDamageMinSpeed = c.num(
            "wallDamageMinSpeed",
            10F,
            0F,
            200F,
            "Минимальная скорость удара о стену для дополнительного урона, блоков/секунду.");
        wallDamagePerSpeed = c.num(
            "wallDamagePerSpeed",
            0.25F,
            0F,
            10F,
            "Урон в половинках сердца за каждую единицу превышения wallDamageMinSpeed.");
        c = CorpseConfig.section("reactions.recovery", "Подъём после падения.");
        autoGetUp = c
            .on("autoGetUp", true, "Вставать автоматически после minRagdollTicks и stillTicksToGetUp тиков покоя.");
        minimumTicks = c.num(
            "minRagdollTicks",
            60,
            0,
            1200,
            "Минимальное время автоматического падения в тиках перед разрешением вставать.");
        stillTicks = c.num(
            "stillTicksToGetUp",
            30,
            1,
            1200,
            "Сколько тиков подряд тело должно почти не двигаться для раннего автоматического вставания.");
        maximumTicks = c.num(
            "maxRagdollTicks",
            200,
            20,
            12000,
            "Предельная длительность автоматического падения в тиках, даже при autoGetUp=false. Не поднимает умирающего в настоящем нокдауне.");
        c = CorpseConfig.section("reactions.client", "Клиентские настройки реакций.");
        cameraMode = c.pick(
            "cameraMode",
            "HEAD",
            new String[] { "HEAD", "THIRD_PERSON" },
            "Камера физического рэгдолла: HEAD — от головы, THIRD_PERSON — со стороны.");
        sneakToGetUp = c.on(
            "sneakToGetUp",
            true,
            "Приседание просит встать; удержание повторяет запрос, пока не истечёт минимальное время.");
        showHint = c.on("showHint", true, "Показывать подсказку клавиши вставания.");
    }

    public static void preInit() {
        UkyCorpses.net.registerMessage(MsgReaction.Server.class, MsgReaction.class, 50, Side.SERVER);
        UkyCorpses.net.registerMessage(MsgReaction.Client.class, MsgReaction.class, 51, Side.CLIENT);
    }

    public static void init() {
        Reactions handler = new Reactions();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    public static boolean is(Entity e) {
        if (!(e instanceof EntityPlayer) || e.worldObj == null) return false;
        if (!e.worldObj.isRemote) return active.containsKey(e.getUniqueID());
        Replica r = replicas.get(e.getEntityId());
        return r != null && r.world == e.worldObj && r.uuid.equals(e.getUniqueID());
    }

    public static boolean isDragged(EntityPlayer target) {
        if (target.worldObj.isRemote) {
            Replica r = replicas.get(target.getEntityId());
            return r != null && r.world == target.worldObj
                && target.getUniqueID()
                    .equals(r.uuid)
                && r.holder != 0;
        }
        Active a = active.get(target.getUniqueID());
        return a != null && a.holder != null;
    }

    public static int draggedEntity(EntityPlayer actor) {
        if (actor.worldObj.isRemote) for (java.util.Map.Entry<Integer, Replica> entry : replicas.entrySet()) {
            Replica r = entry.getValue();
            if (r.world == actor.worldObj && r.holder == actor.getEntityId()) return entry.getKey();
        }
        else for (Active a : active.values()) if (a.holder == actor) return a.player.getEntityId();
        return -1;
    }

    public static void dragInput(EntityPlayerMP actor, int entity, long life, boolean held) {
        Entity target = actor.worldObj.getEntityByID(entity);
        if (target == null) return;
        Active a = active.get(target.getUniqueID());
        if (a == null || a.world != actor.worldObj || a.generation != life || a.holder != actor) return;
        if (held) a.dragPing = clock;
        else {
            a.holder = null;
            a.rig.hold(-1, null);
            send(a);
        }
    }

    public static boolean isDragging(EntityPlayer actor) {
        if (actor.worldObj.isRemote) {
            for (Replica r : replicas.values())
                if (r.world == actor.worldObj && r.holder == actor.getEntityId()) return true;
            return false;
        }
        for (Active a : active.values()) if (a.holder == actor) return true;
        return false;
    }

    public static net.minecraft.util.Vec3 dragGrip(EntityPlayer actor, float partial) {
        if (actor == null || actor.worldObj == null) return null;
        Entity target = null;
        int part = -1;
        if (actor.worldObj.isRemote) {
            for (Map.Entry<Integer, Replica> entry : replicas.entrySet()) {
                Replica r = entry.getValue();
                if (r.world == actor.worldObj && r.holder == actor.getEntityId()) {
                    target = actor.worldObj.getEntityByID(entry.getKey());
                    part = r.heldPart;
                    break;
                }
            }
        } else for (Active a : active.values()) if (a.holder == actor) {
            target = a.player;
            part = a.heldPart;
            break;
        }
        if (target == null) return null;
        Skeleton.Shape shape = physicalShape(target, partial);
        if (shape == null) return null;
        int left = part == Skeleton.HEAD || part == Skeleton.LARM || part == Skeleton.RARM ? Skeleton.LARM
            : Skeleton.LLEG;
        int right = left == Skeleton.LARM ? Skeleton.RARM : Skeleton.RLEG;
        double[] l = shape.bounds(left), r = shape.bounds(right);
        return net.minecraft.util.Vec3.createVectorHelper(
            target.posX + (l[0] + l[3] + r[0] + r[3]) * .25,
            target.posY + (l[1] + l[4] + r[1] + r[4]) * .25,
            target.posZ + (l[2] + l[5] + r[2] + r[5]) * .25);
    }

    public static int severedMask(Entity entity) {
        if (entity.worldObj.isRemote) {
            Replica r = replicas.get(entity.getEntityId());
            return r == null ? 0 : r.rig.severed();
        }
        Active a = active.get(entity.getUniqueID());
        return a == null ? 0 : a.rig.severed();
    }

    public static long physicalLife(Entity entity) {
        if (entity.worldObj.isRemote) {
            Replica r = replicas.get(entity.getEntityId());
            return r == null ? 0 : r.generation;
        }
        Active a = active.get(entity.getUniqueID());
        return a == null ? 0 : a.generation;
    }

    public static Skeleton.Shape physicalShape(Entity e, float partial) {
        if (!is(e)) return null;
        if (!e.worldObj.isRemote) {
            Active a = active.get(e.getUniqueID());
            return a.rig.shape(partial, a.x - e.posX, a.y - e.posY, a.z - e.posZ);
        }
        Replica r = replicas.get(e.getEntityId());
        float lerp = Math.max(0, Math.min(1, (e.worldObj.getTotalWorldTime() - r.at + partial) / INTERVAL));
        return r.rig.shape(lerp, r.x - e.posX, r.y - e.posY, r.z - e.posZ);
    }

    public static void receive(MsgReaction m, World world) {
        if (world == null || m.dimension != world.provider.dimensionId) return;
        Replica old = replicas.get(m.entity);
        if (old != null && (m.generation < old.generation || m.generation == old.generation && m.age < old.age)) return;
        if (!m.active) {
            closed.put(m.entity, Math.max(closed.getOrDefault(m.entity, 0L), m.generation));
            replicas.remove(m.entity);
            return;
        }
        if (m.generation <= closed.getOrDefault(m.entity, 0L)) return;
        UUID uuid = new UUID(m.uuidMost, m.uuidLeast);
        Entity present = world.getEntityByID(m.entity);
        if (present != null && !present.getUniqueID()
            .equals(uuid)) return;
        if (!MsgReaction.validState(m.state)) return;
        Replica r = replicas.get(m.entity);
        if (r == null || r.world != world
            || r.generation != m.generation
            || !uuid.equals(r.uuid)
            || r.x != m.x
            || r.y != m.y
            || r.z != m.z) {
            r = new Replica();
            r.world = world;
            r.rig = new Ragdoll(m.state, m.mask);
            replicas.put(m.entity, r);
        } else r.rig.applyState(m.state, m.mask);
        r.x = m.x;
        r.y = m.y;
        r.z = m.z;
        r.manual = m.manual;
        r.lethal = m.downed;
        r.uuid = uuid;
        r.generation = m.generation;
        r.age = m.age;
        r.holder = m.holder;
        r.heldPart = m.heldPart;
        r.at = world.getTotalWorldTime();
    }

    public static void forgetClient(int entity) {
        replicas.remove(entity);
        closed.remove(entity);
    }

    public static void clearClient() {
        replicas.clear();
        closed.clear();
    }

    public static void request(EntityPlayerMP p, int action) {
        if (p == null || p.isDead || p.getHealth() <= 0) return;
        Long last = requested.get(p.getUniqueID());
        if (last != null && clock - last < 5) return;
        requested.put(p.getUniqueID(), clock);
        Active a = active.get(p.getUniqueID());
        if (a != null) {
            if (!mayGetUp(a.manual, a.lethal || Downed.is(p), a.age)) return;
            stop(p);
        } else if (action == MsgReaction.TOGGLE && manual && !Downed.is(p)) start(p, true, false, velocity(p));
    }

    public static void startDowned(EntityPlayerMP p) {
        if (CorpseConfig.crawlDowned) {
            Active current = active.get(p.getUniqueID());
            if (current != null) remove(current);
            return;
        }
        if (!Ragdoll.enabled || !com.uky.corpses.corpse.PhysicsConfig.playerRagdolls || p.posY < -60) return;
        Active a = active.get(p.getUniqueID());
        if (a != null) {
            a.lethal = true;
            a.manual = false;
            send(a);
        } else start(p, false, true, velocity(p));
    }

    static boolean mayGetUp(boolean manual, boolean lethal, int age) {
        return !lethal && (manual || age >= minimumTicks);
    }

    static boolean automaticRecovery(boolean manual, boolean lethal, int age, int still) {
        return !manual && !lethal && (age >= maximumTicks || autoGetUp && age >= minimumTicks && still >= stillTicks);
    }

    private static boolean available(EntityPlayerMP p) {
        return enabled && Ragdoll.enabled
            && com.uky.corpses.corpse.PhysicsConfig.playerRagdolls
            && p.posY > -60
            && p.isEntityAlive()
            && !p.isPlayerSleeping()
            && !p.capabilities.isFlying
            && !Downed.is(p)
            && (affectCreative || !p.capabilities.isCreativeMode)
            && cooldown.getOrDefault(p.getUniqueID(), -10000L) + cooldownTicks <= clock;
    }

    private static Vector3f velocity(Entity e) {
        return new Vector3f((float) e.motionX * 20, (float) e.motionY * 20, (float) e.motionZ * 20);
    }

    private static void start(EntityPlayerMP p, boolean byHand, boolean lethal, Vector3f velocity) {
        if (!Ragdoll.enabled || !com.uky.corpses.corpse.PhysicsConfig.playerRagdolls
            || p.posY < -60
            || is(p)
            || p.isDead
            || !p.isEntityAlive()
            || p.isPlayerSleeping()) return;
        if (!byHand && !lethal && !available(p)) return;
        if (byHand && cooldown.getOrDefault(p.getUniqueID(), -10000L) + 10 > clock) return;
        if (!Float.isFinite(velocity.x) || !Float.isFinite(velocity.y) || !Float.isFinite(velocity.z))
            velocity.set(0, 0, 0);
        if (p.ridingEntity != null) p.mountEntity(null);
        if (velocity.length() > launchLimit) velocity.scale(launchLimit / velocity.length());
        Active a = new Active();
        a.player = p;
        a.world = p.worldObj;
        a.x = p.posX;
        a.y = p.boundingBox.minY;
        a.z = p.posZ;
        a.manual = byHand;
        a.lethal = lethal;
        a.generation = ++serial;
        a.noClip = p.noClip;
        a.height = p.height;
        a.flying = p.capabilities.isFlying;
        a.expectedX = p.posX;
        a.expectedY = p.posY;
        a.expectedZ = p.posZ;
        int pose = Downed.fallPose(p, null);
        Mat4 frame = new Mat4();
        Skeleton.place(frame, pose, p.renderYawOffset, 0, 0, Float.NaN, 0);
        float[] angles = new float[15];
        angles[0] = (float) Math.toRadians(p.rotationPitch);
        angles[1] = (float) Math.toRadians(p.rotationYawHead - p.renderYawOffset);
        a.rig = new Ragdoll(frame, angles, velocity, pose, p.getEntityId());
        RagdollWorld.refresh(a.rig, a.world, p, a.x, a.y, a.z);
        active.put(p.getUniqueID(), a);
        p.noClip = true;
        Downed.height(p, Downed.LOW);
        p.capabilities.isFlying = false;
        p.sendPlayerAbilities();
        if (byHand ? ignoreManualMobs : ignoreMobs)
            for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(32, 16, 32))) {
                EntityLiving mob = (EntityLiving) o;
                if (mob.getAttackTarget() == p) mob.setAttackTarget(null);
            }
        send(a);
    }

    /** Returns false when standing would put the player's head inside terrain. */
    public static boolean stop(EntityPlayerMP p) {
        Active a = active.get(p.getUniqueID());
        if (a == null) return true;
        if (Downed.is(p)) return false;
        // The torso anchor may be slightly below the supporting floor after settling.
        double feet = p.boundingBox.minY, y = feet;
        for (Object value : p.worldObj.func_147461_a(
            p.boundingBox.copy()
                .expand(0, .5, 0))) {
            net.minecraft.util.AxisAlignedBB box = (net.minecraft.util.AxisAlignedBB) value;
            if (box.minY < feet + .05 && box.maxY > y && box.maxY <= feet + .5) y = box.maxY;
        }
        if (y > feet) p.setPosition(p.posX, p.posY + y - feet + .001, p.posZ);
        remove(a);
        if (!Downed.room(p)) Downed.crawlOut(p);
        return true;
    }

    public static void teleported(EntityPlayerMP p) {
        com.uky.corpses.medicine.Medicine.teleported(p);
        Active a = active.get(p.getUniqueID());
        if (a != null) remove(a);
        motions.remove(p.getUniqueID());
        teleports.put(p.getUniqueID(), clock + 20);
        p.fallDistance = 0;
        p.motionX = p.motionY = p.motionZ = 0;
    }

    private static void remove(Active a) {
        active.remove(a.player.getUniqueID());
        a.rig.disposePhysics();
        a.player.noClip = a.noClip;
        a.player.capabilities.isFlying = a.flying && a.player.capabilities.allowFlying;
        a.player.sendPlayerAbilities();
        if (!Downed.is(a.player) && !a.lethal) Downed.height(a.player, a.height);
        a.player.fallDistance = 0;
        a.player.motionX = a.player.motionY = a.player.motionZ = 0;
        cooldown.put(a.player.getUniqueID(), clock);
        motions.remove(a.player.getUniqueID());
        UkyCorpses.net.sendToDimension(
            MsgReaction.gone(
                a.player.getEntityId(),
                a.world.provider.dimensionId,
                a.player.getUniqueID(),
                a.generation,
                a.age),
            a.world.provider.dimensionId);
    }

    /** A corpse can inherit the same physical body at death rather than restarting its fall. */
    public static final class DeathPose {

        public final float[] state;
        public final double originX, originY, originZ;
        public final int severed;
        public final long generation;

        private DeathPose(Active a) {
            generation = a.generation;
            state = a.rig.state();
            originX = a.x;
            originY = a.y;
            originZ = a.z;
            severed = a.rig.severed();
        }
    }

    public static DeathPose takeDeath(EntityPlayer p) {
        DeathPose saved = deaths.remove(p.getUniqueID());
        if (saved != null) return saved;
        Active a = active.get(p.getUniqueID());
        if (a == null) return null;
        DeathPose d = new DeathPose(a);
        UkyCorpses.net.sendTo(new MsgBodyLink(p.getUniqueID(), d.generation), a.player);
        remove(a);
        return d;
    }

    public static boolean severPart(EntityPlayerMP p, int part, Vector3f impulse) {
        Active a = active.get(p.getUniqueID());
        if (a == null || part < 0 || part >= Skeleton.PARTS || part == Skeleton.BODY) return false;
        if (!a.rig.severVelocity(part, impulse)) return false;
        send(a);
        return true;
    }

    public static void revived(EntityPlayerMP p) {
        Active a = active.get(p.getUniqueID());
        if (a != null) remove(a);
    }

    private static MsgReaction snapshot(Active a) {
        MsgReaction m = new MsgReaction(MsgReaction.STATE);
        m.entity = a.player.getEntityId();
        m.dimension = a.world.provider.dimensionId;
        m.active = true;
        m.manual = a.manual;
        m.downed = a.lethal;
        m.uuidMost = a.player.getUniqueID()
            .getMostSignificantBits();
        m.uuidLeast = a.player.getUniqueID()
            .getLeastSignificantBits();
        m.generation = a.generation;
        m.age = a.age;
        m.holder = a.holder == null ? 0 : a.holder.getEntityId();
        m.heldPart = a.heldPart;
        m.x = a.x;
        m.y = a.y;
        m.z = a.z;
        m.mask = a.rig.severed();
        m.state = a.rig.state();
        return m;
    }

    private static void send(Active a) {
        UkyCorpses.net.sendToDimension(snapshot(a), a.world.provider.dimensionId);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.START) clock++;
        else processHits();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        Active a = active.get(p.getUniqueID());
        if (a == null && Downed.is(p) && !CorpseConfig.crawlDowned) {
            startDowned(p);
            a = active.get(p.getUniqueID());
        }
        if (enabled && pistons && (a != null || available(p))) {
            ReactionPistons.Contact contact = ReactionPistons.contact(p);
            if (contact != null) {
                if (a == null) {
                    start(p, false, false, contact.launch());
                    a = active.get(p.getUniqueID());
                }
                if (a != null && contact.sticky) a.piston = contact;
            }
        }
        if (a == null) {
            detect(p);
            return;
        }
        double relocated = (p.posX - a.expectedX) * (p.posX - a.expectedX)
            + (p.posY - a.expectedY) * (p.posY - a.expectedY)
            + (p.posZ - a.expectedZ) * (p.posZ - a.expectedZ);
        if (!Ragdoll.enabled || !com.uky.corpses.corpse.PhysicsConfig.playerRagdolls
            || !enabled && !a.manual && !a.lethal
            || a.world != p.worldObj
            || !p.isEntityAlive()
            || relocated > 16
            || p.posY < -60) {
            remove(a);
            return;
        }
        if (a.lethal && !Downed.is(p)) {
            remove(a);
            return;
        }
        if (Downed.is(p)) a.lethal = true;
        if (a.holder != null) {
            EntityPlayerMP h = a.holder;
            if (!h.isEntityAlive() || clock - a.dragPing > 30
                || h.worldObj != a.world
                || is(h)
                || h.getDistanceSqToEntity(p) > 49) {
                a.holder = null;
                a.rig.hold(-1, null);
            } else {
                net.minecraft.util.Vec3 look = h.getLook(1);
                a.rig.hold(
                    a.heldPart,
                    new Vector3f(
                        (float) (h.posX + look.xCoord * .48 - a.x),
                        (float) (h.posY + .62 - a.y),
                        (float) (h.posZ + look.zCoord * .48 - a.z)));
            }
        }
        if (a.holder == null && a.piston != null && !a.piston.hold(a.rig, a.world, a.x, a.y, a.z)) {
            a.piston = null;
            a.rig.hold(-1, null);
        }
        float wallSpeed = a.rig.takeWallSpeed();
        float damage = ReactionRules.wallDamage(wallSpeed, wallDamageMinSpeed, wallDamagePerSpeed);
        if (wallDamage && damage > 0 && clock - a.wallAt >= 10) {
            a.wallAt = clock;
            p.attackEntityFrom(new net.minecraft.util.DamageSource("flyIntoWall"), damage);
            if (!p.isEntityAlive() || active.get(p.getUniqueID()) != a) return;
        }
        RagdollWorld.refresh(a.rig, a.world, p, a.x, a.y, a.z);
        a.rig.tick();
        Skeleton.Shape shape = a.rig.shape(1, 0, 0, 0);
        double[] torso = shape.parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
        p.setPosition(a.x + torso[0], a.y + torso[1] - (a.lethal ? .35 : .3), a.z + torso[2]);
        a.expectedX = p.posX;
        a.expectedY = p.posY;
        a.expectedZ = p.posZ;
        p.noClip = true;
        Downed.height(p, Downed.LOW);
        p.motionX = p.motionY = p.motionZ = 0;
        p.fallDistance = 0;
        if (++a.age % INTERVAL == 0) {
            p.playerNetServerHandler.setPlayerLocation(p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch);
            send(a);
        }
        boolean still = a.rig.sleeping() || a.lastTorso != null
            && sq(torso[0] - a.lastTorso[0]) + sq(torso[1] - a.lastTorso[1]) + sq(torso[2] - a.lastTorso[2])
                < .03 * .03;
        a.still = still ? a.still + 1 : 0;
        a.lastTorso = torso;
        if (automaticRecovery(a.manual, a.lethal, a.age, a.still)) stop(p);
    }

    private static void detect(EntityPlayerMP p) {
        Long until = teleports.get(p.getUniqueID());
        if (until != null) {
            motions.remove(p.getUniqueID());
            if (clock < until) return;
            teleports.remove(p.getUniqueID());
        }
        Motion old = motions.get(p.getUniqueID());
        Entity mover = p;
        while (mover.ridingEntity != null) mover = mover.ridingEntity;
        Motion m = new Motion();
        m.mover = mover;
        m.x = mover.posX;
        m.y = mover.posY;
        m.z = mover.posZ;
        m.ground = mover.onGround;
        m.world = p.worldObj;
        // MP movement is received as position updates; vehicles supply their actual engine velocity.
        m.vx = mover == p && old != null && old.mover == mover && old.world == p.worldObj ? (m.x - old.x) * 20
            : mover.motionX * 20;
        m.vy = mover == p && old != null && old.mover == mover && old.world == p.worldObj ? (m.y - old.y) * 20
            : mover.motionY * 20;
        m.vz = mover == p && old != null && old.mover == mover && old.world == p.worldObj ? (m.z - old.z) * 20
            : mover.motionZ * 20;
        if (old != null && old.world == p.worldObj && old.mover == mover && available(p)) {
            double gap = sq(mover.prevPosX - old.x) + sq(mover.prevPosY - old.y) + sq(mover.prevPosZ - old.z);
            if (gap < .01) {
                net.minecraft.block.Block floor = p.worldObj.getBlock(
                    (int) Math.floor(m.x),
                    (int) Math.floor(mover.boundingBox.minY - .01),
                    (int) Math.floor(m.z));
                String floorID = String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(floor));
                boolean bouncy = floor instanceof net.minecraft.block.BlockBed || floorID.endsWith(":slime_block");
                double sens = Math.max(.1, sensitivity);
                int reaction = ReactionRules.detect(
                    new double[] { old.vx, old.vy, old.vz },
                    new double[] { m.vx, m.vy, m.vz },
                    mover.isCollidedHorizontally,
                    mover.isCollidedVertically,
                    mover.onGround,
                    old.ground,
                    bouncy,
                    shieldsBlockKnockback && p.isBlocking(),
                    impacts,
                    landings,
                    knockback,
                    impactSpeed / sens,
                    landingSpeed / sens,
                    shoveSpeed / sens);
                if (reaction != ReactionRules.NONE) start(
                    p,
                    false,
                    false,
                    reaction == ReactionRules.KNOCKBACK ? new Vector3f((float) m.vx, (float) m.vy, (float) m.vz)
                        : new Vector3f((float) old.vx, (float) old.vy, (float) old.vz));
            }
        }
        if (buckedOff && old != null
            && old.mover instanceof net.minecraft.entity.passive.EntityHorse
            && mover == p
            && !p.isSneaking()) {
            net.minecraft.entity.passive.EntityHorse horse = (net.minecraft.entity.passive.EntityHorse) old.mover;
            if (horse.isEntityAlive() && !horse.isTame() && horse.isRearing()) {
                double yaw = Math.toRadians(horse.rotationYaw);
                if (available(p))
                    start(p, false, false, new Vector3f((float) Math.sin(yaw) * 4, 5, (float) -Math.cos(yaw) * 4));
            }
        }
        motions.put(p.getUniqueID(), m);
    }

    private static double sq(double n) {
        return n * n;
    }

    private static final class PendingHit {

        EntityPlayerMP player;
        Entity cause;
        String type;
        float health;
        boolean namedHeavy;
    }

    private static final Map<UUID, PendingHit> pendingHits = new java.util.LinkedHashMap<>();
    private static final java.util.List<ExplosionEvent.Detonate> pendingBlasts = new java.util.ArrayList<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayerMP) || e.ammount <= 0 || e.isCanceled()) return;
        EntityPlayerMP p = (EntityPlayerMP) e.entityLiving;
        if (!available(p) || is(p)) return;
        Entity cause = e.source.getEntity();
        String type = e.source.damageType;
        boolean crush = fallingBlocks && ("fallingBlock".equals(type) || "anvil".equals(type));
        boolean bolt = lightning && "lightningBolt".equals(type);
        boolean possibleHeavy = heavyHits && cause != null
            && !e.source.isExplosion()
            && !e.source.isFireDamage()
            && !e.source.isMagicDamage();
        if (!crush && !bolt && !possibleHeavy) return;
        PendingHit hit = new PendingHit();
        hit.player = p;
        hit.cause = cause;
        hit.type = type;
        hit.health = p.getHealth();
        if (possibleHeavy) {
            String id = net.minecraft.entity.EntityList.getEntityString(cause);
            if (cause instanceof EntityIronGolem) id = "minecraft:iron_golem";
            else if (cause instanceof EntityDragon) id = "minecraft:ender_dragon";
            else if (id != null && id.indexOf(':') < 0) id = "minecraft:" + id;
            for (String heavy : heavyHitters) if (heavy.equals(id)) hit.namedHeavy = true;
        }
        if (!crush && !bolt
            && !hit.namedHeavy
            && !(heavyDamage > 0 && e.ammount >= heavyDamage / Math.max(.1F, sensitivity))) return;
        pendingHits.putIfAbsent(p.getUniqueID(), hit);
    }

    private static Vector3f away(EntityPlayerMP p, Entity cause, float speed) {
        double x = cause == null ? 0 : p.posX - cause.posX, z = cause == null ? 0 : p.posZ - cause.posZ;
        double length = Math.hypot(x, z);
        if (length < .01) {
            double yaw = Math.toRadians(p.rotationYaw);
            x = Math.sin(yaw);
            z = -Math.cos(yaw);
            length = 1;
        }
        return new Vector3f((float) (x / length * speed), 0, (float) (z / length * speed));
    }

    private static void processHits() {
        for (PendingHit hit : pendingHits.values()) {
            EntityPlayerMP p = hit.player;
            if (!available(p) || is(p)) continue;
            Vector3f launch;
            if (fallingBlocks && ("fallingBlock".equals(hit.type) || "anvil".equals(hit.type)))
                launch = new Vector3f(0, -4, 0);
            else if (lightning && "lightningBolt".equals(hit.type)) {
                launch = away(p, hit.cause, 3);
                launch.y = 5;
            } else {
                float actual = Math.max(0, hit.health - p.getHealth());
                if (!heavyHits
                    || !hit.namedHeavy && !(heavyDamage > 0 && actual >= heavyDamage / Math.max(.1F, sensitivity)))
                    continue;
                launch = velocity(p);
                launch.x *= heavyHitLaunchScale;
                launch.z *= heavyHitLaunchScale;
                if (Math.hypot(launch.x, launch.z) < 6) {
                    Vector3f flat = away(p, hit.cause, 6);
                    launch.x = flat.x;
                    launch.z = flat.z;
                }
                launch.y = Math.max(launch.y, (float) Math.hypot(launch.x, launch.z) * .4F);
            }
            start(p, false, false, launch);
        }
        pendingHits.clear();
        for (ExplosionEvent.Detonate e : pendingBlasts) {
            for (Map.Entry<EntityPlayer, net.minecraft.util.Vec3> hit : e.explosion.func_77277_b()
                .entrySet()) {
                if (!(hit.getKey() instanceof EntityPlayerMP)) continue;
                EntityPlayerMP p = (EntityPlayerMP) hit.getKey();
                net.minecraft.util.Vec3 v = hit.getValue();
                if (!available(p) || is(p)
                    || !explosions
                    || v.lengthVector() * 20 < explosionSpeed / Math.max(.1F, sensitivity)) continue;
                if (shieldsBlockExplosions && p.isBlocking()) {
                    net.minecraft.util.Vec3 look = p.getLook(1);
                    if (look.xCoord * (e.explosion.explosionX - p.posX)
                        + look.zCoord * (e.explosion.explosionZ - p.posZ) > 0) continue;
                }
                Vector3f launch = new Vector3f(
                    (float) v.xCoord * 20 * explosionScale,
                    (float) v.yCoord * 20 * explosionScale,
                    (float) v.zCoord * 20 * explosionScale);
                launch.y = Math.max(launch.y, (float) Math.hypot(launch.x, launch.z) * .4F);
                start(p, false, false, launch);
            }
        }
        pendingBlasts.clear();
    }

    @SubscribeEvent
    public void blast(ExplosionEvent.Detonate e) {
        if (!e.world.isRemote && enabled && explosions) pendingBlasts.add(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void use(PlayerInteractEvent e) {
        if (!isDragging(e.entityPlayer) && e.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
            && com.uky.corpses.medicine.Medicine.maySelfAdrenaline(e.entityPlayer)) return;
        if (isDragging(e.entityPlayer)
            || is(e.entityPlayer) && (!Downed.is(e.entityPlayer) || OriginalGameplay.blockActionsWhileKnocked))
            e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hit(AttackEntityEvent e) {
        if (isDragging(e.entityPlayer)
            || is(e.entityPlayer) && (!Downed.is(e.entityPlayer) || OriginalGameplay.blockActionsWhileKnocked))
            e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interact(EntityInteractEvent e) {
        if (e.entityPlayer instanceof EntityPlayerMP && isDragging(e.entityPlayer)) {
            for (Active held : active.values()) if (held.holder == e.entityPlayer) {
                held.holder = null;
                held.rig.hold(-1, null);
                send(held);
            }
            e.setCanceled(true);
            return;
        }
        if (isDragging(e.entityPlayer)
            || is(e.entityPlayer) && (!Downed.is(e.entityPlayer) || OriginalGameplay.blockActionsWhileKnocked)) {
            e.setCanceled(true);
            return;
        }
        if (!(e.entityPlayer instanceof EntityPlayerMP) || !e.entityPlayer.isSneaking() || !CorpseConfig.drag) return;
        Active a = active.get(e.target.getUniqueID());
        if (a == null || a.holder != null && a.holder != e.entityPlayer
            || e.entityPlayer.worldObj != a.world
            || e.entityPlayer.getDistanceSqToEntity(e.target) > 25) return;
        EntityPlayerMP h = (EntityPlayerMP) e.entityPlayer;
        net.minecraft.util.Vec3 look = h.getLook(1);
        Skeleton.Shape shape = physicalShape(e.target, 1);
        int[] part = new int[1];
        if (shape.ray(
            h.posX - e.target.posX,
            h.posY + h.getEyeHeight() - e.target.posY,
            h.posZ - e.target.posZ,
            look.xCoord,
            look.yCoord,
            look.zCoord,
            5,
            part) < 0) return;
        part[0] = OriginalGameplay.choosePullPart(
            part[0],
            shape,
            h.posX - e.target.posX,
            h.posY + h.getEyeHeight() - e.target.posY,
            h.posZ - e.target.posZ);
        a.holder = h;
        a.dragPing = clock;
        a.heldPart = part[0];
        send(a);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void target(LivingSetAttackTargetEvent e) {
        if (e.target == null || e.target.worldObj.isRemote || !(e.entityLiving instanceof EntityLiving)) return;
        Active a = active.get(e.target.getUniqueID());
        if (a != null && (a.manual ? ignoreManualMobs : ignoreMobs))
            ((EntityLiving) e.entityLiving).setAttackTarget(null);
    }

    @SubscribeEvent
    public void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking e) {
        Active a = active.get(e.target.getUniqueID());
        if (a != null && e.entityPlayer instanceof EntityPlayerMP)
            UkyCorpses.net.sendTo(snapshot(a), (EntityPlayerMP) e.entityPlayer);
    }

    private static void reset(EntityPlayer p) {
        Active a = active.get(p.getUniqueID());
        if (a != null) remove(a);
        motions.remove(p.getUniqueID());
        cooldown.remove(p.getUniqueID());
        requested.remove(p.getUniqueID());
        teleports.remove(p.getUniqueID());
        pendingHits.remove(p.getUniqueID());
        deaths.remove(p.getUniqueID());
        for (Active held : active.values()) if (held.holder == p) {
            held.holder = null;
            held.rig.hold(-1, null);
            send(held);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.entityLiving;
        Active a = active.get(p.getUniqueID());
        if (a != null) {
            deaths.put(p.getUniqueID(), new DeathPose(a));
            UkyCorpses.net.sendTo(new MsgBodyLink(p.getUniqueID(), a.generation), p);
            remove(a);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        reset(e.player);
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        reset(e.player);
    }

    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        reset(e.player);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent e) {
        reset(e.player);
    }

    @SubscribeEvent
    public void unload(net.minecraftforge.event.world.WorldEvent.Unload e) {
        if (e.world.isRemote) return;
        for (Active a : new java.util.ArrayList<>(active.values())) if (a.world == e.world) reset(a.player);
    }
}
