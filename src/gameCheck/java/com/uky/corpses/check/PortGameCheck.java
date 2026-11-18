package com.uky.corpses.check;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import com.google.gson.GsonBuilder;
import com.uky.corpses.EntityCorpse;
import com.uky.corpses.MsgReaction;
import com.uky.corpses.Reactions;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.client.RagdollCamera;
import com.uky.corpses.corpse.RagdollWorld;
import com.uky.corpses.mobs.EntityMobRagdoll;
import com.uky.corpses.pc.PcCorpses;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Exercises the real client, integrated server, network, native solver and camera in a new disposable world. */
@Mod(
    modid = "ukyportcheck",
    name = "Uky local game checks",
    version = "1",
    dependencies = "required-after:ukycorpses",
    acceptableRemoteVersions = "*")
public final class PortGameCheck {

    private volatile int stage, phaseTicks;
    private volatile boolean mobsSeen, limpSeen, deathSeen, respawnSeen, blindnessSeen;
    private boolean launched, respawnSent;
    private int clientTicks, doneTicks;
    private boolean downHudShot;
    private long life, launchTime;
    private EntityCorpse corpse;
    private int woundMobId = -1;
    private Object warmScene;
    private boolean testingLimits, savedPlayerRagdolls, savedMobRagdolls;
    private boolean wallInjected, nightSeen;
    private volatile boolean bloodSeen;
    private int independentMobId;
    private com.uky.corpses.stains.client.Stain wallMark;
    private final AtomicBoolean done = new AtomicBoolean();
    private final Map<String, Object> evidence = new LinkedHashMap<>();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        evidence.put("javaVersion", System.getProperty("java.version"));
        if (Boolean.getBoolean("uky.portqa")) FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.pauseOnLostFocus = false;
        if (done.get()) {
            if (++doneTicks == 40 && mc.theWorld != null) {
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
                System.gc();
                mc.displayGuiScreen(new GuiMainMenu());
            }
            if (doneTicks > 60) mc.shutdown();
            return;
        }
        try {
            if (!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                launchTime = System.currentTimeMillis();
                org.lwjgl.opengl.Display.setTitle("UkyCorpses Port QA");
                WorldSettings settings = new WorldSettings(
                    4312,
                    WorldSettings.GameType.SURVIVAL,
                    false,
                    false,
                    WorldType.FLAT);
                settings.enableCommands();
                mc.launchIntegratedServer("PortQA-" + System.currentTimeMillis(), "Port QA TEMP", settings);
                return;
            }
            if (mc.thePlayer == null || mc.theWorld == null) {
                if (launched && System.currentTimeMillis() - launchTime > 120000) {
                    shot("00-login-failure.png");
                    throw new AssertionError("World login did not finish: " + mc.currentScreen);
                }
                return;
            }
            if (++clientTicks > 1800) throw new AssertionError("Client timed out at stage " + stage);
            if (stage == 1 && phaseTicks >= 30 && !wallInjected) {
                wallMark = com.uky.corpses.stains.client.StainPlacement.impact(
                    net.minecraftforge.common.util.ForgeDirection.NORTH,
                    5.9,
                    67.6,
                    5,
                    .4F,
                    -.2F,
                    .05F,
                    .08F,
                    0x8e1111,
                    0x4e140e,
                    com.uky.corpses.stains.client.Surface.PLAIN,
                    mc.theWorld.getTotalWorldTime(),
                    new java.util.Random(84));
                java.lang.reflect.Method add = com.uky.corpses.stains.client.SurfaceBloodClient.class
                    .getDeclaredMethod("add", com.uky.corpses.stains.client.Stain.class);
                add.setAccessible(true);
                add.invoke(null, wallMark);
                wallInjected = true;
            }
            if (stage == 1 && phaseTicks >= 90 && !nightSeen && mc.theWorld.getWorldTime() % 24000 > 13000) {
                shot("05-wall-night.png");
                nightSeen = true;
            }
            if (stage == 1 && phaseTicks >= 55 && !mobsSeen) {
                int count = 0;
                for (Object entity : mc.theWorld.loadedEntityList) if (entity instanceof EntityMobRagdoll) {
                    EntityMobRagdoll body = (EntityMobRagdoll) entity;
                    if (body.visualEntity() != null && body.physicalTransforms(1).length > 0) count++;
                }
                if (count >= 4) {
                    java.lang.reflect.Field stains = com.uky.corpses.stains.client.SurfaceBloodClient.class
                        .getDeclaredField("stains");
                    stains.setAccessible(true);
                    if (((java.util.List<?>) stains.get(null)).stream()
                        .noneMatch(
                            stain -> ((com.uky.corpses.stains.client.Stain) stain).face
                                == net.minecraftforge.common.util.ForgeDirection.UP))
                        return;
                    require(
                        ((java.util.List<?>) stains.get(null)).stream()
                            .anyMatch(
                                stain -> ((com.uky.corpses.stains.client.Stain) stain).face
                                    == net.minecraftforge.common.util.ForgeDirection.UP),
                        "SurfaceBlood droplets hit the actual block surface");
                    java.lang.reflect.Field cache = com.uky.corpses.stains.client.StainMesh.class
                        .getDeclaredField("cache");
                    cache.setAccessible(true);
                    Object mesh = ((java.util.Map<?, ?>) cache.get(null)).get(wallMark);
                    require(mesh != null, "Wall decal entered the actual block-face mesh cache");
                    java.lang.reflect.Field faces = mesh.getClass()
                        .getDeclaredField("faces");
                    faces.setAccessible(true);
                    require(
                        !((java.util.List<?>) faces.get(mesh)).isEmpty(),
                        "Wall decal clips to supported block geometry");
                    checkStainSave(mc.theWorld, wallMark, (java.util.List<?>) stains.get(null));
                    require(
                        com.uky.corpses.stains.client.StainShader.available(),
                        "SurfaceBlood original stain shader compiled on the game GPU");
                    Entity wounded = mc.theWorld.getEntityByID(woundMobId);
                    if (wounded == null || !com.uky.corpses.stains.client.ModelWounds.hasPainted(wounded)) return;
                    require(true, "SurfaceBlood wound painted through the actual mob model UVs");
                    shot("01-mobs.png");
                    mobsSeen = true;
                } else if (clientTicks % 40 == 0) {
                    StringBuilder types = new StringBuilder();
                    for (Object entity : mc.theWorld.loadedEntityList) if (entity instanceof EntityMobRagdoll) {
                        EntityMobRagdoll body = (EntityMobRagdoll) entity;
                        types.append(body.sourceType())
                            .append(':')
                            .append(body.snapshot() == null ? 0 : body.snapshot().length / 13)
                            .append(' ');
                    }
                    UkyCorpses.LOG.info("PORTQA client sees {} physical mobs: {}", count, types);
                    shot("qa-progress.png");
                }
            }
            if (stage == 1 && phaseTicks >= 55 && !bloodSeen)
                bloodSeen = com.uky.corpses.stains.client.BloodGameCheck.tick();
            if (stage == 2 && Reactions.is(mc.thePlayer) && RagdollCamera.active()) {
                if (!limpSeen) {
                    shot("02-physical-player.png");
                    limpSeen = true;
                }
                if (mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness)) {
                    require(
                        mc.renderViewEntity.isPotionActive(net.minecraft.potion.Potion.blindness),
                        "Physical camera retains existing blindness");
                    blindnessSeen = true;
                }
            }
            if (stage == 3 && phaseTicks > 20
                && mc.thePlayer.getHealth() <= 0
                && mc.currentScreen instanceof GuiGameOver
                && RagdollCamera.active()) {
                EntityCorpse target = null;
                for (Object entity : mc.theWorld.loadedEntityList) if (entity instanceof EntityCorpse) {
                    EntityCorpse body = (EntityCorpse) entity;
                    if (mc.thePlayer.getUniqueID()
                        .equals(body.physicalOwner()) && body.physicalLife() == life) target = body;
                }
                if (target != null && !deathSeen) {
                    shot("03-death-camera.png");
                    deathSeen = true;
                }
            }
            if (stage == 3 && deathSeen && phaseTicks > 40 && !respawnSent) {
                respawnSent = true;
                mc.thePlayer.respawnPlayer();
                mc.displayGuiScreen(null);
            }
            if (stage == 4 && mc.thePlayer.getHealth() > 0
                && !Reactions.is(mc.thePlayer)
                && !RagdollCamera.active()
                && mc.renderViewEntity == mc.thePlayer) {
                require(
                    com.uky.corpses.stains.client.SkinBlood.texture(mc.thePlayer, mc.thePlayer.getLocationSkin(), false)
                        .equals(mc.thePlayer.getLocationSkin()),
                    "Respawned player has a clean skin instead of wounds from the previous life");
                checkRespawnEmitters(mc.thePlayer);
                com.uky.corpses.client.ArmorGameCheck.run();
                shot("04-respawn.png");
                respawnSeen = true;
            }
            if (stage == 4) com.uky.corpses.RecoveryGameCheck.clientTick();
            if (stage == 11) {
                com.uky.corpses.SoloDownedGameCheck.clientTick();
                if (phaseTicks > 130 && !downHudShot) {
                    shot("07-downed-hud.png");
                    downHudShot = true;
                }
            }
            if (stage == 12) com.uky.corpses.VoidReturnGameCheck.clientTick(phaseTicks);
            if (stage == 13) com.uky.corpses.PenaltyGameCheck.clientTick(phaseTicks);
        } catch (Throwable failure) {
            finish(false, failure);
        }
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done.get()) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        EntityPlayerMP p = server.getConfigurationManager()
            .func_152612_a("PortQA");
        if (p == null) return;
        try {
            if (++phaseTicks > 400) throw new AssertionError("Server timed out at stage " + stage);
            WorldServer world = (WorldServer) p.worldObj;
            if (stage == 0) {
                com.uky.corpses.CompatibilityGameCheck.setup(p);
                if (Boolean.getBoolean("uky.portqa.penaltyOnly")) {
                    advance(13);
                    return;
                }
                if (Boolean.getBoolean("uky.portqa.voidOnly")) {
                    world.getGameRules()
                        .setOrCreateGameRule("doMobSpawning", "false");
                    advance(12);
                    return;
                }
                warmScene = scene(world);
                require(
                    com.github.stephengold.joltjni.JoltPhysicsObject.isCleanerStarted(),
                    "Native temporary objects have automatic Java 8 cleanup");
                require(warmScene != null, "Native solver warmed before the first death");
                for (int cx = -1; cx <= 0; cx++)
                    for (int cz = -1; cz <= 0; cz++) world.theChunkProviderServer.loadChunk(cx, cz);
                java.util.Set<Integer> ids = new java.util.HashSet<>();
                for (Class<? extends Entity> entity : java.util.Arrays.<Class<? extends Entity>>asList(
                    EntityCorpse.class,
                    com.uky.corpses.medicine.EntitySeat.class,
                    com.uky.corpses.amp.EntityDetachedPart.class,
                    EntityMobRagdoll.class))
                    require(
                        ids.add(
                            cpw.mods.fml.common.registry.EntityRegistry.instance()
                                .lookupModSpawn(entity, false)
                                .getModEntityId()),
                        "Unique network entity ID: " + entity.getSimpleName());
                world.getGameRules()
                    .setOrCreateGameRule("doMobSpawning", "false");
                world.setWorldTime(6000);
                for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
                    world.setBlock(x, 64, z, Blocks.stone);
                    for (int y = 65; y <= 70; y++) world.setBlockToAir(x, y, z);
                }
                for (int x = -5; x <= 5; x++) for (int y = 65; y < 68; y++) world.setBlock(x, y, 5, Blocks.stone);
                p.playerNetServerHandler.setPlayerLocation(0, 65, -6, 0, 12);
                advance(10);
                return;
            }
            if (stage == 10 && phaseTicks == 2) Reactions.request(p, MsgReaction.TOGGLE);
            if (stage == 10 && phaseTicks == 5) {
                require(Reactions.stop(p), "Initial physical transition can recover");
                require(scene(world) == warmScene, "Last body recovery retains the warmed native solver");
            }
            if (stage == 10 && phaseTicks >= 20) {
                EntityCow wounded = new EntityCow(world);
                wounded.setPosition(0, 65, -4);
                wounded.forceSpawn = true;
                require(world.spawnEntityInWorld(wounded), "Wound test cow spawned");
                woundMobId = wounded.getEntityId();
                EntityLivingBase[] mobs = { new EntityZombie(world), new EntityCow(world), new EntitySpider(world),
                    new EntityCreeper(world) };
                for (int i = 0; i < mobs.length; i++) {
                    EntityLivingBase mob = mobs[i];
                    mob.setPosition(-4 + i * 2.7, 66, 2);
                    mob.setHealth(1);
                    mob.forceSpawn = true;
                    boolean spawned = world.spawnEntityInWorld(mob);
                    require(
                        spawned,
                        "Test mob entered a loaded chunk: " + mob.getClass()
                            .getSimpleName());
                    boolean hit = mob.attackEntityFrom(DamageSource.generic, 100);
                    UkyCorpses.LOG.info(
                        "PORTQA birth {} spawned={} hit={} hp={}",
                        mob.getClass()
                            .getSimpleName(),
                        spawned,
                        hit,
                        mob.getHealth());
                }
                advance(1);
                return;
            }
            if (stage == 1 && phaseTicks == 10) {
                p.inventory.setInventorySlotContents(0, new ItemStack(Items.iron_sword));
                Entity wounded = world.getEntityByID(woundMobId);
                require(
                    wounded != null && wounded.attackEntityFrom(DamageSource.causePlayerDamage(p), 4),
                    "Sharp wound sent through the server blood packet");
            }
            if (stage == 1 && phaseTicks == 40) {
                StringBuilder types = new StringBuilder();
                for (Object entity : world.loadedEntityList) if (entity instanceof EntityMobRagdoll) {
                    EntityMobRagdoll body = (EntityMobRagdoll) entity;
                    types.append(body.sourceType())
                        .append(':')
                        .append(body.snapshot() == null ? 0 : body.snapshot().length / 13)
                        .append(' ');
                }
                UkyCorpses.LOG.info("PORTQA server bodies {}", types);
            }
            if (stage == 1 && phaseTicks == 70) {
                world.setWorldTime(18000);
                p.playerNetServerHandler.sendPacket(
                    new net.minecraft.network.play.server.S03PacketTimeUpdate(
                        world.getTotalWorldTime(),
                        world.getWorldTime(),
                        true));
            }
            if (stage == 1 && phaseTicks >= 100 && mobsSeen && nightSeen && bloodSeen) {
                world.setWorldTime(6000);
                require(p.posY >= 64.9, "Standing player remains above the platform");
                int count = 0;
                for (Object entity : world.loadedEntityList) if (entity instanceof EntityMobRagdoll) {
                    EntityMobRagdoll body = (EntityMobRagdoll) entity;
                    if (!RagdollWorld.RigScene.validState(body.snapshot()))
                        throw new AssertionError("Invalid native mob state");
                    require(body.posY >= 64.5, "Mob ragdoll collides with platform: " + body.sourceType());
                    count++;
                }
                require(count == 4, "Four mob rigs survive after vanilla mob removal");
                Reactions.request(p, MsgReaction.TOGGLE);
                life = Reactions.physicalLife(p);
                require(life > 0, "Manual physical identity exists");
                advance(2);
                return;
            }
            if (stage == 2 && phaseTicks == 12) p.attackEntityFrom(DamageSource.generic, 2);
            if (stage == 2 && phaseTicks == 5)
                p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.blindness.id, 20));
            if (stage == 2 && phaseTicks >= 40 && limpSeen && blindnessSeen) {
                p.inventory.setInventorySlotContents(0, new ItemStack(Items.diamond, 3));
                require(Reactions.physicalLife(p) == life, "Manual body identity remains stable");
                p.attackEntityFrom(DamageSource.outOfWorld, 100);
                advance(3);
                return;
            }
            if (stage == 3) {
                for (Object entity : world.loadedEntityList) if (entity instanceof EntityCorpse) {
                    EntityCorpse body = (EntityCorpse) entity;
                    if (p.getUniqueID()
                        .equals(body.physicalOwner())) corpse = body;
                }
                if (corpse != null) {
                    require(corpse.physicalLife() == life, "Death preserves the same physical life");
                    require(diamonds(corpse) == 3, "Death inventory moved to one corpse");
                }
                if (deathSeen && respawnSent && p.getHealth() > 0) {
                    require(corpse != null, "Player corpse exists after respawn");
                    com.uky.corpses.CompatibilityGameCheck.recovered(corpse);
                    int compasses = 0;
                    for (ItemStack s : p.inventory.mainInventory)
                        if (s != null && s.getItem() == PcCorpses.compass) compasses += s.stackSize;
                    require(compasses == 1, "One corpse compass issued on respawn");
                    require(diamonds(corpse) == 3, "Respawn did not duplicate or remove corpse loot");
                    advance(4);
                    return;
                }
            }
            if (stage == 4 && respawnSeen && phaseTicks > 10) {
                if (!com.uky.corpses.RecoveryGameCheck.serverTick(p)) return;
                require(!p.noClip && p.height > 1.7F, "Player collision and standing height restored");
                p.playerNetServerHandler.setPlayerLocation(0, 65, -6, 0, 12);
                savedPlayerRagdolls = com.uky.corpses.corpse.PhysicsConfig.playerRagdolls;
                savedMobRagdolls = com.uky.corpses.corpse.PhysicsConfig.mobRagdolls;
                testingLimits = true;
                com.uky.corpses.corpse.PhysicsConfig.mobRagdolls = false;
                Reactions.request(p, MsgReaction.TOGGLE);
                require(Reactions.is(p), "Player ragdoll remains enabled when mob ragdolls are disabled");
                advance(5);
                return;
            }
            if (stage == 5 && phaseTicks >= 30) {
                require(mobBodies(world) == 0, "Disabling mobs removes existing native mob bodies");
                require(Reactions.stop(p), "Player can recover with mob physics disabled");
                com.uky.corpses.corpse.PhysicsConfig.playerRagdolls = false;
                com.uky.corpses.corpse.PhysicsConfig.mobRagdolls = true;
                EntityCow mob = new EntityCow(world);
                mob.setPosition(2, 66, 2);
                mob.setHealth(1);
                mob.forceSpawn = true;
                require(world.spawnEntityInWorld(mob), "Independent mob-switch test entered a loaded chunk");
                independentMobId = mob.getEntityId();
                require(
                    mob.attackEntityFrom(DamageSource.generic, 100),
                    "Independent mob-switch test delivered fatal damage");
                advance(6);
                return;
            }
            if (stage == 6 && phaseTicks >= 15) {
                java.util.UUID corpseId = corpse.getUniqueID();
                for (Object value : world.loadedEntityList)
                    if (value instanceof EntityCorpse && ((Entity) value).getUniqueID()
                        .equals(corpseId)) corpse = (EntityCorpse) value;
                boolean independentBody = false;
                for (Object value : world.loadedEntityList)
                    if (value instanceof EntityMobRagdoll && !((Entity) value).isDead
                        && ((EntityMobRagdoll) value).sourceEntityId() == independentMobId) independentBody = true;
                require(
                    independentBody,
                    "Mob ragdolls remain enabled when player physics is disabled (bodies=" + mobBodies(world) + ")");
                Reactions.request(p, MsgReaction.TOGGLE);
                require(!Reactions.is(p), "Disabling player physics blocks manual ragdoll");
                corpse.startPhysical(new javax.vecmath.Vector3f(), 0, 0, 0);
                require(
                    !corpse.ragdollActive() && diamonds(corpse) == 3,
                    "Disabling player physics retains static corpse inventory (active=" + corpse.ragdollActive()
                        + ", diamonds="
                        + diamonds(corpse)
                        + ", loaded="
                        + world.loadedEntityList.contains(corpse)
                        + ")");
                net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
                corpse.writeToNBT(saved);
                require(saved.hasKey("CleanupBorn"), "Hard cleanup birth time persists in corpse NBT");
                require(
                    com.uky.corpses.corpse.PhysicsConfig.corpseLifetimeMinutes == 20,
                    "Default hard body lifetime is twenty minutes");
                world.getWorldInfo()
                    .incrementTotalWorldTime(world.getTotalWorldTime() + 20 * 1200L);
                advance(7);
                return;
            }
            if (stage == 7 && phaseTicks >= 5) {
                require(
                    corpse.isDead && diamonds(corpse) == 0,
                    "Twenty-minute hard expiry deletes a corpse containing loot");
                require(
                    !PcCorpses.retrieve(corpse.getUniqueID(), p),
                    "Expired corpse loot cannot be recovered from the PC index");
                int floorDiamonds = 0;
                for (Object entity : world.loadedEntityList)
                    if (entity instanceof net.minecraft.entity.item.EntityItem) {
                        ItemStack stack = ((net.minecraft.entity.item.EntityItem) entity).getEntityItem();
                        if (stack != null && stack.getItem() == Items.diamond) floorDiamonds += stack.stackSize;
                    }
                require(floorDiamonds == 0, "Hard cleanup does not replace corpse piles with dropped loot piles");
                advance(8);
            }
            if (stage == 8 && com.uky.corpses.medicine.EffectsGameCheck.tick(p, phaseTicks)) advance(9);
            if (stage == 9 && com.uky.corpses.DownedItemsGameCheck.tick(p, phaseTicks)) advance(11);
            if (stage == 11 && com.uky.corpses.SoloDownedGameCheck.tick(p, phaseTicks)) advance(12);
            if (stage == 12 && com.uky.corpses.VoidReturnGameCheck.tick(p, phaseTicks)) finish(true, null);
            if (stage == 13 && com.uky.corpses.PenaltyGameCheck.tick(p, phaseTicks)) finish(true, null);
        } catch (Throwable failure) {
            finish(false, failure);
        }
    }

    private static int diamonds(EntityCorpse body) {
        int count = 0;
        for (int i = 0; i < body.getSizeInventory(); i++) {
            ItemStack s = body.getStackInSlot(i);
            if (s != null && s.getItem() == Items.diamond) count += s.stackSize;
        }
        return count;
    }

    private static int mobBodies(WorldServer world) {
        int count = 0;
        for (Object entity : world.loadedEntityList)
            if (entity instanceof EntityMobRagdoll && !((Entity) entity).isDead) count++;
        return count;
    }

    private void checkStainSave(net.minecraft.world.World world, com.uky.corpses.stains.client.Stain wall,
        java.util.List<?> stains) throws Exception {
        Class<?> saves = Class.forName("com.uky.corpses.stains.client.StainSaves");
        java.lang.reflect.Method key = saves.getDeclaredMethod("worldKey");
        key.setAccessible(true);
        String name = (String) key.invoke(null);
        java.lang.reflect.Method save = saves
            .getDeclaredMethod("save", java.util.List.class, String.class, int.class, long.class);
        java.lang.reflect.Method load = saves
            .getDeclaredMethod("load", java.util.List.class, String.class, int.class, long.class);
        save.setAccessible(true);
        load.setAccessible(true);
        save.invoke(null, stains, name, world.provider.dimensionId, world.getTotalWorldTime());
        java.util.List<com.uky.corpses.stains.client.Stain> restored = new java.util.ArrayList<>();
        load.invoke(null, restored, name, world.provider.dimensionId, world.getTotalWorldTime());
        require(
            restored.stream()
                .anyMatch(
                    stain -> stain.x == wall.x && stain.y == wall.y
                        && stain.z == wall.z
                        && stain.sprite.u == wall.sprite.u
                        && stain.sprite.v == wall.sprite.v
                        && stain.sprite.w == wall.sprite.w
                        && stain.sprite.h == wall.sprite.h),
            "Stain save/reload preserves the exact sprite variant and wall plane");
    }

    private void checkRespawnEmitters(net.minecraft.entity.player.EntityPlayer player) throws Exception {
        java.lang.reflect.Field field = com.uky.corpses.stains.client.SurfaceBloodClient.class
            .getDeclaredField("emitters");
        field.setAccessible(true);
        for (Object emitter : (java.util.List<?>) field.get(null)) {
            java.lang.reflect.Field id = emitter.getClass()
                .getDeclaredField("entity");
            java.lang.reflect.Field source = emitter.getClass()
                .getDeclaredField("source");
            id.setAccessible(true);
            source.setAccessible(true);
            require(
                id.getInt(emitter) != player.getEntityId() || source.get(emitter) == player,
                "Old-life bleeding cannot attach to a respawned entity ID");
        }
    }

    private void advance(int next) {
        stage = next;
        phaseTicks = 0;
        UkyCorpses.LOG.info("PORTQA stage {}", next);
    }

    private void require(boolean ok, String check) {
        if (!ok) throw new AssertionError(check);
        synchronized (evidence) {
            evidence.put(check, true);
        }
    }

    private void shot(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private static Object scene(WorldServer world) throws Exception {
        java.lang.reflect.Field field = Class.forName("com.uky.corpses.corpse.JoltBridge")
            .getDeclaredField("SHARED");
        field.setAccessible(true);
        return ((java.util.Map<?, ?>) field.get(null)).get(world);
    }

    private void finish(boolean success, Throwable failure) {
        if (!done.compareAndSet(false, true)) return;
        if (testingLimits) {
            com.uky.corpses.corpse.PhysicsConfig.playerRagdolls = savedPlayerRagdolls;
            com.uky.corpses.corpse.PhysicsConfig.mobRagdolls = savedMobRagdolls;
        }
        try {
            synchronized (evidence) {
                evidence.put("success", success);
                evidence.put("stage", stage);
                evidence.put("mobsSeen", mobsSeen);
                evidence.put("physicalPlayerCamera", limpSeen);
                evidence.put("deathCameraSameLife", deathSeen);
                evidence.put("respawnCameraRestored", respawnSeen);
                if (failure != null) evidence.put("failure", failure.toString());
                File root = Minecraft.getMinecraft().mcDataDir;
                Files.write(
                    new File(root, "portqa-result.json").toPath(),
                    new GsonBuilder().setPrettyPrinting()
                        .create()
                        .toJson(evidence)
                        .getBytes(StandardCharsets.UTF_8));
            }
            if (failure != null) UkyCorpses.LOG.error("PORTQA FAILED", failure);
            else UkyCorpses.LOG.info("PORTQA PASSED");
        } catch (Exception error) {
            UkyCorpses.LOG.error("Cannot save local game check evidence", error);
        }
    }
}
