package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.vecmath.Vector3f;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;

import com.uky.corpses.stains.DamageCategory;
import com.uky.corpses.stains.SurfaceBloodClientConfig;

public final class SourceSprayEmitter {

    private static final int MAX_PER_HIT = 220;
    private static final int MAX_STREAMS = 64;
    private static final int MAX_PARTS = 16;
    private final SourceDrops droplets;
    private final List<SourceSprayEmitter.Stream> streams = new ArrayList<>();
    private SourceSprayEmitter.Poses poses = (id, positions, rotations) -> 0;
    private final float[] positions = new float[48];
    private final float[] rotations = new float[64];

    public SourceSprayEmitter(SourceDrops droplets) {
        this.droplets = droplets;
    }

    public void follow(SourceSprayEmitter.Poses poses) {
        this.poses = poses;
    }

    public void emit(EntityLivingBase victim, BloodVec impact, BloodVec direction, float damage, float speed,
        DamageCategory category, boolean fatal, int seed, BloodTypes.Type blood) {
        if (blood.flows && category != DamageCategory.NONE) {
            this.droplets.emitting(victim.getEntityId());

            try {
                this.throwAll(victim, impact, direction, damage, speed, category, fatal, seed, blood);
            } finally {
                this.droplets.emitting(-1);
            }
        }
    }

    public void backSpatter(EntityLivingBase attacker, int source, BloodVec impact, float damage, int seed,
        BloodTypes.Type blood) {
        if (blood.flows && (Boolean) SurfaceBloodClientConfig.SPATTER.get()
            && (Boolean) SurfaceBloodClientConfig.SPRAY.get()) {
            AxisAlignedBB box = attacker.boundingBox;
            if (!(BloodVec.center(box)
                .distanceToSqr(impact) > 30.25)
                && !box.isVecInside(net.minecraft.util.Vec3.createVectorHelper(impact.x, impact.y, impact.z))) {
                Random random = new Random((long) (seed ^ 47813));
                float scale = ((Double) SurfaceBloodClientConfig.AMOUNT.get()).floatValue() * blood.amount
                    * SurfaceBloodClientConfig.preset().spray;
                int count = WoundMath.clamp(
                    Math.round(
                        (1.0F + damage * 0.45F) * scale
                            * SurfaceBloodClientConfig.hitScale(damage, false)
                            * (0.5F + random.nextFloat())),
                    0,
                    12);
                this.droplets.emitting(source);

                for (int i = 0; i < count; i++) {
                    BloodVec target = new BloodVec(
                        WoundMath.lerp(0.2 + random.nextDouble() * 0.6, box.minX, box.maxX),
                        WoundMath.lerp(0.35 + random.nextDouble() * 0.6, box.minY, box.maxY),
                        WoundMath.lerp(0.2 + random.nextDouble() * 0.6, box.minZ, box.maxZ));
                    float size = WoundMath.clamp(0.026F * (float) Math.exp(random.nextGaussian() * 0.4), 0.014F, 0.06F)
                        * dropletSize();
                    int ticks = WoundMath
                        .clamp((int) Math.round(target.distanceTo(impact) / (0.3 + random.nextDouble() * 0.15)), 2, 14);
                    BloodVec v = SourceDrops.aim(impact, target, size, ticks);
                    BloodVec d = cone(random, v.normalize(), 0.1F).scale(v.length());
                    this.droplets.spawn(
                        impact.x,
                        impact.y,
                        impact.z,
                        (float) d.x,
                        (float) d.y,
                        (float) d.z,
                        size,
                        blood.color,
                        blood.dried,
                        1,
                        0.85F + random.nextFloat() * 0.25F,
                        120);
                }

                this.droplets.emitting(-1);
            }
        }
    }

