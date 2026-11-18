package com.uky.corpses.stains.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.util.ForgeDirection;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

/** Client stain cache; replace only after a complete write, bounded records on read. */
final class StainSaves {

    private static final String[] FAMILIES = { "splat", "pool", "run", "print_shoe_l", "print_shoe_r", "print_square",
        "print_hoof", "print_hoof_big", "print_paw", "print_claw", "print_dot", "print_square_small", "crown", "drop",
        "streak", "streak_diag" };

    static String worldKey() {
        Minecraft mc = Minecraft.getMinecraft();
        String name = mc.isSingleplayer() && mc.getIntegratedServer() != null ? "world:" + mc.getIntegratedServer()
            .getFolderName() : mc.func_147104_D() == null ? null : "server:" + mc.func_147104_D().serverIP;
        if (name == null) return null;
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (byte b : hash) s.append(String.format("%02x", b & 255));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path file(String world, int dimension) {
        Path game = Minecraft.getMinecraft().mcDataDir.toPath();
        Path root = game.resolve("ukycorpses")
            .resolve("blood");
        try {
            root = com.uky.corpses.RuntimeFiles.blood(game);
        } catch (IOException failure) {
            UkyCorpses.LOG.warn("Cannot migrate the blood directory", failure);
        }
        return root.resolve(world)
            .resolve("dimension-" + dimension + ".stains");
    }

    static void save(List<Stain> stains, String world, int dimension, long now) {
        if (world == null) return;
        Path target = file(world, dimension), temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
                out.writeInt(0x43525552);
                out.writeInt(2);
                out.writeInt(stains.size());
                for (Stain s : stains) {
                    int family = 0;
                    for (int i = 0; i < FAMILIES.length; i++) if (FAMILIES[i].equals(s.family)) family = i;
                    out.writeByte(s.face.ordinal());
                    out.writeByte(family);
                    out.writeInt(StainSprites.variant(s.family, s.sprite));
                    out.writeDouble(s.x);
                    out.writeDouble(s.y);
                    out.writeDouble(s.z);
                    out.writeFloat(s.length);
                    out.writeFloat(s.width);
                    out.writeFloat(s.angle);
                    out.writeFloat(s.opacity);
                    out.writeFloat(s.wash);
                    out.writeInt(s.color);
                    out.writeInt(s.dried);
                    out.writeLong(Math.max(0, now - s.born));
                    out.writeBoolean(s.print);
                    out.writeByte((s.pooled ? 1 : 0) | (s.ran ? 2 : 0) | (s.soaked ? 4 : 0));
                    out.writeByte(s.surface.ordinal());
                    out.writeFloat(s.poolArea);
                    out.writeFloat(s.levelFrom);
                    out.writeFloat(s.levelTo);
                    out.writeLong(now - s.rampStart);
                    out.writeInt(s.rampTicks);
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            UkyCorpses.LOG.warn("Cannot save SurfaceBlood stains {}", target, e);
        }
    }

    private static void removeEmpty(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return;
        try (java.nio.file.DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
            if (children.iterator()
                .hasNext()) return;
        }
        Files.deleteIfExists(folder);
    }

    static void load(List<Stain> stains, String world, int dimension, long now) {
        if (world == null) return;
        Path target = file(world, dimension);
        if (!Files.isRegularFile(target)) {
            Path old;
            try {
                old = com.uky.corpses.RuntimeFiles
                    .legacyBlood(Minecraft.getMinecraft().mcDataDir.toPath(), world, dimension);
            } catch (IOException failure) {
                UkyCorpses.LOG.warn("Cannot find legacy blood cache", failure);
                old = null;
            }
            if (old != null && Files.isRegularFile(old)) try {
                Files.createDirectories(target.getParent());
                Files.move(old, target);
                removeEmpty(old.getParent());
                removeEmpty(
                    old.getParent()
                        .getParent());
            } catch (IOException failure) {
                UkyCorpses.LOG.warn("Cannot migrate blood cache {}", old, failure);
                if (!Files.isRegularFile(target)) target = old;
            }
        }
        if (!Files.isRegularFile(target)) return;
        List<Stain> loaded = new ArrayList<>();
        try {
            if (Files.size(target) > 16 * 1024 * 1024) throw new IOException("Stain cache too large");
            try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(target))))) {
                if (in.readInt() != 0x43525552) throw new IOException("Unknown stain cache format");
                int version = in.readInt();
                if (version != 1 && version != 2) throw new IOException("Unknown stain cache version");
                int count = in.readInt();
                if (count < 0 || count > 100000) throw new IOException("Invalid stain count");
                for (int i = 0; i < count; i++) {
                    Stain s = new Stain();
                    int face = in.readUnsignedByte(), family = in.readUnsignedByte();
                    if (face >= 6 || family >= FAMILIES.length) throw new IOException("Invalid stain surface");
                    int variant = version >= 2 ? in.readInt() : -1;
                    s.face = ForgeDirection.getOrientation(face);
                    s.family = FAMILIES[family];
                    s.x = in.readDouble();
                    s.y = in.readDouble();
                    s.z = in.readDouble();
                    s.length = in.readFloat();
                    s.width = in.readFloat();
                    s.angle = in.readFloat();
                    s.opacity = in.readFloat();
                    s.wash = in.readFloat();
                    s.color = in.readInt();
                    s.dried = in.readInt();
                    long age = in.readLong();
                    s.born = now - age;
                    s.print = in.readBoolean();
                    if (version >= 2) {
                        int flags = in.readUnsignedByte(), surface = in.readUnsignedByte();
                        if (flags > 7 || surface >= Surface.values().length)
                            throw new IOException("Invalid stain flags");
                        s.pooled = (flags & 1) != 0;
                        s.ran = (flags & 2) != 0;
                        s.soaked = (flags & 4) != 0;
                        s.surface = Surface.values()[surface];
                        s.poolArea = in.readFloat();
                        s.levelFrom = in.readFloat();
                        s.levelTo = in.readFloat();
                        s.rampStart = now - in.readLong();
                        s.rampTicks = in.readInt();
                        if (!Float.isFinite(s.poolArea) || s.poolArea < 0
                            || !Float.isFinite(s.levelFrom)
                            || s.levelFrom < -1
                            || s.levelFrom > 1
                            || !Float.isFinite(s.levelTo)
                            || s.levelTo < -1
                            || s.levelTo > 1
                            || Math.abs((long) s.rampTicks) > 2400000) throw new IOException("Invalid stain spread");
                    } else {
                        s.pooled = s.family.equals("pool");
                        s.ran = true;
                        s.poolArea = (float) (Math.PI * s.length * s.width);
                    }
                    if (!Double.isFinite(s.x) || !Double.isFinite(s.y)
                        || !Double.isFinite(s.z)
                        || !Float.isFinite(s.length)
                        || !Float.isFinite(s.width)
                        || !Float.isFinite(s.angle)
                        || !Float.isFinite(s.opacity)
                        || !Float.isFinite(s.wash)
                        || s.length < 0
                        || s.length > 4
                        || s.width < 0
                        || s.width > 4
                        || age < 0) throw new IOException("Invalid stain geometry");
                    if (i < SurfaceBloodClientConfig.MAX_STAINS.get() && s.alpha(now) > 0) {
                        s.sprite = variant >= 0 ? StainSprites.variant(s.family, variant)
                            : StainSprites.choose(s.family, new java.util.Random(i));
                        if (version == 1 && !s.print
                            && !s.pooled
                            && SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL) {
                            s.sprite = StainSprites.nearest(
                                s.family,
                                Math.max(1, Math.round(Math.min(s.length, s.width) * 40)),
                                new java.util.Random(i));
                            s.length = s.sprite.texelWidth / 32;
                            s.width = s.sprite.texelHeight / 32;
                        }
                        loaded.add(s);
                    }
                }
            }
            stains.addAll(loaded);
        } catch (IOException | RuntimeException e) {
            UkyCorpses.LOG.warn("Cannot load SurfaceBlood stains {}", target, e);
        }
    }

    private StainSaves() {}
}
