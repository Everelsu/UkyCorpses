package com.uky.corpses.stains.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;

/** Clips cached stain polygons to actual exposed block faces; normals never participate in texel snapping. */
public final class StainMesh {

    private static final double LIFT = .0015, PLANE_EPSILON = .003;
    private static final Map<Stain, Mesh> cache = new WeakHashMap<>();
    private static World world;
    private static final Map<Long, Integer> revisions = new java.util.HashMap<>();
    private static double maximumRadius = 1;
    private static final Watcher watcher = new Watcher();

    private static final class Face {

        float[] polygon;
        int light;

        Face(float[] polygon, int light) {
            this.polygon = polygon;
            this.light = light;
        }
    }

    private static final class Mesh {

        double x, y, z;
        float length, width, angle;
        Object sprite;
        double[] origin;
        int revision;
        final List<Face> faces = new ArrayList<>();

        boolean matches(Stain s) {
            return x == s.x && y == s.y
                && z == s.z
                && length == s.length
                && width == s.width
                && angle == s.angle
                && sprite == s.sprite;
        }
    }

    public static void clear() {
        cache.clear();
        revisions.clear();
        maximumRadius = 1;
        if (world != null) world.removeWorldAccess(watcher);
        world = null;
    }

    public static void draw(World level, Stain stain, Tessellator out, double cameraX, double cameraY, double cameraZ,
        float partial) {
        if (!loaded(level, stain)) {
            cache.remove(stain);
            return;
        }
        Mesh mesh = mesh(level, stain);
        if (mesh == null) return;
        drawMesh(level, stain, mesh, out, cameraX, cameraY, cameraZ, partial);
    }

    public static boolean loaded(World level, Stain stain) {
        int x = (int) Math.floor(stain.x), z = (int) Math.floor(stain.z);
        int margin = (int) Math.ceil(stain.length + stain.width) + 1;
        // Client chunkExists() always returns true in 1.7.10, including its empty placeholder.
        for (int cx = (x - margin) >> 4; cx <= (x + margin) >> 4; cx++)
            for (int cz = (z - margin) >> 4; cz <= (z + margin) >> 4; cz++)
                if (!level.getChunkFromChunkCoords(cx, cz).isChunkLoaded) return false;
        return true;
    }

    public static boolean supported(World level, Stain stain) {
        Mesh mesh = mesh(level, stain);
        return mesh != null && !mesh.faces.isEmpty();
    }

    private static Mesh mesh(World level, Stain stain) {
        if (world != level) {
            clear();
            world = level;
            level.addWorldAccess(watcher);
        }
        if (stain.sprite == null || stain.face == null) return null;
        Mesh mesh = cache.get(stain);
        if (mesh == null || !mesh.matches(stain) || mesh.revision != revision(stain.x, stain.z)) {
            mesh = build(level, stain);
            cache.put(stain, mesh);
        }
        return mesh;
    }

    private static void drawMesh(World level, Stain stain, Mesh mesh, Tessellator out, double cameraX, double cameraY,
        double cameraZ, float partial) {
        long now = level.getTotalWorldTime();
        int color = Stain.blend(stain.color, stain.dried, stain.dry(now));
        float shade = stain.face.offsetY > 0 ? 1 : stain.face.offsetY < 0 ? .5F : stain.face.offsetX != 0 ? .6F : .8F;
        out.setColorRGBA_F(
            (color >> 16 & 255) / 255F * shade,
            (color >> 8 & 255) / 255F * shade,
            (color & 255) / 255F * shade,
            stain.alpha(now));
        double[] origin = mesh.origin;
        for (Face face : mesh.faces) {
            StainShader.surface(out, stain.level(now + partial), 1 - stain.dry(now), face.light);
            int count = face.polygon.length / 4;
            // Same quad fan as the original mesher, including a degenerate last triangle for odd polygons.
            for (int i = 1; i + 1 < count; i += 2) {
                vertex(out, face.polygon, 0, origin, stain, cameraX, cameraY, cameraZ);
                vertex(out, face.polygon, i, origin, stain, cameraX, cameraY, cameraZ);
                vertex(out, face.polygon, i + 1, origin, stain, cameraX, cameraY, cameraZ);
                vertex(out, face.polygon, Math.min(i + 2, count - 1), origin, stain, cameraX, cameraY, cameraZ);
            }
        }
    }