    private void throwAll(EntityLivingBase victim, BloodVec impact, BloodVec direction, float damage, float speed,
        DamageCategory category, boolean fatal, int seed, BloodTypes.Type blood) {
        Random random = new Random((long) seed);
        float scale = ((Double) SurfaceBloodClientConfig.AMOUNT.get()).floatValue()
            * ((SurfaceBloodClientConfig.Quality) SurfaceBloodClientConfig.QUALITY.get()).droplets
            * blood.amount
            * SurfaceBloodClientConfig.preset().spray;
        int count = WoundMath.clamp(
            Math.round(
                damage * 5.0F * scale * SurfaceBloodClientConfig.hitScale(damage, fatal) * (fatal ? 1.5F : 1.0F)),
            0,
            220);
        if (count != 0) {
            float power = (0.09F + 0.035F * WoundMath.sqrt(Math.min(damage, 40.0F)))
                * ((Double) SurfaceBloodClientConfig.SPRAY_SPEED.get()).floatValue();
            BloodVec dir = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : new BloodVec(0.0, 0.0, 1.0);
            AxisAlignedBB box = victim.boundingBox;
            BloodVec centre = BloodVec.center(box);
            BloodVec out = new BloodVec(impact.x - centre.x, 0.0, impact.z - centre.z);
            out = out.lengthSqr() < 1.0E-4 ? dir.scale(-1.0) : out.normalize();
            out = out.add(0.0, 0.35, 0.0)
                .normalize();
            BloodVec exit = category == DamageCategory.PIERCE ? SourceDrops.exit(box, impact, dir) : null;
            boolean through = exit != null && SourceGunshotMist.exits(speed, damage, exit.distanceTo(impact));
            int burst = Math.max(1, count * 3 / 5);
            if (category != DamageCategory.FALL && (Boolean) SurfaceBloodClientConfig.BURST.get()) {
                this.droplets
                    .burst(impact.x, impact.y, impact.z, damage >= 6.0F || fatal, random.nextBoolean(), blood.color);
            }

            if ((Boolean) SurfaceBloodClientConfig.SPRAY.get()) {
                switch (category) {
                    case SHARP:
                        BloodVec side = dir.cross(new BloodVec(0.0, 1.0, 0.0));
                        side = side.lengthSqr() < 1.0E-4 ? new BloodVec(1.0, 0.0, 0.0) : side.normalize();
                        if (random.nextBoolean()) {
                            side = side.scale(-1.0);
                        }

                        int castOff = burst * 2 / 3;

                        for (int i = 0; i < castOff; i++) {
                            float along = random.nextFloat();
                            BloodVec d = side.scale(0.8 + (double) along * 0.4)
                                .add(dir.scale(0.35))
                                .add(0.0, 0.15 + (double) random.nextFloat() * 0.35, 0.0);
                            this.throwDrop(
                                random,
                                blood,
                                impact,
                                cone(random, d.normalize(), 0.3F),
                                power * (0.8F + along * 0.7F),
                                1.0F);
                        }

                        BloodVec down = dir.scale(-0.4)
                            .add(0.0, 0.5, 0.0)
                            .normalize();

                        for (int i = castOff; i < burst; i++) {
                            this.throwDrop(
                                random,
                                blood,
                                impact,
                                cone(random, down, 1.1F),
                                power * (0.25F + random.nextFloat() * 0.35F),
                                0.8F);
                        }
                        break;
                    case BLUNT:
                        BloodVec spray = dir.scale(-0.5)
                            .add(0.0, 0.6, 0.0)
                            .normalize();

                        for (int i = 0; i < burst; i++) {
                            this.throwDrop(
                                random,
                                blood,
                                impact,
                                cone(random, spray, 1.2F),
                                power * (0.3F + random.nextFloat() * 0.5F),
                                0.9F);
                        }
                        break;
                    case PIERCE:
                        SourceGunshotMist
                            .emit(this.droplets, impact, through ? exit : null, dir, speed, damage, blood, random);
                        int forward = !(damage >= 4.0F) || !(speed < 4.0F) && !through ? 0 : burst * 3 / 5;

                        for (int i = 0; i < forward; i++) {
                            this.throwDrop(
                                random,
                                blood,
                                exit,
                                cone(random, dir, 0.32F),
                                power * (1.2F + random.nextFloat() * 0.8F),
                                1.1F);
                        }

                        for (int i = forward; i < burst; i++) {
                            this.throwDrop(
                                random,
                                blood,
                                impact,
                                cone(random, dir.scale(-1.0), 0.6F),
                                power * (0.25F + random.nextFloat() * 0.25F),
                                0.55F);
                        }
                        break;
                    case EXPLOSION:
                        for (int i = 0; i < Math.min(220, count * 3 / 2); i++) {
                            BloodVec from = new BloodVec(
                                WoundMath.lerp(random.nextDouble(), box.minX, box.maxX),
                                WoundMath.lerp(random.nextDouble(), box.minY, box.maxY),
                                WoundMath.lerp(random.nextDouble(), box.minZ, box.maxZ));
                            BloodVec d = new BloodVec(
                                random.nextGaussian(),
                                random.nextGaussian() + 0.6,
                                random.nextGaussian());
                            this.throwDrop(
                                random,
                                blood,
                                from,
                                d.normalize(),
                                power * (0.8F + random.nextFloat() * 1.2F),
                                1.1F);
                        }
                        break;
                    case FALL:
                        BloodVec feet = new BloodVec(victim.posX, box.minY + 0.1, victim.posZ);

                        for (int i = 0; i < Math.max(1, count / 2); i++) {
                            float a = random.nextFloat() * (float) (Math.PI * 2);
                            BloodVec d = new BloodVec(
                                (double) WoundMath.cos(a),
                                0.15 + (double) random.nextFloat() * 0.3,
                                (double) WoundMath.sin(a)).normalize();
                            this.throwDrop(random, blood, feet, d, power * (0.3F + random.nextFloat() * 0.4F), 0.9F);
                        }
                }
            }

            if (category != DamageCategory.FALL && category != DamageCategory.EXPLOSION) {
                int mist = SurfaceBloodClientConfig.MIST.get() && SourceGunshotMist.strength(speed, damage) <= 0.0F
                    ? Math.min(40, count / 2 + 4)
                    : 0;

                for (int i = 0; i < mist; i++) {
                    BloodVec d = cone(
                        random,
                        out.add(dir.scale(-0.3))
                            .normalize(),
                        1.4F);
                    float v = 0.03F + random.nextFloat() * 0.07F;
                    this.droplets.spawn(
                        impact.x,
                        impact.y,
                        impact.z,
                        (float) d.x * v,
                        (float) d.y * v,
                        (float) d.z * v,
                        0.006F + random.nextFloat() * 0.005F,
                        blood.color,
                        blood.dried,
                        4,
                        0.85F + random.nextFloat() * 0.3F,
                        8 + random.nextInt(12));
                }

                int gobs = damage >= 5.0F && SurfaceBloodClientConfig.GOUTS.get()
                    ? 1 + (damage >= 10.0F ? 1 : 0) + random.nextInt(2)
                    : 0;

                for (int i = 0; i < gobs; i++) {
                    BloodVec d = cone(
                        random,
                        out.add(0.0, 0.3, 0.0)
                            .normalize(),
                        0.6F);
                    float v = power * (0.45F + random.nextFloat() * 0.3F);
                    this.droplets.spawn(
                        impact.x,
                        impact.y,
                        impact.z,
                        (float) d.x * v,
                        (float) d.y * v,
                        (float) d.z * v,
                        (0.065F + random.nextFloat() * 0.035F) * dropletSize(),
                        blood.color,
                        blood.dried,
                        1,
                        0.85F + random.nextFloat() * 0.15F,
                        120);
                }

                int stream = count - burst;
                if (stream > 0 && this.streams.size() < 64 && (Boolean) SurfaceBloodClientConfig.STREAM.get()) {
                    float yaw = victim.renderYawOffset * (float) (Math.PI / 180.0);
                    boolean onwards = through && damage >= 4.0F;
                    BloodVec local = (onwards ? exit : impact).subtract(BloodVec.at(victim))
                        .yRot(yaw);
                    BloodVec streamOut = onwards ? dir : out;
                    this.streams.add(
                        new SourceSprayEmitter.Stream(
                            victim,
                            local,
                            streamOut.yRot(yaw),
                            blood,
                            seed ^ 24301,
                            Math.max(
                                1,
                                Math.round(
                                    WoundMath.clamp(4 + Math.round(damage * 0.6F), 4, 14)
                                        * SurfaceBloodClientConfig.hitScale(damage, fatal))),
                            stream,
                            power * 0.75F));
                }
            }
        }
    }

