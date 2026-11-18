package com.uky.corpses.corpse;

import net.minecraftforge.common.config.Configuration;

import com.uky.corpses.CorpseConfig;

public final class PhysicsConfig {

    public static float gravity = 15, massScale = 1, initialVelocityScale = 1, linearDamping = .1F,
        angularDamping = .15F, differentialDrag = 1, friction = .65F, restitution = 0, maxLinearSpeed = 90,
        maxAngularSpeed = 8, rangeScale = 1.5F, relaxFrequency = .3F, relaxDamping = .5F, relaxTorque = 1.5F,
        airborneScale = .15F;
    public static float waterBuoyancy = 2.2F, lavaBuoyancy = 2.2F;
    public static int ragdollLifetime = 600, solverThreads = 1, corpseLifetimeMinutes = 20;
    public static boolean playerRagdolls = true, mobRagdolls = true;
    public static float[] neck = { -40, -35, -35, 40, 55, 35 }, hip = { -55, -35, -45, 75, 35, 45 },
        shoulder = { -120, -50, -95, 120, 50, 95 };
    public static final float[] weight = { 1, 1, 1, 1, 1, 1 }, partGravity = { 1, 1, 1, 1, 1, 1 };

    public static void config(Configuration ignored) {
        CorpseConfig.Section performance = CorpseConfig
            .section("corpse.performance", "Лимиты тел и отдельные переключатели физики.");
        playerRagdolls = performance
            .on("playerRagdolls", true, "Физика игроков и их трупов. Выключение сохраняет статические трупы с вещами.");
        mobRagdolls = performance
            .on("mobRagdolls", true, "Физические тела мобов. При выключении остаются обычная смерть и добыча.");
        corpseLifetimeMinutes = performance.num(
            "corpseLifetimeMinutes",
            20,
            0,
            10080,
            "Удалить тело вместе с оставшимися вещами через столько минут игрового времени, включая выгруженные чанки. 0 — без этого лимита.");
        CorpseConfig.Section c = CorpseConfig.section("physics.solver", "Настройки физического решателя.");
        waterBuoyancy = c.num("waterBuoyancy", 2.2F, 0F, 10F, "Water lift as a multiple of gravity.");
        lavaBuoyancy = c.num("lavaBuoyancy", 2.2F, 0F, 10F, "Lava lift as a multiple of gravity.");
        gravity = c.num("gravity", 15F, 0F, 50F, "Gravity strength.");
        massScale = c.num("massScale", 1F, .01F, 100F, "Mass multiplier for all parts.");
        initialVelocityScale = c.num("initialVelocityScale", 1F, 0F, 10F, "Inherited death velocity multiplier.");
        linearDamping = c.num("linearDamping", .1F, 0F, 1F, "Loss of translation speed.");
        angularDamping = c.num("angularDamping", .15F, 0F, 1F, "Loss of rotation speed.");
        differentialDrag = c.num("differentialDrag", 1F, 0F, 4F, "Area/mass drag differences between parts.");
        friction = c.num("friction", .65F, 0F, 5F, "Part and terrain surface friction.");
        restitution = c.num("restitution", 0F, 0F, 2F, "Bounciness.");
        maxLinearSpeed = c.num("maxLinearSpeed", 90F, 1F, 500F, "Maximum blocks/second.");
        maxAngularSpeed = c.num("maxAngularSpeed", 8F, .1F, 100F, "Maximum radians/second.");
        solverThreads = c.num("solverThreads", 1, 1, 8, "Native solver worker threads; takes effect on world reload.");
        ragdollLifetime = CorpseConfig.section("corpse.lifetime", "Original body lifetime.")
            .num("ragdollLifetime", 600, 20, 2400000, "Loaded ticks to retain a fully looted body.");
        c = CorpseConfig.section("physics.joints", "Humanoid angular limits in degrees.");
        rangeScale = c.num("rangeScale", 1.5F, .1F, 4F, "Joint twist/roll range multiplier.");
        neck = limits("neck", new float[] { -40, -35, -35, 40, 55, 35 });
        hip = limits("hip", new float[] { -55, -35, -45, 75, 35, 45 });
        shoulder = limits("shoulder", new float[] { -120, -50, -95, 120, 50, 95 });
        c = CorpseConfig.section("physics.joints.jointRelaxation", "Weak joint orientation motors.");
        relaxFrequency = c.num("frequency", .3F, 0F, 10F, "Hz; neck frequency is multiplied by 16.");
        relaxDamping = c.num("damping", .5F, 0F, 2F, "Spring damping ratio.");
        relaxTorque = c.num("maxTorque", 1.5F, 0F, 100F, "Torque cap.");
        airborneScale = c.num("airborneScale", .15F, 0F, 1F, "Spring frequency scale in air.");
        String[] parts = { "head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg" };
        for (int i = 0; i < 6; i++) {
            weight[i] = CorpseConfig.section("physics.partMass", "Part weights.")
                .num(parts[i], 1F, .01F, 100F, "Mass multiplier.");
            partGravity[i] = CorpseConfig.section("physics.partGravity", "Part gravity.")
                .num(parts[i], 1F, 0F, 10F, "Gravity multiplier.");
        }
    }

    public static boolean expired(long now, long born) {
        return corpseLifetimeMinutes > 0 && now >= born && now - born >= corpseLifetimeMinutes * 1200L;
    }

    private static float[] limits(String joint, float[] defaults) {
        CorpseConfig.Section c = CorpseConfig.section("physics.joints." + joint, "Joint angular range.");
        String[] keys = { "minPitch", "minTwist", "minRoll", "maxPitch", "maxTwist", "maxRoll" };
        float[] result = new float[6];
        for (int i = 0; i < 6; i++) result[i] = c.num(keys[i], defaults[i], -180F, 180F, "Degrees.");
        for (int i = 0; i < 3; i++) if (result[i] > result[i + 3]) {
            float t = result[i];
            result[i] = result[i + 3];
            result[i + 3] = t;
        }
        return result;
    }

    private PhysicsConfig() {}
}
