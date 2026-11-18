package com.uky.corpses.medicine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** One class for all six status effects of UkyCorpses; what each does is picked by name. */
public final class Fx extends Potion {

    /** Bleeding's own damage. Public so other subsystems can recognise it. */
    public static final DamageSource BLEED = new DamageSource("ukycorpses.bleeding").setDamageBypassesArmor();

    private final String key;
    private final ResourceLocation icon;

    Fx(int id, String key, boolean bad, int color, boolean hasIcon) {
        super(id, bad, color);
        this.key = key;
        setPotionName("potion.ukycorpses." + key);
        icon = hasIcon ? new ResourceLocation(UkyCorpses.MODID, "textures/gui/" + key + ".png") : null;
        if (key.equals("shock"))
            func_111184_a(SharedMonsterAttributes.movementSpeed, "a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d", -0.3, 2);
        if (key.equals("adrenaline"))
            func_111184_a(SharedMonsterAttributes.movementSpeed, "b2c3d4e5-6f70-4a8b-9c0d-1e2f3a4b5c6d", 0.35, 2);
    }

    /** How much shock slows and adrenaline speeds up: the config's, when the effect is put on (Medicine). */
    @Override
    public double func_111183_a(int amp, AttributeModifier m) {
        if (key.equals("shock")) return -Medicine.shockSlow / 100.0 * (amp + 1);
        if (key.equals("adrenaline")) return Medicine.adrenalineSpeed / 100.0 * (amp + 1);
        return super.func_111183_a(amp, m);
    }

    @Override
    public boolean isReady(int left, int amp) {
        return key.equals("bleeding") && left % Medicine.bleedInterval == 0;
    }

    /** Runs inside the entity's effect loop, where hurting or adding effects would break that loop: only queued. */
    @Override
    public void performEffect(EntityLivingBase e, int amp) {
        if (!e.worldObj.isRemote && key.equals("bleeding")) Medicine.bleed(e);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventoryEffect(int x, int y, PotionEffect effect, Minecraft mc) {
        if (icon == null) return;
        mc.getTextureManager()
            .bindTexture(icon);
        GL11.glColor4f(1, 1, 1, 1);
        Gui.func_146110_a(x + 6, y + 7, 0, 0, 18, 18, 18, 18);
    }
}
