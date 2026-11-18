package com.uky.corpses.mobs.physics;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;
import com.uky.corpses.port.physics.PhysicsWorld;
import com.uky.corpses.port.physics.StepQuality;
import com.uky.corpses.port.physics.jolt.JoltNatives;
import com.uky.corpses.port.physics.jolt.JoltWorld;

/** Native checks against the installed factory's authored rig dimensions and body topology. */
public final class MobFactoryCheck {

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        assert JoltNatives.load() : JoltNatives.failureReason();
        MobRigType[] types = { MobRigType.HUMANOID_STANDARD, MobRigType.HUMANOID_SKELETON, MobRigType.ILLAGER,
            MobRigType.WITCH, MobRigType.CREEPER, MobRigType.QUADRUPED, MobRigType.CHICKEN, MobRigType.WOLF,
            MobRigType.EQUINE, MobRigType.IRON_GOLEM, MobRigType.ENDERMAN, MobRigType.SLIME, MobRigType.MAGMA_CUBE,
            MobRigType.SILVERFISH, MobRigType.SNOW_GOLEM, MobRigType.BLAZE, MobRigType.SPIDER, MobRigType.GHAST,
            MobRigType.SQUID, MobRigType.WITHER, MobRigType.ENDER_DRAGON, MobRigType.BAT };
        for (MobRigType type : types) {
            PhysicsWorld world = new JoltWorld(1);
            world.setGravity(0, -15, 0);
            try {
                List<PhysicsBody> parts = new ArrayList<>();
                List<PhysicsConstraint> joints = new ArrayList<>();
                RagdollBodyFactory.build(
                    world,
                    parts,
                    joints,
                    type,
                    new Vector3f(0, 10, 0),
                    new Quat4f(0, 0, 0, 1),
                    1,
                    new Vector3f(1, 0, 0),
                    null);
                int expected = type == MobRigType.SPIDER || type == MobRigType.GHAST || type == MobRigType.ENDER_DRAGON
                    ? 10
                    : type == MobRigType.SQUID ? 9 : 6;
                assert parts.size() == expected : type + " parts " + parts.size();
                assert joints.size() == expected - 1 : type + " joints " + joints.size();
                if (type == MobRigType.HUMANOID_STANDARD) {
                    assert close(
                        1 / parts.get(0)
                            .getInvMass(),
                        18);
                    assert close(
                        1 / parts.get(1)
                            .getInvMass(),
                        2.4F);
                    assert close(
                        1 / parts.get(2)
                            .getInvMass(),
                        4.6F);
                    assert close(
                        1 / parts.get(4)
                            .getInvMass(),
                        1.4F);
                }
                if (type == MobRigType.HUMANOID_STANDARD) {
                    MobDynamics.velocityKick(parts, new Vector3f(3, 5, -2), null);
                    for (PhysicsBody part : parts) {
                        Vector3f kick = new Vector3f();
                        part.getLinearVelocity(kick);
                        assert close(kick.x, 4) && close(kick.y, 5)
                            && close(kick.z, -2) : "global kick is velocity, independent of mass";
                    }
                    Vector3f point = new Vector3f();
                    parts.get(1)
                        .getPosition(point);
                    MobDynamics.hit(parts, 1, new Vector3f(20, 8, 0), point, Float.NaN);
                    Vector3f torso = new Vector3f();
                    parts.get(0)
                        .getAngularVelocity(torso);
                    assert torso.length() > .01 : "head hit must transfer spin to torso";
                    parts.get(0)
                        .setLinearVelocity(new Vector3f(0, -3, 0));
                    assert MobDynamics.prepare(parts, joints, false);
                    parts.get(0)
                        .setLinearVelocity(new Vector3f(0, -1.5F, 0));
                    assert MobDynamics.prepare(parts, joints, true) : "airborne hysteresis";
                    parts.get(0)
                        .setLinearVelocity(new Vector3f(0, -.9F, 0));
                    assert !MobDynamics.prepare(parts, joints, true);
                }
                Vector3f start = new Vector3f();
                parts.get(0)
                    .getPosition(start);
                for (int i = 0; i < 12; i++) {
                    MobDynamics.prepare(parts, joints, false);
                    world.step(.05F, StepQuality.HIGH);
                }
                Vector3f point = new Vector3f(), velocity = new Vector3f();
                Quat4f rotation = new Quat4f();
                for (PhysicsBody part : parts) {
                    part.getPosition(point);
                    part.getRotation(rotation);
                    part.getLinearVelocity(velocity);
                    assert finite(point) && finite(velocity) && Float.isFinite(rotation.w) : type + " non-finite rig";
                    assert velocity.length() < 100 : type + " unstable joint speed";
                }
                parts.get(0)
                    .getPosition(point);
                assert point.y < start.y : type + " gravity absent";
            } finally {
                world.destroy();
            }
        }
        RagdollBodyFactory.BodyProfile[] profiles = { RagdollBodyFactory.BodyProfile.COW,
            RagdollBodyFactory.BodyProfile.PIG, RagdollBodyFactory.BodyProfile.SHEEP,
            RagdollBodyFactory.BodyProfile.CAT, RagdollBodyFactory.BodyProfile.WOLF,
            RagdollBodyFactory.BodyProfile.CHICKEN, RagdollBodyFactory.BodyProfile.HORSE,
            RagdollBodyFactory.BodyProfile.DONKEY, RagdollBodyFactory.BodyProfile.MULE };
        for (RagdollBodyFactory.BodyProfile profile : profiles) for (boolean baby : new boolean[] { false, true }) {
            PhysicsWorld world = new JoltWorld(1);
            try {
                List<PhysicsBody> parts = new ArrayList<>();
                List<PhysicsConstraint> joints = new ArrayList<>();
                MobRigType type = profile == RagdollBodyFactory.BodyProfile.HORSE
                    || profile == RagdollBodyFactory.BodyProfile.DONKEY
                    || profile == RagdollBodyFactory.BodyProfile.MULE ? MobRigType.EQUINE
                        : profile == RagdollBodyFactory.BodyProfile.CHICKEN ? MobRigType.CHICKEN : MobRigType.QUADRUPED;
                RagdollBodyFactory.build(
                    world,
                    parts,
                    joints,
                    type,
                    new Vector3f(0, 10, 0),
                    new Quat4f(0, 0, 0, 1),
                    1,
                    new Vector3f(),
                    null,
                    profile,
                    baby,
                    false);
                assert parts.size() == 6 && joints.size() == 5 : profile + " baby=" + baby;
                for (PhysicsBody part : parts) {
                    Vector3f half = new Vector3f();
                    part.getShape()
                        .getHalfExtents(half);
                    assert finite(half) && half.x > 0 && half.y > 0 && half.z > 0;
                }
                world.step(.05F, StepQuality.HIGH);
            } finally {
                world.destroy();
            }
        }
        Quat4f q = RagdollBodyFactory.rotationZYX(0, 0, (float) Math.PI / 2);
        Vector3f v = RagdollBodyFactory.rotQ(q, new Vector3f(0, 1, 0));
        assert Math.abs(v.y) < 1e-6 && Math.abs(v.z - 1) < 1e-6;
        System.out.println(
            "Mob factory: original 22 Minecraft 1.7 rig topologies, 18 adult/baby profiles, masses, native joints, gravity, full-body impulses and finite state passed");
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 1e-4;
    }

    private static boolean finite(Vector3f v) {
        return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
    }

    private MobFactoryCheck() {}
}
