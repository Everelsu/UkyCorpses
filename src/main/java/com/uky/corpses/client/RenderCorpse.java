package com.uky.corpses.client;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Settle;
import com.uky.corpses.corpse.Skeleton;

/**
 * The owner's skin on a posed player model, wearing the armor that's inside and holding what was in hand. Placed by
 * Skeleton, the same frame the hitboxes and the death camera use; a fresh body is drawn mid-Settle, starting where and
 * how its player was.
 */
public class RenderCorpse extends Render {

    /** Last skin seen per player name: the owner is often logged off or far away by the time you find the body. */
    private static final Map<String, ResourceLocation> skins = new HashMap<>();
    static final float[] ALIVE = { 1, 1, 1 }, DEAD = { 0.8F, 0.78F, 0.75F }, BURNT = { 0.35F, 0.30F, 0.28F },
        SCORCHED = { 0.55F, 0.5F, 0.47F };
    /**
     * The body a camera looks out of (the death camera's, the crawl camera's): a body or a player. It's drawn without
     * its head and helmet, shifted by viewShift (the camera eases into it from where the view was, and keeps clear of
     * walls; the body moves with it so the two never part), with its arms kept off the eye at eyeAt (world).
     */
    public static Object noHead;
    public static final double[] viewShift = new double[3];
    public static double[] eyeAt;

    private final ModelCorpse body = new ModelCorpse(0F), outer = new ModelCorpse(1.0F), inner = new ModelCorpse(0.5F);
    private final float[] limbs = new float[Settle.N], tint = new float[3], cols = new float[16];
    private final double[] off = new double[3];
    private final Mat4 frame = new Mat4();
    private final FloatBuffer buf = GLAllocation.createDirectFloatBuffer(16);

    public RenderCorpse() {
        shadowSize = 0.5F;
    }

    static void remember(EntityPlayer p) {
        if (p instanceof AbstractClientPlayer)
            skins.put(p.getCommandSenderName(), ((AbstractClientPlayer) p).getLocationSkin());
    }

    /**
     * Where a body is drawn this frame, relative to its position: into out (a frame, lifted to the floor), plus the
     * offset of the whole thing in off. The death camera asks too.
     */
    static Mat4 frame(EntityCorpse c, float partial, float[] angles, double[] off, Mat4 out) {
        c.frameNow(partial, angles, off, out);
        if (afloat(c)) { // rocks gently on the water
            float t = c.ticksExisted + partial + c.getEntityId() * 7;
            off[1] += MathHelper.sin(t * 0.07F) * 0.035F;
        }
        return out;
    }

