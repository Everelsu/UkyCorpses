package com.uky.corpses.corpse;

import java.util.Collections;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.uky.corpses.port.physics.BodyProperties;

/** A native collision, shared-terrain references and owner teardown exercise the single-body bridge. */
public final class SharedLooseCheck {

    public static void run() {
        RigidBody[] math = new RigidBody[6];
        for (int i = 0; i < 6; i++) {
            Transform transform = new Transform();
            transform.setIdentity();
            transform.origin.set(i == 0 ? 0 : 10 + i * 3, 4, 0);
            BoxShape shape = new BoxShape(new Vector3f(.25F, .25F, .25F));
            Vector3f inertia = new Vector3f();
            shape.calculateLocalInertia(1, inertia);
            math[i] = new RigidBody(
                new RigidBodyConstructionInfo(1, new DefaultMotionState(transform), shape, inertia));
        }
        JoltBridge bridge = new JoltBridge(math, new Generic6DofConstraint[6]);
        BodyProperties properties = new BodyProperties();
        properties.mass = 1;
        properties.linearVelocity.set(10, 0, 0);
        properties.friction = .65F;
        properties.ccdMotionThreshold = .08F;
        properties.ccdSweptSphereRadius = .12F;
        JoltBridge.Detached limb = new JoltBridge.Detached(
            bridge,
            -2,
            4,
            0,
            new Vector3f(.25F, .25F, .25F),
            new Quat4f(0, 0, 0, 1),
            properties,
            new Vector3f(),
            1);
        java.util.List<double[]> floor = Collections.singletonList(new double[] { -10, 0, -10, 35, 1, 10 });
        try {
            bridge.terrain(floor);
            limb.terrain(floor);
            Object group = field(bridge, "group");
            java.util.Map<?, ?> terrain = (java.util.Map<?, ?>) field(group, "terrain");
            assert terrain.size() == 1 : "loose and corpse terrain must share one native collider";
            assert ((Integer) field(
                terrain.values()
                    .iterator()
                    .next(),
                "references")) == 2;
            for (int tick = 0; tick < 10; tick++) bridge.step(0, false);
            Vector3f target = math[0].getCenterOfMassPosition(new Vector3f());
            assert target.x > .1F : "loose limb never collided with the main ragdoll scene";
            double[] at = new double[3];
            limb.snapshot(at, new Quat4f(), new Vector3f(), new Vector3f());
            assert Double.isFinite(at[0]);
            bridge.close();
            assert limb.body.isInWorld() : "removing last corpse destroyed its loose limb scene";
            limb.body.getPosition(new Vector3f());
            assert terrain.size() == 1 && ((Integer) field(
                terrain.values()
                    .iterator()
                    .next(),
                "references")) == 1 : "corpse teardown removed terrain still used by a loose limb";
        } finally {
            bridge.close();
            limb.close();
            limb.close();
        }
        System.out.println(
            "Shared loose limbs: real native corpse collision, terrain deduplication and independent teardown passed");
    }

    private static Object field(Object object, String name) {
        try {
            java.lang.reflect.Field field = object.getClass()
                .getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private SharedLooseCheck() {}
}
