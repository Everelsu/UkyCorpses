package com.uky.corpses;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;
import com.uky.corpses.amp.AmpClient;
import com.uky.corpses.amp.AmputationState;
import com.uky.corpses.pc.PcClient;
import com.uky.corpses.pc.PcCorpses;

/** Actual command, player input, low-ceiling recovery and old downed presentation regressions. */
public final class RecoveryGameCheck {

    private static volatile int step;
    private static volatile boolean compassSeen, downedSeen, restored;
    private static float health;
    private static double crawlStart;
    private static int clientAge, view;
    private static EntityOtherPlayerMP friend;
    private static GuiScreen compassScreen;

    public static boolean serverTick(EntityPlayerMP p) throws Exception {
        int n = ++step;
        if (n == 1) {
            health = p.getHealth();
            p.fallDistance = 30;
            tp(p, 3);
            check(p.fallDistance == 0, "Vanilla /tp clears the previous fall distance immediately");
        }
        if (n == 10) check(
            !Reactions.is(p) && p.getHealth() == health,
            "Vanilla /tp preserves health and does not trigger a reaction (active=" + Reactions
                .is(p) + ", health=" + p.getHealth() + "/" + health + ", fall=" + p.fallDistance + ")");
        if (n == 12) {
            Reactions.request(p, MsgReaction.TOGGLE);
            check(Reactions.is(p), "Teleport regression starts a real native ragdoll");
            tp(p, -3);
            check(!Reactions.is(p) && !p.noClip, "Teleport releases active physics immediately");
        }
        if (n == 25) check(p.getHealth() == health && !Reactions.is(p), "Teleport has no delayed impact damage");
        if (n == 30) {
            Reactions.request(p, MsgReaction.TOGGLE);
            check(Reactions.is(p), "Low-ceiling test starts a real ragdoll");
            p.worldObj.setBlock(-3, 66, -6, Blocks.stone);
            check(
                Reactions.stop(p) && !Reactions.is(p) && !p.noClip && p.height == Downed.LOW,
                "Getting up beneath a low ceiling releases physics and permits crawling");
        }
        if (n == 35) p.worldObj.setBlockToAir(-3, 66, -6);
        if (n == 40) {
            check(p.height > 1.7F, "Crawling recovery stands up when headroom returns");
            Downed.down(p, net.minecraft.util.DamageSource.generic);
            crawlStart = p.posZ;
            check(Downed.is(p) && !Reactions.is(p) && !p.noClip, "Old downed mode keeps player movement and collision");
        }
        if (n >= 90 && downedSeen && Downed.is(p)) {
            check(Math.abs(p.posZ - crawlStart) > .05, "Real client movement input crawls the downed server player");
            FakePlayer helper = new FakePlayer(
                (net.minecraft.world.WorldServer) p.worldObj,
                new GameProfile(UUID.randomUUID(), "ReviverQA"));
            helper.setPosition(p.posX + 1, p.posY, p.posZ);
            java.lang.reflect.Method can = Downed.class
                .getDeclaredMethod("canRevive", EntityPlayerMP.class, EntityPlayerMP.class);
            can.setAccessible(true);
            check((Boolean) can.invoke(null, helper, p), "A nearby healthy helper can revive a crawling player");
            check(Downed.reviveNow(p, false), "Revival exits the old downed lifecycle");
        }
        if (n > 75 && restored && compassSeen) {
            check(!Downed.is(p) && !p.noClip && p.height > 1.7F, "Revival restores standing collision");
            return true;
        }
        check(n < 180, "Recovery scenario remains bounded");
        return false;
    }

    private static void tp(EntityPlayerMP p, int x) {
        MinecraftServer.getServer()
            .getCommandManager()
            .executeCommand(p, "tp PortQA " + x + " 65 -6");
    }

    public static void clientTick() {
        if (step == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!compassSeen && step >= 2 && step < 30) {
            if (compassScreen == null) {
                for (ItemStack stack : mc.thePlayer.inventory.mainInventory)
                    if (stack != null && stack.getItem() == PcCorpses.compass) {
                        check(
                            stack.getIconIndex() != null && stack.getIconIndex()
                                .getIconWidth() == 16,
                            "Corpse compass uses a normal atlas icon");
                        PcClient.open(stack);
                        compassScreen = mc.currentScreen;
                        break;
                    }
            } else {
                org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
                org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
                org.lwjgl.opengl.GL11.glPushMatrix();
                org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                org.lwjgl.opengl.GL11.glPushMatrix();
                try {
                    mc.getFramebuffer()
                        .bindFramebuffer(true);
                    mc.entityRenderer.setupOverlayRendering();
                    compassScreen.drawScreen(0, 0, 0);
                    shot("06-compass.png");
                } finally {
                    org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                    org.lwjgl.opengl.GL11.glPopMatrix();
                    org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
                    org.lwjgl.opengl.GL11.glPopMatrix();
                    org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                    org.lwjgl.opengl.GL11.glPopAttrib();
                }
                mc.displayGuiScreen(null);
                compassSeen = true;
                ModelBiped model = new ModelBiped();
                EntityZombie severed = new EntityZombie(mc.theWorld), healthy = new EntityZombie(mc.theWorld);
                severed.getEntityData()
                    .setByte(AmputationState.KEY, (byte) 4);
                AmpClient.apply(model, severed);
                check(!model.bipedRightArm.showModel, "Actual AMP mask hides only a severed arm");
                AmpClient.apply(model, healthy);
                check(
                    model.bipedRightArm.showModel && model.bipedLeftArm.showModel,
                    "An unscoped amputee render cannot hide a healthy zombie arm");
            }
        }
        if (Downed.is(mc.thePlayer) && step >= 40) {
            if (clientAge++ == 0) {
                check(!Reactions.is(mc.thePlayer), "Downed client uses the crawl presentation");
                view = mc.gameSettings.thirdPersonView;
                mc.gameSettings.thirdPersonView = 1;
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                friend = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "FriendQA"));
                friend.setPosition(-3, 65 + friend.yOffset, -3);
                mc.theWorld.addEntityToWorld(987653, friend);
                MsgDowned message = new MsgDowned(friend.getEntityId(), EntityCorpse.PRONE, 180, true, 500, -1, -1, -1);
                message.at = message.since = com.uky.corpses.client.DownedClient.ticks;
                message.leftMax = 500;
                Downed.seen.put(friend.getEntityId(), message);
            }
            if (clientAge == 28) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                shot("07-crawl-downed.png");
                downedSeen = true;
            }
        } else if (downedSeen && !Downed.is(mc.thePlayer)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            mc.gameSettings.thirdPersonView = view;
            if (friend != null) {
                Downed.seen.remove(friend.getEntityId());
                mc.theWorld.removeEntityFromWorld(friend.getEntityId());
                friend = null;
            }
            restored = true;
        }
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        if (!message.endsWith("bounded")) UkyCorpses.LOG.info("RECOVERYQA {}", message);
    }

    private RecoveryGameCheck() {}
}
