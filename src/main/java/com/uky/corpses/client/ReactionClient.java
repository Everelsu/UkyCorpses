package com.uky.corpses.client;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.uky.corpses.Downed;
import com.uky.corpses.MsgReaction;
import com.uky.corpses.Reactions;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Presents server snapshots; never simulates a second, disagreeing client ragdoll. */
public final class ReactionClient {

    private static final KeyBinding toggle = new KeyBinding("Рэгдолл: упасть / встать", Keyboard.KEY_H, "UkyCorpses");
    private static final float[] WHITE = { 1, 1, 1 };
    private static World world;
    private static MovementInput previousInput;
    private static EntityPlayerSP pinnedPlayer, observedPlayer;
    private static boolean pinned, noClip, shift;
    private static float height;
    private static int restoreCamera = -1;
    private static final Set<EntityPlayer> reshaped = new HashSet<>();
    private static final Map<EntityPlayer, Boolean> frustum = new IdentityHashMap<>();

    private static final class Pinned extends MovementInputFromOptions {

        Pinned() {
            super(Minecraft.getMinecraft().gameSettings);
        }

        @Override
        public void updatePlayerMoveState() {
            moveForward = moveStrafe = 0;
            jump = sneak = false;
        }
    }

    public static void init() {
        ClientRegistry.registerKeyBinding(toggle);
        RagdollCamera.init();
        ReactionClient handler = new ReactionClient();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    public static World currentWorld() {
        return Minecraft.getMinecraft().theWorld;
    }

    public static void accept(MsgReaction m, World expected) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != expected) return;
        if (world != expected) changeWorld(expected);
        Reactions.receive(m, expected);
    }

    private static void restorePinned(boolean transition) {
        Minecraft mc = Minecraft.getMinecraft();
        if (restoreCamera >= 0) {
            mc.gameSettings.thirdPersonView = restoreCamera;
            restoreCamera = -1;
        }
        EntityPlayerSP p = pinnedPlayer;
        if (p != null) {
            p.noClip = noClip;
            if (p.movementInput instanceof Pinned) p.movementInput = previousInput != null ? previousInput
                : new MovementInputFromOptions(Minecraft.getMinecraft().gameSettings);
            if (transition || !Downed.is(p)) Downed.height(p, height);
        }
        pinned = false;
        pinnedPlayer = null;
        previousInput = null;
    }

    private static void changeWorld(World next) {
        restorePinned(true);
        for (EntityPlayer p : reshaped) p.setPosition(p.posX, p.posY, p.posZ);
        reshaped.clear();
        for (Map.Entry<EntityPlayer, Boolean> e : frustum.entrySet()) e.getKey().ignoreFrustumCheck = e.getValue();
        frustum.clear();
        world = next;
        Reactions.clearClient();
        shift = false;
    }

    public static boolean pose(EntityPlayer p, float partial, BodyCam.Pose out) {
        Skeleton.Shape shape = Reactions.physicalShape(p, partial);
        if (shape == null) return false;
        out.frame.set(shape.frame);
        out.rest.set(shape.frame);
        System.arraycopy(shape.angles, 0, out.angles, 0, out.angles.length);
        System.arraycopy(shape.angles, 0, out.restAngles, 0, out.restAngles.length);
        out.x = p.posX;
        out.y = p.posY;
        out.z = p.posZ;
        out.pose = Poses.LIE;
        out.k = 1;
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (world != mc.theWorld) changeWorld(mc.theWorld);
        if (observedPlayer != p) {
            if (observedPlayer != null) {
                Reactions.forgetClient(observedPlayer.getEntityId());
                com.uky.corpses.Downed.seen.remove(observedPlayer.getEntityId());
                com.uky.corpses.OriginalGameplay.seen.remove(observedPlayer.getEntityId());
            }
            restorePinned(true);
            observedPlayer = p;
        }
        if (p == null || world == null) return;
        boolean active = Reactions.is(p);
        if (active && !pinned) {
            // Holding sneak before falling must not immediately submit a get-up request.
            shift = true;
            p.setSprinting(false);
            if ("THIRD_PERSON".equals(Reactions.cameraMode) && mc.gameSettings.thirdPersonView == 0) {
                restoreCamera = 0;
                mc.gameSettings.thirdPersonView = 1;
            }
            if (Reactions.showHint && !Downed.is(p)) {
                String key = net.minecraft.client.settings.GameSettings.getKeyDisplayString(toggle.getKeyCode());
                String sneak = net.minecraft.client.settings.GameSettings
                    .getKeyDisplayString(mc.gameSettings.keyBindSneak.getKeyCode());
                mc.ingameGUI.func_110326_a(
                    String
                        .format(Reactions.sneakToGetUp ? "Press %s or %s to get up" : "Press %s to get up", key, sneak),
                    false);
            }
        }
        if (e.phase == TickEvent.Phase.START) {
            while (toggle.isPressed()) if (mc.currentScreen == null && p.isEntityAlive())
                UkyCorpses.net.sendToServer(new MsgReaction(MsgReaction.TOGGLE));
            boolean sneak = mc.gameSettings.keyBindSneak.getIsKeyPressed();
            if (active && Reactions.sneakToGetUp
                && sneak
                && (!shift || p.ticksExisted % 10 == 0)
                && mc.currentScreen == null) UkyCorpses.net.sendToServer(new MsgReaction(MsgReaction.UP));
            shift = sneak;
        }
        if (active) {
            if (!pinned) {
                previousInput = p.movementInput;
                noClip = p.noClip;
                height = p.height;
                pinnedPlayer = p;
                pinned = true;
            }
            if (!(p.movementInput instanceof Pinned)) p.movementInput = new Pinned();
            p.movementInput.moveForward = p.movementInput.moveStrafe = 0;
            p.movementInput.jump = p.movementInput.sneak = false;
            p.motionX = p.motionY = p.motionZ = 0;
            p.fallDistance = 0;
            p.noClip = true;
            Downed.height(p, Downed.LOW);
        } else if (pinned) restorePinned(false);
        if (e.phase == TickEvent.Phase.END) {
            for (Object o : world.playerEntities) {
                EntityPlayer other = (EntityPlayer) o;
                if (other == p) continue;
                Skeleton.Shape shape = Reactions.physicalShape(other, 1);
                if (shape == null) continue;
                double x0 = 1e9, y0 = 1e9, z0 = 1e9, x1 = -1e9, y1 = -1e9, z1 = -1e9;
                for (int i = 0; i < Skeleton.PARTS; i++) {
                    double[] b = shape.bounds(i);
                    x0 = Math.min(x0, b[0]);
                    y0 = Math.min(y0, b[1]);
                    z0 = Math.min(z0, b[2]);
                    x1 = Math.max(x1, b[3]);
                    y1 = Math.max(y1, b[4]);
                    z1 = Math.max(z1, b[5]);
                }
                other.boundingBox.setBounds(
                    other.posX + x0,
                    other.posY + y0,
                    other.posZ + z0,
                    other.posX + x1,
                    other.posY + y1,
                    other.posZ + z1);
                reshaped.add(other);
            }
            for (Iterator<EntityPlayer> it = reshaped.iterator(); it.hasNext();) {
                EntityPlayer other = it.next();
                if (other.worldObj == world && Reactions.is(other)) continue;
                other.setPosition(other.posX, other.posY, other.posZ);
                it.remove();
            }
            for (Iterator<Map.Entry<EntityPlayer, Boolean>> it = frustum.entrySet()
                .iterator(); it.hasNext();) {
                Map.Entry<EntityPlayer, Boolean> saved = it.next();
                if (saved.getKey().worldObj == world && Reactions.is(saved.getKey())) continue;
                saved.getKey().ignoreFrustumCheck = saved.getValue();
                it.remove();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void render(RenderPlayerEvent.Pre e) {
        EntityPlayer p = e.entityPlayer;
        Skeleton.Shape shape = Reactions.physicalShape(p, e.partialRenderTick);
        if (shape == null || e.isCanceled() && !Downed.is(p)) return;
        e.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && p.isInvisibleToPlayer(mc.thePlayer)) return;
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < armor.length; i++) armor[i] = p.getCurrentArmor(i);
        CorpseClient.renderer.drawPhysical(
            p,
            RenderCorpse.skin(p.getCommandSenderName()),
            armor,
            p.getHeldItem(),
            shape,
            WHITE,
            p.posX - net.minecraft.client.renderer.entity.RenderManager.renderPosX,
            p.posY - net.minecraft.client.renderer.entity.RenderManager.renderPosY,
            p.posZ - net.minecraft.client.renderer.entity.RenderManager.renderPosZ);
        frustum.putIfAbsent(p, p.ignoreFrustumCheck);
        p.ignoreFrustumCheck = true;
    }
}
