package com.uky.corpses.port.physics.jolt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.apache.logging.log4j.Logger;

import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.TempAllocator;
import com.github.stephengold.joltjni.TempAllocatorImpl;
import com.uky.corpses.UkyCorpses;

public final class NativeLoader {

    public static final Logger LOGGER = UkyCorpses.LOG;
    public static final String JOLT_JNI_VERSION = "6.0.0";
    private static final String RESOURCE_ROOT = "com/github/stephengold/";
    private static final int TEMP_ALLOCATOR_BYTES = 33554432;
    private static volatile boolean attempted;
    private static volatile boolean ready;
    private static volatile String failureReason;
    private static volatile Path gameDirectory;
    private static TempAllocator sharedTempAllocator;
    private static JobSystem sharedJobSystem;

    private NativeLoader() {}

    public static boolean isReady() {
        return ready;
    }

    public static String failureReason() {
        return failureReason;
    }

    public static void setGameDirectory(Path directory) {
        if (directory != null) {
            gameDirectory = directory;
        }
    }

    public static synchronized boolean load() {
        if (attempted) {
            return ready;
        } else {
            attempted = true;

            try {
                ClassLoader ours = NativeLoader.class.getClassLoader();
                // Keep JNI classes outside Minecraft transformers, preserving native method names.
                try {
                    ours.getClass()
                        .getMethod("addTransformerExclusion", String.class)
                        .invoke(ours, "com.github.stephengold.joltjni.");
                } catch (NoSuchMethodException ignored) {
                    // Standalone physics checks use an ordinary JVM class loader.
                }
                NativeLoader.Platform platform = NativeLoader.Platform.detect();
                if (platform == null) {
                    return fail(
                        "no Jolt native is bundled for os.name=" + System.getProperty("os.name")
                            + " os.arch="
                            + System.getProperty("os.arch"));
                } else {
                    Path extracted = extract(platform);
                    System.load(
                        extracted.toAbsolutePath()
                            .toString());
                    ClassLoader jolts = Jolt.class.getClassLoader();
                    if (ours != jolts) {
                        return fail(
                            "jolt-jni classes were loaded by " + jolts
                                + " but the native library is bound to "
                                + ours
                                + ". Another mod probably bundles its own jolt-jni; only UkyCorpses may.");
                    } else {
                        Jolt.registerDefaultAllocator();
                        Jolt.installDefaultTraceCallback();
                        if (!Jolt.newFactory()) {
                            return fail("Jolt.newFactory() returned false");
                        } else {
                            Jolt.registerTypes();
                            if (!JoltPhysicsObject.isCleanerStarted()) JoltPhysicsObject.startCleaner();
                            ready = true;
                            LOGGER
                                .info("UkyCorpses: Jolt physics ready ({}, {})", safeVersion(), platform.resourcePath);
                            return true;
                        }
                    }
                }
            } catch (Throwable var4) {
                LOGGER.error("UkyCorpses: failed to load the Jolt natives", var4);
                return fail(
                    var4.getClass()
                        .getSimpleName() + ": "
                        + var4.getMessage());
            }
        }
    }

    public static String versionString() {
        return ready ? safeVersion() : "unavailable";
    }

    public static synchronized TempAllocator sharedTempAllocator() {
        requireReady();
        if (sharedTempAllocator == null) {
            sharedTempAllocator = new TempAllocatorImpl(33554432);
        }

        return sharedTempAllocator;
    }

    public static synchronized JobSystem sharedJobSystem() {
        requireReady();
        if (sharedJobSystem == null) {
            int threads = Math.max(
                1,
                Math.min(
                    4,
                    Runtime.getRuntime()
                        .availableProcessors() / 2));
            sharedJobSystem = new JobSystemThreadPool(2048, 8, threads);
        }

        return sharedJobSystem;
    }

    private static void requireReady() {
        if (!ready) {
            throw new IllegalStateException("Jolt is not loaded: call NativeLoader.load() first");
        }
    }

