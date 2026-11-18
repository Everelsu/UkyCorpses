package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.stains.SurfaceBlood;
import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** 1.7.10 world/render bridge; simulation constants and effect controls follow SurfaceBlood 0.1.5-beta. */
public final class SurfaceBloodClient {

    static final List<Stain> stains = new ArrayList<>();
    private static final List<Drop> drops = new ArrayList<>();
    private static final List<Emitter> emitters = new ArrayList<>();
    private static final Random random = new Random();
    private static final SourceSprayEmitter sourceSpray = new SourceSprayEmitter(new SourceDrops());
    private static final List<Burst> bursts = new ArrayList<>();
    private static final SourceClouds clouds = new SourceClouds();
    private static final SourceVoxelCollider collider = new SourceVoxelCollider();
    private static final PoolSpots poolSpots = new PoolSpots();
    static {
        sourceSpray.follow(SourceDrops::pose);
    }

    private static final class Burst {

        double x, y, z;
        boolean big, flip;
        int color, age;
    }

    static void burst(double x, double y, double z, boolean big, boolean flip, int color) {
        if (bursts.size() >= 64) bursts.remove(0);
        Burst burst = new Burst();
        burst.x = x;
        burst.y = y;
        burst.z = z;
        burst.big = big;
        burst.flip = flip;
        burst.color = color;
        bursts.add(burst);
    }

    static void spawnSource(double x, double y, double z, float vx, float vy, float vz, float size, int color,
        int dried, int flags, float shade, int life, int source, float tau, float ax, float ay, float az) {
        if (drops.size() >= SurfaceBloodClientConfig.MAX_DROPLETS.get()) drops.remove(0);
        Drop d = new Drop();
        d.x = d.px = x;
        d.y = d.py = y;
        d.z = d.pz = z;
        d.vx = vx;
        d.vy = vy;
        d.vz = vz;
        d.size = size;
        d.color = shade(color, shade);
        d.dried = shade(dried, shade);
        d.flags = flags;
        d.life = Math.min(120, life);
        d.source = source;
        d.mist = (flags & 4) != 0;
        d.tau = tau > 0 ? tau : Math.max(.02F, .65F * (size / .008F) * (size / .008F));
        d.airX = ax;
        d.airY = ay;
        d.airZ = az;
        drops.add(d);
    }

    private static int shade(int rgb, float shade) {
        return Math.min(255, (int) ((rgb >> 16 & 255) * shade)) << 16
            | Math.min(255, (int) ((rgb >> 8 & 255) * shade)) << 8
            | Math.min(255, (int) ((rgb & 255) * shade));
    }

    private static World lastWorld;
    private static String worldKey;
    private static long ticks;
    private static int soundBudget;

    private static final class Drop {

        double x, y, z, px, py, pz;
        float vx, vy, vz, size, tau, airX, airY, airZ;
        int age, color, dried, flags = 1, life = 120, source = -1;
        boolean mist;
    }

    private static final class Emitter {

        int entity, left;
        Entity source;
        double x, y, z;
        float amount;
        int[] colors;
        boolean dead;
    }

    public static World world() {
        return Minecraft.getMinecraft().theWorld;
    }

    public static void init() {
        SurfaceBloodClient h = new SurfaceBloodClient();
        MinecraftForge.EVENT_BUS.register(h);
        MinecraftForge.EVENT_BUS.register(new BloodVignette());
        FMLCommonHandler.instance()
            .bus()
            .register(h);
    }

