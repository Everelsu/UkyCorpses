package com.uky.corpses.amp;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.Reactions;
import com.uky.corpses.client.RenderCorpse;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Original rendered-limb mask and the cut limb's skin renderer. */
@SideOnly(Side.CLIENT)
public final class AmpClient {

    private static final ThreadLocal<Deque<Map<ModelBiped, boolean[]>>> frames = ThreadLocal
        .withInitial(ArrayDeque::new);
    private static final Map<ModelBiped, boolean[]> unscoped = new java.util.WeakHashMap<>();

    public static void init() {
        AmpClient events = new AmpClient();
        MinecraftForge.EVENT_BUS.register(events);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(events);
        RenderingRegistry.registerEntityRenderingHandler(EntityDetachedPart.class, new LimbRenderer());
    }

    @SubscribeEvent
    public void frame(cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.START) return;
        for (Map.Entry<ModelBiped, boolean[]> entry : unscoped.entrySet()) restore(entry.getKey(), entry.getValue());
        unscoped.clear();
        // A cancelled/failed living render may never emit Post; do not retain its models into another frame.
        while (!frames.get()
            .isEmpty())
            for (Map.Entry<ModelBiped, boolean[]> entry : frames.get()
                .pop()
                .entrySet()) restore(entry.getKey(), entry.getValue());
    }

    public static void apply(ModelBiped model, Entity entity) {
        boolean[] previous = unscoped.remove(model);
        if (previous != null) restore(model, previous);
        if (!(entity instanceof EntityLivingBase) || Reactions.is((EntityLivingBase) entity)) return;
        int mask = AmputationState.mask(entity);
        if (mask == 0) return;
        if (!frames.get()
            .isEmpty())
            frames.get()
                .peek()
                .computeIfAbsent(model, key -> visibility(model));
        unscoped.put(model, visibility(model));
        if ((mask & 1) != 0) {
            model.bipedHead.showModel = false;
            model.bipedHeadwear.showModel = false;
        }
        if ((mask & 4) != 0) model.bipedRightArm.showModel = false;
        if ((mask & 8) != 0) model.bipedLeftArm.showModel = false;
        if ((mask & 16) != 0) model.bipedRightLeg.showModel = false;
        if ((mask & 32) != 0) model.bipedLeftLeg.showModel = false;
    }

    public static boolean hideHeld(Entity entity) {
        return (AmputationState.mask(entity) & 4) != 0;
    }

    private static boolean[] visibility(ModelBiped model) {
        ModelRenderer[] boxes = parts(model);
        boolean[] result = new boolean[boxes.length];
        for (int i = 0; i < boxes.length; i++) result[i] = boxes[i].showModel;
        return result;
    }

    private static void restore(ModelBiped model, boolean[] saved) {
        ModelRenderer[] boxes = parts(model);
        for (int i = 0; i < boxes.length; i++) boxes[i].showModel = saved[i];
    }

    private static ModelRenderer[] parts(ModelBiped model) {
        return new ModelRenderer[] { model.bipedHead, model.bipedHeadwear, model.bipedBody, model.bipedRightArm,
            model.bipedLeftArm, model.bipedRightLeg, model.bipedLeftLeg };
    }

    @SubscribeEvent
    public void before(RenderLivingEvent.Pre event) {
        // First-person and external model calls have no living-render Post event.
        for (Map.Entry<ModelBiped, boolean[]> entry : unscoped.entrySet()) restore(entry.getKey(), entry.getValue());
        unscoped.clear();
        frames.get()
            .push(new IdentityHashMap<>());
    }

    @SubscribeEvent
    public void after(RenderLivingEvent.Post event) {
        if (frames.get()
            .isEmpty()) return;
        for (Map.Entry<ModelBiped, boolean[]> entry : frames.get()
            .pop()
            .entrySet()) {
            ModelRenderer[] boxes = parts(entry.getKey());
            for (int i = 0; i < boxes.length; i++) boxes[i].showModel = entry.getValue()[i];
            unscoped.remove(entry.getKey());
        }
    }

    private static final class LimbRenderer extends Render {

        private final ModelBiped model = new ModelBiped(), skeleton = new com.uky.corpses.client.ModelCorpse(0, true),
            outer = new ModelBiped(1), inner = new ModelBiped(.5F);
        private final FloatBuffer buffer = GLAllocation.createDirectFloatBuffer(16);

        LimbRenderer() {
            shadowSize = .15F;
            model.isChild = skeleton.isChild = outer.isChild = inner.isChild = false;
        }

        @Override
        public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
            EntityDetachedPart limb = (EntityDetachedPart) entity;
            if (AmputationConfig.MAX_LOOSE_LIMBS.get() == 0) return;
            int newer = 0;
            for (Object object : limb.worldObj.loadedEntityList)
                if (object instanceof EntityDetachedPart && ((Entity) object).getEntityId() > limb.getEntityId())
                    newer++;
            if (newer >= AmputationConfig.MAX_LOOSE_LIMBS.get()) return;
            ModelRenderer box = box(limb.kind.equals("skeleton") ? skeleton : model, limb.part);
            float oldX = box.rotationPointX, oldY = box.rotationPointY, oldZ = box.rotationPointZ;
            box.setRotationPoint(0, 0, 0);
            javax.vecmath.Matrix3f rotation = new javax.vecmath.Matrix3f();
            rotation.set(limb.rotation);
            float[] columns = { rotation.m00, rotation.m10, rotation.m20, 0, rotation.m01, rotation.m11, rotation.m21,
                0, rotation.m02, rotation.m12, rotation.m22, 0, 0, 0, 0, 1 };
            buffer.clear();
            buffer.put(columns);
            buffer.flip();
            float[] low = Skeleton.min(limb.part), high = Skeleton.max(limb.part);
            float pixel = 15F / 256;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                GL11.glMultMatrix(buffer);
                GL11.glRotatef(180, 0, 0, 1);
                GL11.glTranslatef(
                    -(low[0] + high[0]) * .5F * pixel,
                    -(low[1] + high[1]) * .5F * pixel,
                    -(low[2] + high[2]) * .5F * pixel);
                bindTexture(getEntityTexture(limb));
                GL11.glColor4f(1, 1, 1, 1);
                box.render(pixel);
                for (int slot = 0; slot < 4; slot++) {
                    net.minecraft.item.ItemStack stack = limb.armor[slot];
                    if (stack == null || !(stack.getItem() instanceof net.minecraft.item.ItemArmor)) continue;
                    boolean covered = limb.part == 0 ? slot == 3 : limb.part >= 4 ? slot == 0 || slot == 1 : slot == 2;
                    if (!covered) continue;
                    ModelRenderer piece = box(slot == 1 ? inner : outer, limb.part);
                    float px = piece.rotationPointX, py = piece.rotationPointY, pz = piece.rotationPointZ;
                    piece.setRotationPoint(0, 0, 0);
                    try {
                        int pass = 3 - slot;
                        bindTexture(
                            net.minecraft.client.renderer.entity.RenderBiped.getArmorResource(limb, stack, pass, null));
                        int color = ((net.minecraft.item.ItemArmor) stack.getItem()).getColor(stack);
                        if (color != -1) {
                            GL11.glColor4f(
                                (color >> 16 & 255) / 255F,
                                (color >> 8 & 255) / 255F,
                                (color & 255) / 255F,
                                1);
                            piece.render(pixel);
                            bindTexture(
                                net.minecraft.client.renderer.entity.RenderBiped
                                    .getArmorResource(limb, stack, pass, "overlay"));
                            GL11.glColor4f(1, 1, 1, 1);
                        }
                        piece.render(pixel);
                    } finally {
                        piece.setRotationPoint(px, py, pz);
                    }
                }
            } finally {
                GL11.glPopMatrix();
                box.setRotationPoint(oldX, oldY, oldZ);
            }
        }

        private static ModelRenderer box(ModelBiped model, int part) {
            return part == 0 ? model.bipedHead
                : part == 2 ? model.bipedRightArm
                    : part == 3 ? model.bipedLeftArm : part == 4 ? model.bipedRightLeg : model.bipedLeftLeg;
        }

        @Override
        protected ResourceLocation getEntityTexture(Entity entity) {
            EntityDetachedPart limb = (EntityDetachedPart) entity;
            return limb.kind.equals("player") ? RenderCorpse.skin(limb.owner)
                : new ResourceLocation(
                    "minecraft",
                    limb.kind.equals("skeleton") ? "textures/entity/skeleton/skeleton.png"
                        : "textures/entity/zombie/zombie.png");
        }
    }

    private AmpClient() {}
}
