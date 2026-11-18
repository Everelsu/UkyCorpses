package com.uky.corpses;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

import com.uky.corpses.medicine.Medicine;

import cpw.mods.fml.common.gameevent.PlayerEvent;

public final class SoloDownedGameCheck {

    private static boolean disabled;
    private static ItemStack item;
    private static volatile boolean holding;
    private static int clientTicks;

    public static boolean tick(EntityPlayerMP p, int phase) {
        if (phase == 1) {
            disabled = OriginalGameplay.disableInSingleplayer;
            item = p.inventory.mainInventory[20];
            OriginalGameplay.disableInSingleplayer = true;
            p.inventory.mainInventory[20] = new ItemStack(Medicine.SYRINGE);
            new Downed().onRespawn(new PlayerEvent.PlayerRespawnEvent(p));
            new Medicine.Events().respawn(new PlayerEvent.PlayerRespawnEvent(p));
        }
        if (phase == 45) {
            p.setHealth(20);
            p.hurtResistantTime = 0;
            p.attackEntityFrom(DamageSource.outOfWorld, 1000);
            check(
                Downed.is(p) && p.getHealth() > 0,
                "A real fatal solo hit enters knockout when inventory contains adrenaline");
        }
        if (phase == 151) {
            check(Downed.is(p), "Inventory adrenaline keeps the solo rescue window open beyond five seconds");
            holding = true;
        }
        if (phase >= 230) {
            holding = false;
            try {
                java.lang.reflect.Field f = Downed.class.getDeclaredField("downed");
                f.setAccessible(true);
                Object st = ((java.util.Map<?, ?>) f.get(null)).get(p.getUniqueID());
                if (st != null) for (String name : new String[] { "giveUpHeld", "giveUpInputAt" }) {
                    java.lang.reflect.Field v = st.getClass()
                        .getDeclaredField(name);
                    v.setAccessible(true);
                    UkyCorpses.LOG.info("SOLOQA diagnostic {}={}", name, v.get(st));
                }
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            check(
                !Downed.is(p) && p.getHealth() <= 0,
                "Holding the actual jump binding sends surrender through the network and kills the downed player");
            OriginalGameplay.disableInSingleplayer = disabled;
            p.inventory.mainInventory[20] = item;
            return true;
        }
        return false;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (holding && ++clientTicks % 20 == 0) UkyCorpses.LOG.info(
            "SOLOQA input hp={} down={} screen={} key={}",
            mc.thePlayer.getHealth(),
            Downed.is(mc.thePlayer),
            mc.currentScreen,
            mc.gameSettings.keyBindJump.getIsKeyPressed());
        if (mc.thePlayer != null) KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), holding);
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("SOLOQA {}", text);
    }

    private SoloDownedGameCheck() {}
}
