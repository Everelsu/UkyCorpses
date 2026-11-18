package com.uky.corpses.mobs.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.vecmath.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBat;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBlaze;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelChicken;
import net.minecraft.client.model.ModelCreeper;
import net.minecraft.client.model.ModelDragon;
import net.minecraft.client.model.ModelGhast;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.client.model.ModelIronGolem;
import net.minecraft.client.model.ModelMagmaCube;
import net.minecraft.client.model.ModelOcelot;
import net.minecraft.client.model.ModelQuadruped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSheep1;
import net.minecraft.client.model.ModelSilverfish;
import net.minecraft.client.model.ModelSlime;
import net.minecraft.client.model.ModelSnowMan;
import net.minecraft.client.model.ModelSpider;
import net.minecraft.client.model.ModelSquid;
import net.minecraft.client.model.ModelVillager;
import net.minecraft.client.model.ModelWither;
import net.minecraft.client.model.ModelWolf;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.mobs.MobTypes;
import com.uky.corpses.mobs.physics.MobRigType;
import com.uky.corpses.port.physics.PhysTransform;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

/** Draws the actual 1.7 model boxes at the original factory's independent body transforms. */
public final class MobMeshes {

    private static final Map<EntityLivingBase, Prepared> cache = new WeakHashMap<>();
    private static final Map<Class<?>, Field[]> privateFields = new IdentityHashMap<>();
    private static Method textureMethod;
    private static final FloatBuffer matrix = GLAllocation.createDirectFloatBuffer(16);
    private static final ModelBiped armorOuter = new ModelBiped(1), armorInner = new ModelBiped(.5F);

    static final class Mesh {

        final ModelRenderer part, anchor;
        final int body;
        final float rx, scale, ox, oy, oz;
        final boolean mirror;

        Mesh(ModelRenderer part, ModelRenderer anchor, int body, float rx, float scale, float ox, float oy, float oz,
            boolean mirror) {
            this.part = part;
            this.anchor = anchor;
            this.body = body;
            this.rx = rx;
            this.scale = scale;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.mirror = mirror;
        }
    }

    private static final class Prepared {

        ModelBase model;
        ResourceLocation texture;
        List<Mesh> meshes;
    }

    public static void init() {
        if (Minecraft.getMinecraft()
            .getResourceManager() instanceof net.minecraft.client.resources.IReloadableResourceManager)
            ((net.minecraft.client.resources.IReloadableResourceManager) Minecraft.getMinecraft()
                .getResourceManager()).registerReloadListener(manager -> clear());
    }

    public static void clear() {
        cache.clear();
    }

    public static com.uky.corpses.mobs.physics.MobPose capture(EntityLivingBase visual) {
        Render renderer = RenderManager.instance.getEntityRenderObject(visual);
        if (!(renderer instanceof RendererLivingEntity)) return null;
        ModelBase model = ObfuscationReflectionHelper
            .getPrivateValue(RendererLivingEntity.class, (RendererLivingEntity) renderer, "mainModel", "field_77045_g");
        model.isChild = visual.isChild();
        model.setLivingAnimations(visual, visual.limbSwing, visual.limbSwingAmount, 1);
        model.setRotationAngles(
            visual.limbSwing,
            visual.limbSwingAmount,
            visual.ticksExisted,
            visual.rotationYawHead - visual.renderYawOffset,
            visual.rotationPitch,
            .0625F,
            visual);
        Map<com.uky.corpses.mobs.physics.PhysicsPart, com.uky.corpses.mobs.physics.MobPose.PartPose> poses = new java.util.EnumMap<>(
            com.uky.corpses.mobs.physics.PhysicsPart.class);
        for (Mesh mesh : layout(model, MobTypes.type(visual), visual.isChild(), MobTypes.meshScale(visual), visual)) {
            com.uky.corpses.mobs.physics.PhysicsPart part = com.uky.corpses.mobs.physics.PhysicsPart.byIndex(mesh.body);
            if (part != null && !poses.containsKey(part)) poses.put(
                part,
                new com.uky.corpses.mobs.physics.MobPose.PartPose(
                    mesh.part.rotateAngleX,
                    mesh.part.rotateAngleY,
                    mesh.part.rotateAngleZ));
        }
        return new com.uky.corpses.mobs.physics.MobPose(poses);
    }

