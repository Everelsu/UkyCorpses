package com.uky.corpses.medicine.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.client.Fancy;
import com.uky.corpses.client.Palette;
import com.uky.corpses.client.Shaders;
import com.uky.corpses.medicine.EntitySeat;
import com.uky.corpses.medicine.MedItem;
import com.uky.corpses.medicine.Medicine;
import com.uky.corpses.medicine.MsgFeed;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the medicine: what's being applied while it's held down, the stunned screen, HP labels from the
 * scanner, euphoria's colors (a vanilla post shader chain) and inverted movement.
 */
public final class MedicineClient {

    private static final ResourceLocation EUPHORIA = new ResourceLocation(
        UkyCorpses.MODID,
        "shaders/post/euphoria.json");

    private ShaderGroup shader;
    private int failed;

    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntitySeat.class, new Render() {

            @Override
            public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {}

            @Override
            protected ResourceLocation getEntityTexture(Entity e) {
                return null;
            }
        });
        MedicineClient h = new MedicineClient();
        MinecraftForge.EVENT_BUS.register(h);
        FMLCommonHandler.instance()
            .bus()
            .register(h);
    }

    /** Both axes flipped: forward walks back, left walks right. Jump and sneak stay. */
    private static final class Inverted extends MovementInputFromOptions {

        Inverted(GameSettings s) {
            super(s);
        }

        @Override
        public void updatePlayerMoveState() {
            super.updatePlayerMoveState();
            moveStrafe = -moveStrafe;
            moveForward = -moveForward;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        inversion(mc, p);
        euphoria(mc, p != null && p.isPotionActive(Medicine.EUPHORIA) && Medicine.euphoriaShader);
    }

    /** Not while downed: DownedClient owns the input then, and puts its own in place of this one. */
    private static void inversion(Minecraft mc, EntityPlayerSP p) {
        if (p == null) return;
        boolean want = p.isPotionActive(Medicine.INVERSION) && !com.uky.corpses.Downed.is(p);
        if (want && p.movementInput.getClass() == MovementInputFromOptions.class)
            p.movementInput = new Inverted(mc.gameSettings);
        else if (!want && p.movementInput instanceof Inverted)
            p.movementInput = new MovementInputFromOptions(mc.gameSettings);
    }

    private void euphoria(Minecraft mc, boolean active) {
        EntityRenderer er = mc.entityRenderer;
        if (shader != null && Shaders.get(er) != shader) shader = null; // reloaded or replaced under us
        if (active && shader == null && failed < 5 && Shaders.get(er) == null && OpenGlHelper.shadersSupported) {
            try {
                ShaderGroup g = new ShaderGroup(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    mc.getFramebuffer(),
                    EUPHORIA);
                g.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
                Shaders.set(er, shader = g);
            } catch (Exception ex) {
                failed++;
                UkyCorpses.LOG.warn("Euphoria shader failed to load", ex);
            }
        } else if (!active) {
            failed = 0;
            if (shader != null) {
                er.deactivateShader();
                shader = null;
            }
        }
    }

    private static final RenderItem items = new RenderItem();
    private final Fancy verb = new Fancy(), stunned = new Fancy(), star = new Fancy();
    /** System.nanoTime the current use and the current stun began; the stun's full length (ticks); eased bars. */
    private long usingAt, stunAt;
    private ItemStack usingWhat;
    private int stunMax, usingTotal;
    private float shownUse;
    private final Fancy fedTitle = new Fancy(), aimHint = new Fancy();

    /** Someone at you with medicine (MsgFeed), when it came (nanoTime), eased bar; null when nobody. */
    private static MsgFeed fed;
    private static long fedAt;
    private float shownFed;

    public static void fed(MsgFeed m) {
        fed = m.ticks > 0 ? m : null;
        fedAt = System.nanoTime();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        try {
            ItemStack s = p.isUsingItem() && mc.currentScreen == null ? p.getItemInUse() : null;
            if (s != null && s.getItem() instanceof MedItem && ((MedItem) s.getItem()).useTicks() > 0)
                using(mc, p, s, (MedItem) s.getItem(), w, h, e.partialTicks);
            else {
                usingWhat = null;
                if (mc.currentScreen == null) aim(mc, p, w, h);
            }
            if (fed != null) beingFed(mc, w, h);
            PotionEffect stun = p.getActivePotionEffect(Medicine.STUN);
            if (stun != null) stunned(mc, stun, w, h);
            else stunMax = 0;
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    /**
     * Under the crosshair while something's being applied: what's happening, the thing itself with its name, a bar
     * filling (green when it heals), the seconds left, and what it'll do.
     */
    private void using(Minecraft mc, EntityPlayerSP p, ItemStack s, MedItem m, int w, int h, float pt) {
        if (usingWhat != s) {
            usingWhat = s;
            usingAt = System.nanoTime();
            usingTotal = Math.max(1, p.getItemInUseCount()); // on someone else a pill takes its own time
            shownUse = 0;
        }
        FontRenderer font = mc.fontRenderer;
        float t = (System.nanoTime() - usingAt) / 1e9F;
        float frac = Math.min(1, Math.max(0, 1 - (p.getItemInUseCount() - pt) / usingTotal));
        shownUse += (frac - shownUse) * 0.35F;
        EntityPlayer to = MedItem.targets.get(p);
        String key = m.useKey();
        String what = to == null ? StatCollector.translateToLocal("ukycorpses.med." + (key != null ? key : "pill"))
            : StatCollector.translateToLocalFormatted(
                m.kind == MedItem.Kind.PILL ? "ukycorpses.med.giving" : "ukycorpses.med.treating",
                to.getDisplayName());
        float y = h / 2F + 14, bw = 100, bx = w / 2F - bw / 2;
        verb.set(t, 0, 0.5F, 0)
            .centred(font, what, w / 2F, y, Palette.text(), 0.95F);

        String name = s.getDisplayName();
        float nx = w / 2F - (18 + font.getStringWidth(name)) / 2F;
        RenderHelper.enableGUIStandardItemLighting();
        items.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), s, (int) nx, (int) y + 10);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        Fancy.text(font, name, nx + 18, y + 14, Palette.dim(), 0.9F, true);

        int color = m.kind == MedItem.Kind.HEAL ? Palette.OK : Palette.accent();
        if (to != null) { // a frame round the other one's name: whom it's going into
            Fancy.gradH(bx - 20, y - 3, w / 2F, y - 2, color, 0, 0.7F);
            Fancy.gradH(w / 2F, y - 3, bx + bw + 20, y - 2, color, 0.7F, 0);
        }
        Fancy.rect(bx, y + 29, bx + bw, y + 31, 0, 0.55F);
        Fancy.rect(bx, y + 29, bx + bw * shownUse, y + 31, color, 1);
        String left = String.format("%.1f", Math.max(0, p.getItemInUseCount() - pt) / 20F);
        Fancy.text(font, left, bx + bw + 4, y + 26, Palette.dim(), 0.8F, true);
        String fx = m.effect();
        if (fx != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(w / 2F, y + 35, 0);
            GL11.glScalef(0.75F, 0.75F, 1);
            Fancy.text(font, fx, -font.getStringWidth(fx) / 2F, 0, color, 0.8F, true);
            GL11.glPopMatrix();
        }
        if (to != null && m.kind == MedItem.Kind.PILL && Medicine.resist) {
            String warn = StatCollector.translateToLocal("ukycorpses.med.canResist");
            GL11.glPushMatrix();
            GL11.glTranslatef(w / 2F, y + (fx != null ? 44 : 35), 0);
            GL11.glScalef(0.75F, 0.75F, 1);
            Fancy.text(font, warn, -font.getStringWidth(warn) / 2F, 0, Palette.dim(), 0.7F, true);
            GL11.glPopMatrix();
        }
    }

    /**
     * Looking at another player with something that works on them: what a right-click does, under the crosshair, so a
     * pill isn't swallowed by mistake and a bandage finds its way.
     */
    private void aim(Minecraft mc, EntityPlayerSP p, int w, int h) {
        ItemStack s = p.getHeldItem();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (s == null || !(s.getItem() instanceof MedItem)
            || !((MedItem) s.getItem()).onOthers()
            || hit == null
            || !(hit.entityHit instanceof EntityPlayer)
            || com.uky.corpses.Downed.is((EntityPlayer) hit.entityHit)) {
            aimAt = null;
            return;
        }
        EntityPlayer t = (EntityPlayer) hit.entityHit;
        if (aimAt != t) {
            aimAt = t;
            aimSince = System.nanoTime();
        }
        MedItem m = (MedItem) s.getItem();
        String line = StatCollector.translateToLocalFormatted(
            m.kind == MedItem.Kind.PILL ? "ukycorpses.med.aimFeed" : "ukycorpses.med.aimTreat",
            t.getDisplayName());
        int color = m.kind == MedItem.Kind.PILL ? Palette.accent() : Palette.OK;
        aimHint.set((System.nanoTime() - aimSince) / 1e9F * 2.5F, 0, 0, 0)
            .centred(mc.fontRenderer, line, w / 2F, h / 2F + 12, color, 0.9F);
    }

    private EntityPlayer aimAt;
    private long aimSince;

    /**
     * Someone's at you: above the hotbar who and with what, a bar of how far along, and, when it can be fought off,
     * the edges pulsing and what to do about it.
     */
    private void beingFed(Minecraft mc, int w, int h) {
        MsgFeed m = fed;
        float t = (System.nanoTime() - fedAt) / 1e9F;
        if (t * 20 > m.ticks + 10) { // the end got lost: let go anyway
            fed = null;
            return;
        }
        FontRenderer font = mc.fontRenderer;
        net.minecraft.entity.Entity who = mc.theWorld.getEntityByID(m.giver);
        // they let go and the word of it got lost or is late: their hands say so first
        if (t > 0.3F && (who == null || !who.isEating())) {
            fed = null;
            return;
        }
        String name = who instanceof EntityPlayer ? ((EntityPlayer) who).getDisplayName() : "?";
        int color = m.forced ? Palette.danger() : Palette.OK;
        if (m.forced) Fancy.edges(w, h, color, 0.25F + 0.15F * (float) Math.sin(t * 8));
        float y = h - 92;
        String title = StatCollector
            .translateToLocalFormatted(m.forced ? "ukycorpses.med.beingFed" : "ukycorpses.med.beingTreated", name);
        GL11.glPushMatrix();
        GL11.glTranslatef(w / 2F, y, 0);
        GL11.glScalef(1.3F, 1.3F, 1);
        fedTitle.set(t * 2, m.forced ? 0.4F : 0, 0, m.forced ? 0.5F : 0)
            .centred(font, title, 0, 0, color, 1);
        GL11.glPopMatrix();
        if (m.item != null) {
            String item = m.item.getDisplayName();
            float nx = w / 2F - (18 + font.getStringWidth(item)) / 2F;
            RenderHelper.enableGUIStandardItemLighting();
            items.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), m.item, (int) nx, (int) y + 12);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            Fancy.text(font, item, nx + 18, y + 16, Palette.dim(), 0.9F, true);
        }
        float bw = 120, bx = w / 2F - bw / 2, frac = Math.min(1, t * 20 / Math.max(1, m.ticks));
        shownFed += (frac - shownFed) * 0.35F;
        Fancy.rect(bx, y + 31, bx + bw, y + 33, 0, 0.55F);
        Fancy.rect(bx, y + 31, bx + bw * shownFed, y + 33, color, 1);
        if (m.forced) {
            String how = StatCollector.translateToLocal("ukycorpses.med.resistHint");
            float pulse = 0.7F + 0.3F * (float) Math.sin(t * 8);
            Fancy.text(font, how, w / 2F - font.getStringWidth(how) / 2F, y + 37, Palette.text(), pulse, true);
        }
    }

    /**
     * Stunned: the blow flashes white, the edges close in, stars go round over the crosshair, the word shakes, and a
     * bar runs out with the seconds left.
     */
    private void stunned(Minecraft mc, PotionEffect stun, int w, int h) {
        if (stun.getDuration() > stunMax) { // a new one (or a longer one)
            stunMax = stun.getDuration();
            stunAt = System.nanoTime();
        }
        FontRenderer font = mc.fontRenderer;
        float t = (System.nanoTime() - stunAt) / 1e9F, frac = stun.getDuration() / (float) Math.max(1, stunMax);
        float flash = Math.max(0, 1 - t / 0.45F);
        if (flash > 0) Fancy.rect(0, 0, w, h, 0xFFFFFF, 0.65F * flash * flash);
        Fancy.edges(w, h, 0, 0.55F + 0.15F * (float) Math.sin(t * 5));

        float cx = w / 2F, cy = h / 2F - 26;
        for (int i = 0; i < 5; i++) {
            double a = t * 3.2 + i * Math.PI * 2 / 5;
            float sx = cx + (float) Math.cos(a) * 26, sy = cy + (float) Math.sin(a) * 7;
            float front = (float) (Math.sin(a) * 0.5 + 0.5);
            GL11.glPushMatrix();
            GL11.glTranslatef(sx, sy, 0);
            GL11.glScalef(0.8F + 0.5F * front, 0.8F + 0.5F * front, 1);
            Fancy.text(
                font,
                "\u2726",
                -font.getStringWidth("\u2726") / 2F,
                -4,
                Palette.accent(),
                0.4F + 0.6F * front,
                true);
            GL11.glPopMatrix();
        }

        float top = h * 0.25F;
        GL11.glPushMatrix();
        GL11.glTranslatef(w / 2F, top, 0);
        GL11.glScalef(2, 2, 1);
        stunned.set(t, 0.6F + 0.8F * flash, 0, 0.4F + 1.2F * flash)
            .centred(font, StatCollector.translateToLocal("ukycorpses.med.stunned"), 0, 0, Palette.text(), 1);
        GL11.glPopMatrix();
        float bw = 120, bx = w / 2F - bw / 2, by = top + 22;
        Fancy.rect(bx, by, bx + bw, by + 2, 0, 0.55F);
        Fancy.rect(bx, by, bx + bw * frac, by + 2, Palette.accent(), 0.95F);
        String secs = String.format("%.1f", stun.getDuration() / 20F);
        Fancy.text(font, secs, w / 2F - font.getStringWidth(secs) / 2F, by + 5, Palette.dim(), 0.85F, true);
    }

    // ---- HP scanner: green "hp / max" over players and villagers in sight ----

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null || mc.currentScreen != null || !p.inventory.hasItem(Medicine.SCANNER))
            return;
        double r = Medicine.scannerRange;
        List<EntityLivingBase> seen = new ArrayList<>();
        for (Object o : mc.theWorld.playerEntities) seen.add((EntityLivingBase) o);
        seen.addAll(mc.theWorld.getEntitiesWithinAABB(EntityVillager.class, p.boundingBox.expand(r, r, r)));
        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY, p.posZ); // the client player's posY is the eyes
        for (EntityLivingBase t : seen) {
            if (t == p || t.isDead || t.isInvisible() || t.getDistanceToEntity(p) > r) continue;
            Vec3 mid = Vec3.createVectorHelper(t.posX, t.boundingBox.minY + t.height * 0.5, t.posZ);
            if (mc.theWorld.rayTraceBlocks(eye, mid) != null) continue;
            float pt = e.partialTicks;
            label(
                (int) Math.ceil(t.getHealth()) + " / " + (int) Math.ceil(t.getMaxHealth()),
                t.lastTickPosX + (t.posX - t.lastTickPosX) * pt - RenderManager.renderPosX,
                t.lastTickPosY + (t.posY - t.lastTickPosY) * pt
                    - t.yOffset
                    - RenderManager.renderPosY
                    + t.height
                    + lift(t),
                t.lastTickPosZ + (t.posZ - t.lastTickPosZ) * pt - RenderManager.renderPosZ);
        }
    }

    /**
     * Above the name tag where there is one (players, named mobs), so the two don't overlap; else where it would be.
     */
    private static double lift(EntityLivingBase t) {
        return t instanceof net.minecraft.entity.player.EntityPlayer || t instanceof net.minecraft.entity.EntityLiving
            && ((net.minecraft.entity.EntityLiving) t).hasCustomNameTag() ? 0.8 : 0.5;
    }

    /** Vanilla's name tag, without the depth test: only called once the line of sight is checked. */
    private static void label(String text, double x, double y, double z) {
        RenderManager rm = RenderManager.instance;
        FontRenderer font = rm.getFontRenderer();
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0, 1, 0);
        GL11.glRotatef(-rm.playerViewY, 0, 1, 0);
        GL11.glRotatef(rm.playerViewX, 1, 0, 0);
        GL11.glScalef(-0.025F, -0.025F, 0.025F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        font.drawStringWithShadow(text, -font.getStringWidth(text) / 2, 0, 0x66FF66);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }
}
