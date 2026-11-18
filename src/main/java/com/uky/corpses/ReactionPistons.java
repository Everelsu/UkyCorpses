package com.uky.corpses;

import javax.vecmath.Vector3f;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import com.uky.corpses.corpse.Ragdoll;
import com.uky.corpses.corpse.Skeleton;

/** 1.7 has no piston event: sample the actual moving tiles around a player. */
final class ReactionPistons {

    static final class Contact {

        int x, y, z, side, part = -1, pending = 20;
        boolean sticky;
        Vector3f anchor;

        Vector3f launch() {
            float speed = Reactions.pistonLaunchSpeed;
            return new Vector3f(
                Facing.offsetsXForSide[side] * speed,
                Facing.offsetsYForSide[side] * speed + (side > 1 ? speed * .35F : 0),
                Facing.offsetsZForSide[side] * speed);
        }

        boolean hold(Ragdoll rig, World world, double ox, double oy, double oz) {
            double out;
            TileEntity base = world.getTileEntity(x, y, z);
            TileEntity head = world.getTileEntity(
                x + Facing.offsetsXForSide[side],
                y + Facing.offsetsYForSide[side],
                z + Facing.offsetsZForSide[side]);
            if (base instanceof TileEntityPiston
                && ((TileEntityPiston) base).getStoredBlockID() == Blocks.sticky_piston)
                out = 1.5 - ((TileEntityPiston) base).func_145860_a(1);
            else if (head instanceof TileEntityPiston && ((TileEntityPiston) head).isExtending()
                && ((TileEntityPiston) head).getStoredBlockID() == Blocks.piston_head)
                out = .5 + ((TileEntityPiston) head).func_145860_a(1);
            else {
                if (world.getBlock(x, y, z) != Blocks.sticky_piston || (world.getBlockMetadata(x, y, z) & 7) != side)
                    return false;
                out = (world.getBlockMetadata(x, y, z) & 8) != 0 ? 1.5 : .5;
            }
            Vector3f normal = new Vector3f(
                Facing.offsetsXForSide[side],
                Facing.offsetsYForSide[side],
                Facing.offsetsZForSide[side]);
            Vector3f face = new Vector3f(
                (float) (x + .5 + normal.x * out - ox),
                (float) (y + .5 + normal.y * out - oy),
                (float) (z + .5 + normal.z * out - oz));
            if (part < 0) {
                Skeleton.Shape shape = rig.shape(1, 0, 0, 0);
                double best = 4;
                Vector3f center = null;
                for (int i = 0; i < Skeleton.PARTS; i++) {
                    double[] b = shape.bounds(i);
                    Vector3f p = new Vector3f(
                        (float) (b[0] + b[3]) * .5F,
                        (float) (b[1] + b[4]) * .5F,
                        (float) (b[2] + b[5]) * .5F);
                    Vector3f delta = new Vector3f(p);
                    delta.sub(face);
                    if (delta.lengthSquared() < best) {
                        best = delta.lengthSquared();
                        part = i;
                        center = p;
                    }
                }
                if (center == null) return --pending > 0;
                anchor = new Vector3f(center);
                anchor.sub(face);
                float dot = anchor.dot(normal);
                anchor.x = clamp(anchor.x - normal.x * dot);
                anchor.y = clamp(anchor.y - normal.y * dot);
                anchor.z = clamp(anchor.z - normal.z * dot);
            }
            face.add(anchor);
            normal.scale(.15F);
            face.add(normal);
            rig.hold(part, face);
            return true;
        }

        private static float clamp(float n) {
            return Math.max(-.4F, Math.min(.4F, n));
        }
    }

    static Contact contact(EntityPlayerMP p) {
        Contact blockHit = null;
        int px = (int) Math.floor(p.posX), py = (int) Math.floor(p.posY), pz = (int) Math.floor(p.posZ);
        for (int x = px - 2; x <= px + 2; x++)
            for (int y = py - 2; y <= py + 2; y++) for (int z = pz - 2; z <= pz + 2; z++) {
                if (!p.worldObj.blockExists(x, y, z)) continue;
                TileEntity tile = p.worldObj.getTileEntity(x, y, z);
                if (!(tile instanceof TileEntityPiston)) continue;
                TileEntityPiston piston = (TileEntityPiston) tile;
                if (!piston.isExtending()) continue;
                boolean head = piston.getStoredBlockID() == Blocks.piston_head;
                String id = String
                    .valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(piston.getStoredBlockID()));
                if (id.endsWith(":slime_block")) continue;
                if (!AxisAlignedBB.getBoundingBox(x - .01, y - .01, z - .01, x + 1.01, y + 1.01, z + 1.01)
                    .intersectsWith(p.boundingBox)) continue;
                Contact c = new Contact();
                c.side = piston.getPistonOrientation();
                if (c.side < 0 || c.side > 5) continue;
                c.x = x - Facing.offsetsXForSide[c.side];
                c.y = y - Facing.offsetsYForSide[c.side];
                c.z = z - Facing.offsetsZForSide[c.side];
                c.sticky = head && (piston.getBlockMetadata() & 8) != 0 && Reactions.stickyPistonsGrab;
                if (head) return c;
                blockHit = c;
            }
        return blockHit;
    }
}
