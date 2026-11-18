package com.uky.corpses.blood.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.TileCorpse;
import com.uky.corpses.blood.Blood;
import com.uky.corpses.blood.MsgBlood;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Splats the server told us about, drawn after the world as the game's own pixels: 1/16-block cells on the world's
 * pixel grid, flat colours, no smoothing (depth tested, offset against z-fighting, lit by the block they lie on). A
 * burst of drops flies off as each lands (BloodDrop); and the smoke over burnt bodies. A stain dries away: over the
 * last third of its time it darkens and loses its pixels from the rim in.
 */
public final class BloodClient {

    private static final int CAP = 300;
    /** No flag tells a fire death from a blast, so every burnt body smokes this long (20 s). */
    private static final int SMOKE = 400;
    /** Dark reds a block texture might have. */
    private static final int[] SHADES = { 0x7A0A0A, 0x5E0606, 0x8C1212 };

    private static final class Decal {

        final float x, y, z, size;
        final int seed, born, delay, grow;
        /** The cells off the centre pixel, in pixels; how far out each is (0 middle, 1 rim); its shade. */
        final byte[] ci, cj, shade;
        final float[] far;

        Decal(MsgBlood m, int born) {
            x = m.x;
            y = m.y;
            z = m.z;
            size = m.size;
            seed = m.seed;
            delay = m.delay;
            grow = m.grow;
            this.born = born;
            // a ragged blot of whole pixels, a pool bigger; a spot throws a few lone drops around it
            Random r = new Random(seed);
            float rad = grow > 0 ? 4.5F * size + 1 : (1.5F + r.nextFloat() * 2.5F) * size;
            int span = (int) rad + 1, lone = grow > 0 ? 0 : 1 + r.nextInt(3), n = 0;
            int max = (2 * span + 1) * (2 * span + 1) + lone;
            byte[] ci = new byte[max], cj = new byte[max], shade = new byte[max];
            float[] far = new float[max];
            for (int i = -span; i <= span; i++) for (int j = -span; j <= span; j++) {
                float f = (float) Math.sqrt(i * i + j * j) / rad + (r.nextFloat() - 0.5F) * 0.5F;
                if (f > 1) continue;
                ci[n] = (byte) i;
                cj[n] = (byte) j;
                far[n] = Math.max(0, f);
                shade[n++] = (byte) r.nextInt(SHADES.length);
            }
            for (int k = 0; k < lone; k++) {
                double a = r.nextFloat() * Math.PI * 2, d = rad + 1.5 + r.nextFloat() * 2.5;
                ci[n] = (byte) Math.round(Math.cos(a) * d);
                cj[n] = (byte) Math.round(Math.sin(a) * d);
                far[n] = 1;
                shade[n++] = (byte) r.nextInt(SHADES.length);
            }
            this.ci = Arrays.copyOf(ci, n);
            this.cj = Arrays.copyOf(cj, n);
            this.shade = Arrays.copyOf(shade, n);
            this.far = Arrays.copyOf(far, n);
        }

        int life() {
            return grow > 0 ? Blood.stainTicks() * 3 + delay + grow : Blood.stainTicks();
        }
    }

    /** Filled by the network thread, drained on the client tick. */
    private static final Queue<MsgBlood> pending = new ConcurrentLinkedQueue<>();
    // ponytail: decals aren't checked against block changes, so one outlives a broken floor until it fades
    private final List<Decal> decals = new ArrayList<>();
    private final Random rand = new Random();
    private World world;
    private int ticks;

    public static void init() {
        BloodClient c = new BloodClient();
        MinecraftForge.EVENT_BUS.register(c);
        FMLCommonHandler.instance()
            .bus()
            .register(c);
    }

