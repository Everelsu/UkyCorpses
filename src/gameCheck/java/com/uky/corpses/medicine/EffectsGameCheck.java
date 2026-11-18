package com.uky.corpses.medicine;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.util.DamageSource;

import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.common.gameevent.PlayerEvent;

public final class EffectsGameCheck {

    private static boolean shock, onHit, adrenaline, stun;
    private static int threshold, boostTime, shockTime;
    private static float health;

    public static boolean tick(EntityPlayerMP player, int phase) {
        try {
            if (phase == 1) {
                shock = Medicine.shockOn;
                onHit = Medicine.shockOnHit;
                adrenaline = Medicine.adrenalineOn;
                stun = Medicine.stun;
                threshold = Medicine.shockDamage;
                boostTime = Medicine.adrenalineSeconds;
                shockTime = Medicine.shockSeconds;
                health = player.getHealth();
                player.setHealth(20);
                player.removePotionEffect(Medicine.ADRENALINE.id);
                player.removePotionEffect(Medicine.SHOCK.id);
                player.removePotionEffect(Medicine.STUN.id);
                new Medicine.Events().respawn(new PlayerEvent.PlayerRespawnEvent(player));
                Medicine.shockOn = Medicine.shockOnHit = Medicine.adrenalineOn = true;
                Medicine.stun = false;
                Medicine.shockDamage = 6;
                Medicine.adrenalineSeconds = 1;
                Medicine.shockSeconds = 3;
                player.hurtResistantTime = 0;
                check(
                    player.attackEntityFrom(
                        new DamageSource("effectsqa").setDamageBypassesArmor()
                            .setDamageIsAbsolute(),
                        6),
                    "Effects test delivers a real non-lethal hit");
            }
            if (phase == 6) check(
                player.isPotionActive(Medicine.ADRENALINE),
                "A hard hit grants adrenaline while physics is enabled");
            if (phase == 35) check(player.isPotionActive(Medicine.SHOCK), "Shock starts after hit adrenaline ends");
            if (phase >= 36) {
                restore(player);
                return true;
            }
            return false;
        } catch (Throwable failure) {
            restore(player);
            throw failure;
        }
    }

    private static void restore(EntityPlayerMP player) {
        Medicine.shockOn = shock;
        Medicine.shockOnHit = onHit;
        Medicine.adrenalineOn = adrenaline;
        Medicine.stun = stun;
        Medicine.shockDamage = threshold;
        Medicine.adrenalineSeconds = boostTime;
        Medicine.shockSeconds = shockTime;
        player.removePotionEffect(Medicine.ADRENALINE.id);
        player.removePotionEffect(Medicine.SHOCK.id);
        player.removePotionEffect(Medicine.BLEEDING.id);
        player.removePotionEffect(Potion.confusion.id);
        player.setHealth(health);
        new Medicine.Events().respawn(new PlayerEvent.PlayerRespawnEvent(player));
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("EFFECTSQA {}", text);
    }

    private EffectsGameCheck() {}
}