    private static String safeVersion() {
        try {
            return Jolt.versionString();
        } catch (Throwable var1) {
            return "unknown";
        }
    }

    private static boolean fail(String reason) {
        failureReason = reason;
        ready = false;
        LOGGER.error("UkyCorpses: Jolt physics unavailable: {}", reason);
        return false;
    }

    private static Path extract(NativeLoader.Platform platform) throws IOException {
        Path directory = com.uky.corpses.RuntimeFiles.cache(resolveGameDirectory())
            .resolve("natives")
            .resolve("6.0.0");
        Files.createDirectories(directory);
        Path target = directory.resolve(platform.libraryFileName);

        try (InputStream in = NativeLoader.class.getClassLoader()
            .getResourceAsStream(platform.resourcePath)) {
            if (in == null) {
                throw new IOException("bundled native not found on the classpath: " + platform.resourcePath);
            }

            if (Files.exists(target)) {
                long bundledSize = sizeOf(platform.resourcePath);
                if (bundledSize >= 0L && Files.size(target) == bundledSize) {
                    return target;
                }
            }

            Path temporary = directory.resolve(platform.libraryFileName + ".tmp");

            try (OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                for (int count; (count = in.read(buffer)) != -1;) out.write(buffer, 0, count);
            }

            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException var11) {
                Files.deleteIfExists(temporary);
                if (!Files.exists(target)) {
                    throw var11;
                }
            }
        }

        return target;
    }

    static Path resolveGameDirectory() {
        Path configured = gameDirectory;
        if (configured != null) {
            return configured;
        } else {
            String[][] candidates = new String[][] { { "net.neoforged.fml.loading.FMLPaths", "GAMEDIR" },
                { "net.minecraftforge.fml.loading.FMLPaths", "GAMEDIR" } };

            for (String[] candidate : candidates) {
                try {
                    Class<?> paths = Class.forName(candidate[0]);
                    Object gameDir = paths.getField(candidate[1])
                        .get(null);
                    Object path = gameDir.getClass()
                        .getMethod("get")
                        .invoke(gameDir);
                    if (path instanceof Path) {
                        return (Path) path;
                    }
                } catch (Throwable var11) {}
            }

            try {
                Class<?> fabric = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object loader = fabric.getMethod("getInstance")
                    .invoke(null);
                Object path = fabric.getMethod("getGameDir")
                    .invoke(loader);
                if (path instanceof Path) {
                    return (Path) path;
                }
            } catch (Throwable var10) {}

            return Paths.get("")
                .toAbsolutePath();
        }
    }

    private static long sizeOf(String resourcePath) {
        try {
            long var6;
            try (InputStream in = NativeLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return -1L;
                }

                long total = 0L;
                byte[] buffer = new byte[16384];

                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += (long) read;
                }

                var6 = total;
            }

            return var6;
        } catch (IOException var10) {
            return -1L;
        }
    }

    private static final class Platform {

        final String resourcePath, libraryFileName;

        private Platform(String resourcePath, String libraryFileName) {
            this.resourcePath = resourcePath;
            this.libraryFileName = libraryFileName;
        }

        static NativeLoader.Platform detect() {
            String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "")
                .toLowerCase(Locale.ROOT);
            boolean x64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
            boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
            if (os.contains("win")) {
                return x64 ? of("windows/x86-64/", "joltjni.dll") : null;
            } else if (!os.contains("mac") && !os.contains("darwin")) {
                if (!os.contains("linux")) {
                    return null;
                } else if (x64) {
                    return of("linux/x86-64/", "libjoltjni.so");
                } else {
                    return arm64 ? of("linux/aarch64/", "libjoltjni.so") : null;
                }
            } else if (x64) {
                return of("osx/x86-64/", "libjoltjni.dylib");
            } else {
                return arm64 ? of("osx/aarch64/", "libjoltjni.dylib") : null;
            }
        }

        private static NativeLoader.Platform of(String directory, String library) {
            return new NativeLoader.Platform(directory + "com/github/stephengold/" + library, library);
        }
    }
}
