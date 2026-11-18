package com.uky.corpses.client;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.ShaderGroup;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

/** EntityRenderer's post shader slot. Reflection: whether the field is public outside dev depends on Forge's AT. */
public final class Shaders {

    private static final String[] FIELD = { "theShaderGroup", "field_147707_d" };

    public static ShaderGroup get(EntityRenderer r) {
        return ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, r, FIELD);
    }

    public static void set(EntityRenderer r, ShaderGroup g) {
        ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, r, g, FIELD);
    }
}
