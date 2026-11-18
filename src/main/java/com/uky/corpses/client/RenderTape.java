package com.uky.corpses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.UkyCorpses;

/** A real 14x9x2 cassette in hand and on the ground; the inventory keeps the flat icon, which reads better. */
public class RenderTape implements IItemRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(UkyCorpses.MODID, "textures/models/tape.png");
    private final ModelRenderer box;

    public RenderTape() {
        ModelBase base = new ModelBase() {};
        base.textureWidth = 32;
        base.textureHeight = 16;
        box = new ModelRenderer(base, 0, 0);
        box.addBox(-7F, -4.5F, -1F, 14, 9, 2);
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        // in hand Forge sets up the same space vanilla draws flat items in: a 0..1 square, 1/16 deep behind z=0
        if (type != ItemRenderType.ENTITY) GL11.glTranslatef(0.5F, 0.5F, -0.03125F);
        GL11.glRotatef(180F, 1F, 0F, 0F); // model space is upside down
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TEXTURE);
        box.render(0.0625F);
        GL11.glPopMatrix();
    }
}
