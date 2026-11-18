package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

class SourceVoxelCollider {

    static final int MISS = 0, BLOCK = 1, FLUID = 2;
    double hitX, hitY, hitZ;
    float hitFraction;
    ForgeDirection hitFace;
    boolean hitLava;
    int hitBlockX, hitBlockY, hitBlockZ;
    private int lastAxis;
    private final List<AxisAlignedBB> shapes = new ArrayList<>();

    public int sweep(World level, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        int ix = floor(x0);
        int iy = floor(y0);
        int iz = floor(z0);
        int ex = floor(x1);
        int ey = floor(y1);
        int ez = floor(z1);
        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tMaxX = stepX > 0 ? ((double) (ix + 1) - x0) * tDeltaX
            : (stepX < 0 ? (x0 - (double) ix) * tDeltaX : Double.MAX_VALUE);
        double tMaxY = stepY > 0 ? ((double) (iy + 1) - y0) * tDeltaY
            : (stepY < 0 ? (y0 - (double) iy) * tDeltaY : Double.MAX_VALUE);
        double tMaxZ = stepZ > 0 ? ((double) (iz + 1) - z0) * tDeltaZ
            : (stepZ < 0 ? (z0 - (double) iz) * tDeltaZ : Double.MAX_VALUE);

        for (int guard = 0; guard < 64; guard++) {
            int result = this.testVoxel(level, ix, iy, iz, x0, y0, z0, dx, dy, dz);
            if (result != 0) {
                return result;
            }

            if (ix == ex && iy == ey && iz == ez) {
                return 0;
            }

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > 1.0) {
                    return 0;
                }

                ix += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > 1.0) {
                    return 0;
                }

                iy += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1.0) {
                    return 0;
                }

                iz += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        return 0;
    }

    double enterTime(double x0, double y0, double z0, double dx, double dy, double dz, double minX, double minY,
        double minZ, double maxX, double maxY, double maxZ) {
        double tNear = -Double.MAX_VALUE;
        double tFar = Double.MAX_VALUE;
        int axis = -1;
        if (Math.abs(dx) < 1.0E-12) {
            if (x0 < minX || x0 > maxX) {
                return -1.0;
            }
        } else {
            double a = (minX - x0) / dx;
            double b = (maxX - x0) / dx;
            if (a > b) {
                double s = a;
                a = b;
                b = s;
            }

            if (a > tNear) {
                tNear = a;
                axis = 0;
            }

            if (b < tFar) {
                tFar = b;
            }
        }

        if (Math.abs(dy) < 1.0E-12) {
            if (y0 < minY || y0 > maxY) {
                return -1.0;
            }
        } else {
            double ax = (minY - y0) / dy;
            double bx = (maxY - y0) / dy;
            if (ax > bx) {
                double s = ax;
                ax = bx;
                bx = s;
            }

            if (ax > tNear) {
                tNear = ax;
                axis = 1;
            }

            if (bx < tFar) {
                tFar = bx;
            }
        }

        if (Math.abs(dz) < 1.0E-12) {
            if (z0 < minZ || z0 > maxZ) {
                return -1.0;
            }
        } else {
            double axx = (minZ - z0) / dz;
            double bxx = (maxZ - z0) / dz;
            if (axx > bxx) {
                double s = axx;
                axx = bxx;
                bxx = s;
            }

            if (axx > tNear) {
                tNear = axx;
                axis = 2;
            }

            if (bxx < tFar) {
                tFar = bxx;
            }
        }

        if (axis >= 0 && !(tNear > tFar) && !(tNear < 0.0) && !(tNear > 1.0)) {
            this.lastAxis = axis;
            return tNear;
        } else {
            return -1.0;
        }
    }

    int testVoxel(World world, int ix, int iy, int iz, double x0, double y0, double z0, double dx, double dy,
        double dz) {
        if (!world.blockExists(ix, iy, iz)) return MISS;
        Block block = world.getBlock(ix, iy, iz);
        Material material = block.getMaterial();
        hitBlockX = ix;
        hitBlockY = iy;
        hitBlockZ = iz;
        if (material == Material.water || material == Material.lava) {
            float height = world.getBlock(ix, iy + 1, iz)
                .getMaterial() == material ? 1
                    : 1 - BlockLiquid.getLiquidHeightPercent(world.getBlockMetadata(ix, iy, iz));
            double t = enterTime(x0, y0, z0, dx, dy, dz, ix, iy, iz, ix + 1, iy + height, iz + 1);
            if (t >= 0) {
                hitLava = material == Material.lava;
                hit(t, x0, y0, z0, dx, dy, dz);
                return FLUID;
            }
        }
        if (material == Material.air && world.getBlock(ix, iy - 1, iz)
            .getMaterial() == Material.air) return MISS;
        shapes.clear();
        AxisAlignedBB cell = AxisAlignedBB.getBoundingBox(ix, iy, iz, ix + 1, iy + 1, iz + 1);
        block.addCollisionBoxesToList(world, ix, iy, iz, cell, shapes, null);
        // Legacy fence/wall shapes can extend above their owning block without a separate block state.
        if (iy > 0) world.getBlock(ix, iy - 1, iz)
            .addCollisionBoxesToList(world, ix, iy - 1, iz, cell, shapes, null);
        double best = 2;
        int axis = -1;
        for (AxisAlignedBB box : shapes) {
            double t = enterTime(x0, y0, z0, dx, dy, dz, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            if (t >= 0 && t < best) {
                best = t;
                axis = lastAxis;
            }
        }
        if (axis < 0) return MISS;
        hit(best, x0, y0, z0, dx, dy, dz);
        hitFace = axis == 0 ? (dx > 0 ? ForgeDirection.WEST : ForgeDirection.EAST)
            : axis == 1 ? (dy > 0 ? ForgeDirection.DOWN : ForgeDirection.UP)
                : (dz > 0 ? ForgeDirection.NORTH : ForgeDirection.SOUTH);
        return BLOCK;
    }

    private void hit(double t, double x, double y, double z, double dx, double dy, double dz) {
        hitX = x + dx * t;
        hitY = y + dy * t;
        hitZ = z + dz * t;
        hitFraction = (float) t;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
