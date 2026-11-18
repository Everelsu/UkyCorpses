package com.uky.corpses.corpse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.uky.corpses.mobs.physics.MobRigType;
import com.uky.corpses.mobs.physics.RagdollBodyFactory;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;

public final class SharedRigCheck {

    public static void run() {
        RigidBody[] math = new RigidBody[6];
        for (int i = 0; i < 6; i++) {
            Transform t = new Transform();
            t.setIdentity();
            t.origin.set(i == 0 ? 0 : 10 + i * 3, 4, 0);
            BoxShape shape = new BoxShape(new Vector3f(.25F, .25F, .25F));
            Vector3f inertia = new Vector3f();
            shape.calculateLocalInertia(1, inertia);
            math[i] = new RigidBody(new RigidBodyConstructionInfo(1, new DefaultMotionState(t), shape, inertia));
        }
        JoltBridge player = new JoltBridge(math, new Generic6DofConstraint[6]);
        JoltBridge.Rig mob = new JoltBridge.Rig(player, 0, 2.8, 0);
        List<PhysicsBody> parts = new ArrayList<>();
        List<PhysicsConstraint> joints = new ArrayList<>();
        mob.own(parts, joints);
        try {
            RagdollBodyFactory.build(
                mob.physics(),
                parts,
                joints,
                MobRigType.CREEPER,
                mob.offset,
                new Quat4f(0, 0, 0, 1),
                1,
                new Vector3f(0, 4, 0),
                null);
            List<double[]> floor = Collections.singletonList(new double[] { -10, 0, -10, 35, 1, 10 });
            player.terrain(floor);
            mob.terrain(floor);
            for (int i = 0; i < 6; i++) player.step(0, false);
            assert math[0].getLinearVelocity(new Vector3f()).y > -3 : "mob bodies never contacted the player scene";
            assert RagdollWorld.RigScene.validState(mob.snapshot());
            float[] saved = mob.snapshot();
            assert mob.restore(saved);
            saved[3] = Float.NaN;
            assert !mob.restore(saved);
            mob.hidePart(1);
            player.step(0, false);
            assert RagdollWorld.RigScene.validState(mob.snapshot());
            player.close();
            assert parts.get(0)
                .isInWorld() : "last player tore down a live mob scene";
        } finally {
            player.close();
            mob.close();
            mob.close();
        }
        assert mob.snapshot().length > 0 : "closed rig lost its safe cached snapshot";
        System.out.println(
            "Shared mob rigs: real player contacts, snapshot validation, missing parts and independent lifetime passed");
    }
}
