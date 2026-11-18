package com.uky.corpses;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.uky.corpses.pc.PcCorpses;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Returns the existing inventory entity; never creates a replacement or changes its expiry. */
public final class VoidReturn {

    public static boolean enabled = true, surfaceFallback = true, spawnFallback = true;
    public static int triggerY = 1, delaySeconds = 1, searchRadius = 12;
    public static float upwardSpeed = 28, sidewaysSpeed = 12, angularSpeed = 1.8F, acceleration = 80;
    public static String mode = "THROW";

    static void config() {
        CorpseConfig.Section s = CorpseConfig.section(
            "corpse.voidReturn",
            "Возврат тела из пустоты в том же измерении. UUID, вещи, опыт и срок удаления сохраняются. Блоки не ломаются.");
        enabled = s.on(
            "enabled",
            true,
            "Вернуть тело на последнюю безопасную землю. false — оставить у нижней границы мира, как раньше.");
        mode = s.pick(
            "mode",
            "THROW",
            new String[] { "THROW", "PLACE" },
            "THROW — непрерывный подъём из пустоты, поворот к земле и свободное приземление; PLACE — сразу положить.");
        triggerY = s.num(
            "triggerY",
            1,
            1,
            32,
            "Высота начала спасения. Не поднимайте без необходимости: тела в нижних пещерах тоже попадут под правило.");
        delaySeconds = s.num("delaySeconds", 1, 0, 30, "Задержка возврата, секунды загруженного мира.");
        searchRadius = s.num(
            "searchRadius",
            12,
            0,
            32,
            "Радиус поиска свободной поверхности около безопасной точки или места падения, блоки.");
        surfaceFallback = s.on(
            "surfaceFallback",
            true,
            "Если безопасная точка занята/неизвестна, искать поверхность рядом. При подъёме тело проходит сквозь препятствия, включая бедрок.");
        spawnFallback = s.on(
            "spawnFallback",
            true,
            "Если рядом нет земли, искать у входа/спавна измерения. Если места нет нигде, тело и лут остаются внизу; повтор через 5 секунд.");
        upwardSpeed = s.num("upwardSpeed", 28F, 1F, 80F, "Скорость подъёма из пустоты, блоков/сек, только THROW.");
        sidewaysSpeed = s.num(
            "sidewaysSpeed",
            12F,
            1F,
            40F,
            "Предельная скорость полёта к суше после подъёма, блоков/сек, только THROW.");
        acceleration = s.num(
            "acceleration",
            80F,
            10F,
            200F,
            "Сила направляющей тяги, блоков/с². Плавно разгоняет и поворачивает летящее тело.");
        angularSpeed = s
            .num("angularSpeed", 1.8F, 0F, 12F, "Начальная угловая скорость физического тела, рад/сек, только THROW.");
    }