    public void tick() {
        for (int i = this.streams.size() - 1; i >= 0; i--) {
            SourceSprayEmitter.Stream stream = this.streams.get(i);
            EntityLivingBase victim = stream.victim;
            if (victim instanceof net.minecraft.entity.player.EntityPlayer
                && victim.worldObj.getEntityByID(victim.getEntityId()) != victim) {
                this.streams.remove(i);
                continue;
            }
            int parts = this.poses.pose(victim.getEntityId(), this.positions, this.rotations);
            if ((!victim.isDead || parts != 0) && stream.left > 0) {
                BloodVec at;
                BloodVec out;
                BloodVec motion;
                if (parts > 0 && (stream.part >= 0 || this.pin(stream, parts))) {
                    int p = stream.part;
                    if (Float.isNaN(this.positions[p * 3])) {
                        this.streams.remove(i);
                        continue;
                    }

                    javax.vecmath.Quat4f turn = new javax.vecmath.Quat4f(
                        this.rotations[p * 4],
                        this.rotations[p * 4 + 1],
                        this.rotations[p * 4 + 2],
                        this.rotations[p * 4 + 3]);
                    javax.vecmath.Matrix3f rotation = new javax.vecmath.Matrix3f();
                    rotation.set(turn);
                    Vector3f local = new Vector3f(stream.partLocal);
                    rotation.transform(local);
                    Vector3f way = new Vector3f(stream.partOut);
                    rotation.transform(way);
                    at = new BloodVec(
                        (double) (this.positions[p * 3] + local.x),
                        (double) (this.positions[p * 3 + 1] + local.y),
                        (double) (this.positions[p * 3 + 2] + local.z));
                    out = new BloodVec((double) way.x, (double) way.y, (double) way.z);
                    BloodVec moved = stream.last == null ? BloodVec.ZERO : at.subtract(stream.last);
                    motion = moved.lengthSqr() > 0.25 ? BloodVec.ZERO : moved;
                } else {
                    at = bodyAt(stream);
                    out = stream.out.yRot(-victim.renderYawOffset * (float) (Math.PI / 180.0));
                    BloodVec moving = BloodVec.motion(victim);
                    motion = new BloodVec(moving.x, 0.0, moving.z);
                }

                stream.last = at;
                this.droplets.emitting(victim.getEntityId());
                float strength = (float) stream.left / (float) stream.total;
                stream.carry = stream.carry + stream.perTick * strength;
                Random random = stream.random;

                while (stream.carry >= 1.0F) {
                    stream.carry--;
                    BloodVec d = cone(random, out, 0.25F);
                    float v = stream.power * (0.35F + 0.65F * strength) * (0.75F + random.nextFloat() * 0.4F);
                    float size = WoundMath.clamp(0.032F * (float) Math.exp(random.nextGaussian() * 0.35), 0.015F, 0.07F)
                        * dropletSize();
                    this.droplets.spawn(
                        at.x,
                        at.y,
                        at.z,
                        (float) (d.x * (double) v + motion.x),
                        (float) (d.y * (double) v + motion.y),
                        (float) (d.z * (double) v + motion.z),
                        size,
                        stream.blood.color,
                        stream.blood.dried,
                        1,
                        0.85F + random.nextFloat() * 0.25F,
                        120);
                }

                this.droplets.emitting(-1);
                stream.left--;
            } else {
                this.streams.remove(i);
            }
        }
    }

