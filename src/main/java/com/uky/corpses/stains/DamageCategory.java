package com.uky.corpses.stains;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.DamageSource;

public enum DamageCategory {

    SHARP,
    BLUNT,
    PIERCE,
    EXPLOSION,
    FALL,
    NONE;

    public static DamageCategory of(DamageSource source) {
        String name = source.damageType;
        if (source.isFireDamage() || source.isMagicDamage()
            || name.equals("drown")
            || name.equals("starve")
            || name.equals("inWall")
            || name.equals("outOfWorld")
            || name.equals("wither")) return NONE;
        if (source.isExplosion()) return EXPLOSION;
        if (name.equals("fall") || name.equals("flyIntoWall")) return FALL;
        if (source.isProjectile() || name.equals("cactus") || name.equals("thorns")) return PIERCE;
        if (source.getEntity() instanceof EntityLivingBase && source.getEntity() == source.getSourceOfDamage()) {
            ItemStack item = ((EntityLivingBase) source.getEntity()).getHeldItem();
            if (item != null && (item.getItem() instanceof ItemSword || item.getItem() instanceof ItemAxe
                || item.getItem() instanceof ItemShears)) return SHARP;
        }
        return BLUNT;
    }
}
