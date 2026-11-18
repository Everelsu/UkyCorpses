package com.uky.corpses;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimeFiles {

    public static Path cache(Path game) throws IOException {
        Path cache = game.resolve("ukycorpses")
            .resolve("cache");
        Path old = game.resolve(".ukycorpses");
        if (Files.isDirectory(old) && !Files.exists(cache)) {
            Files.createDirectories(cache.getParent());
            Files.move(old, cache);
        }
        Files.createDirectories(cache);
        return cache;
    }

    public static Path blood(Path game) throws IOException {
        Path root = game.resolve("ukycorpses")
            .resolve("blood"), old = game.resolve(com.uky.corpses.compat.LegacyIds.BLOOD_NAMESPACE);
        if (Files.isDirectory(old)) {
            Path destination = Files.exists(root) ? root.resolveSibling("blood-legacy-" + java.util.UUID.randomUUID())
                : root;
            Files.createDirectories(destination.getParent());
            Files.move(old, destination);
        }
        return root;
    }

    public static Path legacyBlood(Path game, String world, int dimension) throws IOException {
        String name = "dimension-" + dimension + ".stains";
        Path found = game.resolve(com.uky.corpses.compat.LegacyIds.BLOOD_NAMESPACE)
            .resolve(world)
            .resolve(name);
        if (!Files.isRegularFile(found)) found = null;
        Path parent = game.resolve("ukycorpses");
        if (Files.isDirectory(parent))
            try (java.nio.file.DirectoryStream<Path> backups = Files.newDirectoryStream(parent, "blood-legacy-*")) {
                for (Path folder : backups) {
                    Path candidate = folder.resolve(world)
                        .resolve(name);
                    if (Files.isRegularFile(candidate) && (found == null || Files.getLastModifiedTime(candidate)
                        .compareTo(Files.getLastModifiedTime(found)) > 0)) found = candidate;
                }
            }
        return found;
    }

    private RuntimeFiles() {}
}
