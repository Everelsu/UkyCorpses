package com.uky.corpses.client;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.blood.Blood;
import com.uky.corpses.blood.MsgBlood;
import com.uky.corpses.blood.client.BloodClient;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Skeleton;

/** Client: what a body hitting the floor sounds and looks like. EntityCorpse calls in from its client tick. */
public final class CorpseFx {

    private static final Random rand = new Random();

    private CorpseFx() {}

    /** hit: how hard it came down (0..~3); landed: a thrown body just came out of the air. */
    public static void impact(EntityCorpse c, float hit, boolean landed) {
        World w = c.worldObj;
        float force = Math.max(hit, landed ? 1.5F : 0);
        if (force < 0.05F) return;
        double[] at = c.shape().parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
        double x = c.posX + at[0], y = c.posY, z = c.posZ + at[2];
        Block floor = w
            .getBlock(MathHelper.floor_double(x), MathHelper.floor_double(y - 0.2), MathHelper.floor_double(z));
        float loud = Math.min(1F, 0.35F + force * 0.4F);
        // a dull thump, the floor's own sound under it, and armor clattering
        w.playSound(
            x,
            y,
            z,
            force > 0.6F ? "game.player.hurt.fall.big" : "game.player.hurt.fall.small",
            loud,
            0.7F,
            false);
        if (floor.getMaterial() != Material.air) w.playSound(
            x,
            y,
            z,
            floor.stepSound.getStepResourcePath(),
            floor.stepSound.getVolume() * loud,
            floor.stepSound.getPitch() * 0.6F,
            false);
        boolean armor = false;
        for (int i = 0; i < 4; i++) {
            ItemStack s = c.armor(i);
            armor |= s != null;
        }
        if (armor) w.playSound(x, y, z, "mob.horse.armor", 0.25F * loud, 0.75F + rand.nextFloat() * 0.2F, false);
        // dust off the floor where it lands
        if (floor.getMaterial() != Material.air) {
            String dust = "blockcrack_" + Block.getIdFromBlock(floor)
                + "_"
                + w.getBlockMetadata(
                    MathHelper.floor_double(x),
                    MathHelper.floor_double(y - 0.2),
                    MathHelper.floor_double(z));
            int n = 6 + (int) (force * 10);
            for (int i = 0; i < n; i++) w.spawnParticle(
                dust,
                x + (rand.nextDouble() - 0.5) * 1.4,
                y + 0.1,
                z + (rand.nextDouble() - 0.5) * 1.4,
                (rand.nextDouble() - 0.5) * 0.3,
                0.1 + rand.nextDouble() * 0.15 * force,
                (rand.nextDouble() - 0.5) * 0.3);
        }
        // blood where it hit hard: spattered out from under it (not the burnt)
        int cause = Poses.causeOf(c.look());
        if (!Blood.on() || c.burn() == EntityCorpse.BURNT || cause == Poses.DROWN || force <= 0.3F) return;
        for (int i = 0, n = landed || cause == Poses.FALL ? 3 : 1; i < n; i++) {
            double bx = x + (rand.nextDouble() - 0.5) * 1.2, bz = z + (rand.nextDouble() - 0.5) * 1.2;
            if (!w.getBlock(MathHelper.floor_double(bx), MathHelper.floor_double(y - 0.1), MathHelper.floor_double(bz))
                .getMaterial()
                .isSolid()) continue; // over an edge: nothing to land on
            BloodClient.add(
                new MsgBlood(
                    (float) bx,
                    (float) y + 0.02F,
                    (float) bz,
                    0.6F + Math.min(1F, force * 0.4F),
                    rand.nextInt()));
        }
    }
}
