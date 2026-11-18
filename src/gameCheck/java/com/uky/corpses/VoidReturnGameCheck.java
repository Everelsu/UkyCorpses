package com.uky.corpses;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;

import com.uky.corpses.corpse.PhysicsConfig;
import com.uky.corpses.pc.PcCorpses;

import cpw.mods.fml.common.gameevent.TickEvent;

public final class VoidReturnGameCheck {

    private static EntityCorpse body, endBody;
    private static net.minecraftforge.common.ForgeChunkManager.Ticket endTicket;
    private static UUID id, endId;
    private static boolean downed, respawned, savedPlayers, rose, steered;
    private static volatile boolean sameCamera, clientRose;
    private static double previousY, previousX, previousZ, firstClientY = Double.NaN;

    public static void clientTick(int phase) {
        Minecraft mc = Minecraft.getMinecraft();
        if (phase > 80 && body != null && com.uky.corpses.client.RagdollCamera.active()) {
            try {
                java.lang.reflect.Field life = com.uky.corpses.client.RagdollCamera.class.getDeclaredField("life");
                life.setAccessible(true);
                sameCamera |= life.getLong(null) == body.physicalLife();
                for (Object value : mc.theWorld.loadedEntityList) if (value instanceof EntityCorpse) {
                    EntityCorpse b = (EntityCorpse) value;
                    if (b.physicalLife() == body.physicalLife() && !b.isDead) {
                        if (Double.isNaN(firstClientY)) firstClientY = b.posY;
                        clientRose |= b.posY > firstClientY + 30;
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        if (!respawned && phase > 2 && phase < 30 && mc.thePlayer != null && !mc.thePlayer.isEntityAlive()) {
            mc.thePlayer.respawnPlayer();
            mc.displayGuiScreen(null);
            respawned = true;
        }
    }

    public static boolean tick(EntityPlayerMP p, int phase) {
        if (phase == 1) {
            downed = CorpseConfig.downed;
            CorpseConfig.downed = false;
            savedPlayers = PhysicsConfig.playerRagdolls;
            PhysicsConfig.playerRagdolls = true;
            VoidReturn.enabled = true;
            VoidReturn.delaySeconds = 1;
            VoidReturn.mode = "THROW";
            VoidReturn.upwardSpeed = 28;
            VoidReturn.sidewaysSpeed = 12;
            VoidReturn.acceleration = 80;
            for (int x = 20; x <= 28; x++) for (int z = 20; z <= 28; z++) {
                p.worldObj.setBlock(x, 65, z, Blocks.bedrock);
                for (int y = 66; y < 75; y++) p.worldObj.setBlockToAir(x, y, z);
            }
        }
        if (phase == 50) {
            check(p.isEntityAlive(), "QA respawn completed before void death");
            Reactions.teleported(p);
            p.playerNetServerHandler.setPlayerLocation(24.5, 66, 24.5, 0, 0);
            p.onGround = true;
            p.noClip = false;
            int ticks = p.ticksExisted;
            p.ticksExisted = 0;
            new VoidReturn().playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
            p.ticksExisted = ticks;
            NBTTagCompound safe = p.getEntityData()
                .getCompoundTag(EntityPlayerMP.PERSISTED_NBT_TAG)
                .getCompoundTag("UkyCorpseSafePoints")
                .getCompoundTag(Integer.toString(p.dimension));
            check(
                safe.getDouble("X") == 24.5 && safe.getDouble("Y") == 66,
                "Fixture captures the actual platform as its per-dimension safe point");
            for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = null;
            p.inventory.mainInventory[0] = new ItemStack(Items.diamond, 3);
            p.inventory.mainInventory[0].setTagCompound(new NBTTagCompound());
            p.inventory.mainInventory[0].getTagCompound()
                .setString("VoidQA", "kept");
            p.hurtResistantTime = 0;
            Reactions.teleported(p);
            p.playerNetServerHandler.setPlayerLocation(16.5, -190, 24.5, 0, 0);
            p.motionY = -4.5;
            p.attackEntityFrom(DamageSource.outOfWorld, 1000);
        }
        if (phase == 55) {
            for (Object e : p.worldObj.loadedEntityList) if (e instanceof EntityCorpse) {
                EntityCorpse candidate = (EntityCorpse) e;
                if (!candidate.isDead && p.getUniqueID()
                    .equals(candidate.physicalOwner()) && PcCorpses.isLastDeath(candidate)) body = candidate;
            }
            check(
                body != null && !body.isDead && body.posY < -180 && body.posY > -197,
                "Fast-falling corpse is caught at the void death position without continuing to plunge");
            id = body.getUniqueID();
            previousY = body.posY;
            previousX = body.posX;
            previousZ = body.posZ;
            net.minecraftforge.common.DimensionManager.initDimension(1);
            net.minecraft.world.WorldServer end = net.minecraftforge.common.DimensionManager.getWorld(1);
            for (int x = 97; x <= 103; x++) for (int z = -3; z <= 3; z++) {
                end.setBlock(x, 60, z, Blocks.bedrock);
                for (int y = 61; y <= 70; y++) end.setBlockToAir(x, y, z);
            }
            net.minecraftforge.common.ForgeChunkManager
                .setForcedChunkLoadingCallback(UkyCorpses.instance, (tickets, world) -> {});
            endTicket = net.minecraftforge.common.ForgeChunkManager
                .requestTicket(UkyCorpses.instance, end, net.minecraftforge.common.ForgeChunkManager.Type.NORMAL);
            check(endTicket != null, "Disposable End scene holds a chunk ticket during native flight");
            net.minecraftforge.common.ForgeChunkManager
                .forceChunk(endTicket, new net.minecraft.world.ChunkCoordIntPair(6, 0));
            endBody = new EntityCorpse(end);
            endBody.setPosition(92.5, -70, .5);
            endBody.startPhysical(new javax.vecmath.Vector3f(), 0, 0, 0);
            NBTTagCompound endSafe = new NBTTagCompound();
            endSafe.setDouble("X", 100.5);
            endSafe.setDouble("Y", 61);
            endSafe.setDouble("Z", .5);
            endBody.voidReturn.setTag("Safe", endSafe);
            endId = endBody.getUniqueID();
        }
        if (phase > 55 && phase < 380) {
            double dx = body.posX - previousX, dy = body.posY - previousY, dz = body.posZ - previousZ;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 3)
                throw new AssertionError("Void flight teleported instead of moving continuously: " + dy);
            rose |= body.posY > -30 && body.posX < 17.3;
            steered |= body.posY > 65 && body.posX > 18;
            previousY = body.posY;
            previousX = body.posX;
            previousZ = body.posZ;
            endBody.onUpdate();
        }
        if (phase == 100) {
            check(
                body.voidReturn.hasKey("Flight") && body.voidReturn.getInteger("Returns") == 0 && body.posY < 40,
                "Rising flight remains below land rather than instantly appearing there");
            NBTTagCompound saved = new NBTTagCompound();
            body.writeEntityToNBT(saved);
            EntityCorpse copy = new EntityCorpse(p.worldObj);
            copy.readEntityFromNBT(saved);
            check(
                copy.voidReturn.hasKey("Flight") && copy.voidReturn.hasKey("Safe"),
                "Flight phase and destination survive entity NBT roundtrip");
            copy.disposePhysics();
        }
        if (phase == 380) {
            check(
                !body.isDead && id.equals(body.getUniqueID())
                    && body.voidReturn.getInteger("Returns") == 1
                    && body.posY > 65,
                "Existing native corpse flies to land and lands with the same UUID (y=" + body.posY
                    + ", state="
                    + body.voidReturn
                    + ")");
            check(rose && steered, "Physical trajectory rises vertically before steering sideways to land");
            check(
                sameCamera && clientRose,
                "Client observes the rising body while death camera stays on the same physical life");
            check(
                !endBody.isDead && endId.equals(endBody.getUniqueID())
                    && endBody.voidReturn.getInteger("Returns") == 1
                    && endBody.posY > 60,
                "End corpse completes its physical void flight in its own dimension");
            endBody.disposePhysics();
            net.minecraftforge.common.ForgeChunkManager.releaseTicket(endTicket);
            int count = 0;
            for (int i = 0; i < body.getSizeInventory(); i++) {
                ItemStack s = body.getStackInSlot(i);
                if (s != null && s.getItem() == Items.diamond
                    && s.hasTagCompound()
                    && "kept".equals(
                        s.getTagCompound()
                            .getString("VoidQA")))
                    count += s.stackSize;
            }
            check(
                count == 3 && p.worldObj.getBlock(24, 65, 24) == Blocks.bedrock,
                "Flight preserves loot NBT and leaves bedrock intact");
            VoidReturn.mode = "PLACE";
            body.returnFromVoid(24.5, 66, 24.5);
            VoidReturn.enabled = false;
            body.returnFromVoid(24.5, -90, 24.5);
            VoidReturn.tick(body);
            check(
                !body.isDead && body.posY > 0 && body.posY < 4,
                "Disabled rescue preserves the body at the lower boundary");
            VoidReturn.enabled = true;
            VoidReturn.surfaceFallback = false;
            VoidReturn.spawnFallback = false;
            VoidReturn.delaySeconds = 0;
            body.voidReturn.removeTag("Safe");
            VoidReturn.tick(body);
            check(
                !body.isDead && body.voidReturn.getInteger("Returns") == 1,
                "No destination preserves the original inventory instead of spawning a replacement");
            VoidReturn.config();
            CorpseConfig.downed = downed;
            PhysicsConfig.playerRagdolls = savedPlayers;
            return true;
        }
        return false;
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("VOIDQA {}", text);
    }
}
