package com.uky.corpses.core;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.stains.client.GearBlood;
import com.uky.corpses.stains.client.SkinBlood;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Renderer-only half of the hooks: this class is reached only after a physical-client guard. */
final class ClientHooks {

    private static final ThreadLocal<Deque<EntityLivingBase>> context = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ResourceLocation> modelTexture = new ThreadLocal<>();

    static void modelBegin(EntityLivingBase entity) {
        context.get()
            .push(entity);
        modelTexture.remove();
        com.uky.corpses.stains.client.ModelWounds.begin(entity);
    }

    static void modelEnd() {
        if (!context.get()
            .isEmpty())
            context.get()
                .pop();
        modelTexture.remove();
    }

    static int modelPart(Object model, float scale) {
        EntityLivingBase entity = context.get()
            .peek();
        if (entity == null || !(model instanceof net.minecraft.client.model.ModelRenderer)
            || !com.uky.corpses.stains.client.ModelWounds.has(entity)) return -1;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        com.uky.corpses.stains.client.ModelWounds
            .part(entity, (net.minecraft.client.model.ModelRenderer) model, scale, modelTexture.get());
        return previous;
    }

    static void modelPartEnd(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    static void init() {
        Events events = new Events();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
    }

    static void copied(Object original, Object copy) {
        if (original instanceof ItemStack && copy instanceof ItemStack)
            GearBlood.copied((ItemStack) original, (ItemStack) copy);
    }

    static boolean playerRenderPre(Object bus, Object event) {
        ModelBiped model = ((RenderPlayerEvent.Pre) event).renderer.modelBipedMain;
        ModelRenderer[] parts = { model.bipedHead, model.bipedHeadwear, model.bipedBody, model.bipedRightArm,
            model.bipedLeftArm, model.bipedRightLeg, model.bipedLeftLeg };
        int hidden = 0;
        for (int index = 0; index < parts.length; index++)
            if (parts[index] != null && parts[index].isHidden) hidden |= 1 << index;
        boolean canceled = true;
        try {
            canceled = ((EventBus) bus).post((Event) event);
            return canceled;
        } finally {
            if (canceled) for (int index = 0; index < parts.length; index++)
                if (parts[index] != null) parts[index].isHidden = (hidden & 1 << index) != 0;
        }
    }

    static boolean dragHands(float partial) {
        return com.uky.corpses.client.DragArmPose.renderFirstPerson(partial);
    }

    static void dragArms(Object model, Object entity) {
        if (com.uky.corpses.client.ArmorModels.apply(model)) return;
        if (model instanceof net.minecraft.client.model.ModelBiped && entity instanceof net.minecraft.entity.Entity) {
            com.uky.corpses.client.DragArmPose
                .applyThirdPerson((net.minecraft.client.model.ModelBiped) model, (net.minecraft.entity.Entity) entity);
            com.uky.corpses.amp.AmpClient
                .apply((net.minecraft.client.model.ModelBiped) model, (net.minecraft.entity.Entity) entity);
        }
    }

    static Object texture(Object value) {
        if (!(value instanceof ResourceLocation) || context.get()
            .isEmpty()) return value;
        ResourceLocation resource = (ResourceLocation) value;
        modelTexture.set(resource);
        if (!entityTexture(resource)) return value;
        boolean armor = resource.getResourcePath()
            .contains("textures/models/armor/");
        return SkinBlood.texture(
            context.get()
                .peek(),
            63,
            resource,
            armor);
    }

    static boolean entityTexture(ResourceLocation resource) {
        String path = resource.getResourcePath();
        if (path.contains("ukycorpses_blood_wound") || path.contains("ukycorpses_blood_weapon")) return false;
        return path.startsWith("textures/entity/") || path.startsWith("skins/")
            || path.contains("textures/models/armor/");
    }

    static void inventoryOverlay(Object value, int x, int y) {
        if (value instanceof ItemStack) GearBlood.drawGui((ItemStack) value, x, y);
    }

    static void heldOverlay(Object value) {
        // The Forge item matrix is still active at this hook.
        if (value instanceof ItemStack) GearBlood.drawHeld((ItemStack) value);
    }

    static void firstPersonArm(Object value) {
        if (!(value instanceof net.minecraft.client.entity.AbstractClientPlayer)) return;
        net.minecraft.client.entity.AbstractClientPlayer player = (net.minecraft.client.entity.AbstractClientPlayer) value;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(
                SkinBlood.texture(player, 1 << com.uky.corpses.corpse.Skeleton.RARM, player.getLocationSkin(), false));
    }

    public static final class Events {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void before(RenderLivingEvent.Pre event) {
            Deque<EntityLivingBase> stack = context.get();
            if (stack.size() >= 16) stack.clear();
            stack.push(event.entity);
            modelTexture.remove();
            com.uky.corpses.stains.client.ModelWounds.begin(event.entity);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void after(RenderLivingEvent.Post event) {
            Deque<EntityLivingBase> stack = context.get();
            if (!stack.isEmpty() && stack.peek() == event.entity) stack.pop();
            else stack.remove(event.entity);
        }

        @SubscribeEvent
        public void frame(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) com.uky.corpses.stains.client.ModelWounds.frame();
            if (event.phase == TickEvent.Phase.START) context.get()
                .clear();
        }
    }

    private ClientHooks() {}
}
