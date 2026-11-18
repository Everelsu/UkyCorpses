package com.uky.corpses.port.physics;

import java.util.Locale;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.port.physics.jbullet.JBulletWorld;
import com.uky.corpses.port.physics.jolt.JoltNatives;
import com.uky.corpses.port.physics.jolt.JoltWorld;

public final class PhysicsBackends {

    public static String requestedEngine = "jolt";
    public static int solverThreads = Math.max(
        1,
        Math.min(
            4,
            Runtime.getRuntime()
                .availableProcessors() / 2));
    public static final String JOLT = "jolt";
    public static final String JBULLET = "jbullet";
    private static volatile String resolvedEngine;

    private PhysicsBackends() {}

    public static PhysicsWorld create() {
        String requested = resolvedEngine != null ? resolvedEngine : normalise(requestedEngine);
        if ("jolt".equals(requested) && loadJolt()) {
            resolvedEngine = "jolt";
            UkyCorpses.LOG.info("UkyCorpses physics engine: jolt");
            return createJolt();
        } else {
            if ("jolt".equals(requested)) {
                UkyCorpses.LOG.warn(
                    "UkyCorpses physics engine: jbullet — jolt was configured but is unavailable ({}). Ragdolls still work; they are running on the fallback engine.",
                    reasonOrUnknown());
            } else {
                UkyCorpses.LOG.info("UkyCorpses physics engine: jbullet");
            }

            resolvedEngine = "jbullet";
            return new JBulletWorld();
        }
    }

    private static String reasonOrUnknown() {
        String reason = joltFailureReason();
        return reason == null ? "unknown" : reason;
    }

    public static String resolvedEngine() {
        return resolvedEngine;
    }

    private static String normalise(String configured) {
        String value = configured == null ? ""
            : configured.trim()
                .toLowerCase(Locale.ROOT);
        if (!"jolt".equals(value) && !"jbullet".equals(value)) {
            UkyCorpses.LOG.warn(
                "Unknown physicsEngine '{}'; expected '{}' or '{}'. Using '{}'.",
                new Object[] { configured, "jolt", "jbullet", "jolt" });
            return "jolt";
        } else {
            return value;
        }
    }

    private static boolean loadJolt() {
        try {
            return JoltNatives.load();
        } catch (Throwable var1) {
            UkyCorpses.LOG.error("Jolt native loading threw", var1);
            return false;
        }
    }

    public static String joltFailureReason() {
        try {
            return JoltNatives.failureReason();
        } catch (Throwable var1) {
            return var1.getClass()
                .getSimpleName() + ": "
                + var1.getMessage();
        }
    }

    private static PhysicsWorld createJolt() {
        return new JoltWorld(solverThreads);
    }
}
