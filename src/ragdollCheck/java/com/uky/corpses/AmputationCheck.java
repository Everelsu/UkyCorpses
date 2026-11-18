package com.uky.corpses;

import javax.vecmath.Vector3f;

import net.minecraft.util.Vec3;

import com.uky.corpses.amp.AmputationRoll;
import com.uky.corpses.amp.Part;

public final class AmputationCheck {

    public static void run() {
        assert AmputationRoll.deadChance(Part.TORSO, 100, 0) == 0;
        assert AmputationRoll.deadChance(null, 100, 0) == 0;
        assert AmputationRoll.deadChance(Part.RIGHT_ARM, 13.9, 0) == 0;
        assert Math.abs(AmputationRoll.deadChance(Part.RIGHT_ARM, 20, 0) - .2) < 1e-7;
        assert Math.abs(AmputationRoll.deadChance(Part.RIGHT_ARM, 20, 1) - .12) < 1e-7;
        assert Math.abs(AmputationRoll.deadChance(Part.HEAD, 20, 0) - .1) < 1e-7;
        assert Math.abs(AmputationRoll.deadChance(Part.RIGHT_LEG, 20, 0) - .14) < 1e-7;
        assert Math.abs(AmputationRoll.aliveChance(Part.RIGHT_ARM, 8, true, 0) - .08) < 1e-7;
        assert AmputationRoll.aliveChance(Part.RIGHT_ARM, 8, false, 0) == 0;
        assert Math.abs(AmputationRoll.deathChance(Part.RIGHT_ARM, 8, 5, true, 0) - .45) < 1e-7;
        assert AmputationRoll.deadChance(Part.RIGHT_ARM, 10000, 0) == 1;
        for (int seed = 0; seed < 100; seed++) {
            Vector3f limb = Amputation
                .launch(new java.util.Random(seed), Part.RIGHT_ARM, Vec3.createVectorHelper(10, 0, 0));
            Vector3f head = Amputation.launch(new java.util.Random(seed), Part.HEAD, Vec3.createVectorHelper(10, 0, 0));
            assert limb.length() <= 5.0001 && head.length() <= 5.0001;
            assert Math.abs(head.y - limb.y * .55F) < 1e-6;
        }
        System.out.println(
            "AMP: original alive/death/dead probabilities, armor interpolation, part weights and launch units passed");
    }
}