    private static void vertex(Tessellator out, float[] polygon, int index, double[] origin, Stain stain, double cx,
        double cy, double cz) {
        int p = index * 4;
        double s = origin[0] + polygon[p], t = origin[1] + polygon[p + 1], n = origin[2];
        double x = stain.face.offsetX != 0 ? n : s, y = stain.face.offsetY != 0 ? n : t,
            z = stain.face.offsetX != 0 ? s : stain.face.offsetY != 0 ? t : n;
        out.addVertexWithUV(x - cx, y - cy, z - cz, polygon[p + 2], polygon[p + 3]);
    }

    private static Mesh build(World level, Stain stain) {
        Mesh mesh = new Mesh();
        mesh.x = stain.x;
        mesh.y = stain.y;
        mesh.z = stain.z;
        mesh.length = stain.length;
        mesh.width = stain.width;
        mesh.angle = stain.angle;
        mesh.sprite = stain.sprite;
        mesh.revision = revision(stain.x, stain.z);
        maximumRadius = Math.max(maximumRadius, stain.length + stain.width);
        double[] origin = coordinates(stain.face.offsetX, stain.face.offsetY, stain.x, stain.y, stain.z);
        float co = (float) Math.cos(stain.angle), si = (float) Math.sin(stain.angle);
        float ax = co * stain.length, ay = si * stain.length, bx = -si * stain.width, by = co * stain.width;
        float u = stain.sprite.u, v = stain.sprite.v, w = stain.sprite.w, h = stain.sprite.h;
        float[] corners = { -ax - bx, -ay - by, u, v, ax - bx, ay - by, u + w, v, ax + bx, ay + by, u + w, v + h,
            -ax + bx, -ay + by, u, v + h };
        double extentS = Math.abs(ax) + Math.abs(bx), extentT = Math.abs(ay) + Math.abs(by);
        boolean positive = stain.face.offsetX + stain.face.offsetY + stain.face.offsetZ > 0;
        int bn = (int) Math.floor(origin[2] + (positive ? -.001 : .001));
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (int bs = (int) Math.floor(origin[0] - extentS); bs <= (int) Math.floor(origin[0] + extentS); bs++)
            for (int bt = (int) Math.floor(origin[1] - extentT); bt <= (int) Math.floor(origin[1] + extentT); bt++) {
                int x = stain.face.offsetX != 0 ? bn : bs, y = stain.face.offsetY != 0 ? bn : bt,
                    z = stain.face.offsetX != 0 ? bs : stain.face.offsetY != 0 ? bt : bn;
                if (!level.blockExists(x, y, z)) continue;
                Block block = level.getBlock(x, y, z);
                boxes.clear();
                block.addCollisionBoxesToList(
                    level,
                    x,
                    y,
                    z,
                    AxisAlignedBB.getBoundingBox(x - .01, y - .01, z - .01, x + 1.01, y + 1.51, z + 1.01),
                    boxes,
                    null);
                for (AxisAlignedBB box : boxes) {
                    double[] min = coordinates(stain.face.offsetX, stain.face.offsetY, box.minX, box.minY, box.minZ),
                        max = coordinates(stain.face.offsetX, stain.face.offsetY, box.maxX, box.maxY, box.maxZ);
                    if (Math.abs((positive ? max[2] : min[2]) - origin[2]) > PLANE_EPSILON) continue;
                    boolean boundary = positive ? max[2] >= bn + 1 : min[2] <= bn;
                    if (boundary
                        && level.getBlock(x + stain.face.offsetX, y + stain.face.offsetY, z + stain.face.offsetZ)
                            .isOpaqueCube())
                        continue;
                    float[] polygon = clip(
                        corners,
                        (float) (min[0] - origin[0]),
                        (float) (max[0] - origin[0]),
                        (float) (min[1] - origin[1]),
                        (float) (max[1] - origin[1]));
                    if (polygon.length >= 12) mesh.faces.add(
                        new Face(
                            polygon,
                            level.getLightBrightnessForSkyBlocks(
                                x + (boundary ? stain.face.offsetX : 0),
                                y + (boundary ? stain.face.offsetY : 0),
                                z + (boundary ? stain.face.offsetZ : 0),
                                0)));
                }
            }
        mesh.origin = origin;
        mesh.origin[2] = lift(stain.face.offsetX + stain.face.offsetY + stain.face.offsetZ, origin[2]);
        return mesh;
    }