    public static void hit(SurfaceBlood.Hit h) {
        if (!SurfaceBlood.enabled || world() == null) return;
        Entity who = world().getEntityByID(h.entity);
        if (who == null) return;
        String name = net.minecraft.entity.EntityList.getEntityString(who);
        for (String blocked : SurfaceBloodClientConfig.ENTITY_BLACKLIST.get())
            if (blocked.equalsIgnoreCase(name) || blocked.equalsIgnoreCase("minecraft:" + name)) return;
        BloodTypes.Type blood = BloodTypes.of(who);
        if (!blood.bleeds) return;
        if (h.dead) {
            if (SurfaceBloodClientConfig.DEATH_POOLS.get()) {
                Emitter e = new Emitter();
                e.entity = h.entity;
                e.source = who;
                e.left = (int) (120 * SurfaceBloodClientConfig.BLEED_TIME.get());
                if (!blood.flows) return;
                e.amount = .45F * blood.amount;
                e.colors = colors(who);
                e.dead = true;
                e.x = h.x;
                e.y = who.boundingBox.minY + .1;
                e.z = h.z;
                emitters.add(e);
            }
            return;
        }
        int[] colors = colors(who);
        SkinBlood.hit(
            who,
            h.x,
            h.y,
            h.z,
            h.amount,
            colors[0],
            colors[1],
            com.uky.corpses.stains.DamageCategory.values()[h.category]);
        Entity attacker = world().getEntityByID(h.attacker);
        if (h.melee && attacker instanceof EntityLivingBase)
            GearBlood.hit((EntityLivingBase) attacker, h.amount, colors[0], colors[1]);
        if (!blood.flows) return;
        if (who instanceof EntityLivingBase) {
            BloodTypes.Type tinted = new BloodTypes.Type(
                blood.name,
                colors[0],
                colors[1],
                blood.amount,
                blood.bleeds,
                blood.flows);
            sourceSpray.emit(
                (EntityLivingBase) who,
                new BloodVec(h.x, h.y, h.z),
                new BloodVec(h.dx, h.dy, h.dz),
                h.amount,
                h.speed,
                com.uky.corpses.stains.DamageCategory.values()[h.category],
                h.fatal,
                h.seed,
                tinted);
            if (attacker instanceof EntityLivingBase) sourceSpray.backSpatter(
                (EntityLivingBase) attacker,
                h.entity,
                new BloodVec(h.x, h.y, h.z),
                h.amount,
                h.seed,
                tinted);
        }
        if (SurfaceBloodClientConfig.BLEEDING.get()) {
            float severity = SurfaceBloodClientConfig.hitScale(h.amount, h.fatal);
            Emitter e = new Emitter();
            e.entity = h.entity;
            e.source = who;
            e.left = (int) (120 * SurfaceBloodClientConfig.BLEED_TIME.get() * severity);
            e.colors = colors;
            e.amount = severity * blood.amount
                * (float) (Math.min(2, h.amount * .15) * SurfaceBloodClientConfig.BLEED_AMOUNT.get());
            emitters.add(e);
        }
        sound(
            "hit",
            h.x,
            h.y,
            h.z,
            SurfaceBloodClientConfig.HIT_VOLUME.get()
                .floatValue());
    }

    public static int[] colors(Entity entity) {
        BloodTypes.Type type = BloodTypes.of(entity);
        int[] palette = colors();
        if (SurfaceBloodClientConfig.PALETTE.get() == SurfaceBloodClientConfig.Palette.NATURAL
            || !type.name.equals("red") && !type.name.equals("rotten")) return new int[] { type.color, type.dried };
        if (type.name.equals("rotten")) for (int i = 0; i < 2; i++) {
            int rgb = palette[i];
            palette[i] = ((int) ((rgb >> 16 & 255) * .75F) << 16) | ((int) ((rgb >> 8 & 255) * .75F) << 8)
                | (int) ((rgb & 255) * .75F);
        }
        return palette;
    }

    public static int[] colors() {
        SurfaceBloodClientConfig.Palette p = SurfaceBloodClientConfig.PALETTE.get();
        if (p == SurfaceBloodClientConfig.Palette.CUSTOM) {
            try {
                return new int[] { Integer.parseInt(
                    SurfaceBloodClientConfig.CUSTOM_COLOR.get()
                        .replace("#", ""),
                    16),
                    Integer.parseInt(
                        SurfaceBloodClientConfig.CUSTOM_DRIED.get()
                            .replace("#", ""),
                        16) };
            } catch (NumberFormatException ignored) {}
        }
        return p == SurfaceBloodClientConfig.Palette.NATURAL ? new int[] { 9310481, 5116942 }
            : new int[] { p.color, p.dried };
    }

    public static void spray(double x, double y, double z, float dx, float dy, float dz, float amount, boolean mist) {
        spray(x, y, z, dx, dy, dz, amount, mist, colors());
    }

    private static void spray(double x, double y, double z, float dx, float dy, float dz, float amount, boolean mist,
        int[] color) {
        spray(x, y, z, dx, dy, dz, amount, mist, color, false, -1);
    }

