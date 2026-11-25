package com.uky.corpses.stains.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

final class Footprints {

    private static final Map<Integer, Walker> walkers = new HashMap<>();

    static void tick(World world, List<Stain> stains, long now) {
        if (!SurfaceBloodClientConfig.FOOTPRINTS.get()) {
            clear();
            return;
        }
        Entity camera = net.minecraft.client.Minecraft.getMinecraft().renderViewEntity;
        double range = SurfaceBloodClientConfig.RENDER_DISTANCE.get();
        for (Object object : world.loadedEntityList) if (object instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) object;
            String family = family(living);
            if (!living.isEntityAlive() || family == null
                || camera != null && living.getDistanceSqToEntity(camera) > range * range) continue;
            Walker walker = walkers.get(living.getEntityId());
            Stain pool = null;
            float load = 1;
            if (living.onGround) for (Stain stain : stains) {
                if (stain.face != ForgeDirection.UP || stain.print
                    || stain.dry(now) >= .7F
                    || Math.abs(stain.y - living.boundingBox.minY) > .2) continue;
                double dx = stain.x - living.posX, dz = stain.z - living.posZ;
                float reach = stain.pooled ? stain.level(now) * .8F
                    : Math.max(stain.length, stain.width) + living.width * .3F;
                double co = Math.cos(stain.angle), si = Math.sin(stain.angle);
                double s = co * dx + si * dz, t = -si * dx + co * dz;
                double foot = living.width * .3;
                if (stain.pooled ? reach >= .12F && dx * dx + dz * dz < reach * reach
                    : Math.abs(s) <= stain.length + foot && Math.abs(t) <= stain.width + foot) {
                    if (stain.pooled) {
                        pool = stain;
                        load = 1;
                        break;
                    }
                    if (4 * stain.length * stain.width >= .2F) {
                        pool = stain;
                        load = Math.min(1, 4 * stain.length * stain.width / .8F);
                    }
                }
            }
            if (pool != null) {
                if (walker == null) {
                    if (walkers.size() >= 64) continue;
                    walker = new Walker(living, family);
                    walkers.put(living.getEntityId(), walker);
                }
                int total = Math.max(
                    2,
                    Math.round(
                        (walker.upright ? 8 : 10) * SurfaceBloodClientConfig.preset().prints
                            * SurfaceBloodClientConfig.FOOTPRINT_STEPS.get()
                                .floatValue()));
                int steps = Math.max(2, Math.round(total * load));
                if (load >= 1 || steps > walker.left) {
                    walker.load = walker.left > 0 ? Math.max(walker.load, load) : load;
                    if (load >= 1 && (walker.left < walker.total / 2 || walker.total == 0))
                        SurfaceBloodClient.sound("step", living.posX, living.posY, living.posZ, .18F);
                    walker.left = walker.total = Math.max(walker.left, steps);
                    walker.color = pool.color;
                    walker.dried = pool.dried;
                    walker.lastX = living.posX;
                    walker.lastZ = living.posZ;
                    walker.travelled = Math.max(walker.travelled, walker.stride * .5F);
                    continue;
                }
            }
            if (walker != null) {
                double dx = living.posX - walker.lastX, dz = living.posZ - walker.lastZ;
                walker.lastX = living.posX;
                walker.lastZ = living.posZ;
                double moved = Math.sqrt(dx * dx + dz * dz);
                if (moved <= 2 && living.onGround) {
                    walker.travelled += moved;
                    if (walker.travelled >= walker.stride) {
                        walker.travelled -= walker.stride;
                        step(world, living, walker, now);
                        if (--walker.left <= 0) walkers.remove(living.getEntityId());
                    }
                }
            }
        }
        if (now % 40 == 0) walkers.keySet()
            .removeIf(id -> world.getEntityByID(id) == null);
    }

    private static void step(World world, EntityLivingBase living, Walker walker, long now) {
        float yaw = living.renderYawOffset * (float) Math.PI / 180, side = walker.right ? 1 : -1;
        double x = living.posX - Math.cos(yaw) * walker.gauge * side,
            z = living.posZ - Math.sin(yaw) * walker.gauge * side;
        MovingObjectPosition hit = world.rayTraceBlocks(
            Vec3.createVectorHelper(x, living.boundingBox.minY + .25, z),
            Vec3.createVectorHelper(x, living.boundingBox.minY - .4, z),
            false);
        if (hit != null && hit.sideHit == 1
            && !world.getBlock(hit.blockX, hit.blockY, hit.blockZ)
                .isLeaves(world, hit.blockX, hit.blockY, hit.blockZ)) {
            float angle = (float) Math.atan2(-Math.sin(yaw), -Math.cos(yaw));
            if (SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL)
                angle = Math.round(angle / ((float) Math.PI / 2)) * ((float) Math.PI / 2);
            float left = walker.left / (float) walker.total;
            String family = walker.upright ? walker.family + (walker.right ? "_r" : "_l") : walker.family;
            if ((family.equals("print_hoof") || family.equals("print_paw")) && living.width >= 1.1F)
                family = "print_hoof_big";
            if (SurfaceBloodClientConfig.FOOTPRINT_SHAPE.get() == SurfaceBloodClientConfig.PrintShape.SQUARE) {
                if (family.startsWith("print_shoe") || family.startsWith("print_hoof")) family = "print_square";
                else if (family.equals("print_paw")) family = "print_square_small";
            }
            StainSprites.Sprite sprite = StainSprites
                .nearest(family, left > .3F ? 3 : 2, new java.util.Random(living.getEntityId() + now));
            boolean pixel = SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL;
            float scale = pixel ? .0625F : .015625F;
            Stain stain = StainPlacement.place(
                ForgeDirection.UP,
                x,
                hit.hitVec.yCoord,
                z,
                angle,
                sprite,
                scale,
                scale,
                sprite.texelWidth / 2,
                sprite.texelHeight / 2,
                pixel);
            stain.born = now;
            stain.print = true;
            stain.family = family;
            stain.color = walker.color;
            stain.dried = walker.dried;
            stain.opacity = .95F * (.35F + .65F * walker.load) * (.45F + .55F * left);
            SurfaceBloodClient.add(stain);
            SurfaceBloodClient.sound("squelch", stain.x, stain.y, stain.z, .16F * walker.load * (.4F + .6F * left));
        }
        walker.right = !walker.right;
    }

    static String family(EntityLivingBase entity) {
        if (entity instanceof EntitySlime) return null;
        if (entity instanceof EntitySpider || entity instanceof EntitySilverfish) return "print_dot";
        if (entity instanceof EntityChicken) return "print_claw";
        if (entity instanceof EntityCreeper) return "print_hoof";
        return entity.height >= entity.width * 2.2F ? "print_shoe" : entity.width < .7F ? "print_paw" : "print_hoof";
    }

    static void clear() {
        walkers.clear();
    }

    private static final class Walker {

        final boolean upright;
        final String family;
        final float stride, gauge;
        double lastX, lastZ;
        float travelled, load;
        int left, total, color, dried;
        boolean right;

        Walker(EntityLivingBase entity, String family) {
            this.family = family;
            upright = family.equals("print_shoe");
            stride = upright ? Math.max(.4F, entity.height * .32F) : Math.max(.28F, entity.width * .7F);
            gauge = entity.width * (upright ? .2F : .28F);
        }
    }

    private Footprints() {}
}
