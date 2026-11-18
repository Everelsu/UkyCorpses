package com.uky.corpses.mobs.physics;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.uky.corpses.corpse.PhysicsConfig;
import com.uky.corpses.port.physics.BodyProperties;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;
import com.uky.corpses.port.physics.PhysicsShape;
import com.uky.corpses.port.physics.PhysicsWorld;

public final class RagdollBodyFactory {

    public static boolean freeAngularAxes;
    private static final float MIN_GENERIC_HALF_EXTENT = 0.03F;
    private static final float GENERIC_DENSITY = 90.0F;
    private static final float GENERIC_MIN_MASS = 0.15F;
    private static final Vector3f GENERIC_ANGULAR_LOWER = new Vector3f(-45.0F, -35.0F, -35.0F);
    private static final Vector3f GENERIC_ANGULAR_UPPER = new Vector3f(45.0F, 35.0F, 35.0F);
    private static final Vector3f GENERIC_LINEAR_SLOP_LOWER = new Vector3f(-0.015F, -0.015F, -0.015F);
    private static final Vector3f GENERIC_LINEAR_SLOP_UPPER = new Vector3f(0.015F, 0.015F, 0.015F);
    private static final float CREEPER_HIP_X = 0.125F;
    private static final float CREEPER_HIP_Y = -0.375F;
    private static final float CREEPER_HIP_Z = 0.25F;
    private static final float CREEPER_NECK_Y = 0.375F;
    private static final float CREEPER_HEAD_HALF = 0.25F;
    private static final float CREEPER_LEG_HALF_XZ = 0.12F;
    private static final float CREEPER_LEG_HALF_Y = 0.1875F;
    private static final float BAT_WING_THICKNESS = 0.03F;
    private static final float BAT_SHOULDER_X = 0.09375F;
    private static final float BAT_SHOULDER_Y = 0.15625F;
    private static final float DRAG_RATIO_FLOOR = 0.2F;
    private static final float DRAG_RATIO_CEILING = 12.0F;
    public static final float NECK_RELAX_SCALE = 16.0F;
    private static final BodyProperties PART_PROPERTIES = new BodyProperties();
    private static final Vector3f scratchHalfExtents = new Vector3f();
    private static final float MAX_LIMIT_DEG = 175.0F;

    private RagdollBodyFactory() {}

    public static void build(PhysicsWorld world, List<PhysicsBody> parts, List<PhysicsConstraint> joints,
        MobRigType modelType, Vector3f pos, Quat4f baseQuat, float scale, Vector3f initialVel, MobPose capturedPose) {
        build(world, parts, joints, modelType, pos, baseQuat, scale, initialVel, capturedPose, false);
    }

    public static void build(PhysicsWorld world, List<PhysicsBody> parts, List<PhysicsConstraint> joints,
        MobRigType modelType, Vector3f pos, Quat4f baseQuat, float scale, Vector3f initialVel, MobPose capturedPose,
        boolean isBabyCow) {
        build(
            world,
            parts,
            joints,
            modelType,
            pos,
            baseQuat,
            scale,
            initialVel,
            capturedPose,
            isBabyCow ? RagdollBodyFactory.BodyProfile.COW : RagdollBodyFactory.BodyProfile.DEFAULT,
            isBabyCow,
            false);
    }

    public static void build(PhysicsWorld world, List<PhysicsBody> parts, List<PhysicsConstraint> joints,
        MobRigType modelType, Vector3f pos, Quat4f baseQuat, float scale, Vector3f initialVel, MobPose capturedPose,
        RagdollBodyFactory.BodyProfile bodyProfile, boolean isBaby, boolean babyBigHead) {
        build(
            world,
            parts,
            joints,
            modelType,
            pos,
            baseQuat,
            scale,
            initialVel,
            capturedPose,
            bodyProfile,
            isBaby,
            babyBigHead,
            null);
    }

    public static void build(PhysicsWorld world, List<PhysicsBody> parts, List<PhysicsConstraint> joints,
        MobRigType modelType, Vector3f pos, Quat4f baseQuat, float scale, Vector3f initialVel, MobPose capturedPose,
        RagdollBodyFactory.BodyProfile bodyProfile, boolean isBaby, boolean babyBigHead, GenericRig genericRig) {
        build(
            world,
            parts,
            joints,
            modelType,
            pos,
            baseQuat,
            scale,
            initialVel,
            capturedPose,
            bodyProfile,
            isBaby,
            babyBigHead,
            genericRig,
            1.0F);
    }

