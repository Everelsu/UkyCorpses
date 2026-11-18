package com.uky.corpses.core;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;

import com.uky.corpses.Reactions;

import cpw.mods.fml.common.FMLCommonHandler;

/** Object signatures keep transformed common classes free of client renderer class references. */
public final class Hooks {

    private static boolean registered;

    public static void damagedHealth(Object target, float health, Object source) {
        net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) target;
        if (!com.uky.corpses.Downed.damaged(living, (net.minecraft.util.DamageSource) source, health))
            living.setHealth(health);
    }

    public static void teleported(Object player) {
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP)
            Reactions.teleported((net.minecraft.entity.player.EntityPlayerMP) player);
    }

    public static void modelBegin(Object entity) {
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient() && entity instanceof net.minecraft.entity.EntityLivingBase)
            ClientHooks.modelBegin((net.minecraft.entity.EntityLivingBase) entity);
    }

    public static void modelEnd() {
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) ClientHooks.modelEnd();
    }

    public static int modelPart(Object model, float scale) {
        return FMLCommonHandler.instance()
            .getSide()
            .isClient() ? ClientHooks.modelPart(model, scale) : -1;
    }

    public static void modelPartEnd(int texture) {
        if (texture >= 0 && FMLCommonHandler.instance()
            .getSide()
            .isClient()) ClientHooks.modelPartEnd(texture);
    }

    public static void init() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new HookEvents());
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) ClientHooks.init();
    }

    public static void copied(Object original, Object copied) {
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) ClientHooks.copied(original, copied);
    }

    public static Object texture(Object texture) {
        return FMLCommonHandler.instance()
            .getSide()
            .isClient() ? ClientHooks.texture(texture) : texture;
    }

    public static void inventoryOverlay(Object stack, int x, int y) {
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) ClientHooks.inventoryOverlay(stack, x, y);
    }

    public static void heldOverlay(Object stack) {
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) ClientHooks.heldOverlay(stack);
    }

    public static void firstPersonArm(Object player) {
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) ClientHooks.firstPersonArm(player);
    }

    public static void ampDamage(Object victim, Object source, float blow, float healthLoss) {
        if (victim instanceof net.minecraft.entity.EntityLivingBase
            && source instanceof net.minecraft.util.DamageSource)
            com.uky.corpses.amp.LivingAmputation.damage(
                (net.minecraft.entity.EntityLivingBase) victim,
                (net.minecraft.util.DamageSource) source,
                blow,
                healthLoss);
    }

    public static boolean dragHands(float partial) {
        return FMLCommonHandler.instance()
            .getSide()
            .isClient() && ClientHooks.dragHands(partial);
    }

    public static void dragArms(Object model, Object entity) {
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) ClientHooks.dragArms(model, entity);
    }

    public static float lookScale(Object entity) {
        return entity instanceof EntityPlayer && Reactions.isDragging((EntityPlayer) entity) ? .45F : 1F;
    }

    private Hooks() {}
}
