package com.uky.corpses.corpse;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.uky.corpses.BlockCorpse;
import com.uky.corpses.UkyCorpses;

/** Collision shapes come from blocks, never from the corpse's own interaction geometry. */
public final class RagdollWorld {

    public static void init() {
        JoltBridge.init();
    }

    public static void refresh(Ragdoll rig, World world, Entity exclude, double ox, double oy, double oz) {
        rig.bindWorld(world, ox, oy, oz);
        float[] motion = rig.state();
        for (double v : rig.bounds())
            if (!Double.isFinite(v)) throw new IllegalStateException("Non-finite ragdoll bounds");
        List<double[]> local = new ArrayList<>();
        // Detached limbs can travel apart: query around each part instead of scanning the empty volume between them.
        Skeleton.Shape shape = rig.shape(1, 0, 0, 0);
        java.util.Set<String> visited = new java.util.HashSet<>();
        for (int part = 0; part < Skeleton.PARTS; part++) {
            if (rig.absent(part)) continue;
            double[] p = shape.bounds(part);
            // Include the next fixed step so fast limbs encounter terrain before reaching it.
            double dx = motion[part * 13 + 7] * .05, dy = motion[part * 13 + 8] * .05, dz = motion[part * 13 + 9] * .05;
            AxisAlignedBB area = AxisAlignedBB.getBoundingBox(
                ox + p[0] - 1.5 + Math.min(0, dx),
                oy + p[1] - 1.5 + Math.min(0, dy),
                oz + p[2] - 1.5 + Math.min(0, dz),
                ox + p[3] + 1.5 + Math.max(0, dx),
                oy + p[4] + 1.5 + Math.max(0, dy),
                oz + p[5] + 1.5 + Math.max(0, dz));
            for (int x = MathHelper.floor_double(area.minX); x <= MathHelper.floor_double(area.maxX); x++)
                for (int y = Math.max(0, MathHelper.floor_double(area.minY)); y
                    <= Math.min(255, MathHelper.floor_double(area.maxY)); y++)
                    for (int z = MathHelper.floor_double(area.minZ); z <= MathHelper.floor_double(area.maxZ); z++) {
                        if (!world.blockExists(x, y, z) || !visited.add(x + ":" + y + ":" + z)) continue;
                        Block block = world.getBlock(x, y, z);
                        if (block instanceof BlockCorpse) continue;
                        List<AxisAlignedBB> boxes = new ArrayList<>();
                        try {
                            block.addCollisionBoxesToList(world, x, y, z, area, boxes, exclude);
                        } catch (RuntimeException error) {
                            UkyCorpses.LOG.debug("Cannot query ragdoll collision at {},{},{}", x, y, z, error);
                            continue;
                        }
                        for (AxisAlignedBB box : boxes)
                            if (box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ) local.add(
                                new double[] { box.minX - ox, box.minY - oy, box.minZ - oz, box.maxX - ox,
                                    box.maxY - oy, box.maxZ - oz });
                    }
        }
        rig.terrain(local);
    }

    /** A single detached part sharing the same Jolt world and terrain references as every ragdoll. */
    public static final class LooseBody implements AutoCloseable {

        private final JoltBridge.Detached body;
        private final World world;
        private final float radius;

        public LooseBody(World world, double x, double y, double z, javax.vecmath.Vector3f half,
            javax.vecmath.Quat4f rotation, com.uky.corpses.port.physics.BodyProperties properties,
            javax.vecmath.Vector3f angular, float gravity) {
            if (world == null || world.isRemote)
                throw new IllegalArgumentException("Loose physics requires a server world");
            this.world = world;
            radius = Math.max(half.x, Math.max(half.y, half.z));
            body = new JoltBridge.Detached(world, x, y, z, half, rotation, properties, angular, gravity);
        }

        public void snapshot(double[] position, javax.vecmath.Quat4f rotation, javax.vecmath.Vector3f velocity,
            javax.vecmath.Vector3f angular) {
            body.snapshot(position, rotation, velocity, angular);
        }

        public void refresh(Entity exclude) {
            double[] position = new double[3];
            javax.vecmath.Vector3f velocity = new javax.vecmath.Vector3f();
            body.snapshot(position, new javax.vecmath.Quat4f(), velocity, new javax.vecmath.Vector3f());
            double margin = radius + 1.5, dx = velocity.x * .05, dy = velocity.y * .05, dz = velocity.z * .05;
            AxisAlignedBB area = AxisAlignedBB.getBoundingBox(
                position[0] - margin + Math.min(0, dx),
                position[1] - margin + Math.min(0, dy),
                position[2] - margin + Math.min(0, dz),
                position[0] + margin + Math.max(0, dx),
                position[1] + margin + Math.max(0, dy),
                position[2] + margin + Math.max(0, dz));
            List<double[]> boxes = new ArrayList<>();
            for (int x = MathHelper.floor_double(area.minX); x <= MathHelper.floor_double(area.maxX); x++)
                for (int y = Math.max(0, MathHelper.floor_double(area.minY)); y
                    <= Math.min(255, MathHelper.floor_double(area.maxY)); y++)
                    for (int z = MathHelper.floor_double(area.minZ); z <= MathHelper.floor_double(area.maxZ); z++) {
                        if (!world.blockExists(x, y, z)) continue;
                        Block block = world.getBlock(x, y, z);
                        if (block instanceof BlockCorpse) continue;
                        List<AxisAlignedBB> colliders = new ArrayList<>();
                        try {
                            block.addCollisionBoxesToList(world, x, y, z, area, colliders, exclude);
                        } catch (RuntimeException badShape) {
                            UkyCorpses.LOG.debug("Cannot query loose limb collision at {},{},{}", x, y, z, badShape);
                            continue;
                        }
                        for (AxisAlignedBB box : colliders)
                            if (box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ)
                                boxes.add(new double[] { box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ });
                    }
            body.terrain(boxes);
        }

