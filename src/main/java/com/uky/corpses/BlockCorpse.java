package com.uky.corpses;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A body lying as blocks (Corpses.asBlock). The main block holds the TileCorpse; a lying body's second block has
 * PART set and meta & 3 pointing back at it (0 +z, 1 -x, 2 -z, 3 +x), like a bed. Right-click either to open it;
 * hack either apart the way the entity body is, blow by blow, once it's open to you, and everything spills. It can't
 * be dug: holding the button does nothing. Explosions, withers and pistons leave it be.
 */
public class BlockCorpse extends Block {

    static final int PART = 8;
    private static final int[] DX = { 0, -1, 0, 1 }, DZ = { 1, 0, -1, 0 };

    public BlockCorpse() {
        super(Material.cloth);
        setBlockName(UkyCorpses.MODID + ".corpse");
        setHardness(1.0F);
        setResistance(6000000.0F);
        setStepSound(soundTypeCloth);
        setLightOpacity(0);
        setBlockTextureName("wool_colored_gray"); // breaking particles only, the body is drawn by RenderCorpseTile
        setBlockBounds(0, 0, 0, 1, 0.5F, 1);
    }

    /** The tile of the body that x y z belongs to: its own, or the one its part points at. */
    static TileCorpse master(IBlockAccess w, int x, int y, int z) {
        int meta = w.getBlockMetadata(x, y, z);
        if ((meta & PART) != 0) {
            x += DX[meta & 3];
            z += DZ[meta & 3];
        }
        TileEntity te = w.getTileEntity(x, y, z);
        return te instanceof TileCorpse ? (TileCorpse) te : null;
    }

    private static EntityCorpse body(IBlockAccess w, int x, int y, int z) {
        TileCorpse t = master(w, x, y, z);
        return t == null ? null : t.body;
    }

    /**
     * Server: lays body b down as blocks, if it lies on the grid and the blocks it needs are free (air, grass…).
     * False: it stays an entity.
     */
    static boolean place(World w, EntityCorpse b) {
        if (!b.grid()) return false;
        int y = MathHelper.floor_double(b.posY + 0.01);
        boolean two = b.pose() != EntityCorpse.SIT;
        int mx, mz, px = 0, pz = 0, back = 0;
        if (two) { // lying across the line between two blocks
            double rad = Math.toRadians(b.rotationYaw);
            int ax = (int) Math.round(-Math.sin(rad)), az = (int) Math.round(Math.cos(rad));
            mx = MathHelper.floor_double(b.posX - ax * 0.5);
            mz = MathHelper.floor_double(b.posZ - az * 0.5);
            px = mx + ax;
            pz = mz + az;
            for (int d = 0; d < 4; d++) if (DX[d] == -ax && DZ[d] == -az) back = d;
        } else {
            mx = MathHelper.floor_double(b.posX);
            mz = MathHelper.floor_double(b.posZ);
        }
        if (!free(w, mx, y, mz) || two && !free(w, px, y, pz)) return false;
        w.setBlock(mx, y, mz, UkyCorpses.block, 0, 3);
        TileEntity te = w.getTileEntity(mx, y, mz);
        if (!(te instanceof TileCorpse)) {
            w.setBlockToAir(mx, y, mz);
            return false;
        }
        if (two) w.setBlock(px, y, pz, UkyCorpses.block, PART | back, 3);
        ((TileCorpse) te).adopt(b);
        return true;
    }

    /** Air, grass and the like; not water or lava (a body there floats as an entity). */
    private static boolean free(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b.isReplaceable(w, x, y, z) && !b.getMaterial()
            .isLiquid();
    }

    /** The body at main block x y z and its part, gone. */
    static void removeBody(World w, int x, int y, int z) {
        for (int d = 0; d < 4; d++) {
            int px = x - DX[d], pz = z - DZ[d];
            if (w.getBlock(px, y, pz) == UkyCorpses.block && w.getBlockMetadata(px, y, pz) == (PART | d))
                w.setBlockToAir(px, y, pz);
        }
        if (w.getBlock(x, y, z) == UkyCorpses.block) w.setBlockToAir(x, y, z);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return (meta & PART) == 0;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileCorpse();
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy,
        float hz) {
        EntityCorpse b = body(w, x, y, z);
        if (!w.isRemote && b != null) b.interactFirst(p);
        return true;
    }