    public static void build(PhysicsWorld world, List<PhysicsBody> parts, List<PhysicsConstraint> joints,
        MobRigType modelType, Vector3f pos, Quat4f baseQuat, float scale, Vector3f initialVel, MobPose capturedPose,
        RagdollBodyFactory.BodyProfile bodyProfile, boolean isBaby, boolean babyBigHead, GenericRig genericRig,
        float humanoidMeshScale) {
        if (modelType == MobRigType.GENERIC) {
            if (genericRig == null) {
                throw new IllegalArgumentException("GENERIC ragdoll built without a measured rig");
            } else {
                buildGeneric(world, parts, joints, genericRig, pos, baseQuat, genericRig.renderScale, initialVel);
            }
        } else {
            float bodyScale = bodyProfile == RagdollBodyFactory.BodyProfile.DEFAULT && modelType != MobRigType.BAT
                && modelType != MobRigType.BEE
                && modelType != MobRigType.CREEPER ? scale : 1.0F;
            if (modelType == MobRigType.PHANTOM) {
                int phantomSize = Math.max(0, Math.round((scale * 3.6F - 1.0F) * 4.5F));
                bodyScale = 1.0F + 0.15F * (float) phantomSize;
            }

            switch (modelType) {
                case CREEPER:
                    buildCreeper(world, parts, pos, baseQuat, bodyScale, initialVel, capturedPose);
                    break;
                case QUADRUPED:
                case WOLF:
                case FOX:
                case PANDA:
                case GOAT:
                case POLAR_BEAR:
                case CHICKEN:
                    buildQuadruped(
                        world,
                        parts,
                        pos,
                        baseQuat,
                        bodyScale,
                        initialVel,
                        capturedPose,
                        modelType == MobRigType.CHICKEN,
                        bodyProfile,
                        isBaby);
                    break;
                case EQUINE:
                    buildEquine(world, parts, pos, baseQuat, initialVel, capturedPose, bodyProfile, isBaby);
                    break;
                case IRON_GOLEM:
                    buildIronGolem(world, parts, pos, baseQuat, initialVel);
                    break;
                case TURTLE:
                    buildTurtle(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case ENDERMAN:
                    buildEnderman(world, parts, pos, baseQuat, initialVel);
                    break;
                case CAMEL:
                    buildCamel(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case LLAMA:
                    buildLlama(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case RABBIT:
                    buildRabbit(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case FROG:
                    buildFrog(world, parts, pos, baseQuat, initialVel);
                    break;
                case HOGLIN:
                    buildHoglin(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case SNIFFER:
                    buildSniffer(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case RAVAGER:
                    buildRavager(world, parts, pos, baseQuat, initialVel);
                    break;
                case PHANTOM:
                    buildPhantom(world, parts, pos, baseQuat, initialVel, bodyScale);
                    break;
                case PARROT:
                    buildParrot(world, parts, pos, baseQuat, initialVel);
                    break;
                case SLIME:
                case MAGMA_CUBE:
                    buildCubeMob(world, parts, pos, baseQuat, initialVel);
                    break;
                case SILVERFISH:
                    buildSilverfish(world, parts, pos, baseQuat, initialVel);
                    break;
                case ENDERMITE:
                    buildEndermite(world, parts, pos, baseQuat, initialVel);
                    break;
                case ALLAY:
                    buildAllay(world, parts, pos, baseQuat, initialVel);
                    break;
                case STRIDER:
                    buildStrider(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case SNOW_GOLEM:
                    buildSnowGolem(world, parts, pos, baseQuat, initialVel);
                    break;
                case BLAZE:
                    buildBlaze(world, parts, pos, baseQuat, initialVel);
                    break;
                case BREEZE:
                    buildBreeze(world, parts, pos, baseQuat, initialVel);
                    break;
                case ARMADILLO:
                    buildArmadillo(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case SPIDER:
                    buildSpider(
                        world,
                        parts,
                        pos,
                        baseQuat,
                        initialVel,
                        bodyProfile == RagdollBodyFactory.BodyProfile.CAVE_SPIDER ? 0.7F : 1.0F);
                    break;
                case SHULKER:
                    buildShulker(world, parts, pos, baseQuat, initialVel);
                    break;
                case GHAST:
                    buildGhast(world, parts, pos, baseQuat, initialVel);
                    break;
                case VEX:
                    buildVex(world, parts, pos, baseQuat, initialVel);
                    break;
                case WARDEN:
                    buildWarden(world, parts, pos, baseQuat, initialVel);
                    break;
                case GUARDIAN:
                    buildGuardian(world, parts, pos, baseQuat, initialVel, guardianScale(bodyProfile));
                    break;
                case SQUID:
                    buildSquid(world, parts, pos, baseQuat, initialVel);
                    break;
                case DOLPHIN:
                    buildDolphin(world, parts, pos, baseQuat, initialVel);
                    break;
                case AXOLOTL:
                    buildAxolotl(world, parts, pos, baseQuat, initialVel, isBaby);
                    break;
                case FISH:
                    buildFish(world, parts, pos, baseQuat, initialVel, bodyProfile);
                    break;
                case WITHER:
                    buildWither(world, parts, pos, baseQuat, initialVel);
                    break;
                case ENDER_DRAGON:
                    buildEnderDragon(world, parts, pos, baseQuat, initialVel);
                    break;
                case BAT:
                    buildBat(world, parts, pos, baseQuat, bodyScale, initialVel, capturedPose);
                    break;
                case BEE:
                    buildBee(world, parts, pos, baseQuat, bodyScale, initialVel, capturedPose, isBaby);
                    break;
                default:
                    buildHumanoid(
                        world,
                        parts,
                        pos,
                        baseQuat,
                        humanoidMeshScale,
                        initialVel,
                        capturedPose,
                        isBaby,
                        babyBigHead);
            }

            applyPartTuning(parts, bodyProfile);
            buildJoints(world, parts, joints, modelType, bodyScale, bodyProfile, isBaby, babyBigHead);
        }
    }

    private static void applyPartTuning(List<PhysicsBody> parts, RagdollBodyFactory.BodyProfile profile) {
        for (int i = 0; i < parts.size(); i++) {
            PhysicsPart part = PhysicsPart.byIndex(i);
            if (part != null && (profile != RagdollBodyFactory.BodyProfile.GHAST || i <= 0)
                && (profile != RagdollBodyFactory.BodyProfile.SPIDER
                    && profile != RagdollBodyFactory.BodyProfile.CAVE_SPIDER || i <= 1)
                && (profile != RagdollBodyFactory.BodyProfile.SQUID || i <= 0)
                && (profile != RagdollBodyFactory.BodyProfile.ENDER_DRAGON || i <= 5)) {
                PhysicsBody body = parts.get(i);
                float weight = PhysicsConfig.weight[part.skeletonIndex];
                float invMass = body.getInvMass();
                if (weight != 1.0F && invMass > 0.0F) {
                    body.setMass(1.0F / invMass * weight);
                }

                float gravity = PhysicsConfig.partGravity[part.skeletonIndex];
                if (gravity != 1.0F) {
                    body.setGravityFactor(gravity);
                }
            }
        }
    }

    private static void buildGeneric(PhysicsWorld world, List<PhysicsBody> parts, List<PhysicsConstraint> joints,
        GenericRig rig, Vector3f pos, Quat4f baseQuat, float scale, Vector3f initialVel) {
        Function<Vector3f, Vector3f> tw = local -> {
            Vector3f out = rotQ(baseQuat, local);
            out.add(pos);
            return out;
        };

        for (GenericRig.Part part : rig.parts) {
            Vector3f half = v(
                Math.max(0.03F, part.hx() * scale),
                Math.max(0.03F, part.hy() * scale),
                Math.max(0.03F, part.hz() * scale));
            float mass = Math.max(0.15F, 90.0F * 2.0F * half.x * 2.0F * half.y * 2.0F * half.z);
            parts.add(
                makePart(
                    world,
                    box(world, half),
                    tw.apply(v(part.ox() * scale, part.oy() * scale, part.oz() * scale)),
                    mul(baseQuat, new Quat4f(part.qx(), part.qy(), part.qz(), part.qw())),
                    mass,
                    initialVel));
        }

        int firstJoint = joints.size();

        for (int i = 1; i < rig.parts.size(); i++) {
            GenericRig.Part part = rig.parts.get(i);
            PhysicsBody child = parts.get(i);
            PhysicsBody parent = parts.get(Math.max(0, part.parentIndex()));
            Vector3f anchor = tw.apply(v(part.jx() * scale, part.jy() * scale, part.jz() * scale));
            joints.add(
                joint(
                    world,
                    parent,
                    child,
                    anchor,
                    GENERIC_LINEAR_SLOP_LOWER,
                    GENERIC_LINEAR_SLOP_UPPER,
                    GENERIC_ANGULAR_LOWER,
                    GENERIC_ANGULAR_UPPER,
                    false,
                    true));
        }

        applyRelaxation(joints, firstJoint, 1.0F);
        applyAerodynamicDrag(parts);
    }

    private static void buildHumanoid(PhysicsWorld world, List<PhysicsBody> parts, Vector3f pos, Quat4f baseQuat,
        float meshScale, Vector3f vel, MobPose pose, boolean isBaby, boolean babyBigHead) {
        float bs = isBaby ? 0.5F : 1.0F;
        float hd = isBaby ? (babyBigHead ? 0.75F : 0.5F) : 1.0F;
        Quat4f torsoRot = poseRot(pose, PhysicsPart.TORSO, baseQuat);
        Quat4f headRot = poseRot(pose, PhysicsPart.HEAD, baseQuat);
        Quat4f lLegRot = poseRot(pose, PhysicsPart.LEFT_LEG, baseQuat);
        Quat4f rLegRot = poseRot(pose, PhysicsPart.RIGHT_LEG, baseQuat);
        Quat4f lArmRot = poseRot(pose, PhysicsPart.LEFT_ARM, baseQuat);
        Quat4f rArmRot = poseRot(pose, PhysicsPart.RIGHT_ARM, baseQuat);
        Vector3f authoredLimb = new Vector3f(0.125F * bs, 0.375F * bs, 0.125F * bs);
        Vector3f torsoHalf = poseBox(
            pose,
            meshScale,
            PhysicsPart.TORSO,
            new Vector3f(0.25F * bs, 0.375F * bs, 0.15F * bs));
        Vector3f headHalf = poseBox(
            pose,
            meshScale,
            PhysicsPart.HEAD,
            new Vector3f(0.25F * hd, 0.25F * hd, 0.25F * hd));
        Vector3f lLegHalf = poseBox(pose, meshScale, PhysicsPart.LEFT_LEG, authoredLimb);
        Vector3f rLegHalf = poseBox(pose, meshScale, PhysicsPart.RIGHT_LEG, authoredLimb);
        Vector3f lArmHalf = poseBox(pose, meshScale, PhysicsPart.LEFT_ARM, authoredLimb);
        Vector3f rArmHalf = poseBox(pose, meshScale, PhysicsPart.RIGHT_ARM, authoredLimb);
        Vector3f torsoPos = new Vector3f(posePos(pose, PhysicsPart.TORSO, pos));
        Vector3f headPos = calcPos(
            torsoPos,
            torsoRot,
            humanoidNeck(torsoHalf),
            headRot,
            new Vector3f(0.0F, headHalf.y, 0.0F));
        Vector3f lLegPos = calcPos(
            torsoPos,
            torsoRot,
            humanoidHip(torsoHalf, bs, -1.0F),
            lLegRot,
            new Vector3f(0.0F, -lLegHalf.y, 0.0F));
        Vector3f rLegPos = calcPos(
            torsoPos,
            torsoRot,
            humanoidHip(torsoHalf, bs, 1.0F),
            rLegRot,
            new Vector3f(0.0F, -rLegHalf.y, 0.0F));
        Vector3f lArmPos = calcPos(
            torsoPos,
            torsoRot,
            humanoidShoulder(torsoHalf, lArmHalf, bs, -1.0F),
            lArmRot,
            humanoidArmHang(lArmHalf, bs, -1.0F));
        Vector3f rArmPos = calcPos(
            torsoPos,
            torsoRot,
            humanoidShoulder(torsoHalf, rArmHalf, bs, 1.0F),
            rArmRot,
            humanoidArmHang(rArmHalf, bs, 1.0F));
        float lift = liftToFeet(
            pose,
            new Vector3f[] { lLegPos, rLegPos },
            new Quat4f[] { lLegRot, rLegRot },
            new Vector3f[] { lLegHalf, rLegHalf });

        for (Vector3f placed : new Vector3f[] { torsoPos, headPos, lLegPos, rLegPos, lArmPos, rArmPos }) {
            placed.y += lift;
        }

        parts.add(makePart(world, box(world, torsoHalf), torsoPos, torsoRot, 18.0F * bs, vel));
        parts.add(makePart(world, box(world, headHalf), headPos, headRot, 2.4F * hd, vel));
        parts.add(makePart(world, box(world, lLegHalf), lLegPos, lLegRot, 4.6F * bs, vel));
        parts.add(makePart(world, box(world, rLegHalf), rLegPos, rLegRot, 4.6F * bs, vel));
        parts.add(makePart(world, box(world, lArmHalf), lArmPos, lArmRot, 1.4F * bs, vel));
        parts.add(makePart(world, box(world, rArmHalf), rArmPos, rArmRot, 1.4F * bs, vel));
    }

    private static Vector3f humanoidNeck(Vector3f torsoHalf) {
        return new Vector3f(0.0F, torsoHalf.y, 0.0F);
    }

    private static Vector3f humanoidHip(Vector3f torsoHalf, float bs, float side) {
        return new Vector3f(side * 0.11875F * bs, -torsoHalf.y, 0.0F);
    }

    private static Vector3f humanoidShoulder(Vector3f torsoHalf, Vector3f armHalf, float bs, float side) {
        return new Vector3f(side * (torsoHalf.x + armHalf.x * 0.5F), armHalf.y - 0.0625F * bs, 0.0F);
    }

    private static Vector3f humanoidShoulderOnArm(Vector3f armHalf, float bs, float side) {
        return new Vector3f(-side * armHalf.x * 0.5F, armHalf.y - 0.0625F * bs, 0.0F);
    }

    private static Vector3f humanoidArmHang(Vector3f armHalf, float bs, float side) {
        Vector3f hang = humanoidShoulderOnArm(armHalf, bs, side);
        hang.negate();
        return hang;
    }

    private static float liftToFeet(MobPose pose, Vector3f[] centres, Quat4f[] rotations, Vector3f[] halves) {
        if (pose != null && pose.getOrigin() != null && centres.length != 0) {
            float lowest = Float.MAX_VALUE;

            for (int i = 0; i < centres.length; i++) {
                lowest = Math.min(lowest, lowestY(centres[i], rotations[i], halves[i]));
            }

            return Math.max(0.0F, pose.getOrigin().y - lowest);
        } else {
            return 0.0F;
        }
    }

    private static float lowestY(Vector3f centre, Quat4f rotation, Vector3f half) {
        return centre.y - Math.abs(rotQ(rotation, new Vector3f(half.x, 0.0F, 0.0F)).y)
            - Math.abs(rotQ(rotation, new Vector3f(0.0F, half.y, 0.0F)).y)
            - Math.abs(rotQ(rotation, new Vector3f(0.0F, 0.0F, half.z)).y);
    }

    private static void buildCreeper(PhysicsWorld world, List<PhysicsBody> parts, Vector3f pos, Quat4f baseQuat,
        float s, Vector3f vel, MobPose pose) {
        Quat4f torsoRot = poseRot(pose, PhysicsPart.TORSO, baseQuat);
        Quat4f headRot = poseRot(pose, PhysicsPart.HEAD, baseQuat);
        Quat4f flRot = poseRot(pose, PhysicsPart.LEFT_ARM, baseQuat);
        Quat4f frRot = poseRot(pose, PhysicsPart.RIGHT_ARM, baseQuat);
        Quat4f blRot = poseRot(pose, PhysicsPart.LEFT_LEG, baseQuat);
        Quat4f brRot = poseRot(pose, PhysicsPart.RIGHT_LEG, baseQuat);
        Vector3f torsoPos = new Vector3f(posePos(pose, PhysicsPart.TORSO, pos));
        Vector3f legHalf = new Vector3f(0.12F * s, 0.1875F * s, 0.12F * s);
        Vector3f legHang = new Vector3f(0.0F, -legHalf.y, 0.0F);
        Vector3f headPos = calcPos(torsoPos, torsoRot, creeperNeck(s), headRot, new Vector3f(0.0F, 0.25F * s, 0.0F));
        Vector3f flPos = calcPos(torsoPos, torsoRot, creeperHip(s, -1.0F, -1.0F), flRot, legHang);
        Vector3f frPos = calcPos(torsoPos, torsoRot, creeperHip(s, 1.0F, -1.0F), frRot, legHang);
        Vector3f blPos = calcPos(torsoPos, torsoRot, creeperHip(s, -1.0F, 1.0F), blRot, legHang);
        Vector3f brPos = calcPos(torsoPos, torsoRot, creeperHip(s, 1.0F, 1.0F), brRot, legHang);
        float lift = liftToFeet(
            pose,
            new Vector3f[] { flPos, frPos, blPos, brPos },
            new Quat4f[] { flRot, frRot, blRot, brRot },
            new Vector3f[] { legHalf, legHalf, legHalf, legHalf });

        for (Vector3f placed : new Vector3f[] { torsoPos, headPos, flPos, frPos, blPos, brPos }) {
            placed.y += lift;
        }

        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.25F * s, 0.375F * s, 0.15F * s)),
                torsoPos,
                torsoRot,
                10.0F * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.25F * s, 0.25F * s, 0.25F * s)),
                headPos,
                headRot,
                4.0F * s,
                vel));
        parts.add(makePart(world, box(world, new Vector3f(legHalf)), blPos, blRot, 3.0F * s, vel));
        parts.add(makePart(world, box(world, new Vector3f(legHalf)), brPos, brRot, 3.0F * s, vel));
        parts.add(makePart(world, box(world, new Vector3f(legHalf)), flPos, flRot, 3.0F * s, vel));
        parts.add(makePart(world, box(world, new Vector3f(legHalf)), frPos, frRot, 3.0F * s, vel));
    }

    private static Vector3f creeperHip(float s, float side, float end) {
        return new Vector3f(side * 0.125F * s, -0.375F * s, end * 0.25F * s);
    }

    private static Vector3f creeperNeck(float s) {
        return new Vector3f(0.0F, 0.375F * s, 0.0F);
    }

    private static void buildBat(PhysicsWorld world, List<PhysicsBody> parts, Vector3f pos, Quat4f baseQuat, float s,
        Vector3f vel, MobPose pose) {
        Quat4f torsoRot = poseRot(pose, PhysicsPart.TORSO, baseQuat);
        Quat4f headRot = poseRot(pose, PhysicsPart.HEAD, baseQuat);
        Quat4f lwRot = poseRot(pose, PhysicsPart.LEFT_ARM, baseQuat);
        Quat4f rwRot = poseRot(pose, PhysicsPart.RIGHT_ARM, baseQuat);
        Quat4f lsRot = poseRot(pose, PhysicsPart.LEFT_LEG, baseQuat);
        Quat4f rsRot = poseRot(pose, PhysicsPart.RIGHT_LEG, baseQuat);
        Vector3f headPos = calcPos(pos, torsoRot, v(0.0F, 0.25F * s, 0.0F), headRot, new Vector3f());
        Vector3f lwPos = calcPos(
            pos,
            torsoRot,
            v(-0.09375F * s, 0.15625F * s, 0.0F),
            lwRot,
            v(-0.25F * s, -0.125F * s, 0.0F));
        Vector3f rwPos = calcPos(
            pos,
            torsoRot,
            v(0.09375F * s, 0.15625F * s, 0.0F),
            rwRot,
            v(0.25F * s, -0.125F * s, 0.0F));
        Vector3f lsPos = calcPos(pos, torsoRot, v(-0.04F * s, -0.1F * s, 0.0F), lsRot, new Vector3f());
        Vector3f rsPos = calcPos(pos, torsoRot, v(0.04F * s, -0.1F * s, 0.0F), rsRot, new Vector3f());
        parts
            .add(makePart(world, box(world, v(0.09375F * s, 0.15625F * s, 0.0625F * s)), pos, torsoRot, 1.4F * s, vel));
        parts.add(
            makePart(world, box(world, v(0.125F * s, 0.09375F * s, 0.0625F * s)), headPos, headRot, 0.7F * s, vel));
        parts.add(makePart(world, box(world, v(0.02F * s, 0.02F * s, 0.02F * s)), lsPos, lsRot, 0.1F * s, vel));
        parts.add(makePart(world, box(world, v(0.02F * s, 0.02F * s, 0.02F * s)), rsPos, rsRot, 0.1F * s, vel));
        parts.add(makePart(world, box(world, v(0.25F * s, 0.25F * s, 0.03F * s)), lwPos, lwRot, 0.45F * s, vel));
        parts.add(makePart(world, box(world, v(0.25F * s, 0.25F * s, 0.03F * s)), rwPos, rwRot, 0.45F * s, vel));
    }

    private static void buildBee(PhysicsWorld world, List<PhysicsBody> parts, Vector3f pos, Quat4f baseQuat, float s,
        Vector3f vel, MobPose pose, boolean isBaby) {
        float ns = isBaby ? s * 0.5F : s;
        Quat4f torsoRot = poseRot(pose, PhysicsPart.TORSO, baseQuat);
        Quat4f headRot = poseRot(pose, PhysicsPart.HEAD, baseQuat);
        Quat4f lwRot = poseRot(pose, PhysicsPart.LEFT_ARM, baseQuat);
        Quat4f rwRot = poseRot(pose, PhysicsPart.RIGHT_ARM, baseQuat);
        Quat4f lsRot = poseRot(pose, PhysicsPart.LEFT_LEG, baseQuat);
        Quat4f rsRot = poseRot(pose, PhysicsPart.RIGHT_LEG, baseQuat);
        Vector3f headPos = calcPos(pos, torsoRot, new Vector3f(0.0F, 0.1F * ns, -0.28F * ns), headRot, new Vector3f());
        Vector3f lwPos = calcPos(
            pos,
            torsoRot,
            new Vector3f(-0.414F * ns, 0.219F * ns, -0.079F * ns),
            lwRot,
            new Vector3f());
        Vector3f rwPos = calcPos(
            pos,
            torsoRot,
            new Vector3f(0.414F * ns, 0.219F * ns, -0.079F * ns),
            rwRot,
            new Vector3f());
        Vector3f lsPos = calcPos(pos, torsoRot, new Vector3f(-0.06F * ns, -0.05F * ns, 0.0F), lsRot, new Vector3f());
        Vector3f rsPos = calcPos(pos, torsoRot, new Vector3f(0.06F * ns, -0.05F * ns, 0.0F), rsRot, new Vector3f());
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.21875F * ns, 0.21875F * ns, 0.3125F * ns)),
                pos,
                torsoRot,
                4.0F * ns,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.08F * ns, 0.08F * ns, 0.08F * ns)),
                headPos,
                headRot,
                0.5F * ns,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.05F * ns, 0.05F * ns, 0.05F * ns)),
                lsPos,
                lsRot,
                0.3F * ns,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.05F * ns, 0.05F * ns, 0.05F * ns)),
                rsPos,
                rsRot,
                0.3F * ns,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.28F * ns, 0.02F * ns, 0.1875F * ns)),
                lwPos,
                lwRot,
                0.5F * ns,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(0.28F * ns, 0.02F * ns, 0.1875F * ns)),
                rwPos,
                rwRot,
                0.5F * ns,
                vel));
    }

    private static void buildQuadruped(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f baseQuat,
        float s, Vector3f vel, MobPose pose, boolean isChicken, RagdollBodyFactory.BodyProfile bodyProfile,
        boolean isBaby) {
        Quat4f torsoRot = poseRot(pose, PhysicsPart.TORSO, baseQuat);
        Quat4f headRot = poseRot(pose, PhysicsPart.HEAD, baseQuat);
        Quat4f flRot = poseRot(pose, PhysicsPart.LEFT_ARM, baseQuat);
        Quat4f frRot = poseRot(pose, PhysicsPart.RIGHT_ARM, baseQuat);
        Quat4f blRot = poseRot(pose, PhysicsPart.LEFT_LEG, baseQuat);
        Quat4f brRot = poseRot(pose, PhysicsPart.RIGHT_LEG, baseQuat);
        if (isChicken) {
            float b = isBaby ? 0.5F : 1.0F;
            float hb = isBaby ? 0.5F : 1.0F;
            float headZ = (isBaby ? -0.28F : -0.23F) * hb * s;
            Vector3f headPos = calcPos(
                torsoPos,
                torsoRot,
                new Vector3f(0.0F, 0.16F * hb * s, headZ),
                headRot,
                new Vector3f(0.0F, 0.06F * hb * s, 0.0F));
            Vector3f llPos = calcPos(
                torsoPos,
                torsoRot,
                new Vector3f(0.1F * b * s, -0.2F * b * s, 0.0F),
                blRot,
                new Vector3f(0.0F, -0.07F * b * s, 0.0F));
            Vector3f rlPos = calcPos(
                torsoPos,
                torsoRot,
                new Vector3f(-0.1F * b * s, -0.2F * b * s, 0.0F),
                brRot,
                new Vector3f(0.0F, -0.07F * b * s, 0.0F));
            Vector3f lwPos = calcPos(
                torsoPos,
                torsoRot,
                new Vector3f(0.25F * b * s, 0.05F * b * s, 0.0F),
                flRot,
                new Vector3f(-0.03F * b * s, 0.0F, 0.0F));
            Vector3f rwPos = calcPos(
                torsoPos,
                torsoRot,
                new Vector3f(-0.25F * b * s, 0.05F * b * s, 0.0F),
                frRot,
                new Vector3f(0.03F * b * s, 0.0F, 0.0F));
            parts.add(
                makePart(
                    world,
                    box(world, new Vector3f(0.2F * b * s, 0.22F * b * s, 0.15F * b * s)),
                    torsoPos,
                    torsoRot,
                    5.0F * s,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, new Vector3f(0.12F * s, 0.18F * s, 0.12F * s)),
                    headPos,
                    headRot,
                    2.0F * s,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, new Vector3f(0.06F * b * s, 0.18F * b * s, 0.06F * b * s)),
                    llPos,
                    blRot,
                    1.0F * s,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, new Vector3f(0.06F * b * s, 0.18F * b * s, 0.06F * b * s)),
                    rlPos,
                    brRot,
                    1.0F * s,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, new Vector3f(0.05F * b * s, 0.18F * b * s, 0.12F * b * s)),
                    lwPos,
                    flRot,
                    1.0F * s,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, new Vector3f(0.05F * b * s, 0.18F * b * s, 0.12F * b * s)),
                    rwPos,
                    frRot,
                    1.0F * s,
                    vel));
        } else if (isBaby) {
            RagdollBodyFactory.QuadLayout l = layoutFor(bodyProfile, true);
            buildProfiledQuadruped(world, parts, torsoPos, torsoRot, headRot, flRot, frRot, blRot, brRot, s, vel, l);
        } else {
            RagdollBodyFactory.QuadLayout l = layoutFor(bodyProfile, false);
            buildProfiledQuadruped(world, parts, torsoPos, torsoRot, headRot, flRot, frRot, blRot, brRot, s, vel, l);
        }
    }

    private static void buildEquine(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f baseQuat,
        Vector3f vel, MobPose pose, RagdollBodyFactory.BodyProfile profile, boolean isBaby) {
        float rs = equineRenderScale(profile);
        float bs = isBaby ? 0.5F : 1.0F;
        float hs = isBaby ? 0.5500147F : 1.0F;
        float legHalfY = 0.34375F * rs;
        Vector3f headPos;
        Vector3f flPos;
        Vector3f frPos;
        Vector3f hlPos;
        Vector3f hrPos;
        if (!isBaby) {
            headPos = offset(torsoPos, baseQuat, v(0.0F, 0.296875F * rs, -0.60631F * rs));
            flPos = offset(torsoPos, baseQuat, v(-0.1875F * rs, -0.655625F * rs, -0.55625F * rs));
            frPos = offset(torsoPos, baseQuat, v(0.1875F * rs, -0.655625F * rs, -0.55625F * rs));
            hlPos = offset(torsoPos, baseQuat, v(-0.1875F * rs, -0.655625F * rs, 0.5625F * rs));
            hrPos = offset(torsoPos, baseQuat, v(0.1875F * rs, -0.655625F * rs, 0.5625F * rs));
        } else {
            headPos = offset(torsoPos, baseQuat, v(0.0F, 0.2002F * rs, -0.28984F * rs));
            flPos = offset(torsoPos, baseQuat, v(-0.09375F * rs, -0.33406F * rs, -0.278125F * rs));
            frPos = offset(torsoPos, baseQuat, v(0.09375F * rs, -0.33406F * rs, -0.278125F * rs));
            hlPos = offset(torsoPos, baseQuat, v(-0.09375F * rs, -0.33406F * rs, 0.28125F * rs));
            hrPos = offset(torsoPos, baseQuat, v(0.09375F * rs, -0.33406F * rs, 0.28125F * rs));
        }

        parts.add(
            makePart(
                world,
                box(world, v(0.3125F * bs * rs, 0.3125F * bs * rs, 0.6875F * bs * rs)),
                torsoPos,
                baseQuat,
                14.0F * bs * rs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.1875F * hs * rs, 0.59F * hs * rs, 0.21875F * hs * rs)),
                headPos,
                baseQuat,
                5.0F * hs * rs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.125F * bs * rs, legHalfY, 0.125F * bs * rs)),
                hlPos,
                baseQuat,
                3.0F * bs * rs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.125F * bs * rs, legHalfY, 0.125F * bs * rs)),
                hrPos,
                baseQuat,
                3.0F * bs * rs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.125F * bs * rs, legHalfY, 0.125F * bs * rs)),
                flPos,
                baseQuat,
                3.0F * bs * rs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.125F * bs * rs, legHalfY, 0.125F * bs * rs)),
                frPos,
                baseQuat,
                3.0F * bs * rs,
                vel));
    }

    private static float equineRenderScale(RagdollBodyFactory.BodyProfile profile) {
        return switch (profile) {
            case HORSE -> 1.1F;
            case DONKEY -> 0.87F;
            case MULE -> 0.92F;
            default -> 1.0F;
        };
    }

    private static RagdollBodyFactory.QuadLayout layoutFor(RagdollBodyFactory.BodyProfile profile, boolean baby) {
        RagdollBodyFactory.BodyProfile p = profile == RagdollBodyFactory.BodyProfile.DEFAULT
            ? RagdollBodyFactory.BodyProfile.COW
            : profile;
        float b = baby ? 0.5F : 1.0F;
        float hb = baby ? 0.5F : 1.0F;

        return switch (p) {
            case DEFAULT, COW -> new RagdollBodyFactory.QuadLayout(
                baby ? 0.11F : 0.22F,
                -0.56F * hb,
                -0.15F * hb,
                0.3125F,
                0.28125F,
                0.1875F,
                0.25F * b,
                -0.3125F * b,
                0.4375F * b,
                0.375F * b,
                -0.235F * b,
                0.105F * b,
                0.34F * b,
                0.105F * b,
                0.34F * b,
                0.29F * b,
                0.5F * b,
                10.0F,
                3.0F,
                3.0F,
                -0.3125F * b,
                -0.235F * b,
                0.34F * b);
            case PIG -> new RagdollBodyFactory.QuadLayout(
                baby ? 0.06F : 0.12F,
                -0.37F * hb,
                -0.25F * hb,
                0.25F,
                0.25F,
                0.31F,
                0.1875F * b,
                -0.25F * b,
                0.3125F * b,
                0.4375F * b,
                -0.075F * b,
                0.105F * b,
                0.16F * b,
                0.105F * b,
                0.28F * b,
                0.23F * b,
                0.44F * b,
                6.0F,
                2.5F,
                1.6F,
                -0.25F * b,
                -0.075F * b,
                0.16F * b);
            case SHEEP -> new RagdollBodyFactory.QuadLayout(
                baby ? 0.125F : 0.25F,
                -0.425F * hb,
                -0.16F * hb,
                0.1875F,
                0.1875F,
                0.25F,
                0.1875F * b,
                -0.1875F * b,
                0.3125F * b,
                0.4375F * b,
                -0.235F * b,
                0.105F * b,
                0.34F * b,
                0.105F * b,
                0.23F * b,
                0.17F * b,
                0.44F * b,
                8.0F,
                2.5F,
                2.2F,
                -0.1875F * b,
                -0.235F * b,
                0.34F * b);
            default -> throw new IllegalStateException("Unexpected quadruped profile " + p);
            case CAT -> {
                float chf = baby ? 0.75F : 1.0F;
                yield new RagdollBodyFactory.QuadLayout(
                    0.1F * b,
                    -0.4F * b,
                    -0.125F * chf,
                    0.125F * chf,
                    0.1F * chf,
                    0.125F * chf,
                    0.0576F * b,
                    0.145F * b,
                    0.25F * b,
                    0.3F * b,
                    -0.25F * b,
                    0.05F * b,
                    0.25F * b,
                    0.05F * b,
                    0.1F * b,
                    0.15F * b,
                    0.4F * b,
                    3.0F,
                    1.0F,
                    0.6F,
                    -0.05F * b,
                    -0.15F * b,
                    0.15F * b);
            }
            case WOLF -> {
                float whf = 1.0F;
                yield new RagdollBodyFactory.QuadLayout(
                    0.09375F,
                    baby ? -0.546875F : -0.71875F,
                    baby ? 0.0F : -0.09375F,
                    0.1875F * whf,
                    0.25F * whf,
                    0.21875F * whf,
                    0.09375F * b,
                    -0.125F * b,
                    0.53125F * b,
                    0.15625F * b,
                    -0.25F * b,
                    0.0625F * b,
                    0.25F * b,
                    0.0625F * b,
                    0.25F * b,
                    0.21875F * b,
                    0.47F * b,
                    5.0F,
                    1.5F,
                    0.8F,
                    -0.125F * b,
                    -0.25F * b,
                    0.25F * b);
            }
            case FOX -> {
                float fhf = baby ? 0.75F : 1.0F;
                yield new RagdollBodyFactory.QuadLayout(
                    baby ? 0.1171875F : 0.0F,
                    baby ? -0.25703F : -0.40625F,
                    baby ? 0.0F : -0.21875F,
                    0.25F * fhf,
                    0.25F * fhf,
                    0.28125F * fhf,
                    0.125F * b,
                    -0.0625F * b,
                    0.21875F * b,
                    0.21875F * b,
                    -0.21875F * b,
                    0.0625F * b,
                    0.1875F * b,
                    0.0625F * b,
                    0.1875F * b,
                    0.1875F * b,
                    0.34375F * b,
                    3.0F,
                    1.2F,
                    0.5F,
                    -0.0625F * b,
                    -0.21875F * b,
                    0.1875F * b);
            }
            case PANDA -> {
                float pbs = baby ? 0.33333334F : 1.0F;
                float phs = baby ? 0.5555555F : 1.0F;
                yield new RagdollBodyFactory.QuadLayout(
                    baby ? 0.083333F : 0.0F,
                    baby ? -0.440972F : -1.0625F,
                    baby ? 0.0F : -0.03125F,
                    0.53125F * phs,
                    0.40625F * phs,
                    0.34375F * phs,
                    -0.34375F * pbs,
                    -0.3125F * pbs,
                    0.5625F * pbs,
                    0.5625F * pbs,
                    -0.28125F * pbs,
                    0.1875F * pbs,
                    0.28125F * pbs,
                    0.1875F * pbs,
                    0.59375F * pbs,
                    0.40625F * pbs,
                    0.8125F * pbs,
                    15.0F,
                    5.0F,
                    4.0F,
                    -0.3125F * pbs,
                    -0.28125F * pbs,
                    0.28125F * pbs);
            }
            case GOAT -> {
                float gbs = baby ? 0.5F : 1.0F;
                float ghs = baby ? 0.6F : 1.0F;
                yield new RagdollBodyFactory.QuadLayout(
                    baby ? 0.05F : 0.15625F,
                    baby ? -0.409375F : -0.71875F,
                    baby ? 0.0F : -0.03125F,
                    0.34375F * ghs,
                    0.46875F * ghs,
                    0.15625F * ghs,
                    -0.125F * gbs,
                    -0.0625F * gbs,
                    0.3125F * gbs,
                    0.3125F * gbs,
                    -0.3125F * gbs,
                    0.09375F * gbs,
                    0.3125F * gbs,
                    0.09375F * gbs,
                    0.34375F * gbs,
                    0.4375F * gbs,
                    0.53125F * gbs,
                    9.0F,
                    3.0F,
                    2.0F,
                    -0.0625F * gbs,
                    -0.4375F * gbs,
                    0.1875F * gbs);
            }
            case POLAR_BEAR -> {
                float rs = 1.2F;
                float pbs = baby ? 0.5F : 1.0F;
                float phs = baby ? 0.6666667F : 1.0F;
                yield new RagdollBodyFactory.QuadLayout(
                    baby ? -0.00625F : 0.0375F,
                    baby ? -0.65F : -1.2375F,
                    baby ? 0.0F : -0.0375F,
                    0.28125F * phs * 1.2F,
                    0.25F * phs * 1.2F,
                    0.3125F * phs * 1.2F,
                    -0.28125F * pbs * 1.2F,
                    -0.21875F * pbs * 1.2F,
                    0.375F * pbs * 1.2F,
                    0.5F * pbs * 1.2F,
                    -0.3125F * pbs * 1.2F,
                    0.125F * pbs * 1.2F,
                    0.3125F * pbs * 1.2F,
                    0.1875F * pbs * 1.2F,
                    0.4375F * pbs * 1.2F,
                    0.34375F * pbs * 1.2F,
                    0.8125F * pbs * 1.2F,
                    18.0F,
                    5.0F,
                    4.0F,
                    -0.21875F * pbs * 1.2F,
                    -0.3125F * pbs * 1.2F,
                    0.3125F * pbs * 1.2F);
            }
        };
    }

    private static void buildProfiledQuadruped(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos,
        Quat4f torsoRot, Quat4f headRot, Quat4f flRot, Quat4f frRot, Quat4f blRot, Quat4f brRot, float s, Vector3f vel,
        RagdollBodyFactory.QuadLayout l) {
        Vector3f headPos = calcPos(
            torsoPos,
            torsoRot,
            new Vector3f(0.0F, l.headY * s, l.headZ * s),
            headRot,
            new Vector3f(0.0F, 0.0F, l.headCenterZ * s));
        Vector3f flPos = calcPos(
            torsoPos,
            torsoRot,
            new Vector3f(l.legX * s, l.legY * s, -l.frontZ * s),
            flRot,
            new Vector3f(0.0F, l.legCenterY * s, 0.0F));
        Vector3f frPos = calcPos(
            torsoPos,
            torsoRot,
            new Vector3f(-l.legX * s, l.legY * s, -l.frontZ * s),
            frRot,
            new Vector3f(0.0F, l.legCenterY * s, 0.0F));
        Vector3f blPos = calcPos(
            torsoPos,
            torsoRot,
            new Vector3f(l.legX * s, l.hindLegY * s, l.hindZ * s),
            blRot,
            new Vector3f(0.0F, l.hindLegCenterY * s, 0.0F));
        Vector3f brPos = calcPos(
            torsoPos,
            torsoRot,
            new Vector3f(-l.legX * s, l.hindLegY * s, l.hindZ * s),
            brRot,
            new Vector3f(0.0F, l.hindLegCenterY * s, 0.0F));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(l.torsoHalfX * s, l.torsoHalfY * s, l.torsoHalfZ * s)),
                torsoPos,
                torsoRot,
                l.torsoMass * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(l.headX * s, l.headHalfY * s, l.headHalfZ * s)),
                headPos,
                headRot,
                l.headMass * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(l.legHalfX * s, l.hindLegHalfY * s, l.legHalfZ * s)),
                blPos,
                blRot,
                l.legMass * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(l.legHalfX * s, l.hindLegHalfY * s, l.legHalfZ * s)),
                brPos,
                brRot,
                l.legMass * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(l.legHalfX * s, l.legHalfY * s, l.legHalfZ * s)),
                flPos,
                flRot,
                l.legMass * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, new Vector3f(l.legHalfX * s, l.legHalfY * s, l.legHalfZ * s)),
                frPos,
                frRot,
                l.legMass * s,
                vel));
    }

    private static void buildIronGolem(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f baseQuat,
        Vector3f vel) {
        Vector3f headPos = offset(torsoPos, baseQuat, v(0.0F, 0.828125F, -0.25F));
        Vector3f leftLegPos = offset(torsoPos, baseQuat, v(-0.28125F, -1.015625F, 0.0F));
        Vector3f rightLegPos = offset(torsoPos, baseQuat, v(0.28125F, -1.015625F, 0.0F));
        Vector3f leftArmPos = offset(torsoPos, baseQuat, v(-0.6875F, -0.359375F, 0.03125F));
        Vector3f rightArmPos = offset(torsoPos, baseQuat, v(0.6875F, -0.359375F, 0.03125F));
        parts.add(makePart(world, box(world, v(0.5625F, 0.546875F, 0.34375F)), torsoPos, baseQuat, 25.0F, vel));
        parts.add(makePart(world, box(world, v(0.25F, 0.34375F, 0.3125F)), headPos, baseQuat, 8.0F, vel));
        parts.add(makePart(world, box(world, v(0.1875F, 0.5F, 0.15625F)), leftLegPos, baseQuat, 10.0F, vel));
        parts.add(makePart(world, box(world, v(0.1875F, 0.5F, 0.15625F)), rightLegPos, baseQuat, 10.0F, vel));
        parts.add(makePart(world, box(world, v(0.125F, 0.9375F, 0.1875F)), leftArmPos, baseQuat, 10.0F, vel));
        parts.add(makePart(world, box(world, v(0.125F, 0.9375F, 0.1875F)), rightArmPos, baseQuat, 10.0F, vel));
    }

    private static void buildTurtle(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f baseQuat,
        Vector3f vel, boolean baby) {
        float b = baby ? 0.16666667F : 1.0F;
        Vector3f headPos = offset(
            torsoPos,
            baseQuat,
            baby ? v(0.0F, -0.010417F, -0.135417F) : v(0.0F, -0.0625F, -0.8125F));
        Vector3f leftHind = offset(torsoPos, baseQuat, v(-0.21875F * b, -0.1875F * b, 0.8125F * b));
        Vector3f rightHind = offset(torsoPos, baseQuat, v(0.21875F * b, -0.1875F * b, 0.8125F * b));
        Vector3f leftFront = offset(torsoPos, baseQuat, v(-0.71875F * b, -0.125F * b, -0.40625F * b));
        Vector3f rightFront = offset(torsoPos, baseQuat, v(0.71875F * b, -0.125F * b, -0.40625F * b));
        parts.add(
            makePart(world, box(world, v(0.59375F * b, 0.28125F * b, 0.625F * b)), torsoPos, baseQuat, 9.0F * b, vel));
        parts.add(
            makePart(world, box(world, v(0.1875F * b, 0.15625F * b, 0.1875F * b)), headPos, baseQuat, 1.5F * b, vel));
        parts.add(
            makePart(world, box(world, v(0.125F * b, 0.03125F * b, 0.3125F * b)), leftHind, baseQuat, 1.0F * b, vel));
        parts.add(
            makePart(world, box(world, v(0.125F * b, 0.03125F * b, 0.3125F * b)), rightHind, baseQuat, 1.0F * b, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.40625F * b, 0.03125F * b, 0.15625F * b)),
                leftFront,
                baseQuat,
                1.0F * b,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.40625F * b, 0.03125F * b, 0.15625F * b)),
                rightFront,
                baseQuat,
                1.0F * b,
                vel));
    }

    private static void buildEnderman(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f baseQuat,
        Vector3f vel) {
        parts.add(makePart(world, box(world, v(0.25F, 0.375F, 0.125F)), torsoPos, baseQuat, 7.0F, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.25F, 0.25F, 0.25F)),
                offset(torsoPos, baseQuat, v(0.0F, 0.5625F, 0.0F)),
                baseQuat,
                3.0F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F, 0.9375F, 0.0625F)),
                offset(torsoPos, baseQuat, v(-0.125F, -1.125F, 0.0F)),
                baseQuat,
                4.0F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F, 0.9375F, 0.0625F)),
                offset(torsoPos, baseQuat, v(0.125F, -1.125F, 0.0F)),
                baseQuat,
                4.0F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F, 0.9375F, 0.0625F)),
                offset(torsoPos, baseQuat, v(-0.3125F, -0.5625F, 0.0F)),
                baseQuat,
                3.5F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F, 0.9375F, 0.0625F)),
                offset(torsoPos, baseQuat, v(0.3125F, -0.5625F, 0.0F)),
                baseQuat,
                3.5F,
                vel));
    }

    private static void buildCamel(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f q,
        Vector3f vel, boolean baby) {
        float b = baby ? 0.45F : 1.0F;
        parts.add(makePart(world, box(world, v(0.46875F * b, 0.375F * b, 0.84375F * b)), torsoPos, q, 18.0F * b, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.21875F * b, 0.6875F * b, 0.78125F * b)),
                offset(torsoPos, q, v(0.0F, 0.4375F * b, -1.125F * b)),
                q,
                6.0F * b,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.15625F * b, 0.65625F * b, 0.15625F * b)),
                offset(torsoPos, q, v(-0.30625F * b, -0.96875F * b, 0.625F * b)),
                q,
                4.0F * b,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.15625F * b, 0.65625F * b, 0.15625F * b)),
                offset(torsoPos, q, v(0.30625F * b, -0.96875F * b, 0.625F * b)),
                q,
                4.0F * b,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.15625F * b, 0.65625F * b, 0.15625F * b)),
                offset(torsoPos, q, v(-0.30625F * b, -0.96875F * b, -0.625F * b)),
                q,
                4.0F * b,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.15625F * b, 0.65625F * b, 0.15625F * b)),
                offset(torsoPos, q, v(0.30625F * b, -0.96875F * b, -0.625F * b)),
                q,
                4.0F * b,
                vel));
    }

    private static void buildLlama(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f q,
        Vector3f vel, boolean baby) {
        if (!baby) {
            parts.add(makePart(world, box(world, v(0.375F, 0.3125F, 0.5625F)), torsoPos, q, 11.0F, vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.25F, 0.65625F, 0.3125F)),
                    offset(torsoPos, q, v(0.0F, 0.53125F, -0.75F)),
                    q,
                    4.0F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.125F, 0.4375F, 0.125F)),
                    offset(torsoPos, q, v(-0.21875F, -0.625F, 0.3125F)),
                    q,
                    2.5F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.125F, 0.4375F, 0.125F)),
                    offset(torsoPos, q, v(0.21875F, -0.625F, 0.3125F)),
                    q,
                    2.5F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.125F, 0.4375F, 0.125F)),
                    offset(torsoPos, q, v(-0.21875F, -0.625F, -0.375F)),
                    q,
                    2.5F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.125F, 0.4375F, 0.125F)),
                    offset(torsoPos, q, v(0.21875F, -0.625F, -0.375F)),
                    q,
                    2.5F,
                    vel));
        } else {
            parts.add(makePart(world, box(world, v(0.234375F, 0.142045F, 0.255682F)), torsoPos, q, 5.0F, vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.178571F, 0.426136F, 0.248016F)),
                    offset(torsoPos, q, v(0.0F, 0.34494F, -0.39944F)),
                    q,
                    2.0F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.056818F, 0.180785F, 0.056818F)),
                    offset(torsoPos, q, v(-0.099432F, -0.15496F, 0.142045F)),
                    q,
                    1.0F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.056818F, 0.180785F, 0.056818F)),
                    offset(torsoPos, q, v(0.099432F, -0.15496F, 0.142045F)),
                    q,
                    1.0F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.056818F, 0.180785F, 0.056818F)),
                    offset(torsoPos, q, v(-0.099432F, -0.15496F, -0.170455F)),
                    q,
                    1.0F,
                    vel));
            parts.add(
                makePart(
                    world,
                    box(world, v(0.056818F, 0.180785F, 0.056818F)),
                    offset(torsoPos, q, v(0.099432F, -0.15496F, -0.170455F)),
                    q,
                    1.0F,
                    vel));
        }
    }

    private static void buildRabbit(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f q,
        Vector3f vel, boolean baby) {
        float bs = baby ? 0.4F : 0.6F;
        float hs = baby ? 0.5666667F : 0.6F;
        float headY = baby ? 0.1575F : 0.141F;
        float headZ = baby ? -0.1314F : -0.2487F;
        float hindX = baby ? -0.075F : -0.1125F;
        float hindY = baby ? -0.07475F : -0.112F;
        float hindZ = baby ? 0.03175F : 0.0476F;
        float frontX = baby ? -0.075F : -0.1125F;
        float frontY = baby ? -0.067F : -0.10075F;
        float frontZ = baby ? -0.1185F : -0.1777F;
        parts.add(
            makePart(world, box(world, v(0.1875F * bs, 0.15625F * bs, 0.3125F * bs)), torsoPos, q, 2.5F * bs, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.2F * hs, 0.28125F * hs, 0.2F * hs)),
                offset(torsoPos, q, v(0.0F, headY, headZ)),
                q,
                1.0F * hs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.125F * bs, 0.3F * bs, 0.35F * bs)),
                offset(torsoPos, q, v(hindX, hindY, hindZ)),
                q,
                0.8F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.125F * bs, 0.3F * bs, 0.35F * bs)),
                offset(torsoPos, q, v(-hindX, hindY, hindZ)),
                q,
                0.8F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F * bs, 0.21875F * bs, 0.08333F * bs)),
                offset(torsoPos, q, v(frontX, frontY, frontZ)),
                q,
                0.5F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F * bs, 0.21875F * bs, 0.08333F * bs)),
                offset(torsoPos, q, v(-frontX, frontY, frontZ)),
                q,
                0.5F * bs,
                vel));
    }

    private static void buildFrog(PhysicsWorld world, List<PhysicsBody> parts, Vector3f torsoPos, Quat4f q,
        Vector3f vel) {
        parts.add(makePart(world, box(world, v(0.21875F, 0.09375F, 0.28125F)), torsoPos, q, 2.0F, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.21875F, 0.09375F, 0.28125F)),
                offset(torsoPos, q, v(0.0F, 0.125F, 0.0F)),
                q,
                1.0F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.09375F, 0.09375F, 0.125F)),
                offset(torsoPos, q, v(-0.25F, -0.0625F, 0.21875F)),
                q,
                0.5F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.09375F, 0.09375F, 0.125F)),
                offset(torsoPos, q, v(0.25F, -0.0625F, 0.21875F)),
                q,
                0.5F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F, 0.09375F, 0.09375F)),
                offset(torsoPos, q, v(-0.25F, -0.0625F, -0.15625F)),
                q,
                0.5F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.0625F, 0.09375F, 0.09375F)),
                offset(torsoPos, q, v(0.25F, -0.0625F, -0.15625F)),
                q,
                0.5F,
                vel));
    }

    private static void buildHoglin(PhysicsWorld world, List<PhysicsBody> parts, Vector3f p, Quat4f q, Vector3f vel,
        boolean baby) {
        float bs = baby ? 0.5F : 1.0F;
        float hs = baby ? 0.7894737F : 1.0F;
        float hy = baby ? 0.1163F : -0.1423F;
        float hz = baby ? -0.5974F : -1.1317F;
        parts.add(makePart(world, box(world, v(0.5F * bs, 0.4375F * bs, 0.8125F * bs)), p, q, 18.0F * bs, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.4375F * hs, 0.5751F * hs, 0.5251F * hs)),
                offset(p, q, v(0.0F, hy, hz)),
                q,
                7.0F * hs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.15625F * bs, 0.34375F * bs, 0.15625F * bs)),
                offset(p, q, v(-0.15625F * bs, -0.71875F * bs, 0.625F * bs)),
                q,
                3.0F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.15625F * bs, 0.34375F * bs, 0.15625F * bs)),
                offset(p, q, v(0.15625F * bs, -0.71875F * bs, 0.625F * bs)),
                q,
                3.0F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.1875F * bs, 0.4375F * bs, 0.1875F * bs)),
                offset(p, q, v(-0.25F * bs, -0.625F * bs, -0.53125F * bs)),
                q,
                4.0F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.1875F * bs, 0.4375F * bs, 0.1875F * bs)),
                offset(p, q, v(0.25F * bs, -0.625F * bs, -0.53125F * bs)),
                q,
                4.0F * bs,
                vel));
    }

    private static void buildSniffer(PhysicsWorld world, List<PhysicsBody> parts, Vector3f p, Quat4f q, Vector3f vel,
        boolean baby) {
        float bs = baby ? 0.5F : 1.0F;
        float hs = baby ? 0.6F : 1.0F;
        parts.add(makePart(world, box(world, v(0.78125F * bs, 0.90625F * bs, 1.25F * bs)), p, q, 28.0F * bs, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.469375F * hs, 0.59375F * hs, 0.625F * hs)),
                offset(p, q, baby ? v(0.0F, -0.28125F, -0.905625F) : v(0.0F, -0.5F, -1.87375F)),
                q,
                8.0F * hs,
                vel));
        float lx = 0.46875F * bs;
        float ly = -0.84375F * bs;
        parts.add(
            makePart(
                world,
                box(world, v(0.21875F * bs, 0.3125F * bs, 0.25F * bs)),
                offset(p, q, v(-lx, ly, 0.9375F * bs)),
                q,
                4.0F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.21875F * bs, 0.3125F * bs, 0.25F * bs)),
                offset(p, q, v(lx, ly, 0.9375F * bs)),
                q,
                4.0F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.21875F * bs, 0.3125F * bs, 0.25F * bs)),
                offset(p, q, v(-lx, ly, -0.9375F * bs)),
                q,
                4.0F * bs,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.21875F * bs, 0.3125F * bs, 0.25F * bs)),
                offset(p, q, v(lx, ly, -0.9375F * bs)),
                q,
                4.0F * bs,
                vel));
    }

    private static void buildRavager(PhysicsWorld world, List<PhysicsBody> parts, Vector3f p, Quat4f q, Vector3f vel) {
        parts.add(makePart(world, box(world, v(0.4375F, 0.625F, 0.90625F)), p, q, 32.0F, vel));
        parts.add(
            makePart(world, box(world, v(0.5F, 0.625F, 0.5F)), offset(p, q, v(0.0F, -0.0625F, -1.5F)), q, 13.0F, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.25F, 1.15625F, 0.25F)),
                offset(p, q, v(-0.5F, -0.46875F, 0.71875F)),
                q,
                8.0F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.25F, 1.15625F, 0.25F)),
                offset(p, q, v(0.5F, -0.46875F, 0.71875F)),
                q,
                8.0F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.25F, 1.15625F, 0.25F)),
                offset(p, q, v(-0.5F, -0.46875F, -0.71875F)),
                q,
                8.0F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.25F, 1.15625F, 0.25F)),
                offset(p, q, v(0.5F, -0.46875F, -0.71875F)),
                q,
                8.0F,
                vel));
    }

    private static void buildPhantom(PhysicsWorld world, List<PhysicsBody> parts, Vector3f p, Quat4f q, Vector3f vel,
        float s) {
        parts.add(makePart(world, box(world, v(0.15625F * s, 0.09375F * s, 0.28125F * s)), p, q, 5.0F * s, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.21875F * s, 0.09375F * s, 0.15625F * s)),
                offset(p, q, v(0.0F, -0.0625F * s, -0.375F * s)),
                q,
                2.0F * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.09375F * s, 0.0625F * s, 0.1875F * s)),
                offset(p, q, v(0.0F, 0.03125F * s, 0.46875F * s)),
                q,
                0.7F * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.03125F * s, 0.03125F * s, 0.1875F * s)),
                offset(p, q, v(0.0F, 0.03125F * s, 0.84375F * s)),
                q,
                0.5F * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.59375F * s, 0.0625F * s, 0.28125F * s)),
                offset(p, q, v(-0.75F * s, 0.03125F * s, 0.0F)),
                q,
                2.0F * s,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.59375F * s, 0.0625F * s, 0.28125F * s)),
                offset(p, q, v(0.75F * s, 0.03125F * s, 0.0F)),
                q,
                2.0F * s,
                vel));
    }

    private static void buildParrot(PhysicsWorld world, List<PhysicsBody> parts, Vector3f p, Quat4f q, Vector3f vel) {
        parts.add(makePart(world, box(world, v(0.09375F, 0.1875F, 0.09375F)), p, q, 1.2F, vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.09375F, 0.1875F, 0.1875F)),
                offset(p, q, v(0.0F, 0.238125F, 0.015F)),
                q,
                0.5F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.03125F, 0.0625F, 0.03125F)),
                offset(p, q, v(-0.0625F, -0.21875F, 0.121875F)),
                q,
                0.2F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.03125F, 0.0625F, 0.03125F)),
                offset(p, q, v(0.0625F, -0.21875F, 0.121875F)),
                q,
                0.2F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.03125F, 0.15625F, 0.09375F)),
                offset(p, q, v(-0.09375F, 0.00375F, 0.015F)),
                q,
                0.25F,
                vel));
        parts.add(
            makePart(
                world,
                box(world, v(0.03125F, 0.15625F, 0.09375F)),
                offset(p, q, v(0.09375F, 0.00375F, 0.015F)),
                q,
                0.25F,
                vel));
    }

    private static void buildCubeMob(PhysicsWorld world, List<PhysicsBody> parts, Vector3f p, Quat4f q, Vector3f vel) {
        parts.add(makePart(world, box(world, v(0.25F, 0.25F, 0.25F)), p, q, 2.0F, vel));

        for (int i = 1; i < 6; i++) {
            PhysicsBody proxy = makePart(world, box(world, v(0.005F, 0.005F, 0.005F)), p, q, 0.01F, vel);
            proxy.setContactResponse(false);
            parts.add(proxy);
        }
    }

    private static PhysicsBody proxy(PhysicsWorld w, Vector3f p, Quat4f q, Vector3f vel) {
        PhysicsBody b = makePart(w, box(w, v(0.005F, 0.005F, 0.005F)), p, q, 0.01F, vel);
        b.setContactResponse(false);
        return b;
    }

    private static void buildSilverfish(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.1875F, 0.125F, 0.09375F)), p, q, 1.2F, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.125F, 0.09375F, 0.125F)),
                offset(p, q, v(0.0F, -0.03125F, -0.21875F)),
                q,
                0.6F,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.09375F, 0.09375F, 0.09375F)),
                offset(p, q, v(0.0F, -0.03125F, 0.1875F)),
                q,
                0.4F,
                vel));
        a.add(
            makePart(w, box(w, v(0.0625F, 0.0625F, 0.09375F)), offset(p, q, v(0.0F, -0.0625F, 0.375F)), q, 0.3F, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.0625F, 0.03125F, 0.0625F)),
                offset(p, q, v(0.0F, -0.09375F, 0.53125F)),
                q,
                0.2F,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.03125F, 0.03125F, 0.0625F)),
                offset(p, q, v(0.0F, -0.09375F, 0.65625F)),
                q,
                0.15F,
                vel));
    }

    private static void buildEndermite(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.1875F, 0.125F, 0.15625F)), p, q, 1.0F, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.125F, 0.09375F, 0.0625F)),
                offset(p, q, v(0.0F, -0.03125F, -0.21875F)),
                q,
                0.5F,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.09375F, 0.09375F, 0.03125F)),
                offset(p, q, v(0.0F, -0.03125F, 0.1875F)),
                q,
                0.3F,
                vel));
        a.add(
            makePart(w, box(w, v(0.03125F, 0.0625F, 0.03125F)), offset(p, q, v(0.0F, -0.0625F, 0.25F)), q, 0.15F, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
    }

    private static void buildAllay(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.09375F, 0.1625F, 0.06875F)), p, q, 1.0F, vel));
        a.add(
            makePart(w, box(w, v(0.15625F, 0.15625F, 0.15625F)), offset(p, q, v(0.0F, 0.311875F, 0.0F)), q, 0.6F, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
        a.add(
            makePart(w, box(w, v(0.03125F, 0.125F, 0.0625F)), offset(p, q, v(-0.125F, 0.03125F, 0.0F)), q, 0.25F, vel));
        a.add(
            makePart(w, box(w, v(0.03125F, 0.125F, 0.0625F)), offset(p, q, v(0.125F, 0.03125F, 0.0F)), q, 0.25F, vel));
    }

    private static void buildStrider(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel,
        boolean baby) {
        float s = baby ? 0.5F : 1.0F;
        a.add(makePart(w, box(w, v(0.5F * s, 0.4375F * s, 0.5F * s)), p, q, 8.0F * s, vel));
        a.add(proxy(w, p, q, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.125F * s, 0.5F * s, 0.125F * s)),
                offset(p, q, v(-0.25F * s, -0.8125F * s, 0.0F)),
                q,
                2.0F * s,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.125F * s, 0.5F * s, 0.125F * s)),
                offset(p, q, v(0.25F * s, -0.8125F * s, 0.0F)),
                q,
                2.0F * s,
                vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
    }

    private static void buildSnowGolem(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.28125F, 0.28125F, 0.28125F)), p, q, 4.0F, vel));
        a.add(makePart(w, box(w, v(0.21875F, 0.21875F, 0.21875F)), offset(p, q, v(0.0F, 0.5F, 0.0F)), q, 2.0F, vel));
        a.add(makePart(w, box(w, v(0.34375F, 0.34375F, 0.34375F)), offset(p, q, v(0.0F, -0.625F, 0.0F)), q, 5.0F, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
    }

    private static void buildBlaze(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.25F, 0.25F, 0.25F)), p, q, 4.0F, vel));

        for (int i = 0; i < 5; i++) {
            a.add(proxy(w, p, q, vel));
        }
    }

    private static void buildBreeze(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.3125F, 0.25F, 0.25F)), p, q, 4.0F, vel));

        for (int i = 0; i < 5; i++) {
            a.add(proxy(w, p, q, vel));
        }
    }

    private static void buildArmadillo(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel,
        boolean baby) {
        float s = baby ? 0.6F : 1.0F;
        a.add(makePart(w, box(w, v(0.25F * s, 0.25F * s, 0.375F * s)), p, q, 6.0F * s, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.09375F * s, 0.15625F * s, 0.0625F * s)),
                offset(p, q, v(0.0F, -0.0625F * s, -0.4375F * s)),
                q,
                1.2F * s,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.0625F * s, 0.09375F * s, 0.0625F * s)),
                offset(p, q, v(-0.125F * s, -0.28125F * s, -0.25F * s)),
                q,
                0.6F * s,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.0625F * s, 0.09375F * s, 0.0625F * s)),
                offset(p, q, v(0.125F * s, -0.28125F * s, -0.25F * s)),
                q,
                0.6F * s,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.0625F * s, 0.09375F * s, 0.0625F * s)),
                offset(p, q, v(-0.125F * s, -0.28125F * s, 0.25F * s)),
                q,
                0.6F * s,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.0625F * s, 0.09375F * s, 0.0625F * s)),
                offset(p, q, v(0.125F * s, -0.28125F * s, 0.25F * s)),
                q,
                0.6F * s,
                vel));
    }

    private static void buildSpider(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel, float s) {
        a.add(makePart(w, box(w, v(0.3125F * s, 0.25F * s, 0.5625F * s)), p, q, 5.0F * s, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.25F * s, 0.25F * s, 0.25F * s)),
                offset(p, q, v(0.0F, 0.0F, -0.8125F * s)),
                q,
                2.0F * s,
                vel));
        addSpiderLegBody(w, a, p, q, vel, s, 4.0F, 2.0F, 7.0F, (float) (-Math.PI / 4), (float) (Math.PI / 4));
        addSpiderLegBody(w, a, p, q, vel, s, -4.0F, 2.0F, -7.0F, (float) (Math.PI / 4), (float) (-Math.PI / 4));
        addSpiderLegBody(w, a, p, q, vel, s, 4.0F, 1.0F, 7.0F, (float) (-Math.PI / 8), 0.58119464F);
        addSpiderLegBody(w, a, p, q, vel, s, -4.0F, 1.0F, -7.0F, (float) (Math.PI / 8), -0.58119464F);
        addSpiderLegBody(w, a, p, q, vel, s, 4.0F, 0.0F, 7.0F, (float) (Math.PI / 8), 0.58119464F);
        addSpiderLegBody(w, a, p, q, vel, s, -4.0F, 0.0F, -7.0F, (float) (-Math.PI / 8), -0.58119464F);
        addSpiderLegBody(w, a, p, q, vel, s, 4.0F, -1.0F, 7.0F, (float) (Math.PI / 4), (float) (Math.PI / 4));
        addSpiderLegBody(w, a, p, q, vel, s, -4.0F, -1.0F, -7.0F, (float) (-Math.PI / 4), (float) (-Math.PI / 4));
    }

    private static void buildShulker(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.5F, 0.25F, 0.5F)), p, q, 8.0F, vel));
        a.add(makePart(w, box(w, v(0.5F, 0.375F, 0.5F)), offset(p, q, v(0.0F, 0.625F, 0.0F)), q, 7.0F, vel));

        for (int i = 0; i < 4; i++) {
            a.add(proxy(w, p, q, vel));
        }
    }

    private static void buildGhast(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(2.25F, 2.25F, 2.25F)), p, q, 24.0F, vel));
        int[] lengths = new int[] { 8, 13, 9, 11, 11, 10, 12, 9, 12 };

        for (int i = 0; i < 9; i++) {
            addGhastTentacleBody(w, a, p, q, vel, i, lengths[i]);
        }
    }

    private static void addSpiderLegBody(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel,
        float s, float pivotX, float pivotZ, float cubeCenterX, float yRot, float zRot) {
        Vector3f center = rotQ(rotationZYX(zRot, yRot, 0), new Vector3f(cubeCenterX, 0, 0));
        Vector3f local = v(
            -(pivotX + center.x) * s / 16.0F,
            -center.y * s / 16.0F,
            (pivotZ + center.z - 6.0F) * s / 16.0F);
        PhysicsShape shape = box(w, v(0.37F * s, 0.028F * s, 0.028F * s));
        a.add(makePart(w, shape, offset(p, q, local), modelPartRotation(q, 0.0F, yRot, zRot), 0.35F * s, vel));
    }

    private static void addGhastTentacleBody(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel,
        int i, int length) {
        float pivotX = ((float) (i % 3) - (float) (i / 3 % 2) * 0.5F - 0.75F) * 5.0F;
        float pivotZ = (float) (i / 3 - 1) * 5.0F;
        Vector3f center = rotQ(rotationZYX(0, 0, .4F), new Vector3f(0, (float) length * .5F, 0));
        float scale = 4.5F;
        Vector3f local = v(
            -(pivotX + center.x) * scale / 16.0F,
            -(7.0F + center.y) * scale / 16.0F,
            (pivotZ + center.z) * scale / 16.0F);
        PhysicsShape shape = box(w, v(0.18F, (float) length * scale / 32.0F, 0.18F));
        a.add(makePart(w, shape, offset(p, q, local), modelPartRotation(q, 0.4F, 0.0F, 0.0F), 1.2F, vel));
    }

    private static void buildVex(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.09375F, 0.15625F, 0.0625F)), p, q, 1.0F, vel));
        a.add(
            makePart(w, box(w, v(0.15625F, 0.15625F, 0.15625F)), offset(p, q, v(0.0F, 0.28125F, 0.0F)), q, 0.7F, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.05625F, 0.1125F, 0.05625F)),
                offset(p, q, v(-0.125F, -0.109375F, 0.0F)),
                q,
                0.25F,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.05625F, 0.1125F, 0.05625F)),
                offset(p, q, v(0.125F, -0.109375F, 0.0F)),
                q,
                0.25F,
                vel));
    }

    private static void buildWarden(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.5625F, 0.65625F, 0.34375F)), p, q, 18.0F, vel));
        a.add(makePart(w, box(w, v(0.5F, 0.5F, 0.3125F)), offset(p, q, v(0.0F, 1.15625F, 0.0F)), q, 8.0F, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.1875F, 0.40625F, 0.1875F)),
                offset(p, q, v(-0.36875F, -1.0625F, 0.0F)),
                q,
                4.0F,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.1875F, 0.40625F, 0.1875F)),
                offset(p, q, v(0.36875F, -1.0625F, 0.0F)),
                q,
                4.0F,
                vel));
        a.add(
            makePart(w, box(w, v(0.25F, 0.875F, 0.25F)), offset(p, q, v(-0.8125F, -0.21875F, 0.0625F)), q, 6.0F, vel));
        a.add(makePart(w, box(w, v(0.25F, 0.875F, 0.25F)), offset(p, q, v(0.8125F, -0.21875F, 0.0625F)), q, 6.0F, vel));
    }

    private static void buildGuardian(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel,
        float s) {
        a.add(makePart(w, box(w, v(0.4375F * s, 0.4375F * s, 0.5F * s)), p, q, 14.0F * s * s * s, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.125F * s, 0.125F * s, 0.25F * s)),
                offset(p, q, v(0.0F, 0.0F, 0.6875F * s)),
                q,
                2.5F * s * s * s,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.09375F * s, 0.09375F * s, 0.21875F * s)),
                offset(p, q, v(0.0F, 0.0F, 1.09375F * s)),
                q,
                1.6F * s * s * s,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.0625F * s, 0.140625F * s, 0.1875F * s)),
                offset(p, q, v(0.0F, 0.0F, 1.625F * s)),
                q,
                1.1F * s * s * s,
                vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
    }

    static float guardianScale(RagdollBodyFactory.BodyProfile profile) {
        return profile == RagdollBodyFactory.BodyProfile.ELDER_GUARDIAN ? 2.35F : 1.0F;
    }

    private static void buildSquid(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.375F, 0.5F, 0.375F)), p, q, 9.0F, vel));

        for (int k = 0; k < 8; k++) {
            double ang = (double) k * Math.PI * 2.0 / 8.0;
            float lx = -((float) Math.cos(ang)) * 0.3125F;
            float lz = (float) Math.sin(ang) * 0.3125F;
            a.add(makePart(w, box(w, v(0.0625F, 0.5625F, 0.0625F)), offset(p, q, v(lx, -1.0F, lz)), q, 0.7F, vel));
        }
    }

    private static void buildDolphin(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.25F, 0.21875F, 0.40625F)), p, q, 7.0F, vel));
        a.add(makePart(w, box(w, v(0.25F, 0.21875F, 0.1875F)), offset(p, q, v(0.0F, 0.0F, -0.59375F)), q, 3.0F, vel));
        a.add(
            makePart(w, box(w, v(0.125F, 0.15625F, 0.34375F)), offset(p, q, v(0.0F, -0.0625F, 0.625F)), q, 2.5F, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
    }

    private static void buildAxolotl(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel,
        boolean baby) {
        float s = baby ? 0.5F : 1.0F;
        float m = baby ? 0.15F : 1.0F;
        a.add(makePart(w, box(w, v(0.25F * s, 0.15625F * s, 0.3125F * s)), p, q, 2.2F * m, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.25F * s, 0.15625F * s, 0.15625F * s)),
                offset(p, q, v(0.0F, 0.0F, -0.46875F * s)),
                q,
                1.0F * m,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.03125F * s, 0.15625F * s, 0.375F * s)),
                offset(p, q, v(0.0F, 0.0F, 0.6875F * s)),
                q,
                0.6F * m,
                vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
        a.add(proxy(w, p, q, vel));
    }

    private static float[] fishDimensions(RagdollBodyFactory.BodyProfile profile) {
        switch (profile) {
            case SALMON:
                return new float[] { 0.046875F, 0.078125F, 0.171875F, 0.046875F, 0.078125F, 0.125F, 0.59375F, 1.1F };
            case TROPICAL_FISH:
                return new float[] { 0.03125F, 0.046875F, 0.09375F, 0.015625F, 0.046875F, 0.09375F, 0.375F, 0.5F };
            case TROPICAL_FISH_LARGE:
                return new float[] { 0.03125F, 0.09375F, 0.09375F, 0.015625F, 0.09375F, 0.078125F, 0.34375F, 0.8F };
            case PUFFERFISH:
                return new float[] { 0.046875F, 0.03125F, 0.046875F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5F };
            case PUFFERFISH_MID:
                return new float[] { 0.15625F, 0.15625F, 0.15625F, 0.0F, 0.0F, 0.0F, 0.0F, 0.8F };
            case PUFFERFISH_BIG:
                return new float[] { 0.25F, 0.25F, 0.25F, 0.0F, 0.0F, 0.0F, 0.0F, 1.2F };
            case TADPOLE:
                return new float[] { 0.046875F, 0.03125F, 0.046875F, 0.015625F, 0.03125F, 0.109375F, 0.3125F, 0.35F };
            default:
                return new float[] { 0.03125F, 0.078125F, 0.171875F, 0.015625F, 0.0625F, 0.0625F, 0.46875F, 0.8F };
        }
    }

    private static void buildFish(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel,
        RagdollBodyFactory.BodyProfile profile) {
        float[] d = fishDimensions(profile);
        float m = d[7];
        a.add(makePart(w, box(w, v(d[0], d[1], d[2])), p, q, m, vel));
        if (d[6] == 0.0F) {
            a.add(proxy(w, p, q, vel));
        } else {
            a.add(makePart(w, box(w, v(d[3], d[4], d[5])), offset(p, q, v(0.0F, 0.0F, d[6])), q, m * 0.35F, vel));
        }

        for (int i = 0; i < 4; i++) {
            a.add(proxy(w, p, q, vel));
        }
    }

    private static void buildWither(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.625F, 0.40625F, 0.09375F)), p, q, 40.0F, vel));
        a.add(makePart(w, box(w, v(0.25F, 0.25F, 0.25F)), offset(p, q, v(0.0F, 1.3F, -0.125F)), q, 10.0F, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.09375F, 0.1875F, 0.09375F)),
                offset(p, q, v(0.0625F, -1.1615F, 0.2535F)),
                q,
                6.0F,
                vel));
        a.add(proxy(w, p, q, vel));
        a.add(
            makePart(w, box(w, v(0.1875F, 0.1875F, 0.1875F)), offset(p, q, v(-1.125F, 0.925F, -0.25F)), q, 7.0F, vel));
        a.add(makePart(w, box(w, v(0.1875F, 0.1875F, 0.1875F)), offset(p, q, v(1.125F, 0.925F, -0.25F)), q, 7.0F, vel));
    }

    private static void buildEnderDragon(PhysicsWorld w, List<PhysicsBody> a, Vector3f p, Quat4f q, Vector3f vel) {
        a.add(makePart(w, box(w, v(0.75F, 0.9375F, 2.0F)), p, q, 400.0F, vel));
        a.add(makePart(w, box(w, v(0.5F, 0.625F, 0.9375F)), offset(p, q, v(0.0F, -0.3125F, -5.9375F)), q, 60.0F, vel));
        a.add(
            makePart(
                w,
                box(w, v(0.25F, 1.59375F, 0.65625F)),
                offset(p, q, v(-0.75F, -1.78125F, -1.78125F)),
                q,
                45.0F,
                vel));
        a.add(
            makePart(
                w,
                box(w, v(0.25F, 1.59375F, 0.65625F)),
                offset(p, q, v(0.75F, -1.78125F, -1.78125F)),
                q,
                45.0F,
                vel));
        a.add(makePart(w, box(w, v(1.75F, 0.25F, 1.9375F)), offset(p, q, v(-2.5F, 0.5F, 0.3125F)), q, 70.0F, vel));
        a.add(makePart(w, box(w, v(1.75F, 0.25F, 1.9375F)), offset(p, q, v(2.5F, 0.5F, 0.3125F)), q, 70.0F, vel));
        a.add(
            makePart(w, box(w, v(0.5625F, 2.28125F, 0.875F)), offset(p, q, v(-1.0F, -2.21875F, 0.75F)), q, 65.0F, vel));
        a.add(
            makePart(w, box(w, v(0.5625F, 2.28125F, 0.875F)), offset(p, q, v(1.0F, -2.21875F, 0.75F)), q, 65.0F, vel));
        a.add(makePart(w, box(w, v(0.3125F, 0.4375F, 0.9375F)), offset(p, q, v(0.0F, -0.3125F, -3.5F)), q, 55.0F, vel));
        a.add(makePart(w, box(w, v(0.3125F, 0.4375F, 3.75F)), offset(p, q, v(0.0F, 0.3125F, 5.6875F)), q, 90.0F, vel));
    }

    private static void buildJoints(PhysicsWorld world, List<PhysicsBody> parts, List<PhysicsConstraint> joints,
        MobRigType modelType, float s, RagdollBodyFactory.BodyProfile bodyProfile, boolean isBaby,
        boolean babyBigHead) {
        if (parts.size() >= 6) {
            PhysicsBody torso = parts.get(PhysicsPart.TORSO.index);
            PhysicsBody head = parts.get(PhysicsPart.HEAD.index);
            PhysicsBody lLeg = parts.get(PhysicsPart.LEFT_LEG.index);
            PhysicsBody rLeg = parts.get(PhysicsPart.RIGHT_LEG.index);
            PhysicsBody lArm = parts.get(PhysicsPart.LEFT_ARM.index);
            PhysicsBody rArm = parts.get(PhysicsPart.RIGHT_ARM.index);
            PhysTransform tTorso = wt(torso);
            PhysTransform tHead = wt(head);
            PhysTransform tLLeg = wt(lLeg);
            PhysTransform tRLeg = wt(rLeg);
            PhysTransform tLArm = wt(lArm);
            PhysTransform tRArm = wt(rArm);
            int firstJoint = joints.size();
            Function<Vector3f, Vector3f> tw = local -> {
                Vector3f out = rotQ(tTorso.getRotation(new Quat4f()), local);
                out.add(tTorso.origin);
                return out;
            };
            switch (modelType) {
                case CREEPER:
                    buildCreeperJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tHead, tw, s);
                    break;
                case QUADRUPED:
                case WOLF:
                case FOX:
                    buildQuadJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lArm,
                        rArm,
                        lLeg,
                        rLeg,
                        tHead,
                        tLArm,
                        tRArm,
                        tLLeg,
                        tRLeg,
                        tw,
                        s,
                        bodyProfile,
                        isBaby);
                    break;
                case PANDA:
                    buildQuadJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lArm,
                        rArm,
                        lLeg,
                        rLeg,
                        tHead,
                        tLArm,
                        tRArm,
                        tLLeg,
                        tRLeg,
                        tw,
                        s,
                        bodyProfile,
                        isBaby);
                    break;
                case GOAT:
                case POLAR_BEAR:
                    buildQuadJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lArm,
                        rArm,
                        lLeg,
                        rLeg,
                        tHead,
                        tLArm,
                        tRArm,
                        tLLeg,
                        tRLeg,
                        tw,
                        s,
                        bodyProfile,
                        isBaby);
                    break;
                case CHICKEN:
                    buildChickenJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lArm,
                        rArm,
                        lLeg,
                        rLeg,
                        tHead,
                        tLArm,
                        tRArm,
                        tLLeg,
                        tRLeg,
                        tw,
                        s);
                    break;
                case EQUINE:
                    buildEquineJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lArm,
                        rArm,
                        lLeg,
                        rLeg,
                        tHead,
                        tLArm,
                        tRArm,
                        tLLeg,
                        tRLeg,
                        tw,
                        bodyProfile,
                        isBaby);
                    break;
                case IRON_GOLEM:
                    buildIronGolemJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw);
                    break;
                case TURTLE:
                    buildTurtleJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, isBaby);
                    break;
                case ENDERMAN:
                    buildEndermanJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw);
                    break;
                case CAMEL:
                    buildCamelJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, isBaby);
                    break;
                case LLAMA:
                    buildLlamaJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, isBaby);
                    break;
                case RABBIT:
                    buildRabbitJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, isBaby);
                    break;
                case FROG:
                    buildFrogJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw);
                    break;
                case HOGLIN:
                    buildHoglinJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, isBaby);
                    break;
                case SNIFFER:
                    buildSnifferJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, isBaby);
                    break;
                case RAVAGER:
                    buildRavagerJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw);
                    break;
                case PHANTOM:
                    buildPhantomJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, s);
                    break;
                case PARROT:
                    buildParrotJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw);
                    break;
                case SLIME:
                case MAGMA_CUBE:
                    buildCubeMobJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw);
                    break;
                case SILVERFISH:
                    buildSegmentJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw, false);
                    break;
                case ENDERMITE:
                    buildSegmentJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw, true);
                    break;
                case ALLAY:
                    buildAllayJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case STRIDER:
                    buildStriderJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw, isBaby);
                    break;
                case SNOW_GOLEM:
                    buildSnowGolemJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case BLAZE:
                    buildRadialJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case BREEZE:
                    buildRadialJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case ARMADILLO:
                    buildArmadilloJoints(world, joints, torso, head, lArm, rArm, lLeg, rLeg, tw, isBaby);
                    break;
                case SPIDER:
                    buildSpiderJoints(
                        world,
                        joints,
                        parts,
                        tw,
                        bodyProfile == RagdollBodyFactory.BodyProfile.CAVE_SPIDER ? 0.7F : 1.0F);
                    break;
                case SHULKER:
                    buildShulkerJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case GHAST:
                    buildGhastJoints(world, joints, parts, tw);
                    break;
                case VEX:
                    buildVexJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case WARDEN:
                    buildWardenJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case GUARDIAN:
                    buildGuardianJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lLeg,
                        rLeg,
                        lArm,
                        rArm,
                        tw,
                        guardianScale(bodyProfile));
                    break;
                case SQUID:
                    buildSquidJoints(world, joints, parts, tw);
                    break;
                case DOLPHIN:
                    buildFinnedJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw, -0.34F, 0.34F);
                    break;
                case AXOLOTL:
                    buildFinnedJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lLeg,
                        rLeg,
                        lArm,
                        rArm,
                        tw,
                        isBaby ? -0.145F : -0.29F,
                        isBaby ? 0.16F : 0.32F);
                    break;
                case FISH:
                    buildFishJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw, bodyProfile);
                    break;
                case WITHER:
                    buildWitherJoints(world, joints, torso, head, lLeg, rLeg, lArm, rArm, tw);
                    break;
                case ENDER_DRAGON:
                    buildEnderDragonJoints(world, joints, parts, tw);
                    break;
                case BAT:
                    buildBatJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lArm,
                        rArm,
                        lLeg,
                        rLeg,
                        tHead,
                        tLArm,
                        tRArm,
                        tLLeg,
                        tRLeg,
                        tw,
                        s);
                    break;
                case BEE:
                    buildBeeJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lArm,
                        rArm,
                        lLeg,
                        rLeg,
                        tHead,
                        tLArm,
                        tRArm,
                        tLLeg,
                        tRLeg,
                        tw,
                        s);
                    break;
                default:
                    buildHumanoidJoints(
                        world,
                        joints,
                        torso,
                        head,
                        lLeg,
                        rLeg,
                        lArm,
                        rArm,
                        tHead,
                        tLLeg,
                        tRLeg,
                        tLArm,
                        tRArm,
                        tw,
                        s,
                        isBaby,
                        babyBigHead);
            }

            applyRelaxation(joints, firstJoint, 1.0F);
            applyAerodynamicDrag(parts);
        }
    }

    private static void applyAerodynamicDrag(List<PhysicsBody> parts) {
        float base = (float) PhysicsConfig.linearDamping;
        float spread = (float) PhysicsConfig.differentialDrag;
        if (!(base <= 0.0F) && !(spread <= 0.0F)) {
            float angular = (float) PhysicsConfig.angularDamping;
            int count = parts.size();
            float[] scale = new float[count];
            float[] mass = new float[count];
            float areaTotal = 0.0F;
            float massTotal = 0.0F;

            for (int i = 0; i < count; i++) {
                PhysicsBody body = parts.get(i);
                float invMass = body.getInvMass();
                if (!(invMass <= 0.0F)) {
                    float area = meanProjectedArea(body);
                    if (!(area <= 0.0F)) {
                        mass[i] = 1.0F / invMass;
                        scale[i] = area / mass[i];
                        areaTotal += area;
                        massTotal += mass[i];
                    }
                }
            }

            if (!(areaTotal <= 0.0F) && !(massTotal <= 0.0F)) {
                float mean = areaTotal / massTotal;
                float weighted = 0.0F;

                for (int ix = 0; ix < count; ix++) {
                    if (!(scale[ix] <= 0.0F)) {
                        float ratio = Math.max(0.2F, Math.min(12.0F, scale[ix] / mean));
                        scale[ix] = spread == 1.0F ? ratio : (float) Math.pow((double) ratio, (double) spread);
                        weighted += mass[ix] * scale[ix];
                    }
                }

                if (!(weighted <= 0.0F)) {
                    float norm = massTotal / weighted;

                    for (int ixx = 0; ixx < count; ixx++) {
                        if (!(scale[ixx] <= 0.0F)) {
                            parts.get(ixx)
                                .setDamping(base * scale[ixx] * norm, angular);
                        }
                    }
                }
            }
        }
    }

    private static float meanProjectedArea(PhysicsBody body) {
        PhysicsShape shape = body.getShape();
        if (shape != null && shape.isBox()) {
            Vector3f half = new Vector3f();
            shape.getHalfExtents(half);
            float a = 2.0F * half.x;
            float b = 2.0F * half.y;
            float c = 2.0F * half.z;
            return (a * b + b * c + c * a) * 0.5F;
        } else {
            return 0.0F;
        }
    }

    private static void buildHumanoidJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lLeg, PhysicsBody rLeg, PhysicsBody lArm, PhysicsBody rArm, PhysTransform tHead,
        PhysTransform tLLeg, PhysTransform tRLeg, PhysTransform tLArm, PhysTransform tRArm,
        Function<Vector3f, Vector3f> tw, float s, boolean isBaby, boolean babyBigHead) {
        float bs = isBaby ? 0.5F : 1.0F;
        float hd = isBaby ? (babyBigHead ? 0.75F : 0.5F) : 1.0F;
        float[] neck = PhysicsConfig.neck;
        float[] hip = PhysicsConfig.hip;
        float[] shoulder = PhysicsConfig.shoulder;
        float[] hipR = mirrored(hip);
        float[] shoulderR = mirrored(shoulder);
        Vector3f authoredLimb = new Vector3f(0.125F * bs, 0.375F * bs, 0.125F * bs);
        Vector3f torsoHalf = halfExtentsOf(torso, new Vector3f(0.25F * bs, 0.375F * bs, 0.15F * bs));
        Vector3f headHalf = halfExtentsOf(head, new Vector3f(0.25F * hd, 0.25F * hd, 0.25F * hd));
        Vector3f lLegHalf = halfExtentsOf(lLeg, authoredLimb);
        Vector3f rLegHalf = halfExtentsOf(rLeg, authoredLimb);
        Vector3f lArmHalf = halfExtentsOf(lArm, authoredLimb);
        Vector3f rArmHalf = halfExtentsOf(rArm, authoredLimb);
        joints.add(
            joint(
                world,
                torso,
                head,
                mid(tw.apply(humanoidNeck(torsoHalf)), onBody(tHead, new Vector3f(0.0F, -headHalf.y, 0.0F))),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                lo(neck),
                hi(neck)));
        joints.add(
            joint(
                world,
                torso,
                lLeg,
                mid(tw.apply(humanoidHip(torsoHalf, bs, -1.0F)), onBody(tLLeg, new Vector3f(0.0F, lLegHalf.y, 0.0F))),
                v(-0.05F * bs, 0.0F, -0.05F * bs),
                v(0.05F * bs, 0.0F, 0.05F * bs),
                lo(hip),
                hi(hip)));
        joints.add(
            joint(
                world,
                torso,
                rLeg,
                mid(tw.apply(humanoidHip(torsoHalf, bs, 1.0F)), onBody(tRLeg, new Vector3f(0.0F, rLegHalf.y, 0.0F))),
                v(-0.05F * bs, 0.0F, -0.05F * bs),
                v(0.05F * bs, 0.0F, 0.05F * bs),
                lo(hipR),
                hi(hipR)));
        joints.add(
            joint(
                world,
                torso,
                lArm,
                mid(
                    tw.apply(humanoidShoulder(torsoHalf, lArmHalf, bs, -1.0F)),
                    onBody(tLArm, humanoidShoulderOnArm(lArmHalf, bs, -1.0F))),
                v(-0.02F * bs, -0.02F * bs, -0.02F * bs),
                v(0.02F * bs, 0.02F * bs, 0.02F * bs),
                lo(shoulder),
                hi(shoulder),
                true));
        joints.add(
            joint(
                world,
                torso,
                rArm,
                mid(
                    tw.apply(humanoidShoulder(torsoHalf, rArmHalf, bs, 1.0F)),
                    onBody(tRArm, humanoidShoulderOnArm(rArmHalf, bs, 1.0F))),
                v(-0.02F * bs, -0.02F * bs, -0.02F * bs),
                v(0.02F * bs, 0.02F * bs, 0.02F * bs),
                lo(shoulderR),
                hi(shoulderR),
                true));
    }

    private static Vector3f onBody(PhysTransform body, Vector3f local) {
        Vector3f point = rotQ(body.getRotation(new Quat4f()), local);
        point.add(body.origin);
        return point;
    }

    private static Vector3f halfExtentsOf(PhysicsBody body, Vector3f authored) {
        try {
            PhysicsShape shape = body.getShape();
            if (shape != null && shape.isBox()) {
                Vector3f out = new Vector3f();
                shape.getHalfExtents(out);
                if (out.x > 1.0E-4F && out.y > 1.0E-4F && out.z > 1.0E-4F) {
                    return out;
                }
            }
        } catch (Exception var4) {}

        return authored;
    }

    public static void applyRelaxation(List<PhysicsConstraint> joints, int firstJoint, float scale) {
        relaxJoints(joints, firstJoint, firstJoint + 1, 16.0F * scale);
        relaxJoints(joints, firstJoint + 1, joints.size(), scale);
    }

    private static void relaxJoints(List<PhysicsConstraint> joints, int from, int to, float scale) {
        float frequency = (float) PhysicsConfig.relaxFrequency * scale;
        float damping = (float) PhysicsConfig.relaxDamping;
        float torque = (float) PhysicsConfig.relaxTorque;

        for (int i = Math.max(0, from); i < Math.min(to, joints.size()); i++) {
            joints.get(i)
                .relaxToRest(frequency, damping, torque);
        }
    }

    public static void applyFlail(List<PhysicsConstraint> joints, int firstJoint, float strengthScale,
        float spreadRadians, Random rng) {
        float frequency = (float) PhysicsConfig.relaxFrequency * strengthScale;
        float damping = (float) PhysicsConfig.relaxDamping;
        float torque = (float) PhysicsConfig.relaxTorque;

        for (int i = Math.max(0, firstJoint); i < joints.size(); i++) {
            float ax = (float) rng.nextGaussian();
            float ay = (float) rng.nextGaussian();
            float az = (float) rng.nextGaussian();
            float len = (float) Math.sqrt((double) (ax * ax + ay * ay + az * az));
            if (len < 1.0E-6F) {
                ax = 0.0F;
                ay = 1.0F;
                az = 0.0F;
                len = 1.0F;
            }

            float angle = spreadRadians * (rng.nextFloat() * 2.0F - 1.0F);
            float half = angle * 0.5F;
            float sin = (float) Math.sin((double) half) / len;
            joints.get(i)
                .driveToOrientation(
                    ax * sin,
                    ay * sin,
                    az * sin,
                    (float) Math.cos((double) half),
                    frequency,
                    damping,
                    torque);
        }
    }

    private static float[] mirrored(float[] limits) {
        return new float[] { limits[0], -limits[4], -limits[5], limits[3], -limits[1], -limits[2] };
    }

    private static Vector3f lo(float[] limits) {
        return v(limits[0], limits[1], limits[2]);
    }

    private static Vector3f hi(float[] limits) {
        return v(limits[3], limits[4], limits[5]);
    }

    private static void buildIronGolemJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody leftArm, PhysicsBody rightArm, PhysicsBody leftLeg, PhysicsBody rightLeg,
        Function<Vector3f, Vector3f> tw) {
        Vector3f zero = v(0.0F, 0.0F, 0.0F);
        Vector3f lin = v(-0.015F, -0.015F, -0.015F);
        Vector3f liu = v(0.015F, 0.015F, 0.015F);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(v(0.0F, 0.55F, -0.16F)),
                zero,
                zero,
                v(-25.0F, -30.0F, -20.0F),
                v(35.0F, 30.0F, 20.0F)));
        joints.add(
            joint(
                world,
                torso,
                leftLeg,
                tw.apply(v(-0.28125F, -0.53F, 0.0F)),
                lin,
                liu,
                v(-45.0F, -12.0F, -15.0F),
                v(65.0F, 12.0F, 15.0F)));
        joints.add(
            joint(
                world,
                torso,
                rightLeg,
                tw.apply(v(0.28125F, -0.53F, 0.0F)),
                lin,
                liu,
                v(-45.0F, -12.0F, -15.0F),
                v(65.0F, 12.0F, 15.0F)));
        joints.add(
            joint(
                world,
                torso,
                leftArm,
                tw.apply(v(-0.61F, 0.55F, 0.0F)),
                lin,
                liu,
                v(-110.0F, -20.0F, -35.0F),
                v(110.0F, 20.0F, 35.0F)));
        joints.add(
            joint(
                world,
                torso,
                rightArm,
                tw.apply(v(0.61F, 0.55F, 0.0F)),
                lin,
                liu,
                v(-110.0F, -20.0F, -35.0F),
                v(110.0F, 20.0F, 35.0F)));
    }

    private static void buildTurtleJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody leftFront, PhysicsBody rightFront, PhysicsBody leftHind, PhysicsBody rightHind,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float b = baby ? 0.16666667F : 1.0F;
        Vector3f lin = v(-0.015F * b, -0.015F * b, -0.015F * b);
        Vector3f liu = v(0.015F * b, 0.015F * b, 0.015F * b);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(v(0.0F, -0.02F * b, -0.62F * b)),
                lin,
                liu,
                v(-30.0F, -35.0F, -25.0F),
                v(30.0F, 35.0F, 25.0F)));
        joints.add(
            joint(
                world,
                torso,
                leftFront,
                tw.apply(v(-0.48F * b, -0.15F * b, -0.35F * b)),
                lin,
                liu,
                v(-25.0F, -20.0F, -45.0F),
                v(25.0F, 20.0F, 45.0F)));
        joints.add(
            joint(
                world,
                torso,
                rightFront,
                tw.apply(v(0.48F * b, -0.15F * b, -0.35F * b)),
                lin,
                liu,
                v(-25.0F, -20.0F, -45.0F),
                v(25.0F, 20.0F, 45.0F)));
        joints.add(
            joint(
                world,
                torso,
                leftHind,
                tw.apply(v(-0.22F * b, -0.18F * b, 0.55F * b)),
                lin,
                liu,
                v(-25.0F, -30.0F, -25.0F),
                v(25.0F, 30.0F, 25.0F)));
        joints.add(
            joint(
                world,
                torso,
                rightHind,
                tw.apply(v(0.22F * b, -0.18F * b, 0.55F * b)),
                lin,
                liu,
                v(-25.0F, -30.0F, -25.0F),
                v(25.0F, 30.0F, 25.0F)));
    }

    private static void buildEndermanJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody leftArm, PhysicsBody rightArm, PhysicsBody leftLeg, PhysicsBody rightLeg,
        Function<Vector3f, Vector3f> tw) {
        Vector3f lin = v(-0.015F, -0.015F, -0.015F);
        Vector3f liu = v(0.015F, 0.015F, 0.015F);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(v(0.0F, 0.35F, 0.0F)),
                lin,
                liu,
                v(-35.0F, -35.0F, -25.0F),
                v(45.0F, 35.0F, 25.0F)));
        joints.add(
            joint(
                world,
                torso,
                leftLeg,
                tw.apply(v(-0.125F, -0.28F, 0.0F)),
                lin,
                liu,
                v(-55.0F, -12.0F, -15.0F),
                v(70.0F, 12.0F, 15.0F)));
        joints.add(
            joint(
                world,
                torso,
                rightLeg,
                tw.apply(v(0.125F, -0.28F, 0.0F)),
                lin,
                liu,
                v(-55.0F, -12.0F, -15.0F),
                v(70.0F, 12.0F, 15.0F)));
        joints.add(
            joint(
                world,
                torso,
                leftArm,
                tw.apply(v(-0.3125F, 0.375F, 0.0F)),
                lin,
                liu,
                v(-120.0F, -25.0F, -40.0F),
                v(120.0F, 25.0F, 40.0F)));
        joints.add(
            joint(
                world,
                torso,
                rightArm,
                tw.apply(v(0.3125F, 0.375F, 0.0F)),
                lin,
                liu,
                v(-120.0F, -25.0F, -40.0F),
                v(120.0F, 25.0F, 40.0F)));
    }

    private static void buildCamelJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float b = baby ? 0.45F : 1.0F;
        Vector3f l = v(-0.015F * b, -0.015F * b, -0.015F * b);
        Vector3f u = v(0.015F * b, 0.015F * b, 0.015F * b);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(v(0.0F, 0.25F * b, -0.72F * b)),
                l,
                u,
                v(-45.0F, -30.0F, -25.0F),
                v(55.0F, 30.0F, 25.0F)));
        joints.add(
            joint(
                world,
                torso,
                lf,
                tw.apply(v(-0.31F * b, -0.36F * b, -0.62F * b)),
                l,
                u,
                v(-55.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        joints.add(
            joint(
                world,
                torso,
                rf,
                tw.apply(v(0.31F * b, -0.36F * b, -0.62F * b)),
                l,
                u,
                v(-55.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        joints.add(
            joint(
                world,
                torso,
                lh,
                tw.apply(v(-0.31F * b, -0.36F * b, 0.62F * b)),
                l,
                u,
                v(-55.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        joints.add(
            joint(
                world,
                torso,
                rh,
                tw.apply(v(0.31F * b, -0.36F * b, 0.62F * b)),
                l,
                u,
                v(-55.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
    }

    private static void buildLlamaJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float x = baby ? 0.1F : 0.22F;
        float y = baby ? -0.14F : -0.31F;
        float fz = baby ? -0.17F : -0.375F;
        float hz = baby ? 0.142F : 0.3125F;
        Vector3f l = v(-0.015F, -0.015F, -0.015F);
        Vector3f u = v(0.015F, 0.015F, 0.015F);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(v(0.0F, baby ? 0.2F : 0.38F, baby ? -0.28F : -0.55F)),
                l,
                u,
                v(-45.0F, -30.0F, -25.0F),
                v(55.0F, 30.0F, 25.0F)));
        joints.add(
            joint(world, torso, lf, tw.apply(v(-x, y, fz)), l, u, v(-50.0F, -12.0F, -15.0F), v(55.0F, 12.0F, 15.0F)));
        joints.add(
            joint(world, torso, rf, tw.apply(v(x, y, fz)), l, u, v(-50.0F, -12.0F, -15.0F), v(55.0F, 12.0F, 15.0F)));
        joints.add(
            joint(world, torso, lh, tw.apply(v(-x, y, hz)), l, u, v(-50.0F, -12.0F, -15.0F), v(55.0F, 12.0F, 15.0F)));
        joints.add(
            joint(world, torso, rh, tw.apply(v(x, y, hz)), l, u, v(-50.0F, -12.0F, -15.0F), v(55.0F, 12.0F, 15.0F)));
    }

    private static void buildRabbitJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float k = baby ? 0.667F : 1.0F;
        Vector3f l = v(-0.008F, -0.008F, -0.008F);
        Vector3f u = v(0.008F, 0.008F, 0.008F);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(v(0.0F, 0.12F * k, -0.18F * k)),
                l,
                u,
                v(-40.0F, -35.0F, -25.0F),
                v(55.0F, 35.0F, 25.0F)));
        joints.add(
            joint(
                world,
                torso,
                lf,
                tw.apply(v(-0.11F * k, -0.05F * k, -0.15F * k)),
                l,
                u,
                v(-65.0F, -15.0F, -20.0F),
                v(45.0F, 15.0F, 20.0F)));
        joints.add(
            joint(
                world,
                torso,
                rf,
                tw.apply(v(0.11F * k, -0.05F * k, -0.15F * k)),
                l,
                u,
                v(-65.0F, -15.0F, -20.0F),
                v(45.0F, 15.0F, 20.0F)));
        joints.add(
            joint(
                world,
                torso,
                lh,
                tw.apply(v(-0.11F * k, -0.04F * k, 0.08F * k)),
                l,
                u,
                v(-70.0F, -20.0F, -25.0F),
                v(70.0F, 20.0F, 25.0F)));
        joints.add(
            joint(
                world,
                torso,
                rh,
                tw.apply(v(0.11F * k, -0.04F * k, 0.08F * k)),
                l,
                u,
                v(-70.0F, -20.0F, -25.0F),
                v(70.0F, 20.0F, 25.0F)));
    }

    private static void buildFrogJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.008F, -0.008F, -0.008F);
        Vector3f u = v(0.008F, 0.008F, 0.008F);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(v(0.0F, 0.0625F, 0.0F)),
                l,
                u,
                v(-30.0F, -30.0F, -20.0F),
                v(35.0F, 30.0F, 20.0F)));
        joints.add(
            joint(
                world,
                torso,
                lf,
                tw.apply(v(-0.25F, 0.03125F, -0.1875F)),
                l,
                u,
                v(-50.0F, -25.0F, -45.0F),
                v(50.0F, 25.0F, 45.0F)));
        joints.add(
            joint(
                world,
                torso,
                rf,
                tw.apply(v(0.25F, 0.03125F, -0.1875F)),
                l,
                u,
                v(-50.0F, -25.0F, -45.0F),
                v(50.0F, 25.0F, 45.0F)));
        joints.add(
            joint(
                world,
                torso,
                lh,
                tw.apply(v(-0.21875F, 0.03125F, 0.21875F)),
                l,
                u,
                v(-60.0F, -35.0F, -40.0F),
                v(60.0F, 35.0F, 40.0F)));
        joints.add(
            joint(
                world,
                torso,
                rh,
                tw.apply(v(0.21875F, 0.03125F, 0.21875F)),
                l,
                u,
                v(-60.0F, -35.0F, -40.0F),
                v(60.0F, 35.0F, 40.0F)));
    }

    private static void buildHoglinJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float b = baby ? 0.5F : 1.0F;
        Vector3f l = v(-0.015F * b, -0.015F * b, -0.015F * b);
        Vector3f u = v(0.015F * b, 0.015F * b, 0.015F * b);
        js.add(
            joint(
                w,
                torso,
                head,
                tw.apply(baby ? v(0.0F, 0.18F, -0.45F) : v(0.0F, 0.0F, -0.72F)),
                l,
                u,
                v(-40.0F, -25.0F, -20.0F),
                v(45.0F, 25.0F, 20.0F)));
        js.add(
            joint(
                w,
                torso,
                lf,
                tw.apply(v(-0.25F * b, -0.19F * b, -0.53F * b)),
                l,
                u,
                v(-50.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        js.add(
            joint(
                w,
                torso,
                rf,
                tw.apply(v(0.25F * b, -0.19F * b, -0.53F * b)),
                l,
                u,
                v(-50.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        js.add(
            joint(
                w,
                torso,
                lh,
                tw.apply(v(-0.15625F * b, -0.375F * b, 0.625F * b)),
                l,
                u,
                v(-50.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        js.add(
            joint(
                w,
                torso,
                rh,
                tw.apply(v(0.15625F * b, -0.375F * b, 0.625F * b)),
                l,
                u,
                v(-50.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
    }

    private static void buildSnifferJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float b = baby ? 0.5F : 1.0F;
        Vector3f l = v(-0.02F * b, -0.02F * b, -0.02F * b);
        Vector3f u = v(0.02F * b, 0.02F * b, 0.02F * b);
        js.add(
            joint(
                w,
                torso,
                head,
                tw.apply(baby ? v(0.0F, -0.15F, -0.58F) : v(0.0F, -0.25F, -1.25F)),
                l,
                u,
                v(-35.0F, -25.0F, -20.0F),
                v(40.0F, 25.0F, 20.0F)));
        float x = 0.46875F * b;
        float y = -0.53125F * b;
        js.add(
            joint(
                w,
                torso,
                lf,
                tw.apply(v(-x, y, -0.9375F * b)),
                l,
                u,
                v(-45.0F, -10.0F, -12.0F),
                v(50.0F, 10.0F, 12.0F)));
        js.add(
            joint(
                w,
                torso,
                rf,
                tw.apply(v(x, y, -0.9375F * b)),
                l,
                u,
                v(-45.0F, -10.0F, -12.0F),
                v(50.0F, 10.0F, 12.0F)));
        js.add(
            joint(
                w,
                torso,
                lh,
                tw.apply(v(-x, y, 0.9375F * b)),
                l,
                u,
                v(-45.0F, -10.0F, -12.0F),
                v(50.0F, 10.0F, 12.0F)));
        js.add(
            joint(
                w,
                torso,
                rh,
                tw.apply(v(x, y, 0.9375F * b)),
                l,
                u,
                v(-45.0F, -10.0F, -12.0F),
                v(50.0F, 10.0F, 12.0F)));
    }

    private static void buildRavagerJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.02F, -0.02F, -0.02F);
        Vector3f u = v(0.02F, 0.02F, 0.02F);
        js.add(
            joint(
                w,
                torso,
                head,
                tw.apply(v(0.0F, 0.0F, -1.0F)),
                l,
                u,
                v(-30.0F, -20.0F, -18.0F),
                v(35.0F, 20.0F, 18.0F)));
        js.add(
            joint(
                w,
                torso,
                lf,
                tw.apply(v(-0.5F, 0.6F, -0.71875F)),
                l,
                u,
                v(-40.0F, -8.0F, -10.0F),
                v(45.0F, 8.0F, 10.0F)));
        js.add(
            joint(
                w,
                torso,
                rf,
                tw.apply(v(0.5F, 0.6F, -0.71875F)),
                l,
                u,
                v(-40.0F, -8.0F, -10.0F),
                v(45.0F, 8.0F, 10.0F)));
        js.add(
            joint(
                w,
                torso,
                lh,
                tw.apply(v(-0.5F, 0.6F, 0.71875F)),
                l,
                u,
                v(-40.0F, -8.0F, -10.0F),
                v(45.0F, 8.0F, 10.0F)));
        js.add(
            joint(
                w,
                torso,
                rh,
                tw.apply(v(0.5F, 0.6F, 0.71875F)),
                l,
                u,
                v(-40.0F, -8.0F, -10.0F),
                v(45.0F, 8.0F, 10.0F)));
    }

    private static void buildPhantomJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lw, PhysicsBody rw, PhysicsBody tailBase, PhysicsBody tailTip,
        Function<Vector3f, Vector3f> tw, float s) {
        Vector3f l = v(-0.01F * s, -0.01F * s, -0.01F * s);
        Vector3f u = v(0.01F * s, 0.01F * s, 0.01F * s);
        js.add(
            joint(
                w,
                torso,
                head,
                tw.apply(v(0.0F, 0.0F, -0.28F * s)),
                l,
                u,
                v(-30.0F, -25.0F, -20.0F),
                v(35.0F, 25.0F, 20.0F)));
        js.add(
            joint(
                w,
                torso,
                lw,
                tw.apply(v(-0.15F * s, 0.06F * s, -0.25F * s)),
                l,
                u,
                v(-25.0F, -15.0F, -70.0F),
                v(25.0F, 15.0F, 70.0F)));
        js.add(
            joint(
                w,
                torso,
                rw,
                tw.apply(v(0.15F * s, 0.06F * s, -0.25F * s)),
                l,
                u,
                v(-25.0F, -15.0F, -70.0F),
                v(25.0F, 15.0F, 70.0F)));
        js.add(
            joint(
                w,
                torso,
                tailBase,
                tw.apply(v(0.0F, 0.02F * s, 0.3F * s)),
                l,
                u,
                v(-35.0F, -15.0F, -15.0F),
                v(35.0F, 15.0F, 15.0F)));
        js.add(
            joint(
                w,
                tailBase,
                tailTip,
                tw.apply(v(0.0F, 0.03F * s, 0.66F * s)),
                l,
                u,
                v(-35.0F, -15.0F, -15.0F),
                v(35.0F, 15.0F, 15.0F)));
    }

    private static void buildParrotJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lw, PhysicsBody rw, PhysicsBody ll, PhysicsBody rl,
        Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.005F, -0.005F, -0.005F);
        Vector3f u = v(0.005F, 0.005F, 0.005F);
        js.add(
            joint(
                w,
                torso,
                head,
                tw.apply(v(0.0F, 0.14F, 0.0F)),
                l,
                u,
                v(-35.0F, -30.0F, -25.0F),
                v(45.0F, 30.0F, 25.0F)));
        js.add(
            joint(
                w,
                torso,
                lw,
                tw.apply(v(-0.09F, 0.08F, 0.0F)),
                l,
                u,
                v(-65.0F, -30.0F, -55.0F),
                v(65.0F, 30.0F, 55.0F)));
        js.add(
            joint(
                w,
                torso,
                rw,
                tw.apply(v(0.09F, 0.08F, 0.0F)),
                l,
                u,
                v(-65.0F, -30.0F, -55.0F),
                v(65.0F, 30.0F, 55.0F)));
        js.add(
            joint(
                w,
                torso,
                ll,
                tw.apply(v(-0.0625F, -0.16F, 0.12F)),
                l,
                u,
                v(-35.0F, -15.0F, -15.0F),
                v(40.0F, 15.0F, 15.0F)));
        js.add(
            joint(
                w,
                torso,
                rl,
                tw.apply(v(0.0625F, -0.16F, 0.12F)),
                l,
                u,
                v(-35.0F, -15.0F, -15.0F),
                v(40.0F, 15.0F, 15.0F)));
    }

    private static void buildCubeMobJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody la, PhysicsBody ra, PhysicsBody ll, PhysicsBody rl,
        Function<Vector3f, Vector3f> tw) {
        Vector3f z = v(0.0F, 0.0F, 0.0F);
        Vector3f a = v(-1.0F, -1.0F, -1.0F);
        Vector3f b = v(1.0F, 1.0F, 1.0F);
        Vector3f anchor = tw.apply(z);
        js.add(joint(w, torso, head, anchor, z, z, a, b));
        js.add(joint(w, torso, la, anchor, z, z, a, b));
        js.add(joint(w, torso, ra, anchor, z, z, a, b));
        js.add(joint(w, torso, ll, anchor, z, z, a, b));
        js.add(joint(w, torso, rl, anchor, z, z, a, b));
    }

    private static void buildSegmentJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody s3, PhysicsBody s4, PhysicsBody s5, PhysicsBody s6,
        Function<Vector3f, Vector3f> tw, boolean endermite) {
        Vector3f l = v(-0.004F, -0.004F, -0.004F);
        Vector3f u = v(0.004F, 0.004F, 0.004F);
        Vector3f al = v(-25.0F, -20.0F, -20.0F);
        Vector3f au = v(25.0F, 20.0F, 20.0F);
        js.add(joint(w, head, torso, tw.apply(v(0.0F, 0.0F, -0.08F)), l, u, al, au));
        js.add(joint(w, torso, s3, tw.apply(v(0.0F, 0.0F, 0.1F)), l, u, al, au));
        js.add(joint(w, s3, s4, tw.apply(v(0.0F, -0.04F, 0.28F)), l, u, al, au));
        if (endermite) {
            Vector3f z = v(0.0F, 0.0F, 0.0F);
            Vector3f a = v(-1.0F, -1.0F, -1.0F);
            Vector3f b = v(1.0F, 1.0F, 1.0F);
            js.add(joint(w, torso, s5, tw.apply(z), z, z, a, b));
            js.add(joint(w, torso, s6, tw.apply(z), z, z, a, b));
        } else {
            js.add(joint(w, s4, s5, tw.apply(v(0.0F, -0.07F, 0.46F)), l, u, al, au));
            js.add(joint(w, s5, s6, tw.apply(v(0.0F, -0.09F, 0.59F)), l, u, al, au));
        }
    }

    private static void buildAllayJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody h,
        PhysicsBody lw, PhysicsBody rw, PhysicsBody la, PhysicsBody ra, Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.004F, -0.004F, -0.004F);
        Vector3f u = v(0.004F, 0.004F, 0.004F);
        js.add(joint(w, t, h, tw.apply(v(0.0F, 0.17F, 0.0F)), l, u, v(-35.0F, -30.0F, -25.0F), v(45.0F, 30.0F, 25.0F)));
        js.add(
            joint(w, t, la, tw.apply(v(-0.1F, 0.12F, 0.0F)), l, u, v(-80.0F, -30.0F, -50.0F), v(80.0F, 30.0F, 50.0F)));
        js.add(
            joint(w, t, ra, tw.apply(v(0.1F, 0.12F, 0.0F)), l, u, v(-80.0F, -30.0F, -50.0F), v(80.0F, 30.0F, 50.0F)));
        addFixedProxyJoints(w, js, t, tw, lw, rw);
    }

    private static void buildStriderJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t,
        PhysicsBody proxyHead, PhysicsBody ll, PhysicsBody rl, PhysicsBody p1, PhysicsBody p2,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float s = baby ? 0.5F : 1.0F;
        Vector3f z = v(0.0F, 0.0F, 0.0F);
        Vector3f a = v(-1.0F, -1.0F, -1.0F);
        Vector3f b = v(1.0F, 1.0F, 1.0F);
        Vector3f l = v(-0.01F * s, -0.01F * s, -0.01F * s);
        Vector3f u = v(0.01F * s, 0.01F * s, 0.01F * s);
        js.add(
            joint(
                w,
                t,
                ll,
                tw.apply(v(-0.25F * s, -0.4F * s, 0.0F)),
                l,
                u,
                v(-55.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        js.add(
            joint(
                w,
                t,
                rl,
                tw.apply(v(0.25F * s, -0.4F * s, 0.0F)),
                l,
                u,
                v(-55.0F, -12.0F, -15.0F),
                v(55.0F, 12.0F, 15.0F)));
        js.add(joint(w, t, proxyHead, tw.apply(z), z, z, a, b));
        js.add(joint(w, t, p1, tw.apply(z), z, z, a, b));
        js.add(joint(w, t, p2, tw.apply(z), z, z, a, b));
    }

    private static void buildSnowGolemJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody h,
        PhysicsBody lower, PhysicsBody proxy, PhysicsBody la, PhysicsBody ra, Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.01F, -0.01F, -0.01F);
        Vector3f u = v(0.01F, 0.01F, 0.01F);
        js.add(joint(w, t, h, tw.apply(v(0.0F, 0.28F, 0.0F)), l, u, v(-30.0F, -30.0F, -20.0F), v(40.0F, 30.0F, 20.0F)));
        js.add(
            joint(
                w,
                t,
                lower,
                tw.apply(v(0.0F, -0.3F, 0.0F)),
                l,
                u,
                v(-20.0F, -15.0F, -15.0F),
                v(20.0F, 15.0F, 15.0F)));
        addFixedProxyJoints(w, js, t, tw, proxy, la, ra);
    }

    private static void buildArmadilloJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lf, PhysicsBody rf, PhysicsBody lh, PhysicsBody rh,
        Function<Vector3f, Vector3f> tw, boolean baby) {
        float s = baby ? 0.6F : 1.0F;
        Vector3f l = v(-0.008F, -0.008F, -0.008F);
        Vector3f u = v(0.008F, 0.008F, 0.008F);
        js.add(
            joint(
                w,
                torso,
                head,
                tw.apply(v(0.0F, -0.0625F * s, -0.4375F * s)),
                l,
                u,
                v(-25.0F, -30.0F, -20.0F),
                v(30.0F, 30.0F, 20.0F)));
        js.add(
            joint(
                w,
                torso,
                lf,
                tw.apply(v(-0.125F * s, -0.1875F * s, -0.25F * s)),
                l,
                u,
                v(-45.0F, -15.0F, -25.0F),
                v(45.0F, 15.0F, 25.0F)));
        js.add(
            joint(
                w,
                torso,
                rf,
                tw.apply(v(0.125F * s, -0.1875F * s, -0.25F * s)),
                l,
                u,
                v(-45.0F, -15.0F, -25.0F),
                v(45.0F, 15.0F, 25.0F)));
        js.add(
            joint(
                w,
                torso,
                lh,
                tw.apply(v(-0.125F * s, -0.1875F * s, 0.25F * s)),
                l,
                u,
                v(-45.0F, -15.0F, -25.0F),
                v(45.0F, 15.0F, 25.0F)));
        js.add(
            joint(
                w,
                torso,
                rh,
                tw.apply(v(0.125F * s, -0.1875F * s, 0.25F * s)),
                l,
                u,
                v(-45.0F, -15.0F, -25.0F),
                v(45.0F, 15.0F, 25.0F)));
    }

    private static void buildRadialJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody a,
        PhysicsBody b, PhysicsBody c, PhysicsBody d, PhysicsBody e, Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.01F, -0.01F, -0.01F);
        Vector3f u = v(0.01F, 0.01F, 0.01F);
        Vector3f al = v(-45.0F, -45.0F, -45.0F);
        Vector3f au = v(45.0F, 45.0F, 45.0F);
        Vector3f p = tw.apply(v(0.0F, 0.0F, 0.0F));
        js.add(joint(w, t, a, p, l, u, al, au));
        js.add(joint(w, t, b, p, l, u, al, au));
        js.add(joint(w, t, c, p, l, u, al, au));
        js.add(joint(w, t, d, p, l, u, al, au));
        js.add(joint(w, t, e, p, l, u, al, au));
    }

    private static void buildSpiderJoints(PhysicsWorld w, List<PhysicsConstraint> js, List<PhysicsBody> parts,
        Function<Vector3f, Vector3f> tw, float s) {
        PhysicsBody t = parts.get(0);
        PhysicsBody h = parts.get(1);
        Vector3f l = v(-0.01F * s, -0.01F * s, -0.01F * s);
        Vector3f u = v(0.01F * s, 0.01F * s, 0.01F * s);
        js.add(
            joint(
                w,
                t,
                h,
                tw.apply(v(0.0F, 0.0F, -0.42F * s)),
                l,
                u,
                v(-25.0F, -30.0F, -20.0F),
                v(30.0F, 30.0F, 20.0F)));
        float[] px = new float[] { 4.0F, -4.0F, 4.0F, -4.0F, 4.0F, -4.0F, 4.0F, -4.0F };
        float[] pz = new float[] { 2.0F, 2.0F, 1.0F, 1.0F, 0.0F, 0.0F, -1.0F, -1.0F };

        for (int i = 0; i < 8; i++) {
            Vector3f anchor = tw.apply(v(-px[i] * s / 16.0F, 0.0F, (pz[i] - 6.0F) * s / 16.0F));
            js.add(
                jointAtCurrentPose(
                    w,
                    t,
                    parts.get(i + 2),
                    anchor,
                    l,
                    u,
                    v(-50.0F, -35.0F, -60.0F),
                    v(50.0F, 35.0F, 60.0F)));
        }
    }

    private static void buildGhastJoints(PhysicsWorld w, List<PhysicsConstraint> js, List<PhysicsBody> parts,
        Function<Vector3f, Vector3f> tw) {
        PhysicsBody body = parts.get(0);
        Vector3f l = v(-0.01F, -0.01F, -0.01F);
        Vector3f u = v(0.01F, 0.01F, 0.01F);

        for (int i = 0; i < 9; i++) {
            float px = ((float) (i % 3) - (float) (i / 3 % 2) * 0.5F - 0.75F) * 5.0F;
            float pz = (float) (i / 3 - 1) * 5.0F;
            float scale = 4.5F;
            Vector3f anchor = tw.apply(v(-px * scale / 16.0F, -7.0F * scale / 16.0F, pz * scale / 16.0F));
            js.add(
                jointAtCurrentPose(
                    w,
                    body,
                    parts.get(i + 1),
                    anchor,
                    l,
                    u,
                    v(-35.0F, -18.0F, -35.0F),
                    v(35.0F, 18.0F, 35.0F)));
        }
    }

    private static void buildShulkerJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody lid,
        PhysicsBody p2, PhysicsBody p3, PhysicsBody p4, PhysicsBody p5, Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.006F, -0.006F, -0.006F);
        Vector3f u = v(0.006F, 0.006F, 0.006F);
        js.add(
            joint(w, t, lid, tw.apply(v(0.0F, 0.25F, 0.0F)), l, u, v(-18.0F, -12.0F, -18.0F), v(18.0F, 12.0F, 18.0F)));
        addFixedProxyJoints(w, js, t, tw, p2, p3, p4, p5);
    }

    private static void buildVexJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody h,
        PhysicsBody p2, PhysicsBody p3, PhysicsBody la, PhysicsBody ra, Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.003F, -0.003F, -0.003F);
        Vector3f u = v(0.003F, 0.003F, 0.003F);
        js.add(joint(w, t, h, tw.apply(v(0.0F, 0.16F, 0.0F)), l, u, v(-35.0F, -30.0F, -25.0F), v(45.0F, 30.0F, 25.0F)));
        js.add(
            joint(
                w,
                t,
                la,
                tw.apply(v(-0.09F, 0.04F, 0.0F)),
                l,
                u,
                v(-100.0F, -35.0F, -65.0F),
                v(100.0F, 35.0F, 65.0F)));
        js.add(
            joint(
                w,
                t,
                ra,
                tw.apply(v(0.09F, 0.04F, 0.0F)),
                l,
                u,
                v(-100.0F, -35.0F, -65.0F),
                v(100.0F, 35.0F, 65.0F)));
        addFixedProxyJoints(w, js, t, tw, p2, p3);
    }

    private static void buildWardenJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody h,
        PhysicsBody ll, PhysicsBody rl, PhysicsBody la, PhysicsBody ra, Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.015F, -0.015F, -0.015F);
        Vector3f u = v(0.015F, 0.015F, 0.015F);
        js.add(joint(w, t, h, tw.apply(v(0.0F, 0.65F, 0.0F)), l, u, v(-35.0F, -35.0F, -25.0F), v(45.0F, 35.0F, 25.0F)));
        js.add(
            joint(
                w,
                t,
                ll,
                tw.apply(v(-0.37F, -0.65F, 0.0F)),
                l,
                u,
                v(-45.0F, -15.0F, -18.0F),
                v(65.0F, 15.0F, 18.0F)));
        js.add(
            joint(w, t, rl, tw.apply(v(0.37F, -0.65F, 0.0F)), l, u, v(-45.0F, -15.0F, -18.0F), v(65.0F, 15.0F, 18.0F)));
        js.add(
            joint(
                w,
                t,
                la,
                tw.apply(v(-0.56F, 0.15F, 0.06F)),
                l,
                u,
                v(-100.0F, -35.0F, -45.0F),
                v(100.0F, 35.0F, 45.0F)));
        js.add(
            joint(
                w,
                t,
                ra,
                tw.apply(v(0.56F, 0.15F, 0.06F)),
                l,
                u,
                v(-100.0F, -35.0F, -45.0F),
                v(100.0F, 35.0F, 45.0F)));
    }

    private static void addFixedProxyJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t,
        Function<Vector3f, Vector3f> tw, PhysicsBody... proxies) {
        Vector3f z = v(0.0F, 0.0F, 0.0F);
        Vector3f tinyLow = v(-1.0F, -1.0F, -1.0F);
        Vector3f tinyHigh = v(1.0F, 1.0F, 1.0F);
        Vector3f anchor = tw.apply(z);

        for (PhysicsBody proxy : proxies) {
            js.add(joint(w, t, proxy, anchor, z, z, tinyLow, tinyHigh));
        }
    }

    private static void buildEquineJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody fl, PhysicsBody fr, PhysicsBody hl, PhysicsBody hr, PhysTransform tHead,
        PhysTransform tFL, PhysTransform tFR, PhysTransform tHL, PhysTransform tHR, Function<Vector3f, Vector3f> tw,
        RagdollBodyFactory.BodyProfile profile, boolean isBaby) {
        float rs = equineRenderScale(profile);
        float bs = isBaby ? 0.5F : 1.0F;
        Vector3f zero = v(0.0F, 0.0F, 0.0F);
        joints.add(
            joint(
                world,
                torso,
                head,
                tw.apply(isBaby ? v(0.0F, 0.15F * rs, -0.3F * rs) : v(0.0F, 0.25F * rs, -0.6875F * rs)),
                zero,
                zero,
                v(-45.0F, -35.0F, -25.0F),
                v(55.0F, 35.0F, 25.0F)));
        Vector3f linL = v(-0.015F, -0.015F, -0.015F);
        Vector3f linU = v(0.015F, 0.015F, 0.015F);
        Vector3f angL = v(-55.0F, -12.0F, -15.0F);
        Vector3f angU = v(55.0F, 12.0F, 15.0F);
        float jointX = (isBaby ? 0.125F : 0.25F) * rs;
        float jointY = (isBaby ? -0.12F : -0.375F) * rs;
        float frontJointZ = (isBaby ? -0.278125F : -0.5625F) * rs;
        float hindJointZ = (isBaby ? 0.28F : 0.5F) * rs;
        joints.add(joint(world, torso, fl, tw.apply(v(-jointX, jointY, frontJointZ)), linL, linU, angL, angU));
        joints.add(joint(world, torso, fr, tw.apply(v(jointX, jointY, frontJointZ)), linL, linU, angL, angU));
        joints.add(joint(world, torso, hl, tw.apply(v(-jointX, jointY, hindJointZ)), linL, linU, angL, angU));
        joints.add(joint(world, torso, hr, tw.apply(v(jointX, jointY, hindJointZ)), linL, linU, angL, angU));
    }

    private static void buildCreeperJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody fl, PhysicsBody fr, PhysicsBody bl, PhysicsBody br, PhysTransform tHead,
        Function<Vector3f, Vector3f> tw, float s) {
        joints.add(
            joint(
                world,
                torso,
                head,
                mid(tw.apply(creeperNeck(s)), onBody(tHead, new Vector3f(0.0F, -0.25F * s, 0.0F))),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-20.0F, -20.0F, -20.0F),
                v(20.0F, 20.0F, 20.0F)));
        float ll = 14.0F;
        Vector3f lin = v(0.0F, 0.0F, 0.0F);
        Vector3f liu = v(0.0F, 0.0F, 0.0F);
        Vector3f al = v(-ll, -5.0F, -ll);
        Vector3f au = v(ll, 5.0F, ll);
        joints.add(joint(world, torso, fl, tw.apply(creeperHip(s, -1.0F, -1.0F)), lin, liu, al, au));
        joints.add(joint(world, torso, fr, tw.apply(creeperHip(s, 1.0F, -1.0F)), lin, liu, al, au));
        joints.add(joint(world, torso, bl, tw.apply(creeperHip(s, -1.0F, 1.0F)), lin, liu, al, au));
        joints.add(joint(world, torso, br, tw.apply(creeperHip(s, 1.0F, 1.0F)), lin, liu, al, au));
    }

    private static void buildQuadJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody fl, PhysicsBody fr, PhysicsBody hl, PhysicsBody hr, PhysTransform tHead,
        PhysTransform tFL, PhysTransform tFR, PhysTransform tHL, PhysTransform tHR, Function<Vector3f, Vector3f> tw,
        float s, RagdollBodyFactory.BodyProfile bodyProfile, boolean isBaby) {
        float ll = 40.0F;
        float hl2 = 35.0F;
        RagdollBodyFactory.QuadLayout q = layoutFor(bodyProfile, isBaby);
        Vector3f torsoFront = tw.apply(new Vector3f(0.0F, q.headY * s, q.headZ * s + 0.08F * s));
        Vector3f headBack = rotQ(tHead.getRotation(new Quat4f()), new Vector3f(0.0F, 0.0F, -q.headCenterZ * s));
        headBack.add(tHead.origin);
        joints.add(
            joint(
                world,
                torso,
                head,
                mid(torsoFront, headBack),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-hl2, -25.0F, -20.0F),
                v(hl2, 25.0F, 20.0F)));
        Vector3f lin = v(-0.02F, -0.02F, -0.02F);
        Vector3f liu = v(0.02F, 0.02F, 0.02F);
        Vector3f angL = v(-ll, -10.0F, -10.0F);
        Vector3f angU = v(ll, 10.0F, 10.0F);
        joints.add(
            joint(
                world,
                torso,
                fl,
                tw.apply(new Vector3f(q.legX * s, q.legY * s, -q.frontZ * s)),
                lin,
                liu,
                angL,
                angU));
        joints.add(
            joint(
                world,
                torso,
                fr,
                tw.apply(new Vector3f(-q.legX * s, q.legY * s, -q.frontZ * s)),
                lin,
                liu,
                angL,
                angU));
        joints.add(
            joint(
                world,
                torso,
                hl,
                tw.apply(new Vector3f(q.legX * s, q.hindLegY * s, q.hindZ * s)),
                lin,
                liu,
                angL,
                angU));
        joints.add(
            joint(
                world,
                torso,
                hr,
                tw.apply(new Vector3f(-q.legX * s, q.hindLegY * s, q.hindZ * s)),
                lin,
                liu,
                angL,
                angU));
    }

    private static void buildChickenJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lw, PhysicsBody rw, PhysicsBody ll, PhysicsBody rl, PhysTransform tHead,
        PhysTransform tLW, PhysTransform tRW, PhysTransform tLL, PhysTransform tRL, Function<Vector3f, Vector3f> tw,
        float s) {
        float wl = 28.0F;
        float legl = 35.0F;
        float hl = 30.0F;
        Vector3f torsoTop = tw.apply(new Vector3f(0.0F, 0.25F * s, -0.15F * s));
        Vector3f headBot = rotQ(tHead.getRotation(new Quat4f()), new Vector3f(0.0F, -0.15F * s, 0.0F));
        headBot.add(tHead.origin);
        joints.add(
            joint(
                world,
                torso,
                head,
                mid(torsoTop, headBot),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-hl, -20.0F, -hl),
                v(hl, 20.0F, hl)));
        Vector3f wi = v(-0.01F, -0.01F, -0.01F);
        Vector3f wu = v(0.01F, 0.01F, 0.01F);
        joints.add(
            joint(
                world,
                torso,
                lw,
                tw.apply(new Vector3f(0.22F * s, 0.1F * s, 0.0F)),
                wi,
                wu,
                v(-wl, -8.0F, -wl),
                v(wl, 8.0F, wl)));
        joints.add(
            joint(
                world,
                torso,
                rw,
                tw.apply(new Vector3f(-0.22F * s, 0.1F * s, 0.0F)),
                wi,
                wu,
                v(-wl, -8.0F, -wl),
                v(wl, 8.0F, wl)));
        Vector3f li = v(-0.02F, -0.02F, -0.02F);
        Vector3f lu = v(0.02F, 0.02F, 0.02F);
        joints.add(
            joint(
                world,
                torso,
                ll,
                tw.apply(new Vector3f(0.1F * s, -0.2F * s, 0.0F)),
                li,
                lu,
                v(-legl, -8.0F, -8.0F),
                v(legl, 8.0F, 8.0F)));
        joints.add(
            joint(
                world,
                torso,
                rl,
                tw.apply(new Vector3f(-0.1F * s, -0.2F * s, 0.0F)),
                li,
                lu,
                v(-legl, -8.0F, -8.0F),
                v(legl, 8.0F, 8.0F)));
    }

    private static void buildBatJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lWing, PhysicsBody rWing, PhysicsBody lStub, PhysicsBody rStub,
        PhysTransform tHead, PhysTransform tLWing, PhysTransform tRWing, PhysTransform tLStub, PhysTransform tRStub,
        Function<Vector3f, Vector3f> tw, float s) {
        Vector3f torsoO = tw.apply(v(0.0F, 0.0F, 0.0F));
        joints.add(
            joint(
                world,
                torso,
                head,
                mid(torsoO, tHead.origin),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-40.0F, -40.0F, -40.0F),
                v(40.0F, 40.0F, 40.0F)));
        joints.add(
            joint(
                world,
                torso,
                lWing,
                tw.apply(v(-0.09375F * s, 0.15625F * s, 0.0F)),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-70.0F, -60.0F, -70.0F),
                v(70.0F, 60.0F, 70.0F)));
        joints.add(
            joint(
                world,
                torso,
                rWing,
                tw.apply(v(0.09375F * s, 0.15625F * s, 0.0F)),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-70.0F, -60.0F, -70.0F),
                v(70.0F, 60.0F, 70.0F)));
        joints.add(
            joint(
                world,
                torso,
                lStub,
                tLStub.origin,
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-1.0F, -1.0F, -1.0F),
                v(1.0F, 1.0F, 1.0F)));
        joints.add(
            joint(
                world,
                torso,
                rStub,
                tRStub.origin,
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-1.0F, -1.0F, -1.0F),
                v(1.0F, 1.0F, 1.0F)));
    }

    private static void buildBeeJoints(PhysicsWorld world, List<PhysicsConstraint> joints, PhysicsBody torso,
        PhysicsBody head, PhysicsBody lWing, PhysicsBody rWing, PhysicsBody lStub, PhysicsBody rStub,
        PhysTransform tHead, PhysTransform tLWing, PhysTransform tRWing, PhysTransform tLStub, PhysTransform tRStub,
        Function<Vector3f, Vector3f> tw, float s) {
        Vector3f torsoO = tw.apply(v(0.0F, 0.0F, 0.0F));
        joints.add(
            joint(
                world,
                torso,
                head,
                mid(torsoO, tHead.origin),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-30.0F, -30.0F, -30.0F),
                v(30.0F, 30.0F, 30.0F)));
        joints.add(
            joint(
                world,
                torso,
                lWing,
                mid(torsoO, tLWing.origin),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-60.0F, -50.0F, -60.0F),
                v(60.0F, 50.0F, 60.0F)));
        joints.add(
            joint(
                world,
                torso,
                rWing,
                mid(torsoO, tRWing.origin),
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-60.0F, -50.0F, -60.0F),
                v(60.0F, 50.0F, 60.0F)));
        joints.add(
            joint(
                world,
                torso,
                lStub,
                tLStub.origin,
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-1.0F, -1.0F, -1.0F),
                v(1.0F, 1.0F, 1.0F)));
        joints.add(
            joint(
                world,
                torso,
                rStub,
                tRStub.origin,
                v(0.0F, 0.0F, 0.0F),
                v(0.0F, 0.0F, 0.0F),
                v(-1.0F, -1.0F, -1.0F),
                v(1.0F, 1.0F, 1.0F)));
    }

    public static PhysicsBody makePart(PhysicsWorld world, PhysicsShape shape, Vector3f position, Quat4f rotation,
        float mass, Vector3f initialVel) {
        BodyProperties properties = PART_PROPERTIES.reset();
        properties.mass = mass * (float) PhysicsConfig.massScale;
        properties.linearDamping = (float) PhysicsConfig.linearDamping;
        properties.angularDamping = (float) PhysicsConfig.angularDamping;
        properties.restitution = (float) PhysicsConfig.restitution;
        properties.friction = (float) PhysicsConfig.friction;
        properties.sleepingLinearThreshold = 0.3F;
        properties.sleepingAngularThreshold = 0.3F;
        properties.allowSleeping = false;
        properties.linearVelocity.set(initialVel);
        if (shape.isBox()) {
            shape.getHalfExtents(scratchHalfExtents);
            float smallestHalfExtent = Math
                .min(scratchHalfExtents.x, Math.min(scratchHalfExtents.y, scratchHalfExtents.z));
            properties.ccdSweptSphereRadius = Math.max(0.005F, smallestHalfExtent * 0.8F);
            properties.ccdMotionThreshold = 0.15F;
        }

        PhysicsBody body = world.createDynamicBody(shape, position, rotation, properties);
        if (body == null) {
            throw new IllegalStateException("physics backend refused a ragdoll body (body budget exhausted?)");
        } else {
            world.addBody(body);
            return body;
        }
    }

    private static void buildGuardianJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody t0,
        PhysicsBody t1, PhysicsBody t2, PhysicsBody p4, PhysicsBody p5, Function<Vector3f, Vector3f> tw, float s) {
        Vector3f l = v(-0.01F * s, -0.01F * s, -0.01F * s);
        Vector3f u = v(0.01F * s, 0.01F * s, 0.01F * s);
        Vector3f al = v(-30.0F, -25.0F, -25.0F);
        Vector3f au = v(30.0F, 25.0F, 25.0F);
        js.add(joint(w, t, t0, tw.apply(v(0.0F, 0.0F, 0.5F * s)), l, u, al, au));
        js.add(joint(w, t0, t1, tw.apply(v(0.0F, 0.0F, 0.875F * s)), l, u, al, au));
        js.add(joint(w, t1, t2, tw.apply(v(0.0F, 0.0F, 1.3125F * s)), l, u, al, au));
        addFixedProxyJoints(w, js, t, tw, p4, p5);
    }

    private static void buildSquidJoints(PhysicsWorld w, List<PhysicsConstraint> js, List<PhysicsBody> parts,
        Function<Vector3f, Vector3f> tw) {
        PhysicsBody body = parts.get(0);
        Vector3f l = v(-0.008F, -0.008F, -0.008F);
        Vector3f u = v(0.008F, 0.008F, 0.008F);

        for (int k = 0; k < 8; k++) {
            double ang = (double) k * Math.PI * 2.0 / 8.0;
            Vector3f anchor = tw
                .apply(v(-((float) Math.cos(ang)) * 0.3125F, -0.4375F, (float) Math.sin(ang) * 0.3125F));
            js.add(
                jointAtCurrentPose(
                    w,
                    body,
                    parts.get(k + 1),
                    anchor,
                    l,
                    u,
                    v(-55.0F, -25.0F, -55.0F),
                    v(55.0F, 25.0F, 55.0F)));
        }
    }

    private static void buildFinnedJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody head,
        PhysicsBody tail, PhysicsBody p3, PhysicsBody p4, PhysicsBody p5, Function<Vector3f, Vector3f> tw, float headZ,
        float tailZ) {
        Vector3f l = v(-0.006F, -0.006F, -0.006F);
        Vector3f u = v(0.006F, 0.006F, 0.006F);
        js.add(
            joint(w, t, head, tw.apply(v(0.0F, 0.0F, headZ)), l, u, v(-25.0F, -25.0F, -20.0F), v(25.0F, 25.0F, 20.0F)));
        js.add(
            joint(w, t, tail, tw.apply(v(0.0F, 0.0F, tailZ)), l, u, v(-30.0F, -30.0F, -25.0F), v(30.0F, 30.0F, 25.0F)));
        addFixedProxyJoints(w, js, t, tw, p3, p4, p5);
    }

    private static void buildFishJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody tail,
        PhysicsBody p2, PhysicsBody p3, PhysicsBody p4, PhysicsBody p5, Function<Vector3f, Vector3f> tw,
        RagdollBodyFactory.BodyProfile profile) {
        float[] d = fishDimensions(profile);
        Vector3f l = v(-0.004F, -0.004F, -0.004F);
        Vector3f u = v(0.004F, 0.004F, 0.004F);
        if (d[6] == 0.0F) {
            addFixedProxyJoints(w, js, t, tw, tail);
        } else {
            js.add(
                joint(
                    w,
                    t,
                    tail,
                    tw.apply(v(0.0F, 0.0F, d[2])),
                    l,
                    u,
                    v(-30.0F, -25.0F, -20.0F),
                    v(30.0F, 25.0F, 20.0F)));
        }

        addFixedProxyJoints(w, js, t, tw, p2, p3, p4, p5);
    }

    private static void buildWitherJoints(PhysicsWorld w, List<PhysicsConstraint> js, PhysicsBody t, PhysicsBody h,
        PhysicsBody tail, PhysicsBody proxy, PhysicsBody lh, PhysicsBody rh, Function<Vector3f, Vector3f> tw) {
        Vector3f l = v(-0.02F, -0.02F, -0.02F);
        Vector3f u = v(0.02F, 0.02F, 0.02F);
        js.add(
            joint(w, t, h, tw.apply(v(0.0F, 0.73F, -0.125F)), l, u, v(-35.0F, -40.0F, -25.0F), v(45.0F, 40.0F, 25.0F)));
        js.add(
            joint(
                w,
                t,
                lh,
                tw.apply(v(-0.78F, 0.85F, -0.25F)),
                l,
                u,
                v(-30.0F, -40.0F, -30.0F),
                v(30.0F, 40.0F, 30.0F)));
        js.add(
            joint(
                w,
                t,
                rh,
                tw.apply(v(0.78F, 0.85F, -0.25F)),
                l,
                u,
                v(-30.0F, -40.0F, -30.0F),
                v(30.0F, 40.0F, 30.0F)));
        js.add(
            joint(
                w,
                t,
                tail,
                tw.apply(v(0.0625F, -0.69F, 0.2535F)),
                l,
                u,
                v(-40.0F, -20.0F, -20.0F),
                v(40.0F, 20.0F, 20.0F)));
        addFixedProxyJoints(w, js, t, tw, proxy);
    }

    private static void buildEnderDragonJoints(PhysicsWorld w, List<PhysicsConstraint> js, List<PhysicsBody> parts,
        Function<Vector3f, Vector3f> tw) {
        PhysicsBody body = parts.get(0);
        PhysicsBody head = parts.get(1);
        PhysicsBody neck = parts.get(8);
        PhysicsBody tail = parts.get(9);
        Vector3f l = v(-0.05F, -0.05F, -0.05F);
        Vector3f u = v(0.05F, 0.05F, 0.05F);
        js.add(
            joint(
                w,
                body,
                neck,
                tw.apply(v(0.0F, -0.15F, -2.28F)),
                l,
                u,
                v(-30.0F, -35.0F, -20.0F),
                v(30.0F, 35.0F, 20.0F)));
        js.add(
            joint(
                w,
                neck,
                head,
                tw.apply(v(0.0F, -0.3125F, -4.72F)),
                l,
                u,
                v(-35.0F, -40.0F, -25.0F),
                v(35.0F, 40.0F, 25.0F)));
        js.add(
            joint(
                w,
                body,
                tail,
                tw.apply(v(0.0F, 0.31F, 1.97F)),
                l,
                u,
                v(-30.0F, -35.0F, -20.0F),
                v(30.0F, 35.0F, 20.0F)));
        js.add(
            joint(
                w,
                body,
                parts.get(2),
                tw.apply(v(-0.75F, -0.4375F, -1.375F)),
                l,
                u,
                v(-45.0F, -20.0F, -30.0F),
                v(45.0F, 20.0F, 30.0F)));
        js.add(
            joint(
                w,
                body,
                parts.get(3),
                tw.apply(v(0.75F, -0.4375F, -1.375F)),
                l,
                u,
                v(-45.0F, -20.0F, -30.0F),
                v(45.0F, 20.0F, 30.0F)));
        js.add(
            joint(
                w,
                body,
                parts.get(6),
                tw.apply(v(-1.0F, -0.1875F, 1.125F)),
                l,
                u,
                v(-45.0F, -20.0F, -30.0F),
                v(45.0F, 20.0F, 30.0F)));
        js.add(
            joint(
                w,
                body,
                parts.get(7),
                tw.apply(v(1.0F, -0.1875F, 1.125F)),
                l,
                u,
                v(-45.0F, -20.0F, -30.0F),
                v(45.0F, 20.0F, 30.0F)));
        js.add(
            joint(
                w,
                body,
                parts.get(4),
                tw.apply(v(-0.75F, 0.5F, 0.3125F)),
                l,
                u,
                v(-25.0F, -20.0F, -55.0F),
                v(25.0F, 20.0F, 55.0F)));
        js.add(
            joint(
                w,
                body,
                parts.get(5),
                tw.apply(v(0.75F, 0.5F, 0.3125F)),
                l,
                u,
                v(-25.0F, -20.0F, -55.0F),
                v(25.0F, 20.0F, 55.0F)));
    }

    private static PhysicsShape box(PhysicsWorld world, Vector3f halfExtents) {
        return world.createBoxShape(halfExtents.x, halfExtents.y, halfExtents.z);
    }

    public static PhysicsBody makeStaticBody(PhysicsWorld world, PhysicsShape shape, PhysTransform t) {
        PhysicsBody body = world.createStaticBody(shape, t);
        world.addBody(body);
        return body;
    }

    public static PhysicsConstraint joint(PhysicsWorld world, PhysicsBody a, PhysicsBody b, Vector3f anchor,
        Vector3f linL, Vector3f linU, Vector3f angLDeg, Vector3f angUDeg) {
        return joint(world, a, b, anchor, linL, linU, angLDeg, angUDeg, false, false);
    }

    public static PhysicsConstraint joint(PhysicsWorld world, PhysicsBody a, PhysicsBody b, Vector3f anchor,
        Vector3f linL, Vector3f linU, Vector3f angLDeg, Vector3f angUDeg, boolean keepCollision) {
        return joint(world, a, b, anchor, linL, linU, angLDeg, angUDeg, keepCollision, false);
    }

    public static PhysicsConstraint joint(PhysicsWorld world, PhysicsBody a, PhysicsBody b, Vector3f anchor,
        Vector3f linL, Vector3f linU, Vector3f angLDeg, Vector3f angUDeg, boolean keepCollision,
        boolean bakeCurrentPose) {
        PhysTransform ta = wt(a);
        PhysTransform tb = wt(b);
        PhysTransform localA = new PhysTransform();
        localA.setIdentity();
        localA.origin.set(toLocal(ta, anchor));
        PhysTransform localB = new PhysTransform();
        localB.setIdentity();
        localB.origin.set(toLocal(tb, anchor));
        if (bakeCurrentPose) {
            localB.basis.transpose(tb.basis);
            localB.basis.mul(ta.basis);
        }

        Vector3f[] angular = angularLimits(angLDeg, angUDeg);
        PhysicsConstraint c = world.createSixDofConstraint(a, b, localA, localB, linL, linU, angular[0], angular[1]);
        c.setKeepsCollision(keepCollision);
        a.activate();
        b.activate();
        world.addConstraint(c);
        return c;
    }

    private static PhysicsConstraint jointAtCurrentPose(PhysicsWorld world, PhysicsBody a, PhysicsBody b,
        Vector3f anchor, Vector3f linL, Vector3f linU, Vector3f angLDeg, Vector3f angUDeg) {
        return joint(world, a, b, anchor, linL, linU, angLDeg, angUDeg, false, true);
    }

    private static Vector3f[] angularLimits(Vector3f lowerDeg, Vector3f upperDeg) {
        if (freeAngularAxes) {
            return new Vector3f[] { v(1.0F, 1.0F, 1.0F), v(-1.0F, -1.0F, -1.0F) };
        } else {
            float scale = (float) PhysicsConfig.rangeScale;
            return scale == 1.0F ? new Vector3f[] { rad(lowerDeg), rad(upperDeg) }
                : new Vector3f[] {
                    rad(v(lowerDeg.x, clampLimitDeg(lowerDeg.y * scale), clampLimitDeg(lowerDeg.z * scale))),
                    rad(v(upperDeg.x, clampLimitDeg(upperDeg.y * scale), clampLimitDeg(upperDeg.z * scale))) };
        }
    }

    private static float clampLimitDeg(float deg) {
        return Math.max(-175.0F, Math.min(175.0F, deg));
    }

    public static Quat4f mul(Quat4f q1, Quat4f q2) {
        float w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z;
        float x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y;
        float y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x;
        float z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w;
        return new Quat4f(x, y, z, w);
    }

    private static Quat4f poseRot(MobPose pose, PhysicsPart part, Quat4f baseQuat) {
        return pose == null ? baseQuat : pose.rotationFor(part, baseQuat);
    }

    private static Vector3f posePos(MobPose pose, PhysicsPart part, Vector3f authored) {
        if (pose == null) {
            return authored;
        } else {
            Vector3f captured = pose.centerFor(part);
            return captured != null ? captured : authored;
        }
    }

    private static Vector3f poseBox(MobPose pose, PhysicsPart part, Vector3f authored) {
        if (pose == null) {
            return authored;
        } else {
            Vector3f captured = pose.extentsFor(part);
            return captured != null ? new Vector3f(captured) : authored;
        }
    }

    private static Vector3f poseBox(MobPose pose, float meshScale, PhysicsPart part, Vector3f authored) {
        Vector3f captured = pose == null ? null : pose.extentsFor(part);
        if (captured != null) {
            return new Vector3f(captured);
        } else {
            Vector3f scaled = new Vector3f(authored);
            scaled.scale(meshScale);
            return scaled;
        }
    }

    static Quat4f modelPartRotation(Quat4f base, float xRot, float yRot, float zRot) {
        Quat4f flip = new Quat4f(0, 0, 1, 0);
        Quat4f raw = mulRaw(mulRaw(mulRaw(base, flip), rotationZYX(zRot, yRot, xRot)), new Quat4f(0, 0, -1, 0));
        return new Quat4f(raw.x, raw.y, raw.z, raw.w);
    }

    /** Same Z-Y-X Hamilton product and float cosine recovery used by the original JOML call. */
    static Quat4f rotationZYX(float z, float y, float x) {
        float sx = (float) Math.sin(x * .5F), cx = cosFromSin(sx, x * .5F), sy = (float) Math.sin(y * .5F),
            cy = cosFromSin(sy, y * .5F), sz = (float) Math.sin(z * .5F), cz = cosFromSin(sz, z * .5F);
        float cycz = cy * cz, sysz = sy * sz, sycz = sy * cz, cysz = cy * sz;
        Quat4f result = new Quat4f();
        result.set(sx * cycz - cx * sysz, cx * sycz + sx * cysz, cx * cysz - sx * sycz, cx * cycz + sx * sysz);
        return result;
    }

    private static float cosFromSin(float sine, float angle) {
        float cosine = (float) Math.sqrt(1 - sine * sine), a = angle + 1.5707964F,
            b = a - (int) (a / 6.2831855F) * 6.2831855F;
        if (b < 0) b = 6.2831855F + b;
        return b >= 3.1415927F ? -cosine : cosine;
    }

    private static Quat4f mulRaw(Quat4f a, Quat4f b) {
        Quat4f r = new Quat4f();
        r.set(
            a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
            a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z);
        return r;
    }

    public static Vector3f rotQ(Quat4f q, Vector3f v) {
        Vector3f qv = new Vector3f(q.x, q.y, q.z);
        Vector3f t = new Vector3f(
            2.0F * (qv.y * v.z - qv.z * v.y),
            2.0F * (qv.z * v.x - qv.x * v.z),
            2.0F * (qv.x * v.y - qv.y * v.x));
        Vector3f r = new Vector3f(v);
        Vector3f qt = new Vector3f(t);
        qt.scale(q.w);
        r.add(qt);
        r.add(new Vector3f(qv.y * t.z - qv.z * t.y, qv.z * t.x - qv.x * t.z, qv.x * t.y - qv.y * t.x));
        return r;
    }

    public static Vector3f calcPos(Vector3f parentPos, Quat4f parentRot, Vector3f localOff, Quat4f partRot,
        Vector3f partCenter) {
        Vector3f joint = new Vector3f(parentPos);
        joint.add(rotQ(parentRot, localOff));
        Vector3f r = new Vector3f(joint);
        r.add(rotQ(partRot, partCenter));
        return r;
    }

    private static Vector3f offset(Vector3f parentPos, Quat4f parentRot, Vector3f localOffset) {
        Vector3f result = new Vector3f(parentPos);
        result.add(rotQ(parentRot, localOffset));
        return result;
    }

    private static PhysTransform wt(PhysicsBody b) {
        PhysTransform t = new PhysTransform();
        b.getWorldTransform(t);
        return t;
    }

    private static Vector3f toLocal(PhysTransform wt, Vector3f worldPt) {
        Vector3f d = new Vector3f(worldPt);
        d.sub(wt.origin);
        return rotQ(
            new Quat4f(
                -wt.getRotation(new Quat4f()).x,
                -wt.getRotation(new Quat4f()).y,
                -wt.getRotation(new Quat4f()).z,
                wt.getRotation(new Quat4f()).w),
            d);
    }

    private static Vector3f mid(Vector3f a, Vector3f b) {
        return new Vector3f((a.x + b.x) * 0.5F, (a.y + b.y) * 0.5F, (a.z + b.z) * 0.5F);
    }

    private static Vector3f rad(Vector3f deg) {
        return new Vector3f(
            (float) Math.toRadians((double) deg.x),
            (float) Math.toRadians((double) deg.y),
            (float) Math.toRadians((double) deg.z));
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    public static enum BodyProfile {
        DEFAULT,
        COW,
        PIG,
        SHEEP,
        CHICKEN,
        CAT,
        HORSE,
        DONKEY,
        MULE,
        WOLF,
        FOX,
        PANDA,
        GOAT,
        POLAR_BEAR,
        TURTLE,
        CAMEL,
        LLAMA,
        RABBIT,
        FROG,
        HOGLIN,
        SNIFFER,
        RAVAGER,
        PHANTOM,
        PARROT,
        SLIME,
        MAGMA_CUBE,
        SILVERFISH,
        ENDERMITE,
        ALLAY,
        STRIDER,
        SNOW_GOLEM,
        BLAZE,
        SPIDER,
        CAVE_SPIDER,
        SHULKER,
        GHAST,
        VEX,
        WARDEN,
        GUARDIAN,
        ELDER_GUARDIAN,
        SQUID,
        DOLPHIN,
        AXOLOTL,
        COD,
        SALMON,
        TROPICAL_FISH,
        TROPICAL_FISH_LARGE,
        PUFFERFISH,
        PUFFERFISH_MID,
        PUFFERFISH_BIG,
        TADPOLE,
        WITHER,
        ENDER_DRAGON,
        ARMADILLO,
        BREEZE;
    }

    private static final class QuadLayout {

        final float headY;
        final float headZ;
        final float headCenterZ;
        final float headX;
        final float headHalfY;
        final float headHalfZ;
        final float legX;
        final float legY;
        final float frontZ;
        final float hindZ;
        final float legCenterY;
        final float legHalfX;
        final float legHalfY;
        final float legHalfZ;
        final float torsoHalfX;
        final float torsoHalfY;
        final float torsoHalfZ;
        final float torsoMass;
        final float headMass;
        final float legMass;
        final float hindLegY;
        final float hindLegCenterY;
        final float hindLegHalfY;

        QuadLayout(float headY, float headZ, float headCenterZ, float headX, float headHalfY, float headHalfZ,
            float legX, float legY, float frontZ, float hindZ, float legCenterY, float legHalfX, float legHalfY,
            float legHalfZ, float torsoHalfX, float torsoHalfY, float torsoHalfZ, float torsoMass, float headMass,
            float legMass, float hindLegY, float hindLegCenterY, float hindLegHalfY) {
            this.headY = headY;
            this.headZ = headZ;
            this.headCenterZ = headCenterZ;
            this.headX = headX;
            this.headHalfY = headHalfY;
            this.headHalfZ = headHalfZ;
            this.legX = legX;
            this.legY = legY;
            this.frontZ = frontZ;
            this.hindZ = hindZ;
            this.legCenterY = legCenterY;
            this.legHalfX = legHalfX;
            this.legHalfY = legHalfY;
            this.legHalfZ = legHalfZ;
            this.torsoHalfX = torsoHalfX;
            this.torsoHalfY = torsoHalfY;
            this.torsoHalfZ = torsoHalfZ;
            this.torsoMass = torsoMass;
            this.headMass = headMass;
            this.legMass = legMass;
            this.hindLegY = hindLegY;
            this.hindLegCenterY = hindLegCenterY;
            this.hindLegHalfY = hindLegHalfY;
        }

        float headY() {
            return headY;
        }

        float headZ() {
            return headZ;
        }

        float headCenterZ() {
            return headCenterZ;
        }

        float headX() {
            return headX;
        }

        float headHalfY() {
            return headHalfY;
        }

        float headHalfZ() {
            return headHalfZ;
        }

        float legX() {
            return legX;
        }

        float legY() {
            return legY;
        }

        float frontZ() {
            return frontZ;
        }

        float hindZ() {
            return hindZ;
        }

        float legCenterY() {
            return legCenterY;
        }

        float legHalfX() {
            return legHalfX;
        }

        float legHalfY() {
            return legHalfY;
        }

        float legHalfZ() {
            return legHalfZ;
        }

        float torsoHalfX() {
            return torsoHalfX;
        }

        float torsoHalfY() {
            return torsoHalfY;
        }

        float torsoHalfZ() {
            return torsoHalfZ;
        }

        float torsoMass() {
            return torsoMass;
        }

        float headMass() {
            return headMass;
        }

        float legMass() {
            return legMass;
        }

        float hindLegY() {
            return hindLegY;
        }

        float hindLegCenterY() {
            return hindLegCenterY;
        }

        float hindLegHalfY() {
            return hindLegHalfY;
        }
    }
}
