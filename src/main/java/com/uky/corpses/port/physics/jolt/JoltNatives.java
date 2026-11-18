package com.uky.corpses.port.physics.jolt;

import com.uky.corpses.UkyCorpses;

public final class JoltNatives {

    public static final String JOLT_JNI_VERSION = "6.0.0";
    private static boolean reported, attached;
    private static String bootstrapFailure;

    private JoltNatives() {}

    public static boolean isReady() {
        return runtimeProblem() == null && attached && NativeLoader.isReady();
    }

    public static String failureReason() {
        String problem = runtimeProblem();
        return problem != null ? problem
            : bootstrapFailure != null ? bootstrapFailure
                : attached ? NativeLoader.failureReason() : "Jolt has not been loaded";
    }

    /** Checked before NativeLoader or any JNI class is resolved. */
    public static boolean supportsRuntime(String version) {
        if (version == null) return false;
        try {
            if (version.startsWith("1.")) version = version.substring(2);
            int dot = version.indexOf('.');
            if (dot >= 0) version = version.substring(0, dot);
            return Integer.parseInt(version) >= 8;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static String runtimeProblem() {
        String version = System.getProperty("java.specification.version", "unknown");
        return supportsRuntime(version) ? null
            : "Jolt 6.0.0 requires Java 8 or newer; current Java runtime: " + version;
    }

    public static synchronized boolean load() {
        String problem = runtimeProblem();
        if (problem != null) {
            if (!reported) UkyCorpses.LOG.error(problem);
            reported = true;
            return false;
        }
        try {
            JoltLibrary.attach();
            attached = true;
        } catch (Exception failure) {
            bootstrapFailure = "Cannot attach bundled Jolt JNI: " + failure;
            UkyCorpses.LOG.error(bootstrapFailure, failure);
            return false;
        }
        boolean ready = NativeLoader.load();
        if (!reported) {
            reported = true;
            if (ready) {
                UkyCorpses.LOG.info("Jolt physics ready via UkyCorpses: {}", NativeLoader.versionString());
            } else {
                UkyCorpses.LOG.error("Jolt physics unavailable ({})", NativeLoader.failureReason());
            }
        }

        return ready;
    }
}