    public static boolean render(EntityLivingBase visual, PhysTransform[] parts, double x, double y, double z,
        float meshScale, float partial, int hiddenMask) {
        if (visual == null || parts == null || parts.length == 0) return false;
        Render renderer = RenderManager.instance.getEntityRenderObject(visual);
        if (!(renderer instanceof RendererLivingEntity)) return false;
        try {
            ModelBase model = ObfuscationReflectionHelper.getPrivateValue(
                RendererLivingEntity.class,
                (RendererLivingEntity) renderer,
                "mainModel",
                "field_77045_g");
            Prepared prepared = cache.get(visual);
            if (prepared == null || prepared.model != model) {
                prepared = new Prepared();
                prepared.model = model;
                model.isChild = visual.isChild();
                model.isRiding = false;
                model.onGround = 0;
                model.setLivingAnimations(visual, 0, 0, partial);
                model.setRotationAngles(0, 0, 0, 0, 0, .0625F, visual);
                prepared.meshes = layout(model, MobTypes.type(visual), visual.isChild(), meshScale, visual);
                prepared.texture = texture(renderer, visual);
                cache.put(visual, prepared);
            }
            if (prepared.meshes.isEmpty()) return false;
            com.uky.corpses.core.Hooks.modelBegin(visual);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            float oldLightX = OpenGlHelper.lastBrightnessX, oldLightY = OpenGlHelper.lastBrightnessY;
            try {
                GL11.glTranslated(x, y, z);
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                GL11.glColor4f(1, 1, 1, 1);
                bind(prepared.texture);
                draw(prepared.meshes, parts, hiddenMask);
                if (model instanceof ModelBiped) armor(visual, parts, meshScale, hiddenMask);
                if (visual instanceof EntitySheep && !((EntitySheep) visual).getSheared()) {
                    float[] color = EntitySheep.fleeceColorTable[((EntitySheep) visual).getFleeceColor()];
                    GL11.glColor4f(color[0], color[1], color[2], 1);
                    bind(new ResourceLocation("minecraft", "textures/entity/sheep/sheep_fur.png"));
                    draw(
                        layout(new ModelSheep1(), MobRigType.QUADRUPED, visual.isChild(), meshScale, visual),
                        parts,
                        hiddenMask);
                }
                if (MobTypes.type(visual) == MobRigType.SPIDER || MobTypes.type(visual) == MobRigType.ENDERMAN) {
                    bind(
                        new ResourceLocation(
                            "minecraft",
                            MobTypes.type(visual) == MobRigType.SPIDER ? "textures/entity/spider_eyes.png"
                                : "textures/entity/enderman/enderman_eyes.png"));
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                    GL11.glDepthMask(false);
                    GL11.glColor4f(1, 1, 1, 1);
                    draw(prepared.meshes, parts, hiddenMask);
                }
            } finally {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldLightX, oldLightY);
                GL11.glPopMatrix();
                GL11.glPopAttrib();
                com.uky.corpses.core.Hooks.modelEnd();
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            UkyCorpses.LOG.warn(
                "Cannot draw physical mob {}",
                visual.getClass()
                    .getName(),
                failure);
            return false;
        }
    }