    /** Never dug: it comes apart from blows (onBlockClicked). Creative breaks it outright, as any block. */
    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer p, World w, int x, int y, int z) {
        return 0;
    }

    /** Each click is a blow, as on the entity body (EntityCorpse.hitByEntity): a few in a row break it apart. */
    @Override
    public void onBlockClicked(World w, int x, int y, int z, EntityPlayer p) {
        EntityCorpse b = body(w, x, y, z);
        if (w.isRemote || b == null) return;
        if (b.canOpen(p)) b.hitByEntity(p);
        else locked(p, b);
    }

    private static void locked(EntityPlayer p, EntityCorpse b) {
        long left = b.lockLeft();
        p.addChatMessage(
            left == Long.MAX_VALUE ? new ChatComponentTranslation("ukycorpses.msg.lockedForever", b.owner())
                : new ChatComponentTranslation("ukycorpses.msg.locked", b.owner(), (left + 1199) / 1200));
    }

    @Override
    public boolean removedByPlayer(World w, EntityPlayer p, int x, int y, int z, boolean willHarvest) {
        EntityCorpse b = body(w, x, y, z);
        if (b != null && !b.canOpen(p)) {
            if (!w.isRemote) locked(p, b);
            return false;
        }
        return super.removedByPlayer(w, p, x, y, z, willHarvest);
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        if (!w.isRemote) {
            if ((meta & PART) != 0) { // the main block takes the body with it
                int mx = x + DX[meta & 3], mz = z + DZ[meta & 3];
                if (w.getBlock(mx, y, mz) == this) w.setBlockToAir(mx, y, mz);
            } else {
                TileEntity te = w.getTileEntity(x, y, z);
                if (te instanceof TileCorpse) ((TileCorpse) te).broken();
                removeBody(w, x, y, z);
            }
        }
        super.breakBlock(w, x, y, z, block, meta); // Forge drops the tile
    }

    /** The body's parts that fall in block x y z, as boxes relative to it; one slab when the body isn't here yet. */
    private static List<float[]> parts(World w, int x, int y, int z) {
        List<float[]> out = new ArrayList<>();
        EntityCorpse b = body(w, x, y, z);
        if (b == null) {
            out.add(new float[] { 0, 0, 0, 1, 0.5F, 1 });
            return out;
        }
        for (AxisAlignedBB p : b.parts()) {
            double x0 = Math.max(p.minX, x), x1 = Math.min(p.maxX, x + 1), z0 = Math.max(p.minZ, z),
                z1 = Math.min(p.maxZ, z + 1);
            if (x1 - x0 > 0.01 && z1 - z0 > 0.01) // each half of a lying body owns what's over it
                out.add(
                    new float[] { (float) (x0 - x), (float) (p.minY - y), (float) (z0 - z), (float) (x1 - x),
                        (float) (p.maxY - y), (float) (z1 - z) });
        }
        return out;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addCollisionBoxesToList(World w, int x, int y, int z, AxisAlignedBB mask, List list, Entity e) {
        for (float[] p : parts(w, x, y, z)) {
            AxisAlignedBB box = AxisAlignedBB
                .getBoundingBox(x + p[0], y + p[1], z + p[2], x + p[3], y + p[4], z + p[5]);
            if (mask.intersectsWith(box)) list.add(box);
        }
    }

    /** Ray-tests each part and leaves the bounds on the one hit, so the outline is that part. */
    @Override
    public MovingObjectPosition collisionRayTrace(World w, int x, int y, int z, Vec3 start, Vec3 end) {
        MovingObjectPosition best = null;
        float[] bestPart = null;
        for (float[] p : parts(w, x, y, z)) {
            setBlockBounds(p[0], p[1], p[2], p[3], p[4], p[5]);
            MovingObjectPosition hit = super.collisionRayTrace(w, x, y, z, start, end);
            if (hit != null
                && (best == null || hit.hitVec.squareDistanceTo(start) < best.hitVec.squareDistanceTo(start))) {
                best = hit;
                bestPart = p;
            }
        }
        if (bestPart != null)
            setBlockBounds(bestPart[0], bestPart[1], bestPart[2], bestPart[3], bestPart[4], bestPart[5]);
        return best;
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public int getMobilityFlag() {
        return 2; // pistons would split the halves or carry the body off
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return null;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }
}
