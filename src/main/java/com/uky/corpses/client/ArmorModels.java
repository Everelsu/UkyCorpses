package com.uky.corpses.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.mojang.authlib.GameProfile;
import com.uky.corpses.EntityCorpse;

/** Keeps a mod's armor geometry and textures while supplying the corpse's current limb pose. */
public final class ArmorModels {

    private static final ThreadLocal<State> rendering = new ThreadLocal<>();
    private static final Map<EntityCorpse, Proxy> owners = new WeakHashMap<>();
    private static final Map<Class<?>, Field[]> objFields = new WeakHashMap<>();
    private static final String[] OBJ_PARTS = { "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg",
        "rightFoot", "leftFoot" };
    private static final int[] OBJ_INDEX = { 0, 1, 2, 3, 4, 5, 4, 5 };

    private static final class Proxy {

        EntityOtherPlayerMP player;
        long tick = Long.MIN_VALUE;
    }

    public static EntityLivingBase owner(Entity entity, ItemStack[] armor) {
        if (entity instanceof EntityLivingBase) return (EntityLivingBase) entity;
        EntityCorpse corpse = (EntityCorpse) entity;
        Proxy proxy = owners.get(corpse);
        if (proxy == null) {
            proxy = new Proxy();
            proxy.player = new EntityOtherPlayerMP(
                entity.worldObj,
                new GameProfile(
                    corpse.physicalOwner() == null ? corpse.getUniqueID() : corpse.physicalOwner(),
                    corpse.owner()));
            owners.put(corpse, proxy);
        }
        if (proxy.tick != entity.worldObj.getTotalWorldTime()) {
            proxy.tick = entity.worldObj.getTotalWorldTime();
            for (int i = 0; i < 4; i++) proxy.player.inventory.armorInventory[i] = ItemStack.copyItemStack(armor[i]);
            proxy.player.setPosition(entity.posX, entity.posY, entity.posZ);
        }
        return proxy.player;
    }

    public static boolean apply(Object model) {
        State state = rendering.get();
        if (state == null || state.model != model) return false;
        for (Part part : state.parts) part.apply(state.pose, state.mask);
        return true;
    }

    public static void render(ModelBiped model, ModelCorpse pose, int mask, Entity entity, float swing, float amount) {
        pose.setRotationAngles(swing, amount, 0, 0, 0, .0625F, entity);
        State previous = rendering.get(), state = new State(model, pose, mask);
        rendering.set(state);
        try {
            model.isChild = false;
            apply(model);
            model.render(entity, swing, amount, 0, 0, 0, .0625F);
        } finally {
            for (Part part : state.parts) part.restore();
            model.isChild = state.child;
            rendering.set(previous);
        }
    }

    private static ModelRenderer[] biped(ModelBiped m) {
        return new ModelRenderer[] { m.bipedHead, m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg,
            m.bipedLeftLeg, m.bipedHeadwear };
    }

    private static final class State {

        final ModelBiped model;
        final ModelRenderer[] pose;
        final int mask;
        final boolean child;
        final List<Part> parts = new ArrayList<>();

        State(ModelBiped model, ModelCorpse source, int mask) {
            this.model = model;
            this.pose = biped(source);
            this.mask = mask;
            child = model.isChild;
            ModelRenderer[] vanilla = biped(model);
            for (int i = 0; i < vanilla.length; i++) parts.add(new Part(vanilla[i], i == 6 ? 0 : i));
            if (isHbm(model.getClass())) for (int i = 0; i < OBJ_PARTS.length; i++) {
                try {
                    parts.add(
                        new Part(
                            model.getClass()
                                .getField(OBJ_PARTS[i])
                                .get(model),
                            OBJ_INDEX[i]));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Unsupported HBM armor part: " + OBJ_PARTS[i], failure);
                }
            }
        }
    }

    private static boolean isHbm(Class<?> type) {
        for (; type != null; type = type.getSuperclass()) if (type.getName()
            .equals("com.hbm.render.model.ModelArmorBase")) return true;
        return false;
    }

    private static final class Part {

        final Object object;
        final int index;
        float x, y, z;
        boolean visible;
        Field[] fields;

        Part(Object object, int index) {
            this.object = object;
            this.index = index;
            if (object instanceof ModelRenderer) {
                ModelRenderer p = (ModelRenderer) object;
                x = p.rotateAngleX;
                y = p.rotateAngleY;
                z = p.rotateAngleZ;
                visible = p.showModel;
            } else try {
                fields = objFields.get(object.getClass());
                if (fields == null) {
                    fields = new Field[] { object.getClass()
                        .getField("rotateAngleX"),
                        object.getClass()
                            .getField("rotateAngleY"),
                        object.getClass()
                            .getField("rotateAngleZ"),
                        object.getClass()
                            .getField("doRender") };
                    objFields.put(object.getClass(), fields);
                }
                x = fields[0].getFloat(object);
                y = fields[1].getFloat(object);
                z = fields[2].getFloat(object);
                visible = fields[3].getBoolean(object);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Unsupported armor geometry", failure);
            }
        }

        void apply(ModelRenderer[] pose, int mask) {
            ModelRenderer p = pose[index];
            set(p.rotateAngleX, p.rotateAngleY, p.rotateAngleZ, visible && (mask & 1 << index) != 0);
        }

        void restore() {
            set(x, y, z, visible);
        }

        void set(float ax, float ay, float az, boolean show) {
            if (object instanceof ModelRenderer) {
                ModelRenderer p = (ModelRenderer) object;
                p.rotateAngleX = ax;
                p.rotateAngleY = ay;
                p.rotateAngleZ = az;
                p.showModel = show;
            } else try {
                fields[0].setFloat(object, ax);
                fields[1].setFloat(object, ay);
                fields[2].setFloat(object, az);
                fields[3].setBoolean(object, show);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private ArmorModels() {}
}