    static double[] coordinates(int nx, int ny, double x, double y, double z) {
        return ny != 0 ? new double[] { x, z, y } : nx != 0 ? new double[] { z, y, x } : new double[] { x, y, z };
    }

    /** Original Sutherland-Hodgman clipping, interpolating UVs together with positions. */
    static float[] clip(float[] corners, float minS, float maxS, float minT, float maxT) {
        float[] polygon = corners.clone();
        for (int edge = 0; edge < 4 && polygon.length > 0; edge++) {
            int component = edge < 2 ? 0 : 1;
            float limit = edge == 0 ? minS : edge == 1 ? maxS : edge == 2 ? minT : maxT;
            boolean above = (edge & 1) == 0;
            float[] scratch = new float[48];
            int count = polygon.length / 4, out = 0;
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                float ci = polygon[i * 4 + component], cj = polygon[j * 4 + component];
                boolean inI = above ? ci >= limit : ci <= limit, inJ = above ? cj >= limit : cj <= limit;
                if (inI) {
                    System.arraycopy(polygon, i * 4, scratch, out * 4, 4);
                    out++;
                }
                if (inI != inJ) {
                    float f = (limit - ci) / (cj - ci);
                    for (int k = 0; k < 4; k++)
                        scratch[out * 4 + k] = polygon[i * 4 + k] + (polygon[j * 4 + k] - polygon[i * 4 + k]) * f;
                    out++;
                }
            }
            polygon = java.util.Arrays.copyOf(scratch, out * 4);
        }
        return polygon;
    }

    static double lift(int sign, double plane) {
        return plane + sign * LIFT;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int revision(double x, double z) {
        return revisions.getOrDefault(key((int) Math.floor(x / 16), (int) Math.floor(z / 16)), 0);
    }

    private static void changed(int x0, int y0, int z0, int x1, int y1, int z1) {
        // Update chunk stamps rather than scanning every stain for every lighting/block notification.
        int margin = (int) Math.ceil(maximumRadius) + 1;
        for (int x = (int) Math.floor((x0 - margin) / 16.0); x <= (int) Math.floor((x1 + margin) / 16.0); x++)
            for (int z = (int) Math.floor((z0 - margin) / 16.0); z <= (int) Math.floor((z1 + margin) / 16.0); z++) {
                long key = key(x, z);
                revisions.put(key, revisions.getOrDefault(key, 0) + 1);
            }
    }

    private static final class Watcher implements IWorldAccess {

        public void markBlockForUpdate(int x, int y, int z) {
            changed(x, y, z, x, y, z);
        }

        public void markBlockForRenderUpdate(int x, int y, int z) {
            changed(x, y, z, x, y, z);
        }

        public void markBlockRangeForRenderUpdate(int x, int y, int z, int xx, int yy, int zz) {
            changed(x, y, z, xx, yy, zz);
        }

        public void playSound(String sound, double x, double y, double z, float volume, float pitch) {}

        public void playSoundToNearExcept(EntityPlayer player, String sound, double x, double y, double z, float volume,
            float pitch) {}

        public void spawnParticle(String particle, double x, double y, double z, double vx, double vy, double vz) {}

        public void onEntityCreate(Entity entity) {}

        public void onEntityDestroy(Entity entity) {}

        public void playRecord(String record, int x, int y, int z) {}

        public void broadcastSound(int sound, int x, int y, int z, int data) {}

        public void playAuxSFX(EntityPlayer player, int sound, int x, int y, int z, int data) {}

        public void destroyBlockPartially(int breaker, int x, int y, int z, int progress) {}

        public void onStaticEntitiesChanged() {}
    }

    private StainMesh() {}
}