    private static BloodVec bodyAt(SourceSprayEmitter.Stream stream) {
        return BloodVec.at(stream.victim)
            .add(stream.local.yRot(-stream.victim.renderYawOffset * (float) (Math.PI / 180.0)));
    }

    private boolean pin(SourceSprayEmitter.Stream stream, int parts) {
        BloodVec at = stream.last != null ? stream.last : bodyAt(stream);
        BloodVec out = stream.out.yRot(-stream.victim.renderYawOffset * (float) (Math.PI / 180.0));
        int best = -1;
        double bestDistance = Double.MAX_VALUE;

        for (int p = 0; p < Math.min(parts, 16); p++) {
            if (!Float.isNaN(this.positions[p * 3])) {
                double distance = at.distanceToSqr(
                    (double) this.positions[p * 3],
                    (double) this.positions[p * 3 + 1],
                    (double) this.positions[p * 3 + 2]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = p;
                }
            }
        }

        if (best < 0) {
            return false;
        } else {
            javax.vecmath.Quat4f undo = new javax.vecmath.Quat4f(
                this.rotations[best * 4],
                this.rotations[best * 4 + 1],
                this.rotations[best * 4 + 2],
                this.rotations[best * 4 + 3]);
            undo.conjugate();
            javax.vecmath.Matrix3f inverse = new javax.vecmath.Matrix3f();
            inverse.set(undo);
            stream.part = best;
            stream.partLocal = new Vector3f(
                (float) at.x - this.positions[best * 3],
                (float) at.y - this.positions[best * 3 + 1],
                (float) at.z - this.positions[best * 3 + 2]);
            inverse.transform(stream.partLocal);
            stream.partOut = new Vector3f((float) out.x, (float) out.y, (float) out.z);
            inverse.transform(stream.partOut);
            return true;
        }
    }

