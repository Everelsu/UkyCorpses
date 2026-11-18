package com.uky.corpses.port.physics.jolt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** Contains no JNI type references, so the library is attached before Java links NativeLoader. */
public final class JoltLibrary {

    public static Path directory = Paths.get("")
        .toAbsolutePath();

    public static synchronized void attach() throws Exception {
        ClassLoader loader = JoltLibrary.class.getClassLoader();
        try {
            loader.getClass()
                .getMethod("addTransformerExclusion", String.class)
                .invoke(loader, "com.github.stephengold.joltjni.");
        } catch (NoSuchMethodException standalone) {}
        if (loader.getResource("com/github/stephengold/joltjni/Jolt.class") != null) return;
        Path directory = com.uky.corpses.RuntimeFiles.cache(JoltLibrary.directory)
            .resolve("libraries");
        Files.createDirectories(directory);
        Path target = directory.resolve("joltjni-6.0.0-java8.jar");
        Path temporary = Files.createTempFile(directory, "joltjni-", ".tmp");
        try {
            try (InputStream in = loader.getResourceAsStream("META-INF/libraries/joltjni-local.jar")) {
                if (in == null) throw new IOException("Bundled Jolt JNI library is missing");
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        loader.getClass()
            .getMethod("addURL", java.net.URL.class)
            .invoke(
                loader,
                target.toUri()
                    .toURL());
    }

    private JoltLibrary() {}
}