    private static NBTTagCompound points(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10))
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey("UkyCorpseSafePoints", 10)) persisted.setTag("UkyCorpseSafePoints", new NBTTagCompound());
        return persisted.getCompoundTag("UkyCorpseSafePoints");
    }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.END || p.worldObj.isRemote
            || p.ticksExisted % 5 != 0
            || !p.isEntityAlive()
            || !p.onGround
            || p.noClip
            || Downed.is(p)
            || p.posY <= triggerY + 1) return;
        double y = p.boundingBox.minY;
        if (!clear(p.worldObj, p.posX, y, p.posZ)) return;
        NBTTagCompound point = new NBTTagCompound();
        point.setDouble("X", p.posX);
        point.setDouble("Y", y);
        point.setDouble("Z", p.posZ);
        points(p).setTag(Integer.toString(p.dimension), point);
    }

    static void attach(EntityCorpse body, EntityPlayer player) {
        if (player.boundingBox.minY <= triggerY) body.voidReturn.setBoolean("Falling", true);
        NBTTagCompound point = points(player).getCompoundTag(Integer.toString(player.dimension));
        if (valid(point)) body.voidReturn.setTag("Safe", point.copy());
    }

    static boolean valid(NBTTagCompound point) {
        return point.hasKey("X", 6) && point.hasKey("Y", 6)
            && point.hasKey("Z", 6)
            && EntityCorpse.validRagdollAnchor(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z"));
    }

    static boolean clear(World w, double x, double y, double z) {
        if (!EntityCorpse.validRagdollAnchor(x, y, z) || y <= triggerY || y + 2 >= w.getActualHeight()) return false;
        AxisAlignedBB space = AxisAlignedBB.getBoundingBox(x - .85, y + .03, z - .85, x + .85, y + 2, z + .85);
        if (!w.func_147461_a(space)
            .isEmpty() || w.isAnyLiquid(space)) return false;
        for (double dx : new double[] { -.55, .55 }) for (double dz : new double[] { -.55, .55 }) {
            int bx = MathHelper.floor_double(x + dx), by = MathHelper.floor_double(y - .05),
                bz = MathHelper.floor_double(z + dz);
            AxisAlignedBB ground = w.getBlock(bx, by, bz)
                .getCollisionBoundingBoxFromPool(w, bx, by, bz);
            if (ground == null || Math.abs(ground.maxY - y) > .1) return false;
        }
        return true;
    }

    private static double[] surface(World w, double x, double z) {
        int cx = MathHelper.floor_double(x), cz = MathHelper.floor_double(z);
        for (int ring = 0; ring <= searchRadius; ring++)
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int bx = cx + dx, bz = cz + dz;
                int top = Math.min(w.getActualHeight() - 3, w.getHeightValue(bx, bz));
                for (int by = top; by > triggerY; by--) {
                    AxisAlignedBB ground = w.getBlock(bx, by - 1, bz)
                        .getCollisionBoundingBoxFromPool(w, bx, by - 1, bz);
                    if (ground != null && clear(w, bx + .5, ground.maxY, bz + .5))
                        return new double[] { bx + .5, ground.maxY, bz + .5 };
                }
            }
        return null;
    }

    static double birthY(double y) {
        return enabled && "THROW".equals(mode) && y <= triggerY ? Math.max(-512, y) : Math.max(1, y);
    }

    static boolean lifting(EntityCorpse body) {
        return body.voidReturn.hasKey("Flight", 10) || enabled && "THROW".equals(mode)
            && (body.voidReturn.getBoolean("Falling") || body.voidReturn.hasKey("Pending"));
    }

    private static void followOwner(EntityCorpse body, boolean fade) {
        EntityPlayer p = body.physicalOwner() == null ? null : body.worldObj.func_152378_a(body.physicalOwner());
        if (!(p instanceof EntityPlayerMP) || p.isEntityAlive() || !PcCorpses.isLastDeath(body)) return;
        if (fade) UkyCorpses.net.sendTo(new MsgVoidReturn(body.physicalLife()), (EntityPlayerMP) p);
        p.fallDistance = 0;
        p.motionX = p.motionY = p.motionZ = 0;
        ((EntityPlayerMP) p).playerNetServerHandler
            .setPlayerLocation(body.posX, body.posY + .5, body.posZ, p.rotationYaw, p.rotationPitch);
    }

    private static void flight(EntityCorpse body, NBTTagCompound flight) {
        if (!valid(flight)) {
            body.voidReturn.removeTag("Flight");
            body.finishVoidFlight();
            return;
        }
        double dx = flight.getDouble("X") - body.posX, dz = flight.getDouble("Z") - body.posZ;
        double altitude = flight.getDouble("Y") + 5;
        boolean turn = flight.getBoolean("Turn");
        if (!turn && body.posY >= altitude) {
            turn = true;
            flight.setBoolean("Turn", true);
        }
        if (turn && dx * dx + dz * dz < .64 && body.posY > flight.getDouble("Y") + 2 && body.voidFlightClear()) {
            body.voidReturn.removeTag("Flight");
            body.voidReturn.setInteger("Returns", body.voidReturn.getInteger("Returns") + 1);
            body.finishVoidFlight();
            PcCorpses.moved(body);
            followOwner(body, false);
            return;
        }
        double vx = 0, vz = 0, vy = upwardSpeed;
        if (turn) {
            double distance = Math.sqrt(dx * dx + dz * dz);
            double speed = Math.min(sidewaysSpeed, distance * 1.8);
            if (distance > .001) {
                vx = dx / distance * speed;
                vz = dz / distance * speed;
            }
            vy = Math.max(-upwardSpeed * .5, Math.min(upwardSpeed, (altitude - body.posY) * 2));
        }
        boolean launch = !flight.getBoolean("Launched");
        body.guideVoidFlight(vx, vy, vz, launch ? angularSpeed : 0, launch);
        flight.setBoolean("Launched", true);
        if (body.ticksExisted % 4 == 0) followOwner(body, false);
    }

    static void tick(EntityCorpse body) {
        if (body.worldObj.isRemote || body.isDead || body.host != null) return;
        NBTTagCompound state = body.voidReturn;
        if (state.hasKey("Flight", 10)) {
            flight(body, state.getCompoundTag("Flight"));
            return;
        }
        if (!state.getBoolean("Falling") && !body.belowVoidBoundary()) return;
        if (!enabled || !"THROW".equals(mode)) body.pinToVoidFloor();
        else body.guideVoidFlight(0, 0, 0, 0, !state.hasKey("Pending"));
        if (!enabled) return;
        long now = body.worldObj.getTotalWorldTime();
        if (!state.hasKey("Pending")) state.setLong("Pending", now);
        if (now - state.getLong("Pending") < delaySeconds * 20L
            || state.hasKey("Attempt") && now - state.getLong("Attempt") < 100) return;
        state.setLong("Attempt", now);
        NBTTagCompound safe = state.getCompoundTag("Safe");
        double[] at = null;
        if (valid(safe) && clear(body.worldObj, safe.getDouble("X"), safe.getDouble("Y"), safe.getDouble("Z")))
            at = new double[] { safe.getDouble("X"), safe.getDouble("Y"), safe.getDouble("Z") };
        if (at == null && surfaceFallback) at = surface(body.worldObj, body.posX, body.posZ);
        if (at == null && spawnFallback) {
            ChunkCoordinates spawn = body.worldObj.provider.getEntrancePortalLocation();
            if (spawn == null) spawn = body.worldObj.getSpawnPoint();
            at = surface(body.worldObj, spawn.posX, spawn.posZ);
            if (at == null && body.dimension == 1) at = surface(body.worldObj, 0, 0);
        }
        if (at == null) return;
        state.removeTag("Falling");
        state.removeTag("Pending");
        state.removeTag("Attempt");
        if ("THROW".equals(mode)) {
            NBTTagCompound target = new NBTTagCompound();
            target.setDouble("X", at[0]);
            target.setDouble("Y", at[1]);
            target.setDouble("Z", at[2]);
            state.setTag("Flight", target);
            flight(body, target);
        } else {
            body.finishVoidFlight();
            body.returnFromVoid(at[0], at[1], at[2]);
            state.setInteger("Returns", state.getInteger("Returns") + 1);
            followOwner(body, true);
            PcCorpses.moved(body);
        }
    }
}