    private static void spray(double x, double y, double z, float dx, float dy, float dz, float amount, boolean mist,
        int[] color, boolean drip, int source) {
        int count = (int) Math.min(
            256,
            Math.max(
                1,
                amount * 9
                    * SurfaceBloodClientConfig.AMOUNT.get()
                    * SurfaceBloodClientConfig.preset().spray
                    * SurfaceBloodClientConfig.QUALITY.get().droplets));
        for (int i = 0; i < count; i++) {
            if (drops.size() >= SurfaceBloodClientConfig.MAX_DROPLETS.get()) drops.remove(0);
            Drop d = new Drop();
            d.x = d.px = x;
            d.y = d.py = y;
            d.z = d.pz = z;
            d.mist = mist;
            d.source = source;
            float speed = SurfaceBloodClientConfig.SPRAY_SPEED.get()
                .floatValue() * (mist ? .08F : .24F);
            d.vx = (dx + (float) random.nextGaussian() * .5F) * speed;
            d.vy = (dy + (float) random.nextGaussian() * .4F) * speed;
            d.vz = (dz + (float) random.nextGaussian() * .5F) * speed;
            if (drip) {
                d.vx = 0;
                d.vy = -.03F;
                d.vz = 0;
                d.flags = 3;
            }
            d.size = (float) ((.018 + random.nextDouble() * .035) * SurfaceBloodClientConfig.DROPLET_SIZE.get());
            float microns = d.size / .008F;
            d.tau = Math.max(.02F, .65F * microns * microns);
            d.color = color[0];
            d.dried = color[1];
            drops.add(d);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        World world = world();
        if (world != lastWorld) {
            if (lastWorld != null && SurfaceBloodClientConfig.KEEP_STAINS.get())
                StainSaves.save(stains, worldKey, lastWorld.provider.dimensionId, ticks);
            StainMesh.clear();
            drops.clear();
            sourceSpray.clear();
            bursts.clear();
            clouds.clear();
            poolSpots.clear();
            SkinBlood.clear();
            GearBlood.clear();
            Stumps.clear();
            emitters.clear();
            Footprints.clear();
            stains.clear();
            lastWorld = world;
            if (world != null) {
                ticks = world.getTotalWorldTime();
                worldKey = StainSaves.worldKey();
                if (SurfaceBloodClientConfig.KEEP_STAINS.get())
                    StainSaves.load(stains, worldKey, world.provider.dimensionId, ticks);
                for (Stain stain : stains) if (stain.pooled) poolSpots.remember(stain);
            }
        }
        if (world == null || !SurfaceBlood.enabled
            || Minecraft.getMinecraft()
                .isGamePaused())
            return;
        ticks = world.getTotalWorldTime();
        BloodVignette.tick();
        GearBlood.tick();
        Stumps.tick(world);
        sourceSpray.tick();
        clouds.tick(world);
        bursts.removeIf(burst -> ++burst.age >= 6);
        for (Object entity : world.loadedEntityList)
            if (entity instanceof EntityLivingBase) SkinBlood.tick((EntityLivingBase) entity);
        if (ticks % 20 == 0) soundBudget = 6;
        for (Iterator<Drop> it = drops.iterator(); it.hasNext();) {
            Drop d = it.next();
            d.px = d.x;
            d.py = d.y;
            d.pz = d.z;
            double stepX, stepY, stepZ;
            if (d.mist) {
                float tau = Math.max(.02F, d.tau);
                float rx = d.vx - d.airX, ry = d.vy - d.airY, rz = d.vz - d.airZ;
                float relative = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                float reynolds = relative * 100 * (float) Math.sqrt(tau / .65F) * 1.326F;
                if (reynolds > .1F) tau /= 1 + .15F * (float) Math.pow(reynolds, .687);
                float k = (float) Math.exp(-1 / tau), span = tau * (1 - k);
                float tx = d.airX, ty = d.airY - Math.min(.06F, .024525002F * tau), tz = d.airZ;
                stepX = tx + (d.vx - tx) * span;
                stepY = ty + (d.vy - ty) * span;
                stepZ = tz + (d.vz - tz) * span;
                d.vx = tx + (d.vx - tx) * k;
                d.vy = ty + (d.vy - ty) * k;
                d.vz = tz + (d.vz - tz) * k;
                d.airX = d.airX * .85F + (float) random.nextGaussian() * .0025F;
                d.airY = d.airY * .85F + (float) random.nextGaussian() * .0025F;
                d.airZ = d.airZ * .85F + (float) random.nextGaussian() * .0025F;
            } else {
                float drag = 1 - Math.min(.5F, .0022F / d.size);
                d.vx *= drag;
                d.vy = (d.vy - .055F) * drag;
                d.vz *= drag;
                stepX = d.vx;
                stepY = d.vy;
                stepZ = d.vz;
            }
            Vec3 from = Vec3.createVectorHelper(d.x, d.y, d.z), to = from.addVector(stepX, stepY, stepZ);
            int collision = collider.sweep(world, d.x, d.y, d.z, to.xCoord, to.yCoord, to.zCoord);
            Vec3 end = collision == SourceVoxelCollider.MISS ? to
                : Vec3.createVectorHelper(collider.hitX, collider.hitY, collider.hitZ);
            if (spatter(world, d, from, end)) {
                it.remove();
                continue;
            }
            if (collision == SourceVoxelCollider.BLOCK) {
                land(
                    d,
                    new MovingObjectPosition(
                        collider.hitBlockX,
                        collider.hitBlockY,
                        collider.hitBlockZ,
                        collider.hitFace.ordinal(),
                        Vec3.createVectorHelper(collider.hitX, collider.hitY, collider.hitZ)));
                it.remove();
                continue;
            }
            if (collision == SourceVoxelCollider.FLUID) {
                if (collider.hitLava) sound("sizzle", collider.hitX, collider.hitY, collider.hitZ, d.size);
                else if (SurfaceBloodClientConfig.WATER.get() && !d.mist)
                    clouds.add(collider.hitX, collider.hitY - .05, collider.hitZ, d.size, d.color, d.vx, d.vz);
                it.remove();
                continue;
            }
            d.x = to.xCoord;
            d.y = to.yCoord;
            d.z = to.zCoord;
            Material material = world
                .getBlock(MathHelper.floor_double(d.x), MathHelper.floor_double(d.y), MathHelper.floor_double(d.z))
                .getMaterial();
            if (material == Material.lava) {
                sound(
                    "sizzle",
                    d.x,
                    d.y,
                    d.z,
                    SurfaceBloodClientConfig.SIZZLE_VOLUME.get()
                        .floatValue());
                it.remove();
                continue;
            }
            if (material == Material.water) {
                if (SurfaceBloodClientConfig.WATER.get() && !d.mist)
                    clouds.add(d.x, d.y - .05, d.z, d.size, d.color, d.vx, d.vz);
                it.remove();
                continue;
            }
            if (++d.age > d.life || d.y < 0) it.remove();
        }
        for (Iterator<Emitter> it = emitters.iterator(); it.hasNext();) {
            Emitter e = it.next();
            Entity source = world.getEntityByID(e.entity);
            if (source != e.source
                || source instanceof EntityLivingBase && !((EntityLivingBase) source).isEntityAlive()) source = null;
            if (--e.left < 0 || source == null && !e.dead) {
                it.remove();
                continue;
            }
            if (source != null) {
                e.x = source.posX;
                e.y = source.boundingBox.minY + (e.dead ? .12 : source.height * .6);
                e.z = source.posZ;
            }
            if (ticks % 4 == 0) spray(
                e.x,
                e.y,
                e.z,
                0,
                e.dead ? -.03F : 0,
                0,
                e.amount * (float) SurfaceBloodClientConfig.preset().bleed,
                false,
                e.colors == null ? colors() : e.colors,
                true,
                e.entity);
        }
        for (Iterator<Stain> it = stains.iterator(); it.hasNext();) {
            Stain s = it.next();
            if (s.alpha(ticks) <= 0) {
                it.remove();
                continue;
            }
            // An unloaded client chunk reports air; it is not evidence that the stained block was removed.
            if (!StainMesh.loaded(world, s)) continue;
            int bx = MathHelper.floor_double(s.x - s.face.offsetX * .02),
                by = MathHelper.floor_double(s.y - s.face.offsetY * .02),
                bz = MathHelper.floor_double(s.z - s.face.offsetZ * .02);
            Material m = world
                .getBlock(MathHelper.floor_double(s.x), MathHelper.floor_double(s.y), MathHelper.floor_double(s.z))
                .getMaterial();
            if (m == Material.water || world.isRaining() && world.canBlockSeeTheSky(bx, by + 1, bz))
                s.wash = Math.min(1, s.wash + (m == Material.water ? .04F : .002F));
            if (s.alpha(ticks) <= 0 || world.isAirBlock(bx, by, bz) && !StainMesh.supported(world, s)) {
                it.remove();
                continue;
            }
            if (!s.ran && SurfaceBloodClientConfig.RUNS.get()
                && s.face.offsetY == 0
                && s.length > .08
                && ticks - s.born > 20) {
                float scale = SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL ? 1 / 16F : 1 / 32F;
                StainSprites.Sprite sprite = StainSprites.nearest("run", s.length * 16 >= 3 ? 2 : 1, random);
                Stain run = StainPlacement.place(
                    s.face,
                    s.x,
                    s.y,
                    s.z,
                    (float) Math.PI,
                    sprite,
                    scale,
                    scale,
                    sprite.ax,
                    sprite.ay,
                    SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL);
                s.x = run.x;
                s.y = run.y;
                s.z = run.z;
                s.length = run.length;
                s.width = run.width;
                s.angle = run.angle;
                s.sprite = sprite;
                s.family = "run";
                s.ran = true;
                s.spread(1, -80, ticks);

            }
            if (SurfaceBloodClientConfig.DRIPS.get() && s.face == ForgeDirection.DOWN
                && s.dry(ticks) < .5
                && random.nextInt(60) == 0) spray(s.x, s.y - .03, s.z, 0, -.1F, 0, .1F, false);
            if (!s.soaked && s.surface.through()
                && SurfaceBloodClientConfig.SOAK_THROUGH.get()
                && ticks - s.born > SurfaceBloodClientConfig.SOAK_TIME.get() * 20L) {
                s.soaked = true;
                Drop d = new Drop();
                d.x = s.x - s.face.offsetX * 1.02;
                d.y = s.y - s.face.offsetY * 1.02;
                d.z = s.z - s.face.offsetZ * 1.02;
                d.vy = -.02F;
                d.size = .02F;
                d.color = s.color;
                d.dried = s.dried;
                if (drops.size() < SurfaceBloodClientConfig.MAX_DROPLETS.get()) drops.add(d);
            }
        }
        if (SurfaceBloodClientConfig.FOOTPRINTS.get()) footprints(world);
        if (SurfaceBloodClientConfig.KEEP_STAINS.get() && ticks % 1200 == 0)
            StainSaves.save(stains, worldKey, world.provider.dimensionId, ticks);
    }

    private static void land(Drop d, MovingObjectPosition hit) {
        if (d.mist && (d.flags & 1) == 0) return;
        ForgeDirection face = ForgeDirection.getOrientation(hit.sideHit);
        Stain s = StainPlacement.impact(
            face,
            hit.hitVec.xCoord,
            hit.hitVec.yCoord,
            hit.hitVec.zCoord,
            d.vx,
            d.vy,
            d.vz,
            d.size,
            d.color,
            d.dried,
            Surface.of(world().getBlock(hit.blockX, hit.blockY, hit.blockZ)),
            ticks,
            random);
        if (s == null) return;
        if (poolSpots.catchDrop(s, d.size, (d.flags & 2) != 0, ticks, random)) return;
        add(s);
        sound(
            "splat",
            s.x,
            s.y,
            s.z,
            SurfaceBloodClientConfig.SPLAT_VOLUME.get()
                .floatValue());
    }

    private static boolean spatter(World world, Drop drop, Vec3 from, Vec3 to) {
        if (!SurfaceBloodClientConfig.SPATTER.get() || (drop.flags & 1) == 0) return false;
        net.minecraft.util.AxisAlignedBB path = net.minecraft.util.AxisAlignedBB
            .getBoundingBox(
                Math.min(from.xCoord, to.xCoord),
                Math.min(from.yCoord, to.yCoord),
                Math.min(from.zCoord, to.zCoord),
                Math.max(from.xCoord, to.xCoord),
                Math.max(from.yCoord, to.yCoord),
                Math.max(from.zCoord, to.zCoord))
            .expand(.05, .05, .05);
        EntityLivingBase target = null;
        Vec3 point = null;
        double nearest = Double.MAX_VALUE;
        for (Object value : world.getEntitiesWithinAABB(EntityLivingBase.class, path)) {
            EntityLivingBase living = (EntityLivingBase) value;
            if (living.getEntityId() == drop.source || !living.isEntityAlive()) continue;
            MovingObjectPosition hit = living.boundingBox.calculateIntercept(from, to);
            Vec3 at = living.boundingBox.isVecInside(from) ? from : hit == null ? null : hit.hitVec;
            if (at != null && from.squareDistanceTo(at) < nearest) {
                target = living;
                point = at;
                nearest = from.squareDistanceTo(at);
            }
        }
        if (target == null) return false;
        SkinBlood.spatter(target, point.xCoord, point.yCoord, point.zCoord, drop.size, drop.color, drop.dried);
        return true;
    }

    static void add(Stain s) {
        while (stains.size() >= SurfaceBloodClientConfig.MAX_STAINS.get()) stains.remove(0);
        if (s.sprite == null) s.sprite = StainSprites.choose(s.family, random);
        stains.add(s);
    }

    private static void footprints(World world) {
        Footprints.tick(world, stains, ticks);
    }

    static void sound(String kind, double x, double y, double z, float volume) {
        if (!SurfaceBloodClientConfig.SOUNDS.get() || volume <= 0 || soundBudget <= 0) return;
        soundBudget--;
        List<? extends String> ids = kind.equals("hit") ? SurfaceBloodClientConfig.HIT_SOUNDS.get()
            : kind.equals("step") ? SurfaceBloodClientConfig.STEP_SOUNDS.get()
                : kind.equals("squelch") ? SurfaceBloodClientConfig.SQUELCH_SOUNDS.get()
                    : kind.equals("sizzle") ? SurfaceBloodClientConfig.SIZZLE_SOUNDS.get()
                        : SurfaceBloodClientConfig.SPLAT_SOUNDS.get();
        if (!ids.isEmpty()) world().playSound(
            x,
            y,
            z,
            ids.get(random.nextInt(ids.size())),
            volume * SurfaceBloodClientConfig.VOLUME.get()
                .floatValue(),
            .9F + random.nextFloat() * .2F,
            false);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (!SurfaceBlood.enabled || world() == null) return;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_POLYGON_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(StainSprites.texture());
        Tessellator t = Tessellator.instance;
        StainShader.begin(event.partialTicks);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1, -1);
        t.startDrawingQuads();
        double range = SurfaceBloodClientConfig.RENDER_DISTANCE.get(), cx = RenderManager.renderPosX,
            cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        for (Stain s : stains) {
            if ((s.x - cx) * (s.x - cx) + (s.y - cy) * (s.y - cy) + (s.z - cz) * (s.z - cz) > range * range) continue;
            StainMesh.draw(world(), s, t, cx, cy, cz, event.partialTicks);
        }
        t.draw();
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        t.startDrawingQuads();
        StainSprites.Sprite sprite = StainSprites.largest("droplet");
        for (Drop d : drops) {
            double x = d.px + (d.x - d.px) * event.partialTicks - cx, y = d.py + (d.y - d.py) * event.partialTicks - cy,
                z = d.pz + (d.z - d.pz) * event.partialTicks - cz;
            if (x * x + y * y + z * z > range * range) continue;
            float speed = (float) Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
            if (SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL) {
                int texels = d.mist ? 1 : Math.max(1, Math.round(d.size * 40));
                StainSprites.Sprite pixel = StainSprites.nearest("droplet", texels);
                float half = texels * .03125F;
                billboard(t, x, y, z, half, pixel, d.color, d.mist ? .2F : .9F);
                int trail = Math.min(3, (int) (speed * 8));
                if (!d.mist && speed > 0) for (int k = 1; k <= trail; k++) {
                    float back = .0625F * Math.max(1, texels - 1) / speed * (k + .5F * (texels - 1));
                    billboard(
                        t,
                        x - d.vx * back,
                        y - d.vy * back,
                        z - d.vz * back,
                        k == trail ? .03125F : Math.max(1, texels - 1) * .03125F,
                        pixel,
                        d.color,
                        .9F * (1 - k / (trail + 1F)));
                }
            } else if (speed > .02F) {
                streak(t, x, y, z, d, speed, sprite);
            } else billboard(t, x, y, z, d.size * .5F, sprite, d.color, d.mist ? .2F : .9F);
        }
        for (Burst burst : bursts) {
            double x = burst.x - cx, y = burst.y - cy, z = burst.z - cz;
            if (x * x + y * y + z * z > range * range) continue;
            StainSprites.Sprite burstSprite = burst.big ? StainSprites.largest("burst")
                : StainSprites.nearest("burst", 1);
            int frame = Math
                .min(burstSprite.frames - 1, (int) ((burst.age + event.partialTicks) / 6 * burstSprite.frames));
            StainSprites.Sprite animated = new StainSprites.Sprite(
                burstSprite.u + frame * burstSprite.w + (burst.flip ? burstSprite.w : 0),
                burstSprite.v,
                burst.flip ? -burstSprite.w : burstSprite.w,
                burstSprite.h,
                burstSprite.pixels,
                1);
            float texel = SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL ? .0625F : .03125F;
            billboard(t, x, y, z, burstSprite.pixels * texel * .5F, animated, burst.color, .95F);
        }
        clouds.render(t, event.partialTicks);
        t.draw();
        StainShader.end();
        GL11.glPopAttrib();
    }

