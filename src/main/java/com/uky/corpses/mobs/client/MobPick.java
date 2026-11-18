package com.uky.corpses.mobs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITickableTextureObject;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.mobs.EntityMobRagdoll;
import com.uky.corpses.mobs.MsgMobHit;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Refines the normal pick against physical boxes, so empty space between long mob limbs stays clickable. */
public final class MobPick extends AbstractTexture implements ITickableTextureObject {

    public static void init() {
        MobPick pick = new MobPick();
        Minecraft.getMinecraft()
            .getTextureManager()
            .loadTickableTexture(new ResourceLocation("ukycorpses", "mob_pick"), pick);
        MinecraftForge.EVENT_BUS.register(pick);
    }

    @Override
    public void loadTexture(IResourceManager manager) {}

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
            && !(mc.objectMouseOver.entityHit instanceof EntityMobRagdoll)) return;
        Vec3 eye = mc.thePlayer.getPosition(1), look = mc.thePlayer.getLook(1);
        double reach = mc.thePlayer.capabilities.isCreativeMode ? 5 : 3;
        MovingObjectPosition block = mc.theWorld
            .rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        double nearest = block == null ? reach : eye.distanceTo(block.hitVec);
        EntityMobRagdoll chosen = null;
        double[] at = new double[3], point = null;
        for (Object object : mc.theWorld.loadedEntityList)
            if (object instanceof EntityMobRagdoll && !((EntityMobRagdoll) object).isDead) {
                EntityMobRagdoll body = (EntityMobRagdoll) object;
                if (body.ray(
                    new double[] { eye.xCoord, eye.yCoord, eye.zCoord },
                    new double[] { look.xCoord, look.yCoord, look.zCoord },
                    nearest,
                    at) >= 0) {
                    double dx = at[0] - eye.xCoord, dy = at[1] - eye.yCoord, dz = at[2] - eye.zCoord;
                    nearest = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    chosen = body;
                    point = at.clone();
                }
            }
        if (chosen != null) {
            mc.objectMouseOver = new MovingObjectPosition(
                chosen,
                Vec3.createVectorHelper(point[0], point[1], point[2]));
            mc.pointedEntity = chosen;
        } else if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityMobRagdoll) {
            mc.objectMouseOver = block;
            mc.pointedEntity = null;
        }
    }

    @SubscribeEvent
    public void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!event.buttonstate || event.button != 0
            || mc.currentScreen != null
            || mc.thePlayer == null
            || mc.objectMouseOver == null
            || !(mc.objectMouseOver.entityHit instanceof EntityMobRagdoll)) return;
        UkyCorpses.net.sendToServer(new MsgMobHit((EntityMobRagdoll) mc.objectMouseOver.entityHit));
        mc.thePlayer.swingItem();
        event.setCanceled(true);
    }
}
