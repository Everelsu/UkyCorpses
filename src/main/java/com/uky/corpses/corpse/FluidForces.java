package com.uky.corpses.corpse;

import javax.vecmath.Vector3f;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidBlock;

import com.uky.corpses.port.physics.PhysicsBody;

public final class FluidForces {

    static void apply(World world, PhysicsBody body, double ox, double oy, double oz, float gravityScale,
        float buoyancyOverride) {
        if (world == null || body.isStatic()) return;
        Vector3f position = new Vector3f();
        body.getPosition(position);
        int x = MathHelper.floor_double(ox + position.x), y = MathHelper.floor_double(oy + position.y),
            z = MathHelper.floor_double(oz + position.z);
        if (y < 0 || y > 255 || !world.blockExists(x, y, z)) return;
        Block block = world.getBlock(x, y, z);
        if (!block.getMaterial()
            .isLiquid() && !(block instanceof IFluidBlock)) return;
        float depth = (float) (y + height(world, block, x, y, z) - (oy + position.y));
        while (depth <= 1 && y < 255 && world.blockExists(x, y + 1, z)) {
            Block above = world.getBlock(x, y + 1, z);
            if (!same(block, above)) break;
            y++;
            depth = (float) (y + height(world, above, x, y, z) - (oy + position.y));
        }
        Vector3f low = new Vector3f(), high = new Vector3f(), velocity = new Vector3f(), angular = new Vector3f();
        body.getWorldAabb(low, high);
        body.getLinearVelocity(velocity);
        body.getAngularVelocity(angular);
        Vec3 flow = Vec3.createVectorHelper(0, 0, 0);
        try {
            block.velocityToAddToEntity(world, x, MathHelper.floor_double(oy + position.y), z, null, flow);
        } catch (RuntimeException incompatibleFluid) {
            com.uky.corpses.UkyCorpses.LOG.debug("Cannot sample ragdoll fluid flow", incompatibleFluid);
        }
        float buoyancy = Float.isNaN(buoyancyOverride)
            ? block.getMaterial() == Material.lava ? PhysicsConfig.lavaBuoyancy : PhysicsConfig.waterBuoyancy
            : buoyancyOverride;
        advance(
            velocity,
            angular,
            depth,
            high.y - low.y,
            PhysicsConfig.gravity,
            gravityScale,
            buoyancy,
            (float) flow.xCoord,
            (float) flow.zCoord);
        body.setLinearVelocity(velocity);
        body.setAngularVelocity(angular);
    }

    private static float height(World world, Block block, int x, int y, int z) {
        if (block instanceof IFluidBlock)
            return Math.max(0, Math.min(1, Math.abs(((IFluidBlock) block).getFilledPercentage(world, x, y, z))));
        return block instanceof BlockLiquid ? 1 - BlockLiquid.getLiquidHeightPercent(world.getBlockMetadata(x, y, z))
            : 1;
    }

    private static boolean same(Block a, Block b) {
        if (a instanceof IFluidBlock && b instanceof IFluidBlock)
            return ((IFluidBlock) a).getFluid() == ((IFluidBlock) b).getFluid();
        return a.getMaterial()
            .isLiquid() && a.getMaterial() == b.getMaterial();
    }

    public static void advance(Vector3f velocity, Vector3f angular, float depth, float height, float gravity,
        float gravityScale, float buoyancy, float flowX, float flowZ) {
        height = Math.max(.05F, height);
        float submersion = Math.min(1, (depth + height * .5F) / height);
        if (submersion <= 0) return;
        float sideways = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        boolean skidding = submersion < .8F && sideways > 5;
        float lift = gravity * gravityScale * buoyancy * submersion;
        if (skidding) lift += (sideways - 5) * 5;
        velocity.y = (velocity.y + lift * .05F) * .78F;
        float drag = skidding ? .95F : .7F;
        velocity.x = velocity.x * drag + flowX * .4F * submersion;
        velocity.z = velocity.z * drag + flowZ * .4F * submersion;
        angular.scale(drag);
    }

    private FluidForces() {}
}