    private static void streak(Tessellator t, double x, double y, double z, Drop d, float speed,
        StainSprites.Sprite sprite) {
        float half = d.size * .5F * (d.mist ? 1 + d.age * .12F : 1);
        float stretch = half + speed * .5F;
        double ax = d.vx / speed * stretch, ay = d.vy / speed * stretch, az = d.vz / speed * stretch;
        double sx = ay * z - az * y, sy = az * x - ax * z, sz = ax * y - ay * x;
        double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (length < .000001) {
            billboard(t, x, y, z, half, sprite, d.color, d.mist ? .2F : .9F);
            return;
        }
        sx *= half / length;
        sy *= half / length;
        sz *= half / length;
        t.setNormal(-1, -1, 1);
        t.setColorRGBA_F(
            (d.color >> 16 & 255) / 255F,
            (d.color >> 8 & 255) / 255F,
            (d.color & 255) / 255F,
            d.mist ? .2F : .9F);
        for (int i = 0; i < 4; i++) {
            int a = i < 2 ? -1 : 1, b = i == 0 || i == 3 ? -1 : 1;
            t.addVertexWithUV(
                x + a * ax + b * sx,
                y + a * ay + b * sy,
                z + a * az + b * sz,
                sprite.u + (b > 0 ? sprite.w : 0),
                sprite.v + (a > 0 ? sprite.h : 0));
        }
    }