        @Override
        public void close() {
            body.close();
        }
    }

    public static final class RigScene implements AutoCloseable {

        private final JoltBridge.Rig rig;
        private final World world;
        private final double x, y, z;

        public RigScene(World world, double x, double y, double z) {
            if (world == null || world.isRemote || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Articulated physics requires a finite server-world origin");
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            rig = new JoltBridge.Rig(world, x, y, z);
        }

        public com.uky.corpses.port.physics.PhysicsWorld physics() {
            return rig.physics();
        }

        public javax.vecmath.Vector3f offset() {
            return new javax.vecmath.Vector3f(rig.offset);
        }

        public void own(java.util.List<com.uky.corpses.port.physics.PhysicsBody> parts,
            java.util.List<com.uky.corpses.port.physics.PhysicsConstraint> joints) {
            rig.own(parts, joints);
        }

        public float[] snapshot() {
            return rig.snapshot();
        }

        public boolean restore(float[] state) {
            return rig.restore(state);
        }

        public void hidePart(int index) {
            rig.hidePart(index);
        }

        public void fluidProfile(float gravityScale, float buoyancy) {
            rig.gravityScale = gravityScale;
            rig.buoyancy = buoyancy;
        }

        public static boolean validState(float[] state) {
            if (state == null || state.length < 13 || state.length % 13 != 0 || state.length > 64 * 13) return false;
            for (int j = 0; j < state.length; j += 13) {
                for (int k = 0; k < 13; k++)
                    if (!Float.isFinite(state[j + k]) || Math.abs(state[j + k]) > (k < 3 ? 4096 : 1024)) return false;
                double q = 0;
                for (int k = 3; k < 7; k++) q += state[j + k] * state[j + k];
                if (q < .5 || q > 1.5) return false;
            }
            return true;
        }

        public void refresh(Entity exclude) {
            java.util.List<double[]> boxes = new java.util.ArrayList<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            javax.vecmath.Vector3f low = new javax.vecmath.Vector3f(), high = new javax.vecmath.Vector3f(),
                velocity = new javax.vecmath.Vector3f();
            for (com.uky.corpses.port.physics.PhysicsBody body : rig.parts) if (body != null && !body.isStatic()) {
                body.getWorldAabb(low, high);
                body.getLinearVelocity(velocity);
                double gx = x - rig.offset.x, gy = y - rig.offset.y, gz = z - rig.offset.z;
                AxisAlignedBB area = AxisAlignedBB.getBoundingBox(
                    gx + low.x - 1.5 + Math.min(0, velocity.x * .05),
                    gy + low.y - 1.5 + Math.min(0, velocity.y * .05),
                    gz + low.z - 1.5 + Math.min(0, velocity.z * .05),
                    gx + high.x + 1.5 + Math.max(0, velocity.x * .05),
                    gy + high.y + 1.5 + Math.max(0, velocity.y * .05),
                    gz + high.z + 1.5 + Math.max(0, velocity.z * .05));
                for (int bx = MathHelper.floor_double(area.minX); bx <= MathHelper.floor_double(area.maxX); bx++)
                    for (int by = Math.max(0, MathHelper.floor_double(area.minY)); by
                        <= Math.min(255, MathHelper.floor_double(area.maxY)); by++)
                        for (int bz = MathHelper.floor_double(area.minZ); bz
                            <= MathHelper.floor_double(area.maxZ); bz++) {
                                if (!world.blockExists(bx, by, bz) || !visited.add(bx + ":" + by + ":" + bz)) continue;
                                Block block = world.getBlock(bx, by, bz);
                                if (block instanceof BlockCorpse) continue;
                                java.util.List<AxisAlignedBB> colliders = new java.util.ArrayList<>();
                                try {
                                    block.addCollisionBoxesToList(world, bx, by, bz, area, colliders, exclude);
                                } catch (RuntimeException error) {
                                    UkyCorpses.LOG
                                        .debug("Cannot query mob ragdoll collision at {},{},{}", bx, by, bz, error);
                                    continue;
                                }
                                for (AxisAlignedBB box : colliders)
                                    if (box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ) boxes.add(
                                        new double[] { box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ });
                            }
            }
            rig.terrain(boxes);
        }

        @Override
        public void close() {
            rig.close();
        }
    }

    private RagdollWorld() {}
}
