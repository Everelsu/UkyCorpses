package com.uky.corpses.mobs.client;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;

import com.uky.corpses.mobs.EntityMobRagdoll;
import com.uky.corpses.mobs.MobRagdolls;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Per-part renderer for the server-owned visual corpse; ordinary loot remains vanilla. */
public final class RenderMobRagdoll extends Render {

    public RenderMobRagdoll() {
        shadowSize = .4F;
    }

    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityMobRagdoll.class, new RenderMobRagdoll());
        MinecraftForge.EVENT_BUS.register(new SuppressSource());
        MobMeshes.init();
        MobPick.init();
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityMobRagdoll body = (EntityMobRagdoll) entity;
        MobMeshes.render(
            body.visualEntity(),
            body.physicalTransforms(partial),
            x,
            y,
            z,
            body.meshScale(),
            partial,
            body.hiddenMask);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    public static final class SuppressSource {

        @SubscribeEvent
        public void before(RenderLivingEvent.Pre event) {
            if (MobRagdolls.hasBody(event.entity)) event.setCanceled(true);
        }
    }
}
