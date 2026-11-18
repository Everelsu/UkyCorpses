package com.uky.corpses.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.Reactions;

/** Installed PR DragArmPose, adapted to ModelBiped and the two fixed-function first-person arms. */
public final class DragArmPose {

    private static final Map<UUID, Grab> grabs = new HashMap<>();
    private static boolean renderingFirstPerson;

    private static final class Grab {

        float blend, previous;
        Vec3 grip;

        Grab(float blend, float previous, Vec3 grip) {
            this.blend = blend;
            this.previous = previous;
            this.grip = grip;
        }
    }

    private DragArmPose() {}

    static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            clear();
            return;
        }
        for (Object value : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) value;
            Vec3 grip = Reactions.dragGrip(player, 1);
            if (grip != null) {
                Grab current = grabs.get(player.getUniqueID());
                grabs.put(
                    player.getUniqueID(),
                    new Grab(
                        current == null ? .125F : Math.min(1, current.blend + .125F),
                        current == null ? 0 : current.blend,
                        grip));
            }
        }
        for (Iterator<Map.Entry<UUID, Grab>> it = grabs.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<UUID, Grab> entry = it.next();
            EntityPlayer player = mc.theWorld.func_152378_a(entry.getKey());
            Grab grab = entry.getValue();
            if (player == null || Reactions.dragGrip(player, 1) == null) {
                float decayed = grab.blend - .125F;
                if (decayed <= 0) it.remove();
                else entry.setValue(new Grab(decayed, grab.blend, null));
            }
        }
    }

    static void clear() {
        grabs.clear();
    }

    public static void applyThirdPerson(ModelBiped model, Entity entity) {
        if (renderingFirstPerson || !(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        Grab grab = grabs.get(player.getUniqueID());
        if (grab == null) return;
        Vec3 target = grab.grip;
        if (target == null) {
            double yaw = Math.toRadians(player.renderYawOffset);
            target = Vec3.createVectorHelper(
                player.posX - Math.sin(yaw) * .8,
                player.posY + .62,
                player.posZ + Math.cos(yaw) * .8);
        }
        float[] angle = angles(
            target.xCoord - player.posX,
            player.posY + 1.35 - target.yCoord,
            target.zCoord - player.posZ,
            player.renderYawOffset);
        blendArm(model.bipedRightArm, angle[0], angle[1] - .16F, .14F, grab.blend);
        blendArm(model.bipedLeftArm, angle[0], angle[1] + .16F, -.14F, grab.blend);
    }

    static float[] angles(double dx, double dy, double dz, float bodyYaw) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = Math.max(-1.92F, Math.min(-.42F, (float) (Math.atan2(dy, horizontal) - Math.PI / 2)));
        float yaw = (float) (Math.atan2(-dx, dz) - Math.toRadians(bodyYaw % 360));
        yaw %= (float) (Math.PI * 2);
        if (yaw >= Math.PI) yaw -= (float) (Math.PI * 2);
        if (yaw < -Math.PI) yaw += (float) (Math.PI * 2);
        return new float[] { pitch, Math.max(-.8F, Math.min(.8F, yaw)) };
    }

    private static void blendArm(ModelRenderer arm, float pitch, float yaw, float roll, float blend) {
        arm.rotateAngleX += (pitch - arm.rotateAngleX) * blend;
        arm.rotateAngleY += (yaw - arm.rotateAngleY) * blend;
        arm.rotateAngleZ += (roll - arm.rotateAngleZ) * blend;
    }

    /** Called inside ItemRenderer after EntityRenderer established the hand projection, bob and lightmap. */
    public static boolean renderFirstPerson(float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        Grab grab = grabs.get(mc.thePlayer.getUniqueID());
        if (grab == null || grab.blend <= 0) return false;
        Render render = RenderManager.instance.getEntityRenderObject(mc.thePlayer);
        if (!(render instanceof RenderPlayer)) return false;
        RenderPlayer renderer = (RenderPlayer) render;
        ModelBiped model = renderer.modelBipedMain;
        float raw = Math.max(0, Math.min(1, grab.previous + (grab.blend - grab.previous) * partial));
        float blend = raw * raw * (3 - 2 * raw), onGround = model.onGround;
        int heldRight = model.heldItemRight, heldLeft = model.heldItemLeft;
        boolean sneaking = model.isSneak, bow = model.aimedBow;
        float[] old = { model.bipedRightArm.rotateAngleX, model.bipedRightArm.rotateAngleY,
            model.bipedRightArm.rotateAngleZ, model.bipedLeftArm.rotateAngleX, model.bipedLeftArm.rotateAngleY,
            model.bipedLeftArm.rotateAngleZ };
        renderingFirstPerson = true;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            mc.getTextureManager()
                .bindTexture(
                    com.uky.corpses.stains.client.SkinBlood
                        .texture(mc.thePlayer, 63, ((AbstractClientPlayer) mc.thePlayer).getLocationSkin(), false));
            GL11.glColor4f(1, 1, 1, 1);
            model.onGround = 0;
            model.heldItemRight = model.heldItemLeft = 0;
            model.isSneak = model.aimedBow = false;
            model.setRotationAngles(0, 0, 0, 0, 0, .0625F, mc.thePlayer);
            model.bipedRightArm.rotateAngleX = model.bipedLeftArm.rotateAngleX = 0;
            gripArm(model.bipedRightArm, 1, blend);
            gripArm(model.bipedLeftArm, -1, blend);
        } finally {
            renderingFirstPerson = false;
            model.onGround = onGround;
            model.heldItemRight = heldRight;
            model.heldItemLeft = heldLeft;
            model.isSneak = sneaking;
            model.aimedBow = bow;
            model.bipedRightArm.rotateAngleX = old[0];
            model.bipedRightArm.rotateAngleY = old[1];
            model.bipedRightArm.rotateAngleZ = old[2];
            model.bipedLeftArm.rotateAngleX = old[3];
            model.bipedLeftArm.rotateAngleY = old[4];
            model.bipedLeftArm.rotateAngleZ = old[5];
            GL11.glPopAttrib();
        }
        return true;
    }

    private static void gripArm(ModelRenderer arm, float side, float blend) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                side * (.64F + (.74F - .64F) * blend),
                -.6F + (-.34F + .6F) * blend,
                -.72F + (-.9F + .72F) * blend);
            GL11.glRotatef(side * 45, 0, 1, 0);
            GL11.glTranslatef(side * -1, 3.6F, 3.5F);
            GL11.glRotatef(side * 120, 0, 0, 1);
            GL11.glRotatef(200, 1, 0, 0);
            GL11.glRotatef(side * -135, 0, 1, 0);
            GL11.glTranslatef(side * 5.6F, 0, 0);
            arm.render(.0625F);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