    public void clear() {
        this.streams.clear();
    }

    private void throwDrop(Random random, BloodTypes.Type blood, BloodVec from, BloodVec direction, float speed,
        float sizeScale) {
        float size = WoundMath.clamp(0.028F * sizeScale * (float) Math.exp(random.nextGaussian() * 0.55), 0.008F, 0.14F)
            * dropletSize();
        float v = speed * WoundMath.clamp(1.15F - size * 4.0F, 0.55F, 1.15F);
        this.droplets.spawn(
            from.x,
            from.y,
            from.z,
            (float) direction.x * v,
            (float) direction.y * v,
            (float) direction.z * v,
            size,
            blood.color,
            blood.dried,
            size >= 0.012F ? 1 : 4,
            0.85F + random.nextFloat() * 0.25F,
            size >= 0.012F ? 120 : 10 + random.nextInt(10));
    }

    private static float dropletSize() {
        return ((Double) SurfaceBloodClientConfig.DROPLET_SIZE.get()).floatValue();
    }

    public static BloodVec cone(Random random, BloodVec axis, float halfAngle) {
        BloodVec helper = Math.abs(axis.y) < 0.9 ? new BloodVec(0.0, 1.0, 0.0) : new BloodVec(1.0, 0.0, 0.0);
        BloodVec u = axis.cross(helper)
            .normalize();
        BloodVec w = axis.cross(u);
        float cosMax = WoundMath.cos(halfAngle);
        float cos = 1.0F - random.nextFloat() * (1.0F - cosMax);
        float sin = WoundMath.sqrt(1.0F - cos * cos);
        float phi = random.nextFloat() * (float) (Math.PI * 2);
        return axis.scale((double) cos)
            .add(u.scale((double) (sin * WoundMath.cos(phi))))
            .add(w.scale((double) (sin * WoundMath.sin(phi))));
    }

    public interface Poses {

        int pose(int var1, float[] var2, float[] var3);
    }

    private static final class Stream {

        final EntityLivingBase victim;
        final BloodVec local;
        final BloodVec out;
        final BloodTypes.Type blood;
        final Random random;
        final int total;
        final float power;
        int left;
        float carry;
        final float perTick;
        int part = -1;
        Vector3f partLocal;
        Vector3f partOut;
        BloodVec last;

        Stream(EntityLivingBase victim, BloodVec local, BloodVec out, BloodTypes.Type blood, int seed, int ticks,
            int drops, float power) {
            this.victim = victim;
            this.local = local;
            this.out = out;
            this.blood = blood;
            this.random = new Random((long) seed);
            this.total = ticks;
            this.left = ticks;
            this.power = power;
            this.perTick = (float) drops * 2.0F / (float) ticks;
        }
    }
}