    static void billboard(Tessellator t, double x, double y, double z, float size, StainSprites.Sprite s, int color,
        float alpha) {
        t.setNormal(-1, -1, 1);
        float brightness = SurfaceBloodClientConfig.DROPLET_BRIGHTNESS.get()
            .floatValue();
        t.setColorRGBA_F(
            Math.min(1, (color >> 16 & 255) / 255F * brightness),
            Math.min(1, (color >> 8 & 255) / 255F * brightness),
            Math.min(1, (color & 255) / 255F * brightness),
            alpha);
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        double rx = Math.cos(yaw), rz = Math.sin(yaw), ux = -Math.sin(yaw) * Math.sin(pitch), uy = Math.cos(pitch),
            uz = Math.cos(yaw) * Math.sin(pitch);
        for (int i = 0; i < 4; i++) {
            double a = (i == 1 || i == 2 ? 1 : -1) * size, b = (i >= 2 ? 1 : -1) * size;
            t.addVertexWithUV(
                x + rx * a + ux * b,
                y + uy * b,
                z + rz * a + uz * b,
                s.u + (i == 1 || i == 2 ? s.w : 0),
                s.v + (i >= 2 ? s.h : 0));
        }
    }

    static void quad(Tessellator t, double x, double y, double z, float length, float width, float angle,
        ForgeDirection face, StainSprites.Sprite sprite, int color, float alpha) {
        t.setColorRGBA_F((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha);
        double co = Math.cos(angle), si = Math.sin(angle);
        for (int i = 0; i < 4; i++) {
            double a = (i == 1 || i == 2 ? 1 : -1) * length, b = (i >= 2 ? 1 : -1) * width, u = a * co - b * si,
                v = a * si + b * co;
            double vx = face.offsetX != 0 ? 0 : u, vy = face.offsetY != 0 ? 0 : v,
                vz = face.offsetX != 0 ? u : face.offsetY != 0 ? v : 0;
            t.addVertexWithUV(
                x + vx,
                y + vy,
                z + vz,
                sprite.u + (i == 1 || i == 2 ? sprite.w : 0),
                sprite.v + (i >= 2 ? sprite.h : 0));
        }
    }
}
