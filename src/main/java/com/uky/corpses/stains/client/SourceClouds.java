package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

final class SourceClouds {

    private final List<Cloud> clouds = new ArrayList<>();
    private final Random random = new Random();

    int count() {
        return clouds.size();
    }

    void add(double x, double y, double z, float size, int color, float vx, float vz) {
        for (Cloud cloud : clouds) {
            double dx = cloud.x - x, dy = cloud.y - y, dz = cloud.z - z;
            if (cloud.age < cloud.life / 2 && dx * dx + dy * dy + dz * dz < .09) {
                cloud.life = Math.min(cloud.life + 10, 260);
                cloud.big |= size >= .03F || cloud.life > 160;
                return;
            }
        }
        if (clouds.size() >= 256) clouds.remove(0);
        clouds.add(
            new Cloud(
                x,
                y,
                z,
                vx * .3F,
                vz * .3F,
                70 + random.nextInt(40) + (int) (size * 900),
                size >= .04F,
                random.nextBoolean(),
                color));
    }

    void tick(World world) {
        for (int i = clouds.size() - 1; i >= 0; i--) {
            Cloud cloud = clouds.get(i);
            if (++cloud.age >= cloud.life) {
                clouds.remove(i);
                continue;
            }
            if ((cloud.age & 7) == 1) {
                int x = MathHelper.floor_double(cloud.x), y = MathHelper.floor_double(cloud.y),
                    z = MathHelper.floor_double(cloud.z);
                Block water = world.getBlock(x, y, z);
                if (water.getMaterial() != Material.water) cloud.life = Math.min(cloud.life, cloud.age + 4);
                else {
                    Vec3 flow = Vec3.createVectorHelper(0, 0, 0);
                    water.velocityToAddToEntity(world, x, y, z, null, flow);
                    cloud.vx += flow.xCoord * .012F;
                    cloud.vz += flow.zCoord * .012F;
                    cloud.light = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
                    cloud.surface = surface(world, x, y, z);
                }
            }
            cloud.vx *= .92F;
            cloud.vz *= .92F;
            cloud.vy = cloud.vy * .9F - .0004F;
            cloud.x += cloud.vx;
            cloud.y += cloud.vy;
            cloud.z += cloud.vz;
        }
    }

    private static double surface(World world, int x, int y, int z) {
        for (int up = 0; up < 4; up++, y++) if (world.getBlock(x, y + 1, z)
            .getMaterial() != Material.water) {
                return world.getBlock(x, y + 1, z)
                    .isOpaqueCube() ? Double.NaN
                        : y + 1 - BlockLiquid.getLiquidHeightPercent(world.getBlockMetadata(x, y, z));
            }
        return Double.NaN;
    }

    void render(Tessellator tessellator, float partial) {
        net.minecraft.entity.Entity camera = Minecraft.getMinecraft().renderViewEntity;
        if (camera == null) return;
        boolean underwater = camera.isInsideOfMaterial(Material.water);
        double range = SurfaceBloodClientConfig.RENDER_DISTANCE.get(), cx = RenderManager.renderPosX,
            cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        for (Cloud cloud : clouds) {
            double x = cloud.x + cloud.vx * partial - cx,
                y = (underwater ? cloud.y + cloud.vy * partial : cloud.surface + .004) - cy,
                z = cloud.z + cloud.vz * partial - cz;
            if (x * x + y * y + z * z > range * range || !underwater
                && (Double.isNaN(cloud.surface) || cloud.surface - cloud.y > 1.2 || cloud.surface < cloud.y)) continue;
            StainSprites.Sprite sprite = cloud.big ? StainSprites.largest("cloud") : StainSprites.nearest("cloud", 1);
            float age = (cloud.age + partial) / cloud.life;
            int frame = Math.min(sprite.frames - 1, (int) (Math.sqrt(age) * sprite.frames));
            StainSprites.Sprite animated = new StainSprites.Sprite(
                sprite.u + (frame + (cloud.flip ? 1 : 0)) * sprite.w,
                sprite.v,
                cloud.flip ? -sprite.w : sprite.w,
                sprite.h,
                sprite.pixels,
                1);
            float alpha = (underwater ? .8F : .65F) * (1 - age * age)
                * (underwater ? 1 : 1 - (float) (cloud.surface - cloud.y) / 1.2F);
            float texel = SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL ? .0625F : .03125F,
                half = sprite.pixels * texel * .5F;
            tessellator.setBrightness(Math.max(0, cloud.light));
            tessellator.setNormal(underwater ? -1 : 0, -1, 1);
            if (underwater) SurfaceBloodClient.billboard(tessellator, x, y, z, half, animated, cloud.color, alpha);
            else SurfaceBloodClient.quad(
                tessellator,
                x,
                y,
                z,
                half,
                half,
                0,
                net.minecraftforge.common.util.ForgeDirection.UP,
                animated,
                cloud.color,
                alpha);
        }
    }

    void clear() {
        clouds.clear();
    }

    private static final class Cloud {

        double x, y, z, surface = Double.NaN;
        float vx, vy, vz;
        int age, life, light = -1;
        boolean big;
        final boolean flip;
        final int color;

        Cloud(double x, double y, double z, float vx, float vz, int life, boolean big, boolean flip, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
            this.life = life;
            this.big = big;
            this.flip = flip;
            this.color = color;
        }
    }
}