    private static void bind(ResourceLocation texture) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture((ResourceLocation) com.uky.corpses.core.Hooks.texture(texture));
    }

    private static ResourceLocation texture(Render renderer, Entity visual) throws ReflectiveOperationException {
        if (textureMethod == null) {
            for (String name : new String[] { "getEntityTexture", "func_110775_a" }) try {
                textureMethod = Render.class.getDeclaredMethod(name, Entity.class);
                textureMethod.setAccessible(true);
                break;
            } catch (NoSuchMethodException missing) {}
            if (textureMethod == null) throw new NoSuchMethodException("Render.getEntityTexture");
        }
        return (ResourceLocation) textureMethod.invoke(renderer, visual);
    }

    private static void armor(EntityLivingBase visual, PhysTransform[] parts, float scale, int hiddenMask) {
        for (int pass = 0; pass < 4; pass++) {
            ItemStack stack = visual.getEquipmentInSlot(4 - pass);
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) continue;
            ModelBiped model = pass == 2 ? armorInner : armorOuter;
            model.isChild = false;
            List<Mesh> meshes = new ArrayList<>();
            if (pass == 0) add(meshes, model.bipedHead, 1, 0, scale);
            if (pass == 1) {
                add(meshes, model.bipedBody, 0, 0, scale);
                add(meshes, model.bipedLeftArm, 4, 0, scale);
                add(meshes, model.bipedRightArm, 5, 0, scale);
            }
            if (pass == 2) {
                add(meshes, model.bipedBody, 0, 0, scale);
                add(meshes, model.bipedLeftLeg, 2, 0, scale);
                add(meshes, model.bipedRightLeg, 3, 0, scale);
            }
            if (pass == 3) {
                add(meshes, model.bipedLeftLeg, 2, 0, scale);
                add(meshes, model.bipedRightLeg, 3, 0, scale);
            }
            bind(RenderBiped.getArmorResource(visual, stack, pass, null));
            int color = ((ItemArmor) stack.getItem()).getColor(stack);
            if (color != -1)
                GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1);
            else GL11.glColor4f(1, 1, 1, 1);
            draw(meshes, parts, hiddenMask);
            if (color != -1) {
                bind(RenderBiped.getArmorResource(visual, stack, pass, "overlay"));
                GL11.glColor4f(1, 1, 1, 1);
                draw(meshes, parts, hiddenMask);
            }
        }
    }

    private static void add(List<Mesh> out, ModelRenderer part, int body, float rx, float scale) {
        if (part != null) out.add(new Mesh(part, part, body, rx, scale, 0, 0, 0, false));
    }

    private static void attach(List<Mesh> out, ModelRenderer part, ModelRenderer anchor, int body, float rx,
        float scale) {
        if (part != null) out.add(new Mesh(part, anchor, body, rx, scale, 0, 0, 0, false));
    }

    private static void shifted(List<Mesh> out, ModelRenderer part, int body, float rx, float scale, float x, float y,
        float z) {
        if (part != null) out.add(new Mesh(part, part, body, rx, scale, x, y, z, false));
    }

    static List<Mesh> layout(ModelBase model, MobRigType type, boolean baby, float scale, EntityLivingBase visual) {
        List<Mesh> out = new ArrayList<>();
        float halfPi = (float) Math.PI / 2, bodyScale = scale * (baby ? .5F : 1), headScale = scale * (baby ? .75F : 1);
        if (model instanceof ModelBiped) {
            ModelBiped m = (ModelBiped) model;
            add(out, m.bipedBody, 0, 0, bodyScale);
            add(out, m.bipedHead, 1, 0, baby ? scale : headScale);
            attach(out, m.bipedHeadwear, m.bipedHead, 1, 0, baby ? scale : headScale);
            add(out, m.bipedLeftLeg, 2, 0, bodyScale);
            add(out, m.bipedRightLeg, 3, 0, bodyScale);
            add(out, m.bipedLeftArm, 4, 0, bodyScale);
            add(out, m.bipedRightArm, 5, 0, bodyScale);
        } else if (model instanceof ModelCreeper) {
            ModelCreeper m = (ModelCreeper) model;
            add(out, m.body, 0, 0, scale);
            add(out, m.head, 1, 0, scale);
            add(out, m.leg2, 2, 0, scale);
            add(out, m.leg1, 3, 0, scale);
            add(out, m.leg4, 4, 0, scale);
            add(out, m.leg3, 5, 0, scale);
        } else if (model instanceof ModelQuadruped) {
            ModelQuadruped m = (ModelQuadruped) model;
            add(out, m.body, 0, halfPi, bodyScale);
            add(out, m.head, 1, 0, scale);
            add(out, m.leg2, 2, 0, bodyScale);
            add(out, m.leg1, 3, 0, bodyScale);
            add(out, m.leg4, 4, 0, bodyScale);
            add(out, m.leg3, 5, 0, bodyScale);
        } else if (model instanceof ModelChicken) {
            ModelChicken m = (ModelChicken) model;
            add(out, m.body, 0, halfPi, bodyScale);
            add(out, m.head, 1, 0, scale);
            attach(out, m.bill, m.head, 1, 0, scale);
            attach(out, m.chin, m.head, 1, 0, scale);
            add(out, m.leftLeg, 2, 0, bodyScale);
            add(out, m.rightLeg, 3, 0, bodyScale);
            add(out, m.leftWing, 4, 0, bodyScale);
            add(out, m.rightWing, 5, 0, bodyScale);
        } else if (model instanceof ModelVillager) {
            ModelVillager m = (ModelVillager) model;
            add(out, m.villagerBody, 0, 0, bodyScale);
            add(out, m.villagerHead, 1, 0, headScale);
            add(out, m.leftVillagerLeg, 2, 0, bodyScale);
            add(out, m.rightVillagerLeg, 3, 0, bodyScale);
            attach(out, m.villagerArms, m.villagerBody, 0, -.75F, bodyScale);
        } else if (model instanceof ModelIronGolem) {
            ModelIronGolem m = (ModelIronGolem) model;
            add(out, m.ironGolemBody, 0, 0, scale);
            add(out, m.ironGolemHead, 1, 0, scale);
            add(out, m.ironGolemLeftLeg, 2, 0, scale);
            add(out, m.ironGolemRightLeg, 3, 0, scale);
            add(out, m.ironGolemLeftArm, 4, 0, scale);
            add(out, m.ironGolemRightArm, 5, 0, scale);
        } else if (model instanceof ModelWolf) {
            ModelWolf m = (ModelWolf) model;
            add(out, m.wolfBody, 0, halfPi, bodyScale);
            attach(out, field(m, ModelWolf.class, 7), m.wolfBody, 0, halfPi, bodyScale);
            add(out, m.wolfHeadMain, 1, 0, headScale);
            add(out, m.wolfLeg2, 2, 0, bodyScale);
            add(out, m.wolfLeg1, 3, 0, bodyScale);
            add(out, m.wolfLeg4, 4, 0, bodyScale);
            add(out, m.wolfLeg3, 5, 0, bodyScale);
            attach(out, field(m, ModelWolf.class, 6), m.wolfBody, 0, 0, bodyScale);
        } else if (model instanceof ModelOcelot) {
            ModelOcelot m = (ModelOcelot) model;
            ModelRenderer body = field(m, ModelOcelot.class, 7);
            add(out, body, 0, halfPi, bodyScale * .8F);
            add(out, field(m, ModelOcelot.class, 6), 1, 0, headScale * .8F);
            for (int i = 0; i < 4; i++) add(out, field(m, ModelOcelot.class, i), i + 2, 0, bodyScale * .8F);
            attach(out, field(m, ModelOcelot.class, 4), body, 0, 0, bodyScale * .8F);
            attach(out, field(m, ModelOcelot.class, 5), body, 0, 0, bodyScale * .8F);
        } else if (model instanceof ModelBat) {
            ModelBat m = (ModelBat) model;
            add(out, field(m, ModelBat.class, 1), 0, 0, scale);
            add(out, field(m, ModelBat.class, 0), 1, 0, scale);
            add(out, field(m, ModelBat.class, 3), 4, 0, scale);
            add(out, field(m, ModelBat.class, 2), 5, 0, scale);
        } else if (model instanceof ModelSpider) {
            ModelSpider m = (ModelSpider) model;
            float s = visual instanceof EntityCaveSpider ? .7F : scale;
            shifted(out, m.spiderNeck, 0, 0, s, 0, 0, -.375F * s);
            shifted(out, m.spiderBody, 0, 0, s, 0, 0, .1875F * s);
            add(out, m.spiderHead, 1, 0, s);
            ModelRenderer[] legs = { m.spiderLeg2, m.spiderLeg1, m.spiderLeg4, m.spiderLeg3, m.spiderLeg6, m.spiderLeg5,
                m.spiderLeg8, m.spiderLeg7 };
            for (int i = 0; i < 8; i++) add(out, legs[i], i + 2, 0, s);
        } else if (model instanceof ModelGhast) {
            add(out, field(model, ModelGhast.class, 0), 0, 0, 4.5F);
            ModelRenderer[] legs = array(model, ModelGhast.class, 1);
            for (int i = 0; i < legs.length; i++) add(out, legs[i], i + 1, 0, 4.5F);
        } else if (model instanceof ModelSquid) {
            add(out, field(model, ModelSquid.class, 0), 0, 0, scale);
            ModelRenderer[] legs = array(model, ModelSquid.class, 1);
            for (int i = 0; i < legs.length; i++) add(out, legs[i], i + 1, 0, scale);
        } else if (model instanceof ModelSnowMan) {
            ModelSnowMan m = (ModelSnowMan) model;
            add(out, m.body, 0, 0, scale);
            add(out, m.head, 1, 0, scale);
            add(out, m.bottomBody, 2, 0, scale);
            attach(out, m.leftHand, m.body, 0, 0, scale);
            attach(out, m.rightHand, m.body, 0, 0, scale);
        } else if (model instanceof ModelBlaze) {
            add(out, field(model, ModelBlaze.class, 1), 0, 0, scale);
        } else if (model instanceof ModelSlime) {
            ModelRenderer body = field(model, ModelSlime.class, 0);
            add(out, body, 0, 0, scale);
            for (int i = 1; i <= 3; i++) attach(out, field(model, ModelSlime.class, i), body, 0, 0, scale);
        } else if (model instanceof ModelMagmaCube) {
            ModelRenderer core = field(model, ModelMagmaCube.class, 1);
            ModelRenderer[] segments = array(model, ModelMagmaCube.class, 0);
            for (ModelRenderer part : segments) attach(out, part, core, 0, 0, scale);
            add(out, core, 0, 0, scale);
        } else if (model instanceof ModelSilverfish) {
            ModelRenderer[] body = array(model, ModelSilverfish.class, 0),
                wings = array(model, ModelSilverfish.class, 1);
            shifted(out, body[0], 1, 0, scale, 0, -.03125F, -.0625F);
            shifted(out, body[1], 1, 0, scale, 0, 0, .0625F);
            add(out, body[2], 0, 0, scale);
            for (int i = 3; i < 7; i++) add(out, body[i], i - 1, 0, scale);
            shifted(out, wings[0], 0, 0, scale, 0, .125F, 0);
            shifted(out, wings[1], 3, 0, scale, 0, .0625F, 0);
            shifted(out, wings[2], 1, 0, scale, 0, .0625F, .0625F);
        } else if (model instanceof ModelWither) {
            ModelRenderer[] body = array(model, ModelWither.class, 0), heads = array(model, ModelWither.class, 1);
            shifted(out, body[0], 0, 0, 2, 0, .625F, 0);
            shifted(out, body[1], 0, 0, 2, .0625F, -.1875F, 0);
            add(out, body[2], 2, 0, 2);
            add(out, heads[0], 1, 0, 2);
            add(out, heads[1], 4, 0, 2);
            add(out, heads[2], 5, 0, 2);
        } else if (model instanceof ModelHorse) horse(out, (ModelHorse) model, visual, bodyScale, headScale);
        else if (model instanceof ModelDragon) dragon(out, (ModelDragon) model);
        return out;
    }

    private static ModelRenderer field(Object instance, Class<?> owner, int index) {
        return (ModelRenderer) value(instance, owner, index);
    }

    private static ModelRenderer[] array(Object instance, Class<?> owner, int index) {
        return (ModelRenderer[]) value(instance, owner, index);
    }

    private static Object value(Object instance, Class<?> owner, int index) {
        try {
            Field[] fields = privateFields.get(owner);
            if (fields == null) {
                fields = owner.getDeclaredFields();
                for (Field f : fields) f.setAccessible(true);
                privateFields.put(owner, fields);
            }
            return fields[index].get(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Cannot access vanilla model field " + owner.getSimpleName() + "#" + index,
                e);
        }
    }

    private static void horse(List<Mesh> out, ModelHorse model, EntityLivingBase visual, float bodyScale,
        float headScale) {
        EntityHorse horse = visual instanceof EntityHorse ? (EntityHorse) visual : null;
        float species = horse == null ? 1 : horse.getHorseType() == 1 ? .87F : horse.getHorseType() == 2 ? .92F : 1.1F;
        bodyScale *= species;
        headScale *= species;
        ModelRenderer body = field(model, ModelHorse.class, 10), head = field(model, ModelHorse.class, 0);
        add(out, body, 0, 0, bodyScale);
        add(out, head, 1, (float) Math.PI / 6, headScale);
        for (int i : new int[] { 1, 2, 7, 9 })
            attach(out, field(model, ModelHorse.class, i), head, 1, (float) Math.PI / 6, headScale);
        int[] ears = horse != null && (horse.getHorseType() == 1 || horse.getHorseType() == 2) ? new int[] { 5, 6 }
            : new int[] { 3, 4 };
        for (int i : ears) attach(out, field(model, ModelHorse.class, i), head, 1, (float) Math.PI / 6, headScale);
        for (int i : new int[] { 11, 12, 13 }) attach(out, field(model, ModelHorse.class, i), body, 0, 0, bodyScale);
        int[] legRoots = { 14, 17, 20, 23 };
        for (int i = 0; i < 4; i++) {
            ModelRenderer leg = field(model, ModelHorse.class, legRoots[i]);
            add(out, leg, i + 2, 0, bodyScale);
            attach(out, field(model, ModelHorse.class, legRoots[i] + 1), leg, i + 2, 0, bodyScale);
            attach(out, field(model, ModelHorse.class, legRoots[i] + 2), leg, i + 2, 0, bodyScale);
        }
        if (horse != null && horse.isChested())
            for (int i : new int[] { 26, 27 }) attach(out, field(model, ModelHorse.class, i), body, 0, 0, bodyScale);
        if (horse != null && horse.isHorseSaddled()) {
            for (int i = 28; i <= 30; i++) attach(out, field(model, ModelHorse.class, i), body, 0, 0, bodyScale);
            for (int i = 35; i <= 36; i++)
                attach(out, field(model, ModelHorse.class, i), head, 1, (float) Math.PI / 6, headScale);
        }
    }

    private static void dragon(List<Mesh> out, ModelDragon model) {
        ModelRenderer head = field(model, ModelDragon.class, 0), spine = field(model, ModelDragon.class, 1),
            body = field(model, ModelDragon.class, 3);
        add(out, body, 0, 0, 1);
        add(out, head, 1, 0, 1);
        attach(out, field(model, ModelDragon.class, 2), head, 1, 0, 1);
        int[] bodyIndices = { 2, 3, 6, 7 };
        for (int i = 0; i < 4; i++) {
            boolean hind = i >= 2;
            ModelRenderer leg = field(model, ModelDragon.class, hind ? 4 : 5);
            float oy = hind ? 1.28125F : .84375F, oz = hind ? .375F : .40625F;
            out.add(new Mesh(leg, leg, bodyIndices[i], 0, 1, 0, oy, oz, (i & 1) != 0));
        }
        ModelRenderer wing = field(model, ModelDragon.class, 10);
        out.add(new Mesh(wing, wing, 4, 0, 1, 0, 0, 0, false));
        out.add(new Mesh(wing, wing, 5, 0, 1, 0, 0, 0, true));
        for (int i = 0; i < 5; i++) shifted(out, spine, 8, 0, 1, 0, 0, 1.25F - i * .625F);
        for (int i = 0; i < 12; i++) shifted(out, spine, 9, 0, 1, 0, 0, 3.4375F - i * .625F);
    }

    private static void draw(List<Mesh> meshes, PhysTransform[] parts, int hiddenMask) {
        for (Mesh mesh : meshes) {
            if (mesh.body >= parts.length || parts[mesh.body] == null || (hiddenMask & (1 << mesh.body)) != 0) continue;
            PhysTransform at = parts[mesh.body];
            GL11.glPushMatrix();
            try {
                float[] columns = { at.basis.m00, at.basis.m10, at.basis.m20, 0, at.basis.m01, at.basis.m11,
                    at.basis.m21, 0, at.basis.m02, at.basis.m12, at.basis.m22, 0, at.origin.x, at.origin.y, at.origin.z,
                    1 };
                matrix.clear();
                matrix.put(columns);
                matrix.flip();
                GL11.glMultMatrix(matrix);
                GL11.glTranslatef(mesh.ox, mesh.oy, mesh.oz);
                GL11.glRotatef(180, 0, 0, 1);
                GL11.glScalef(mesh.mirror ? -mesh.scale : mesh.scale, mesh.scale, mesh.scale);
                ModelRenderer part = mesh.part;
                float[] old = { part.rotationPointX, part.rotationPointY, part.rotationPointZ, part.rotateAngleX,
                    part.rotateAngleY, part.rotateAngleZ };
                boolean shown = part.showModel, hidden = part.isHidden;
                Map<ModelRenderer, Boolean> children = new IdentityHashMap<>();
                if (part.childModels != null)
                    for (ModelRenderer child : part.childModels) for (Mesh other : meshes) if (other.part == child) {
                        children.put(child, child.showModel);
                        child.showModel = false;
                    }
                try {
                    Vector3f center = center(mesh.anchor);
                    float cos = (float) Math.cos(mesh.rx), sin = (float) Math.sin(mesh.rx);
                    part.rotationPointX = -center.x + (part.rotationPointX - mesh.anchor.rotationPointX);
                    part.rotationPointY = -(center.y * cos - center.z * sin)
                        + (part.rotationPointY - mesh.anchor.rotationPointY);
                    part.rotationPointZ = -(center.y * sin + center.z * cos)
                        + (part.rotationPointZ - mesh.anchor.rotationPointZ);
                    part.rotateAngleX = mesh.rx;
                    part.rotateAngleY = part.rotateAngleZ = 0;
                    part.showModel = true;
                    part.isHidden = false;
                    part.render(.0625F);
                } finally {
                    part.rotationPointX = old[0];
                    part.rotationPointY = old[1];
                    part.rotationPointZ = old[2];
                    part.rotateAngleX = old[3];
                    part.rotateAngleY = old[4];
                    part.rotateAngleZ = old[5];
                    part.showModel = shown;
                    part.isHidden = hidden;
                    for (Map.Entry<ModelRenderer, Boolean> child : children.entrySet())
                        child.getKey().showModel = child.getValue();
                }
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    static Vector3f center(ModelRenderer part) {
        float[] b = { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY };
        bounds(part, 0, 0, 0, b, 0);
        return Float.isFinite(b[0]) ? new Vector3f((b[0] + b[3]) * .5F, (b[1] + b[4]) * .5F, (b[2] + b[5]) * .5F)
            : new Vector3f();
    }

    private static void bounds(ModelRenderer part, float x, float y, float z, float[] b, int depth) {
        if (part.cubeList != null && !part.cubeList.isEmpty()) for (ModelBox box : part.cubeList) {
            b[0] = Math.min(b[0], x + box.posX1);
            b[1] = Math.min(b[1], y + box.posY1);
            b[2] = Math.min(b[2], z + box.posZ1);
            b[3] = Math.max(b[3], x + box.posX2);
            b[4] = Math.max(b[4], y + box.posY2);
            b[5] = Math.max(b[5], z + box.posZ2);
        }
        else if (depth < 16 && part.childModels != null) for (ModelRenderer child : part.childModels)
            bounds(child, x + child.rotationPointX, y + child.rotationPointY, z + child.rotationPointZ, b, depth + 1);
    }

    private MobMeshes() {}
}
