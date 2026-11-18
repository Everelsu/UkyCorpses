package com.uky.corpses.corpse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Vector3f;

/** Runs without a game or display: real solver, real model dimensions. */
public final class RagdollCheck {

    public static void main(String[] args) throws Exception {
        com.uky.corpses.compat.LegacyConfigCheck.run();
        JoltJava8Check.run();
        RagdollAllocationCheck.run();
        com.uky.corpses.stains.client.GearPaintCheck.run();
        float[] initial = new float[15];
        initial[3] = -.8F;
        initial[7] = .3F;
        Mat4 start = new Mat4();
        Skeleton.place(start, Poses.LIE, 35, 0, 0, Float.NaN, 0);
        start.moved(0, 2, 0);
        Ragdoll rig = new Ragdoll(start, initial, new Vector3f(1, 0, 0), Poses.LIE, 123);
        float[] born = rig.state();
        for (int i = 0; i < Skeleton.PARTS; i++) {
            assert born[i * 13 + 7] == 1 : "death momentum lost";
            for (int j = 10; j < 13; j++) assert born[i * 13 + j] == 0 : "scripted initial spin";
        }
        Ragdoll kicked = new Ragdoll(start, initial, new Vector3f(), Poses.LIE, 123);
        kicked.velocityKick(new Vector3f(3, 7, -2));
        float[] kickedState = kicked.state();
        for (int i = 0; i < Skeleton.PARTS; i++) {
            assert Math.abs(kickedState[i * 13 + 7] - 3) < .0001;
            assert Math.abs(kickedState[i * 13 + 8] - 7) < .0001;
            assert Math.abs(kickedState[i * 13 + 9] + 2) < .0001;
        }
        kicked.deathHit(Skeleton.BODY, new Vector3f(20, 8, 0));
        assert Ragdoll.validState(kicked.state()) : "whole-body impact became unstable";
        com.uky.corpses.DeathMotionCheck.run();
        FluidForcesCheck.run();
        SharedRigCheck.run();
        com.uky.corpses.mobs.physics.MobFactoryCheck.run();
        com.uky.corpses.mobs.MobEntityCheck.run();
        com.uky.corpses.mobs.client.MobMeshCheck.run();
        com.uky.corpses.CorpseLifecycleCheck.main(new String[0]);
        com.uky.corpses.OriginalGameplayCheck.main(new String[0]);
        com.uky.corpses.client.PrOverlayCheck.run();
        com.uky.corpses.client.DragArmPoseCheck.run();
        com.uky.corpses.stains.client.WoundImageCheck.run();
        com.uky.corpses.ReactionRulesCheck.main(new String[0]);
        com.uky.corpses.pc.PcCheck.run();
        com.uky.corpses.core.CoreHooksCheck.run();
        assert !com.uky.corpses.port.physics.jolt.JoltNatives.supportsRuntime("1.7");
        assert com.uky.corpses.port.physics.jolt.JoltNatives.supportsRuntime("1.8");
        assert com.uky.corpses.port.physics.jolt.JoltNatives.supportsRuntime("17");
        assert !com.uky.corpses.port.physics.jolt.JoltNatives.supportsRuntime("unknown");
        com.uky.corpses.stains.SurfaceBloodCheck.main(new String[0]);
        com.uky.corpses.stains.client.WoundCanvasCheck.run();
        com.uky.corpses.stains.client.SourceSprayCheck.run();
        com.uky.corpses.stains.client.SourceColliderCheck.run();
        com.uky.corpses.stains.client.StainMeshCheck.run();
        com.uky.corpses.stains.client.StainPlacementCheck.run();
        com.uky.corpses.client.RagdollCameraCheck.run();
        Mat4 frame = new Mat4(start);
        float[] angles = initial.clone();
        rig.pose(0, frame, angles, 0, 0, 0);
        same(start, frame, .00001);
        for (int i = 0; i < angles.length; i++) assert Math.abs(initial[i] - angles[i]) < .00001 : "birth angle " + i;
        // Falling before contact proves gravity is doing work, rather than replaying the target pose.
        double initialY = chest(frame);
        rig.terrain(Collections.emptyList());
        for (int t = 0; t < 8; t++) rig.tick();
        rig.pose(1, frame, angles, 0, 0, 0);
        assert chest(frame) < initialY - .5 : "gravity";

        // Floor, wall, and half-block step. Replacing terrain repeatedly must not leak static bodies.
        List<double[]> terrain = Arrays.asList(
            new double[] { -10, -1, -10, 10, 0, 10 },
            new double[] { 2, 0, -3, 3, 4, 3 },
            new double[] { -3, 0, -3, -1, .5, 3 });
        boolean impact = false;
        for (int t = 8; t < 75; t++) {
            rig.terrain(terrain);
            impact |= rig.tick() > 0;
            rig.pose(1, frame, angles, 0, 0, 0);
            assert Double.isFinite(chest(frame)) && Math.abs(chest(frame)) < 8 : "unstable torso";
            for (float a : angles) assert Float.isFinite(a) : "unstable joint";
            Skeleton.Shape shape = rig.shape(1, 0, 0, 0);
            for (int i = 0; i < Skeleton.PARTS; i++) {
                double[] b = shape.bounds(i);
                assert b[1] > -.25 : "part went through floor: " + i + " y=" + b[1];
                assert b[3] < 2.25 : "part went through wall: " + i;
            }
        }
        assert impact : "floor contact missing";
        // A physical rest pose persists instead of returning to a preset after 110 ticks.
        for (int t = 0; t < 240; t++) rig.tick();
        assert rig.active();
        float[] saved = rig.state();
        assert Ragdoll.validState(saved);
        Ragdoll restored = new Ragdoll(saved, rig.severed());
        sameShape(rig.shape(1, 0, 0, 0), restored.shape(1, 0, 0, 0));
        // Network snapshots change only when the server sends one, with normalized interpolation.
        restored.applyState(saved, 0);
        sameShape(rig.shape(1, 0, 0, 0), restored.shape(1, 0, 0, 0));
        float[] invalid = saved.clone();
        invalid[3] = Float.NaN;
        assert !Ragdoll.validState(invalid);
        invalid = saved.clone();
        Arrays.fill(invalid, 3, 7, 0);
        assert !Ragdoll.validState(invalid);
        assert !Ragdoll.validState(new float[77]);
        // Severing releases one constraint. Its transform survives saving and differs from its shoulder.
        assert restored.sever(Skeleton.RARM, new Vector3f(0, 12, -35));
        assert !restored.sever(Skeleton.BODY, new Vector3f());
        assert !restored.sever(Skeleton.RARM, new Vector3f());
        restored.terrain(terrain);
        for (int t = 0; t < 20; t++) restored.tick();
        Skeleton.Shape detached = restored.shape(1, 0, 0, 0);
        Skeleton.Shape projected = Skeleton.snapshot(detached.frame, detached.angles);
        double[] arm = detached.bounds(Skeleton.RARM), expected = projected.bounds(Skeleton.RARM);
        double separation = 0;
        for (int i = 0; i < 3; i++) separation += Math.pow(arm[i] - expected[i], 2);
        assert separation > .04 : "detached arm snapped to shoulder";
        sameShape(detached, new Ragdoll(restored.state(), restored.severed()).shape(1, 0, 0, 0));
        // The renderer's compensation reproduces each independent part matrix exactly.
        for (int part = 0; part < Skeleton.PARTS; part++) {
            Mat4 local = Skeleton.part(new Mat4(), part, detached.angles, new Mat4());
            Mat4 inverse = new Mat4();
            assert local.invert(inverse);
            Mat4 composed = new Mat4(detached.parts[part]);
            composed.mul(inverse.m);
            composed.mul(local.m);
            same(detached.parts[part], composed, .00001);
        }
        double oldX = detached.parts[Skeleton.HEAD].apply(0, -4, 0, new double[3])[0];
        restored.hold(Skeleton.HEAD, new Vector3f((float) oldX + 2, 1, 0));
        for (int t = 0; t < 20; t++) restored.tick();
        restored.hold(-1, null);
        assert restored.shape(1, 0, 0, 0)
            .bounds(Skeleton.HEAD)[3] < 2.25 : "drag tunneled through wall";
        restored.terrain(java.util.Collections.singletonList(new double[] { -10, -1, -10, 10, 0, 10 }));
        double[] oldHead = restored.shape(1, 0, 0, 0).parts[Skeleton.HEAD].apply(0, -4, 0, new double[3]);
        restored
            .hold(Skeleton.HEAD, new Vector3f((float) oldHead[0], (float) oldHead[1] + .2F, (float) oldHead[2] - 2));
        for (int t = 0; t < 25; t++) restored.tick();
        restored.hold(-1, null);
        double[] newHead = restored.shape(1, 0, 0, 0).parts[Skeleton.HEAD].apply(0, -4, 0, new double[3]);
        assert newHead[2] < oldHead[2] - .2 : "drag failed in open space: " + oldHead[2] + " -> " + newHead[2];
        Skeleton.place(start.identity(), Poses.PRONE, 0, 1, 0, Float.NaN, 0);
        Ragdoll isolated = new Ragdoll(start, new float[15], new Vector3f(), Poses.PRONE, 99);
        isolated.terrain(Collections.singletonList(new double[] { -20, -1, -20, 20, 0, 20 }));
        assert isolated.sever(Skeleton.RARM, new Vector3f());
        for (int tick = 0; tick < 100; tick++) isolated.tick();
        float[] beforeDrag = isolated.state();
        int looseArm = Skeleton.RARM * 13, chestPart = Skeleton.BODY * 13;
        isolated.hold(
            Skeleton.RARM,
            new Vector3f(beforeDrag[looseArm] - 3, beforeDrag[looseArm + 1], beforeDrag[looseArm + 2]));
        for (int tick = 0; tick < 40; tick++) isolated.tick();
        float[] afterDrag = isolated.state();
        assert afterDrag[looseArm] < beforeDrag[looseArm] - .3F : "detached arm must move";
        assert Math.abs(afterDrag[chestPart] - beforeDrag[chestPart])
            < .15F : "dragging detached arm must not tow torso";
        isolated.hold(-1, null);
        isolated.disposePhysics();
        // A contact impulse must topple a standing body without a scripted spin.
        for (int pose : new int[] { Poses.LIE, Poses.PRONE, Poses.SIDE }) {
            Skeleton.place(start.identity(), pose, 0, 0, 0, Float.NaN, 0);
            rig = new Ragdoll(start, new float[15], new Vector3f(), pose, 41);
            rig.terrain(terrain);
            rig.impulseAt(Skeleton.BODY, new Vector3f(0, 0, 20), new Vector3f(0, .3F, 0));
            for (int t = 0; t < 70; t++) rig.tick();
            rig.pose(1, frame, angles, 0, 0, 0);
            assert chest(frame) < .8 : "did not collapse: " + pose + " y=" + chest(frame);
        }
        com.uky.corpses.ReactionCheck.main(new String[0]);
        com.uky.corpses.AmputationCheck.run();
        com.uky.corpses.amp.LivingAmputationCheck.run();
        com.uky.corpses.corpse.SharedLooseCheck.run();
        Ragdoll amputated = new Ragdoll(start, initial, new Vector3f(), Poses.LIE, 1);
        amputated.hideParts((1 << Skeleton.RARM) | (1 << Skeleton.HEAD));
        amputated.deathHit(Skeleton.BODY, new Vector3f(10, 5, 0));
        amputated.terrain(Collections.emptyList());
        amputated.tick();
        assert Ragdoll.validState(amputated.state()) : "absent living limb must not corrupt corpse inertia";
        assert amputated.absent(Skeleton.RARM) && amputated.absent(Skeleton.HEAD);
        amputated.disposePhysics();
        com.uky.corpses.RagdollPacketCheck.run(saved);
        System.out.println(
            "Ragdoll: birth, gravity, contacts, persistent state, snapshots, severing, dragging, render matrices passed");
    }

    private static void sameShape(Skeleton.Shape a, Skeleton.Shape b) {
        same(a.frame, b.frame, .00001);
        for (int i = 0; i < Skeleton.PARTS; i++) same(a.parts[i], b.parts[i], .00001);
    }

    private static double chest(Mat4 frame) {
        return frame.apply(0, 6 / 16.0, 0, new double[3])[1];
    }

    private static void same(Mat4 a, Mat4 b, double tolerance) {
        for (int i = 0; i < 16; i++) assert Math.abs(a.m[i] - b.m[i]) < tolerance : "frame component " + i;
    }
}
