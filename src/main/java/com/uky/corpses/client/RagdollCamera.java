package com.uky.corpses.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.Reactions;
import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class RagdollCamera {

    private static final RagdollCamera INSTANCE = new RagdollCamera();
    private static UUID owner;
    private static long life;
    private static boolean awaitingDeath;
    private static long deathDeadline;
    private static World world;
    private static EntityPlayerSP player;
    private static ViewEye eye;
    private static Object body;
    private static Skeleton.Shape lastShape;
    private static double originX, originY, originZ;
    private static float yaw, pitch, roll;
    private static final double[] position = new double[3];

    public static void init() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    /** Called with a server-issued identity, never inferred from the closest corpse or player name. */
    public static void bind(UUID identity, long generation) {
        Minecraft mc = Minecraft.getMinecraft();
        if (identity == null || generation <= 0 || mc.thePlayer == null || !identity.equals(mc.thePlayer.getUniqueID()))
            return;
        if (owner != null && owner.equals(identity) && life == generation) {
            awaitingDeath = true;
            deathDeadline = System.currentTimeMillis() + 2000;
            return;
        }
        release();
        world = mc.theWorld;
        player = mc.thePlayer;
        owner = identity;
        life = generation;
        awaitingDeath = true;
        deathDeadline = System.currentTimeMillis() + 2000;
    }

    private static long voidFade;

    public static void voidReturn(long generation) {
        Minecraft mc = Minecraft.getMinecraft();
        if (generation == life && mc.thePlayer != null && !mc.thePlayer.isEntityAlive()) voidFade = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fade(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if (event.type != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL || voidFade == 0)
            return;
        float elapsed = (System.nanoTime() - voidFade) / 1e9F;
        float alpha = Math.min(1, Math.max(0, (1.1F - elapsed) / .6F));
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        try {
            Fancy.rect(0, 0, event.resolution.getScaledWidth(), event.resolution.getScaledHeight(), 0, alpha);
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
        if (alpha == 0) voidFade = 0;
    }

    public static boolean active() {
        return eye != null;
    }

    private static void release() {
        Minecraft mc = Minecraft.getMinecraft();
        if (eye != null && mc.renderViewEntity == eye) mc.renderViewEntity = mc.thePlayer;
        if (RenderCorpse.noHead == body) {
            RenderCorpse.noHead = null;
            RenderCorpse.eyeAt = null;
            RenderCorpse.viewShift[0] = RenderCorpse.viewShift[1] = RenderCorpse.viewShift[2] = 0;
        }
        setRoll(0);
        eye = null;
        body = null;
        lastShape = null;
        owner = null;
        life = 0;
        awaitingDeath = false;
        voidFade = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP current = mc.thePlayer;
        if (current == null || mc.theWorld == null
            || world != null && world != mc.theWorld
            || player != null && player != current) {
            release();
            world = mc.theWorld;
            player = current;
            return;
        }
        float partial = event.renderTickTime;
        Skeleton.Shape shape = Reactions.physicalShape(current, partial);
        long live = shape == null ? 0 : Reactions.physicalLife(current);
        if (live > 0 && (owner == null || life != live)) {
            bind(current.getUniqueID(), live);
            awaitingDeath = false;
        }
        if (owner == null || !owner.equals(current.getUniqueID())) return;
        Object next = null;
        if (shape != null && live == life) next = current;
        else {
            shape = null;
            if (current.isEntityAlive()) {
                if (!awaitingDeath || System.currentTimeMillis() > deathDeadline) release();
                return;
            }
            for (Object candidate : world.loadedEntityList) if (candidate instanceof EntityCorpse) {
                EntityCorpse corpse = (EntityCorpse) candidate;
                if (!corpse.isDead && owner.equals(corpse.physicalOwner()) && corpse.physicalLife() == life) {
                    shape = corpse.physicalShape(partial);
                    if (shape != null) {
                        next = corpse;
                        break;
                    }
                }
            }
        }
        if (mc.gameSettings.thirdPersonView != 0) {
            if (eye != null && mc.renderViewEntity == eye) mc.renderViewEntity = current;
            eye = null;
            if (RenderCorpse.noHead == body) {
                RenderCorpse.noHead = null;
                RenderCorpse.eyeAt = null;
            }
            setRoll(0);
            return;
        }
        if (next != null) {
            body = next;
            lastShape = shape;
            net.minecraft.entity.Entity physical = (net.minecraft.entity.Entity) next;
            originX = physical.posX;
            originY = physical.posY;
            originZ = physical.posZ;
        }
        if (lastShape == null) return;
        if (eye == null) eye = new ViewEye(world, current, true, true);
        Mat4 head = lastShape.parts[Skeleton.HEAD];
        double[] anchor = head.apply(0, -4, 0, new double[3]);
        anchor[0] += originX;
        anchor[1] += originY;
        anchor[2] += originZ;
        headView(head, originX, originY, originZ, position);
        clip(world, anchor, position);
        float[] angles = angles(head);
        yaw = angles[0];
        pitch = angles[1];
        roll = angles[2];
        // Vanilla orientCamera places its view 0.1 blocks behind the view entity.
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        eye.place(
            position[0] - .1 * Math.sin(y) * Math.cos(p),
            position[1] - .1 * Math.sin(p),
            position[2] + .1 * Math.cos(y) * Math.cos(p),
            yaw,
            pitch);
        mc.renderViewEntity = eye;
        setRoll(roll);
        double hx = position[0] - anchor[0], hy = position[1] - anchor[1], hz = position[2] - anchor[2];
        RenderCorpse.noHead = hx * hx + hy * hy + hz * hz < .25 ? body : null;
        RenderCorpse.eyeAt = position;
        RenderCorpse.viewShift[0] = RenderCorpse.viewShift[1] = RenderCorpse.viewShift[2] = 0;
    }

    /** Original offset (0,+.05,-.325), converted from the original Y-up head to ModelBiped's flipped axes. */
    public static void headView(Mat4 head, double x, double y, double z, double[] out) {
        head.apply(0, -4, 0, out);
        double[] axis = head.dir(0, -1, 0, new double[3]);
        normalize(axis);
        for (int i = 0; i < 3; i++) out[i] += axis[i] * .05;
        head.dir(0, 0, -1, axis);
        normalize(axis);
        for (int i = 0; i < 3; i++) out[i] += axis[i] * .325;
        out[0] += x;
        out[1] += y;
        out[2] += z;
    }

    /** YXZ Euler decomposition of the original camera quaternion, without introducing a yaw/pitch rest preset. */
    public static float[] angles(Mat4 head) {
        double[] forward = head.dir(0, 0, -1, new double[3]);
        normalize(forward);
        double[] right = head.dir(1, 0, 0, new double[3]);
        normalize(right);
        double[] up = head.dir(0, -1, 0, new double[3]);
        normalize(up);
        double pitch = Math.asin(Math.max(-1, Math.min(1, -forward[1])));
        double yaw = Math.atan2(forward[0], forward[2]);
        double roll = Math.abs(forward[1]) < .999999 ? Math.atan2(right[1], up[1]) : 0;
        return new float[] { (float) -Math.toDegrees(yaw), (float) Math.toDegrees(pitch),
            (float) Math.toDegrees(roll) };
    }

    private static void normalize(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length < 1e-12) return;
        for (int i = 0; i < 3; i++) v[i] /= length;
    }

    private static void clip(World world, double[] anchor, double[] desired) {
        double dx = desired[0] - anchor[0], dy = desired[1] - anchor[1], dz = desired[2] - anchor[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz), allowed = length;
        if (length > 1e-6) for (int corner = 0; corner < 8; corner++) {
            double nx = ((corner & 1) * 2 - 1) * .1, ny = (((corner >> 1) & 1) * 2 - 1) * .1,
                nz = (((corner >> 2) & 1) * 2 - 1) * .1;
            Vec3 from = Vec3.createVectorHelper(anchor[0] + nx, anchor[1] + ny, anchor[2] + nz);
            MovingObjectPosition hit = world.rayTraceBlocks(
                from,
                Vec3.createVectorHelper(desired[0] + nx, desired[1] + ny, desired[2] + nz),
                false);
            if (hit != null) allowed = Math.min(allowed, Math.max(0, hit.hitVec.distanceTo(from) - .05));
        }
        double scale = length > 1e-6 ? Math.min(1, allowed / length) : 0;
        desired[0] = anchor[0] + dx * scale;
        desired[1] = anchor[1] + dy * scale;
        desired[2] = anchor[2] + dz * scale;
        if (clear(world, desired[0], desired[1], desired[2])) return;
        int[][] directions = { { 0, 1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 } };
        for (double distance = .05; distance <= .75001; distance += .05) for (int[] direction : directions) {
            double x = desired[0] + direction[0] * distance, y = desired[1] + direction[1] * distance,
                z = desired[2] + direction[2] * distance;
            if (clear(world, x, y, z)) {
                desired[0] = x;
                desired[1] = y;
                desired[2] = z;
                return;
            }
        }
    }

    private static boolean clear(World world, double x, double y, double z) {
        return world.func_147461_a(AxisAlignedBB.getBoundingBox(x - .1, y - .1, z - .1, x + .1, y + .1, z + .1))
            .isEmpty();
    }

    public static void setRoll(float angle) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            ObfuscationReflectionHelper
                .setPrivateValue(EntityRenderer.class, mc.entityRenderer, angle, "camRoll", "field_78495_O");
            ObfuscationReflectionHelper
                .setPrivateValue(EntityRenderer.class, mc.entityRenderer, angle, "prevCamRoll", "field_78505_P");
        } catch (RuntimeException ex) {
            com.uky.corpses.UkyCorpses.LOG.debug("Cannot set ragdoll camera roll", ex);
        }
    }

    @SubscribeEvent
    public void hand(RenderHandEvent event) {
        if (active()) event.setCanceled(true);
    }
}