    public static void add(MsgBlood m) {
        pending.add(m);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        if (mc.theWorld != world) {
            world = mc.theWorld;
            decals.clear();
            pending.clear();
        }
        MsgBlood m;
        while ((m = pending.poll()) != null && world != null) {
            if (Blood.stains()) {
                decals.add(new Decal(m, ticks));
                if (decals.size() > CAP) decals.remove(0);
            }
            if (m.grow == 0 && Blood.drops()) BloodDrop.burst(world, m.x, m.y, m.z, m.size, rand);
        }
        for (int i = decals.size() - 1; i >= 0; i--) if (ticks - decals.get(i).born > decals.get(i)
            .life()) decals.remove(i);
        if (world != null && ticks % 3 == 0 && Blood.charredSmoke()) smoke(world);
    }

    private void smoke(World w) {
        List<EntityCorpse> bodies = new ArrayList<>();
        for (Object o : w.loadedEntityList) if (o instanceof EntityCorpse) bodies.add((EntityCorpse) o);
        for (Object o : w.loadedTileEntityList)
            if (o instanceof TileCorpse && ((TileCorpse) o).body != null) bodies.add(((TileCorpse) o).body);
        for (EntityCorpse c : bodies) {
            if (!c.charred() || c.ticksExisted > (c.burn() == EntityCorpse.BURNT ? SMOKE : SMOKE / 4)) continue;
            AxisAlignedBB b = c.boundingBox;
            for (int i = 0; i < 2; i++) w.spawnParticle(
                "smoke",
                b.minX + rand.nextDouble() * (b.maxX - b.minX),
                b.minY + 0.2 + rand.nextDouble() * 0.2,
                b.minZ + rand.nextDouble() * (b.maxZ - b.minZ),
                0,
                0.01,
                0);
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase v = mc.renderViewEntity;
        if (decals.isEmpty() || v == null || mc.theWorld == null) return;
        float pt = e.partialTicks;
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt,
            cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_POLYGON_BIT);
        mc.entityRenderer.enableLightmap(pt);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-2F, -2F);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (Decal d : decals) {
            double dy = d.y - cy;
            if ((d.x - cx) * (d.x - cx) + dy * dy + (d.z - cz) * (d.z - cz) > 64 * 64) continue;
            // drying away: over the last third it darkens and its pixels go from the rim in
            float age = ticks + pt - d.born, reach = Math.min(1F, (d.life() - age) / (d.life() / 3F));
            if (d.grow > 0) { // a pool: nothing yet, then spreading out, fast at first
                float g = (age - d.delay) / d.grow;
                reach *= g <= 0 ? 0 : (float) Math.sqrt(Math.min(1F, g));
            }
            if (reach <= 0F) continue;
            float dark = 1F - 0.35F * Math.min(1F, age / (Blood.stainTicks() * 1.5F));
            t.setBrightness(
                mc.theWorld.getLightBrightnessForSkyBlocks(
                    MathHelper.floor_float(d.x),
                    MathHelper.floor_float(d.y),
                    MathHelper.floor_float(d.z),
                    0));
            // the centre on the world's pixel grid, so the cells line up with the block's own texels
            int px = MathHelper.floor_float(d.x * 16), pz = MathHelper.floor_float(d.z * 16);
            for (int k = 0; k < d.far.length; k++) {
                if (d.far[k] > reach) continue;
                int c = SHADES[d.shade[k]];
                t.setColorRGBA_F(
                    (c >> 16 & 255) / 255F * dark,
                    (c >> 8 & 255) / 255F * dark,
                    (c & 255) / 255F * dark,
                    0.88F);
                double x0 = (px + d.ci[k]) / 16.0 - cx, z0 = (pz + d.cj[k]) / 16.0 - cz, x1 = x0 + 1 / 16.0,
                    z1 = z0 + 1 / 16.0;
                t.addVertex(x0, dy, z0);
                t.addVertex(x0, dy, z1);
                t.addVertex(x1, dy, z1);
                t.addVertex(x1, dy, z0);
            }
        }
        t.draw();

        mc.entityRenderer.disableLightmap(pt);
        GL11.glPopAttrib();
    }
}