    /** Lying in water or lava, not on a floor. */
    public static boolean afloat(EntityCorpse c) {
        return c.worldObj
            .getBlock(
                MathHelper.floor_double(c.posX),
                MathHelper.floor_double(c.posY + 0.15),
                MathHelper.floor_double(c.posZ))
            .getMaterial()
            .isLiquid();
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        EntityCorpse c = (EntityCorpse) e;
        if (c.pending()) return; // its player is still drawn standing where it will start
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) armor[i] = c.armor(i);
        float[] dead = c.burn() == EntityCorpse.BURNT ? BURNT : c.burn() == EntityCorpse.SCORCHED ? SCORCHED : DEAD;
        // a fresh body pales over its first moments: it was a living player a second ago
        float pale = c.settle.active() ? c.settle.pale(partial) : 1;
        for (int i = 0; i < 3; i++) tint[i] = ALIVE[i] + (dead[i] - ALIVE[i]) * pale;
        e.ignoreFrustumCheck = c.settle.active(); // thrown: drawn far from where it will lie
        frame(c, partial, limbs, off, frame);
        draw(c, getEntityTexture(c), armor, c.held(), limbs, frame, 0, 0, tint, x + off[0], y + off[1], z + off[2]);
    }

    /**
     * A body at feet position x y z in this frame (relative), limbs at angles. Downed and just-dead players come
     * through here too.
     */
    void draw(Entity e, ResourceLocation skin, ItemStack[] armor, ItemStack held, float[] angles, Mat4 frame,
        float swing, float swingAmount, float[] tint, double x, double y, double z) {
        // the body a camera looks out of: no head (the eye is where it would be), arms kept off the eye
        boolean own = noHead != null && noHead == e;
        int hidden = 0, faded = 0;
        if (own) {
            x += viewShift[0];
            y += viewShift[1];
            z += viewShift[2];
            angles = ownArms(frame, angles, x, y, z);
            hidden = 1 << Skeleton.HEAD;
            if (fade[0] < 0.99F) faded |= 1 << Skeleton.RARM;
            if (fade[1] < 0.99F) faded |= 1 << Skeleton.LARM;
        }
        for (ModelCorpse m : new ModelCorpse[] { body, outer, inner }) m.ang = angles;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        frame.toColumns(cols);
        buf.clear();
        buf.put(cols);
        buf.flip();
        GL11.glMultMatrix(buf);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        layers(e, skin, armor, tint, ~hidden & ~faded, 1, swing, swingAmount);
        if (held != null && (!own || fade[0] > 0.99F)) hold(e, held); // a see-through hand holds nothing solid
        // an arm close to the eye (a hand thrown over the face): see-through, never just gone
        for (int arm = 0; arm < 2; arm++) {
            int bit = 1 << (arm == 0 ? Skeleton.RARM : Skeleton.LARM);
            if ((faded & bit) == 0 || fade[arm] < 0.02F) continue;
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);
            GL11.glDepthMask(false);
            layers(e, skin, armor, tint, bit, fade[arm], swing, swingAmount);
            GL11.glDepthMask(true);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    /** The skin and the armor over it, of the parts in mask (a bit per Skeleton part), at alpha. */
    private void layers(Entity e, ResourceLocation skin, ItemStack[] armor, float[] tint, int mask, float alpha,
        float swing, float swingAmount) {
        bindTexture(skin);
        GL11.glColor4f(tint[0], tint[1], tint[2], alpha);
        show(body, mask);
        render(body, e, swing, swingAmount);
        show(body, -1);
        for (int pass = 0; pass < 4; pass++) { // as RenderPlayer.shouldRenderPass: 0 helmet .. 3 boots
            ItemStack s = armor[3 - pass];
            if (s == null || !(s.getItem() instanceof ItemArmor)) continue;
            ModelCorpse m = pass == 2 ? inner : outer;
            int worn = pass == 0 ? 1 << Skeleton.HEAD
                : pass == 1 ? 1 << Skeleton.BODY | 1 << Skeleton.RARM | 1 << Skeleton.LARM
                    : pass == 2 ? 1 << Skeleton.BODY | 1 << Skeleton.RLEG | 1 << Skeleton.LLEG
                        : 1 << Skeleton.RLEG | 1 << Skeleton.LLEG;
            if ((worn & mask) == 0) continue;
            show(m, worn & mask);
            bindTexture(RenderBiped.getArmorResource(e, s, pass, null));
            int color = ((ItemArmor) s.getItem()).getColor(s);
            if (color != -1) { // leather: dyed base, then the undyed overlay
                GL11.glColor4f(
                    (color >> 16 & 255) / 255F * tint[0],
                    (color >> 8 & 255) / 255F * tint[1],
                    (color & 255) / 255F * tint[2],
                    alpha);
                render(m, e, swing, swingAmount);
                bindTexture(RenderBiped.getArmorResource(e, s, pass, "overlay"));
                GL11.glColor4f(tint[0], tint[1], tint[2], alpha);
            }
            render(m, e, swing, swingAmount);
            show(m, -1);
        }
    }

    /** Not under the body the camera is in: from its eye its shadow would be a smudge right under the view. */
    @Override
    public void doRenderShadowAndFire(Entity e, double x, double y, double z, float yaw, float partial) {
        if (noHead != null && noHead == e) return;
        super.doRenderShadowAndFire(e, x, y, z, yaw, partial);
    }

    /** Which of the model's boxes are drawn, a bit per Skeleton part. */
    private static void show(ModelCorpse m, int mask) {
        m.bipedHead.showModel = m.bipedHeadwear.showModel = (mask & 1 << Skeleton.HEAD) != 0;
        m.bipedBody.showModel = (mask & 1 << Skeleton.BODY) != 0;
        m.bipedRightArm.showModel = (mask & 1 << Skeleton.RARM) != 0;
        m.bipedLeftArm.showModel = (mask & 1 << Skeleton.LARM) != 0;
        m.bipedRightLeg.showModel = (mask & 1 << Skeleton.RLEG) != 0;
        m.bipedLeftLeg.showModel = (mask & 1 << Skeleton.LLEG) != 0;
    }

    private final Mat4 part = new Mat4(), inv = new Mat4();
    private final double[] local = new double[3];
    private final float[] own = new float[Settle.N];
    /** Per arm (right, left), eased: how far it's bent off the eye (radians), how opaque it is. */
    private final float[] push = new float[2], fade = { 1, 1 };
    private long armsAt;

    /**
     * The own body's limbs as drawn, the arms kept off the eye. A raised arm swings out sideways rather than across
     * the face; one still within a few pixels of the eye bends away from it; one the eye would still be in or against
     * turns see-through (fade) instead of being cut open by the near plane.
     */
    private float[] ownArms(Mat4 frame, float[] angles, double x, double y, double z) {
        System.arraycopy(angles, 0, own, 0, Settle.N);
        long now = System.nanoTime();
        float dt = armsAt == 0 ? 1 : Math.min(0.1F, (now - armsAt) / 1e9F);
        armsAt = now;
        double ex = 0, ey = 0, ez = 0;
        boolean eye = eyeAt != null;
        if (eye) { // the eye relative to where the body is drawn
            ex = eyeAt[0] - RenderManager.renderPosX - x;
            ey = eyeAt[1] - RenderManager.renderPosY - y;
            ez = eyeAt[2] - RenderManager.renderPosZ - z;
        }
        float ease = 1 - (float) Math.exp(-dt / 0.08F);
        for (int arm = 0; arm < 2; arm++) {
            int i = arm == 0 ? 3 : 6, part = arm == 0 ? Skeleton.RARM : Skeleton.LARM;
            boolean right = arm == 0, up = own[i] < -1.5F;
            if (up) own[i + 2] = right ? Math.min(own[i + 2], -0.1F) : Math.max(own[i + 2], 0.1F);
            float bend = 0, see = 1;
            if (eye) {
                double d = distance(frame, part, ex, ey, ez);
                if (d < 2.5) bend = 0.7F * (float) Math.min(1, (2.5 - d) / 2.5);
            }
            // outward, whichever way that is with the arm up or down
            push[arm] += (bend - push[arm]) * ease;
            own[i + 2] += (up == right ? -1 : 1) * push[arm];
            if (eye && distance(frame, part, ex, ey, ez) < 1.2) see = 0;
            fade[arm] += (see - fade[arm]) * (1 - (float) Math.exp(-dt / 0.15F));
        }
        return own;
    }

    /** Pixels from the point (frame space) to a part's box with armor on it, as it's posed in own. */
    private double distance(Mat4 frame, int i, double px, double py, double pz) {
        Skeleton.part(frame, i, own, part);
        if (!part.invert(inv)) return 99;
        inv.apply(px, py, pz, local);
        float[] lo = Skeleton.min(i), hi = Skeleton.max(i);
        double s = 0;
        for (int k = 0; k < 3; k++) {
            double out = Math.max(Math.max(lo[k] - 1 - local[k], local[k] - hi[k] - 1), 0);
            s += out * out;
        }
        return Math.sqrt(s);
    }

    /** No arm faded or bent from the last own body: the next starts clean. */
    public static void resetOwn() {
        if (CorpseClient.renderer != null) {
            CorpseClient.renderer.push[0] = CorpseClient.renderer.push[1] = 0;
            CorpseClient.renderer.fade[0] = CorpseClient.renderer.fade[1] = 1;
            CorpseClient.renderer.armsAt = 0;
        }
    }

    /**
     * In the right hand, placed the way RenderPlayer places a held item; the arm still has its last render's angles.
     */
    private void hold(Entity e, ItemStack s) {
        // the item renderer wants a living holder; for a body the viewer stands in
        EntityLivingBase who = e instanceof EntityLivingBase ? (EntityLivingBase) e
            : Minecraft.getMinecraft().thePlayer;
        Item item = s.getItem();
        GL11.glPushMatrix();
        body.bipedRightArm.postRender(0.0625F);
        GL11.glTranslatef(-0.0625F, 0.4375F, 0.0625F);
        IItemRenderer custom = MinecraftForgeClient.getItemRenderer(s, IItemRenderer.ItemRenderType.EQUIPPED);
        boolean block3d = custom != null && custom
            .shouldUseRenderHelper(IItemRenderer.ItemRenderType.EQUIPPED, s, IItemRenderer.ItemRendererHelper.BLOCK_3D);
        if (block3d || item instanceof ItemBlock && RenderBlocks.renderItemIn3d(
            Block.getBlockFromItem(item)
                .getRenderType())) {
            GL11.glTranslatef(0, 0.1875F, -0.3125F);
            GL11.glRotatef(20F, 1, 0, 0);
            GL11.glRotatef(45F, 0, 1, 0);
            GL11.glScalef(-0.375F, -0.375F, 0.375F);
        } else if (item == Items.bow) {
            GL11.glTranslatef(0, 0.125F, 0.3125F);
            GL11.glRotatef(-20F, 0, 1, 0);
            GL11.glScalef(0.625F, -0.625F, 0.625F);
            GL11.glRotatef(-100F, 1, 0, 0);
            GL11.glRotatef(45F, 0, 1, 0);
        } else if (item.isFull3D()) {
            if (item.shouldRotateAroundWhenRendering()) {
                GL11.glRotatef(180F, 0, 0, 1);
                GL11.glTranslatef(0, -0.125F, 0);
            }
            GL11.glTranslatef(0, 0.1875F, 0);
            GL11.glScalef(0.625F, -0.625F, 0.625F);
            GL11.glRotatef(-100F, 1, 0, 0);
            GL11.glRotatef(45F, 0, 1, 0);
        } else {
            GL11.glTranslatef(0.25F, 0.1875F, -0.1875F);
            GL11.glScalef(0.375F, 0.375F, 0.375F);
            GL11.glRotatef(60F, 0, 0, 1);
            GL11.glRotatef(-90F, 1, 0, 0);
            GL11.glRotatef(20F, 0, 0, 1);
        }
        int passes = item.requiresMultipleRenderPasses() ? item.getRenderPasses(s.getItemDamage()) : 1;
        for (int p = 0; p < passes; p++) {
            int c = item.getColorFromItemStack(s, p);
            GL11.glColor4f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, 1);
            renderManager.itemRenderer.renderItem(who, s, p);
        }
        GL11.glPopMatrix();
    }

    private static void render(ModelCorpse m, Entity e, float swing, float amount) {
        m.render(e, swing, amount, 0, 0, 0, 0.0625F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return skin(((EntityCorpse) e).owner());
    }

    static ResourceLocation skin(String owner) {
        EntityPlayer p = Minecraft.getMinecraft().theWorld.getPlayerEntityByName(owner);
        if (p != null) remember(p);
        ResourceLocation skin = skins.get(owner);
        return skin != null ? skin : AbstractClientPlayer.locationStevePng;
    }
}
