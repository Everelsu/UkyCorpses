package com.uky.corpses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.common.Loader;

public final class ArmorGameCheck {

    private static boolean done;

    private static final class Probe extends ModelBiped {

        boolean seen;

        @Override
        public void render(Entity entity, float a, float b, float c, float d, float e, float f) {
            setRotationAngles(a, b, c, d, e, f, entity);
            check(
                Math.abs(bipedRightArm.rotateAngleX - .7F) < .00001 && !bipedRightArm.showModel && bipedBody.showModel,
                "Modded biped armor receives the corpse angles and physical part mask after its own animation");
            seen = true;
        }
    }

    public static void drawBarePlayer(Minecraft mc) {
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glTranslatef(64, 20, 0);
            org.lwjgl.opengl.GL11.glScalef(55, 55, 55);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            CorpseClient.renderer.draw(
                mc.thePlayer,
                mc.thePlayer.getLocationSkin(),
                new ItemStack[4],
                null,
                new float[15],
                new com.uky.corpses.corpse.Mat4(),
                0,
                0,
                new float[] { 1, 1, 1 },
                0,
                0,
                0);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    public static void run() throws Exception {
        if (done) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityCorpse corpse = null;
        for (Object e : mc.theWorld.loadedEntityList) if (e instanceof EntityCorpse && mc.thePlayer.getUniqueID()
            .equals(((EntityCorpse) e).physicalOwner())) corpse = (EntityCorpse) e;
        if (corpse == null) return;
        ModelCorpse pose = new ModelCorpse(1);
        pose.ang = new float[15];
        pose.ang[3] = .7F;
        Probe probe = new Probe();
        probe.bipedRightArm.rotateAngleX = .25F;
        ArmorModels.render(probe, pose, 2, corpse, 0, 0);
        check(
            probe.seen && probe.bipedRightArm.showModel && probe.bipedRightArm.rotateAngleX == .25F,
            "Shared armor model visibility and rotations are restored for living entities");
        if (Loader.isModLoaded("hbm")) {
            ItemStack stack = corpse.armor(2);
            ModelBiped model = net.minecraftforge.client.ForgeHooksClient
                .getArmorModel(ArmorModels.owner(corpse, new ItemStack[] { null, null, stack, null }), stack, 1, pose);
            check(
                model.getClass()
                    .getName()
                    .equals("com.hbm.render.model.ModelArmorHEV"),
                "Actual HBM HEV custom model is selected instead of vanilla armor");
            Class<?> stateClass = Class.forName("com.uky.corpses.client.ArmorModels$State");
            java.lang.reflect.Constructor<?> ctor = stateClass
                .getDeclaredConstructor(ModelBiped.class, ModelCorpse.class, int.class);
            ctor.setAccessible(true);
            Object state = ctor.newInstance(model, pose, 2);
            java.lang.reflect.Field active = ArmorModels.class.getDeclaredField("rendering");
            active.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<Object> scope = (ThreadLocal<Object>) active.get(null);
            try {
                scope.set(state);
                model.setRotationAngles(0, 0, 0, 0, 0, .0625F, corpse);
                Object arm = model.getClass()
                    .getField("rightArm")
                    .get(model);
                check(
                    Math.abs(
                        arm.getClass()
                            .getField("rotateAngleX")
                            .getFloat(arm) - .7F)
                        < .00001
                        && !arm.getClass()
                            .getField("doRender")
                            .getBoolean(arm),
                    "Actual HBM OBJ armor obeys corpse pose and hidden/severed limb mask");
            } finally {
                scope.remove();
                java.lang.reflect.Field parts = stateClass.getDeclaredField("parts");
                parts.setAccessible(true);
                for (Object part : (java.util.List<?>) parts.get(state)) {
                    java.lang.reflect.Method restore = part.getClass()
                        .getDeclaredMethod("restore");
                    restore.setAccessible(true);
                    restore.invoke(part);
                }
            }
        }
        done = true;
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("ARMORQA {}", text);
    }

    private ArmorGameCheck() {}
}
