package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.Reactions;
import com.uky.corpses.TileCorpse;
import com.uky.corpses.corpse.Skeleton;
import com.uky.corpses.stains.SurfaceBloodClientConfig;

/** Physical cut ends use the same body/loose-part matrices as their renderer, not the dead player's old position. */
public final class Stumps {

    private static final List<Cut> cuts = new ArrayList<>();
    private static final Map<Entity, Integer> seen = new WeakHashMap<>();

    private static final class Cut {

        Entity entity;
        int part;
        long born;
        boolean alive, old;
    }

    public static void tick(World world) {
        if (!SurfaceBloodClientConfig.STUMPS.get()) return;
        for (Object e : world.loadedEntityList)
            if (e instanceof EntityCorpse || e instanceof EntityLivingBase && Reactions.is((Entity) e))
                watch((Entity) e);
        for (Object tile : world.loadedTileEntityList)
            if (tile instanceof TileCorpse && ((TileCorpse) tile).body != null) watch(((TileCorpse) tile).body);
        long now = world.getTotalWorldTime();
        for (Iterator<Cut> it = cuts.iterator(); it.hasNext();) {
            Cut cut = it.next();
            Skeleton.Shape shape = SkinBlood.shape(cut.entity);
            if (cut.entity.worldObj != world
                || cut.entity instanceof net.minecraft.entity.player.EntityPlayer
                    && world.getEntityByID(cut.entity.getEntityId()) != cut.entity
                || shape == null
                || now - cut.born > 600 * SurfaceBloodClientConfig.STUMP_TIME.get()) {
                it.remove();
                continue;
            }
            boolean alive = cut.entity instanceof EntityLivingBase && ((EntityLivingBase) cut.entity).isEntityAlive();
            if (cut.alive && !alive) {
                cut.born = now;
                cut.alive = false;
            }
            float age = (float) ((now - cut.born) / SurfaceBloodClientConfig.STUMP_TIME.get());
            float pressure = pressure(age, alive, cut.old);
            if (pressure < .015F || (alive ? (now - cut.born) % 16 >= 3 : (now - cut.born) % 20 != 0)) continue;
            float[] pivot = Skeleton.pivot(cut.part);
            double[] body = shape.parts[Skeleton.BODY].apply(pivot[0], pivot[1], pivot[2], new double[3]);
            double[] limb = shape.parts[cut.part].apply(0, 0, 0, new double[3]);
            double dx = limb[0] - body[0], dy = limb[1] - body[1], dz = limb[2] - body[2],
                length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < .02) {
                dx = 0;
                dy = -1;
                dz = 0;
                length = 1;
            }
            float speed = SurfaceBloodClientConfig.STUMP_SPEED.get()
                .floatValue(),
                amount = pressure * SurfaceBloodClientConfig.STUMP_AMOUNT.get()
                    .floatValue() * .2F;
            SurfaceBloodClient.spray(
                cut.entity.posX + body[0],
                cut.entity.posY + body[1],
                cut.entity.posZ + body[2],
                (float) (dx / length) * speed,
                (float) (dy / length) * speed,
                (float) (dz / length) * speed,
                amount,
                false);
            if (SurfaceBloodClientConfig.STUMP_LIMBS.get()) SurfaceBloodClient.spray(
                cut.entity.posX + limb[0],
                cut.entity.posY + limb[1],
                cut.entity.posZ + limb[2],
                (float) (-dx / length) * speed,
                (float) (-dy / length) * speed,
                (float) (-dz / length) * speed,
                amount * .75F,
                false);
        }
    }

    private static void watch(Entity e) {
        int mask = e instanceof EntityCorpse ? ((EntityCorpse) e).severedMask() : Reactions.severedMask(e),
            before = seen.getOrDefault(e, 0);
        seen.put(e, mask);
        for (int part = 0; part < 6; part++) if ((mask & 1 << part) != 0 && (before & 1 << part) == 0) {
            while (cuts.size() >= 48) cuts.remove(0);
            Cut cut = new Cut();
            cut.entity = e;
            cut.part = part;
            cut.born = e.worldObj.getTotalWorldTime();
            cut.alive = e instanceof EntityLivingBase && ((EntityLivingBase) e).isEntityAlive();
            cut.old = e instanceof EntityCorpse && e.ticksExisted > 40;
            cuts.add(cut);
        }
    }

    public static float pressure(float age, boolean alive, boolean old) {
        return old ? .45F * (float) Math.exp(-age / 28F)
            : alive ? (float) Math.exp(-age / 140F) : (float) Math.exp(-age / 28F);
    }

    public static void clear() {
        cuts.clear();
        seen.clear();
    }

    private Stumps() {}
}
