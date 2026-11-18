package com.uky.corpses.stains.client;

public final class SourceColliderCheck {

    public static void run() {
        SourceVoxelCollider collider = new SourceVoxelCollider();
        assert Math.abs(collider.enterTime(-2, .5, .5, 4, 0, 0, 0, 0, 0, 1, 1, 1) - .5) < 1e-10;
        assert collider.enterTime(-2, 2, .5, 4, 0, 0, 0, 0, 0, 1, 1, 1) < 0;
        assert collider.enterTime(.5, .5, .5, 1, 0, 0, 0, 0, 0, 1, 1, 1) < 0 : "inside voxel became an entry hit";
        SourceVoxelCollider negative = new SourceVoxelCollider() {

            @Override
            int testVoxel(net.minecraft.world.World world, int ix, int iy, int iz, double x, double y, double z,
                double dx, double dy, double dz) {
                if (ix != -2 || iy != 0 || iz != 0) return MISS;
                double t = enterTime(x, y, z, dx, dy, dz, -2, 0, 0, -1, 1, 1);
                if (t < 0) return MISS;
                hitX = x + dx * t;
                hitFraction = (float) t;
                return BLOCK;
            }
        };
        assert negative.sweep(null, 1.5, .5, .5, -3, .5, .5) == SourceVoxelCollider.BLOCK;
        assert Math.abs(negative.hitX + 1) < 1e-10 && Math.abs(negative.hitFraction - 2.5 / 4.5) < .00001;
        assert negative.sweep(null, 1.5, 2, .5, -3, 2, .5) == SourceVoxelCollider.MISS;
        System.out.println(
            "SurfaceBlood: source slab entries, parallel misses, inside starts and negative-coordinate voxel traversal passed");
    }

    private SourceColliderCheck() {}
}
