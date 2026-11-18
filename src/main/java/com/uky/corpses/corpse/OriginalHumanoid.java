package com.uky.corpses.corpse;

import javax.vecmath.Vector3f;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.Transform;

/** ModelBiped's local X/Y axes are reversed relative to the upright physics frame. */
final class OriginalHumanoid {

    // Joint ranges match the installed NeoForge profile (humanoidJointLimits.rangeScale=1.5).
    private static final float[] NECK = { -40, -35, -35, 40, 55, 35 };
    private static final float[] HIP = { -55, -35, -45, 75, 35, 45 };
    private static final float[] SHOULDER = { -120, -50, -95, 120, 50, 95 };
    private static final float PIXEL = .9375F / 16;

    static Generic6DofConstraint joint(RigidBody torso, RigidBody limb, Transform torsoAt, Transform limbAt, int part) {
        float[] p = Skeleton.pivot(part), lo = Skeleton.min(part), hi = Skeleton.max(part);
        Vector3f onTorso = new Vector3f(p[0] * PIXEL, (p[1] - 6) * PIXEL, p[2] * PIXEL);
        torsoAt.transform(onTorso);
        Vector3f onLimb = new Vector3f(
            -(lo[0] + hi[0]) * PIXEL / 2,
            -(lo[1] + hi[1]) * PIXEL / 2,
            -(lo[2] + hi[2]) * PIXEL / 2);
        limbAt.transform(onLimb);
        Vector3f anchor = new Vector3f(onTorso);
        anchor.add(onLimb);
        anchor.scale(.5F);
        Transform a = localFrame(torsoAt, anchor), b = localFrame(limbAt, anchor);
        Generic6DofConstraint joint = new Generic6DofConstraint(torso, limb, a, b, true);
        boolean leg = part == Skeleton.RLEG || part == Skeleton.LLEG;
        boolean arm = part == Skeleton.RARM || part == Skeleton.LARM;
        float slack = leg ? .05F : arm ? .02F : 0;
        joint.setLinearLowerLimit(new Vector3f(-slack, arm ? -slack : 0, -slack));
        joint.setLinearUpperLimit(new Vector3f(slack, arm ? slack : 0, slack));
        float[] limits = part == Skeleton.HEAD ? PhysicsConfig.neck : leg ? PhysicsConfig.hip : PhysicsConfig.shoulder;
        if (part == Skeleton.RLEG || part == Skeleton.RARM) limits = mirrored(limits);
        joint.setAngularLowerLimit(
            radians(-limits[3], -limits[4] * PhysicsConfig.rangeScale, limits[2] * PhysicsConfig.rangeScale));
        joint.setAngularUpperLimit(
            radians(-limits[0], -limits[1] * PhysicsConfig.rangeScale, limits[5] * PhysicsConfig.rangeScale));
        return joint;
    }

    private static Transform localFrame(Transform body, Vector3f anchor) {
        Transform frame = new Transform();
        frame.setIdentity();
        Transform inverse = new Transform(body);
        inverse.inverse();
        frame.origin.set(anchor);
        inverse.transform(frame.origin);
        return frame;
    }

    private static float[] mirrored(float[] limits) {
        return new float[] { limits[0], -limits[4], -limits[5], limits[3], -limits[1], -limits[2] };
    }

    private static Vector3f radians(float x, float y, float z) {
        return new Vector3f((float) Math.toRadians(x), (float) Math.toRadians(y), (float) Math.toRadians(z));
    }

    static void applyAerodynamicDrag(RigidBody[] parts) {
        float[] scale = new float[parts.length], mass = new float[parts.length];
        float areaTotal = 0, massTotal = 0;
        for (int i = 0; i < parts.length; i++) {
            float inverseMass = parts[i].getInvMass();
            if (inverseMass <= 0) continue;
            Vector3f half = ((BoxShape) parts[i].getCollisionShape()).getHalfExtentsWithMargin(new Vector3f());
            float a = 2 * half.x, b = 2 * half.y, c = 2 * half.z;
            float area = (a * b + b * c + c * a) * .5F;
            if (area <= 0) continue;
            mass[i] = 1 / inverseMass;
            scale[i] = area / mass[i];
            areaTotal += area;
            massTotal += mass[i];
        }
        if (areaTotal <= 0 || massTotal <= 0) return;
        float mean = areaTotal / massTotal, weighted = 0;
        for (int i = 0; i < parts.length; i++) if (scale[i] > 0) {
            scale[i] = 1 + (Math.max(.2F, Math.min(12, scale[i] / mean)) - 1) * PhysicsConfig.differentialDrag;
            weighted += mass[i] * scale[i];
        }
        if (weighted <= 0) return;
        float norm = massTotal / weighted;
        for (int i = 0; i < parts.length; i++) if (scale[i] > 0)
            parts[i].setDamping(PhysicsConfig.linearDamping * scale[i] * norm, PhysicsConfig.angularDamping);
    }

    static void clampSpeeds(RigidBody[] parts) {
        Vector3f velocity = new Vector3f();
        for (RigidBody body : parts) {
            body.getLinearVelocity(velocity);
            if (velocity.length() > PhysicsConfig.maxLinearSpeed) {
                velocity.scale(PhysicsConfig.maxLinearSpeed / velocity.length());
                body.setLinearVelocity(velocity);
            }
            body.getAngularVelocity(velocity);
            if (velocity.length() > PhysicsConfig.maxAngularSpeed) {
                velocity.scale(PhysicsConfig.maxAngularSpeed / velocity.length());
                body.setAngularVelocity(velocity);
            }
        }
    }

    static boolean stationary(RigidBody[] parts) {
        Vector3f velocity = new Vector3f();
        for (RigidBody body : parts) {
            body.getLinearVelocity(velocity);
            if (velocity.lengthSquared() > .09F) return false;
            body.getAngularVelocity(velocity);
            if (velocity.lengthSquared() > .09F) return false;
        }
        return true;
    }

    static void applyHit(RigidBody[] parts, int part, Vector3f impulse, Vector3f impactPoint, float supportY) {
        transferToWholeBody(parts, impulse, part, impactPoint, true, supportY);
        float whip = limbWhipFor(parts, part);
        if (whip > 0) {
            Vector3f local = new Vector3f(impulse);
            local.scale(whip);
            applyOffCentreImpulse(parts[part], local, impactPoint);
            parts[part].activate();
        }
    }

    private static void transferToWholeBody(RigidBody[] parts, Vector3f impulse, int partIndex, Vector3f impactPoint,
        boolean fromStanding, float supportY) {
        int count = parts.length;
        float[][] centres = new float[count][];
        float[] masses = new float[count];
        float totalMass = 0.0F;
        float cx = 0.0F;
        float cy = 0.0F;
        float cz = 0.0F;
        Vector3f centre = new Vector3f();
        Vector3f halfExtents = new Vector3f();

        for (int i = 0; i < count; i++) {
            RigidBody part = parts[i];
            float invMass = part.getInvMass();
            if (!(invMass <= 0.0F)) {
                float mass = 1.0F / invMass;
                part.getCenterOfMassPosition(centre);
                centres[i] = new float[] { centre.x, centre.y, centre.z };
                masses[i] = mass;
                totalMass += mass;
                cx += centre.x * mass;
                cy += centre.y * mass;
                cz += centre.z * mass;
            }
        }

        if (!(totalMass <= 1.0E-4F)) {
            cx /= totalMass;
            cy /= totalMass;
            cz /= totalMass;
            float[] inertia = new float[6];

            for (int ix = 0; ix < count; ix++) {
                if (centres[ix] != null) {
                    float mass = masses[ix];
                    float dx = centres[ix][0] - cx;
                    float dy = centres[ix][1] - cy;
                    float dz = centres[ix][2] - cz;
                    inertia[0] += mass * (dy * dy + dz * dz);
                    inertia[1] += mass * (dx * dx + dz * dz);
                    inertia[2] += mass * (dx * dx + dy * dy);
                    inertia[3] -= mass * dx * dy;
                    inertia[4] -= mass * dx * dz;
                    inertia[5] -= mass * dy * dz;
                    BoxShape shape = (BoxShape) parts[ix].getCollisionShape();
                    if (shape != null) {
                        shape.getHalfExtentsWithMargin(halfExtents);
                        float own = mass
                            * (halfExtents.x * halfExtents.x + halfExtents.y * halfExtents.y
                                + halfExtents.z * halfExtents.z)
                            / 3.0F;
                        inertia[0] += own;
                        inertia[1] += own;
                        inertia[2] += own;
                    }
                }
            }

            float px;
            float py;
            float pz;
            if (impactPoint != null) {
                px = impactPoint.x;
                py = impactPoint.y;
                pz = impactPoint.z;
            } else if (centres[partIndex] != null) {
                px = centres[partIndex][0];
                py = centres[partIndex][1];
                pz = centres[partIndex][2];
            } else {
                px = cx;
                py = cy;
                pz = cz;
            }

            float spin = 1.35F;
            float[] omega = solveAboutPivot(inertia, totalMass, cx, cy, cz, cx, cy, cz, px, py, pz, impulse, spin);
            float vx = impulse.x / totalMass;
            float vy = impulse.y / totalMass;
            float vz = impulse.z / totalMass;
            float pivotY = cy;
            float lever = 0.0F;
            if (fromStanding) {
                lever = .7F;
                if (lever > 0.0F) {

                    if (!Float.isNaN(supportY) && !(cy - supportY <= 0.001F)) {
                        float[] pinned = solveAboutPivot(
                            inertia,
                            totalMass,
                            cx,
                            supportY,
                            cz,
                            cx,
                            cy,
                            cz,
                            px,
                            py,
                            pz,
                            impulse,
                            spin);
                        pivotY = supportY;
                        vx += (0.0F - vx) * lever;
                        vy += (0.0F - vy) * lever;
                        vz += (0.0F - vz) * lever;
                        omega[0] += (pinned[0] - omega[0]) * lever;
                        omega[1] += (pinned[1] - omega[1]) * lever;
                        omega[2] += (pinned[2] - omega[2]) * lever;
                    } else {
                        lever = 0.0F;
                    }
                }
            }

            float maxAngular = PhysicsConfig.maxAngularSpeed;
            float angularSpeed = (float) Math
                .sqrt((double) (omega[0] * omega[0] + omega[1] * omega[1] + omega[2] * omega[2]));
            if (angularSpeed > maxAngular && angularSpeed > 1.0E-6F) {
                float trim = maxAngular / angularSpeed;
                omega[0] *= trim;
                omega[1] *= trim;
                omega[2] *= trim;
            }

            float turnY = cy + (pivotY - cy) * lever;
            Vector3f velocity = new Vector3f();

            for (int ixx = 0; ixx < count; ixx++) {
                if (centres[ixx] != null) {
                    RigidBody part = parts[ixx];
                    float dx = centres[ixx][0] - cx;
                    float dy = centres[ixx][1] - turnY;
                    float dz = centres[ixx][2] - cz;
                    part.getLinearVelocity(velocity);
                    velocity.x = velocity.x + vx + (omega[1] * dz - omega[2] * dy);
                    velocity.y = velocity.y + vy + (omega[2] * dx - omega[0] * dz);
                    velocity.z = velocity.z + vz + (omega[0] * dy - omega[1] * dx);
                    part.setLinearVelocity(velocity);
                    part.getAngularVelocity(velocity);
                    velocity.x = velocity.x + omega[0];
                    velocity.y = velocity.y + omega[1];
                    velocity.z = velocity.z + omega[2];
                    part.setAngularVelocity(velocity);
                    part.activate();
                }
            }
        }
    }

    private static float[] solveAboutPivot(float[] inertia, float totalMass, float pivotX, float pivotY, float pivotZ,
        float comX, float comY, float comZ, float pointX, float pointY, float pointZ, Vector3f impulse, float spin) {
        float ax = comX - pivotX;
        float ay = comY - pivotY;
        float az = comZ - pivotZ;
        float[] about = inertia;
        if (ax * ax + ay * ay + az * az > 1.0E-8F) {
            about = new float[] { inertia[0] + totalMass * (ay * ay + az * az),
                inertia[1] + totalMass * (ax * ax + az * az), inertia[2] + totalMass * (ax * ax + ay * ay),
                inertia[3] - totalMass * ax * ay, inertia[4] - totalMass * ax * az, inertia[5] - totalMass * ay * az };
        }

        float lx = pointX - pivotX;
        float ly = pointY - pivotY;
        float lz = pointZ - pivotZ;
        return solveSymmetric3(
            about,
            (ly * impulse.z - lz * impulse.y) * spin,
            (lz * impulse.x - lx * impulse.z) * spin,
            (lx * impulse.y - ly * impulse.x) * spin);
    }

    private static float[] solveSymmetric3(float[] m, float lx, float ly, float lz) {
        float ridge = 1.0E-4F * Math.max(1.0F, Math.max(m[0], Math.max(m[1], m[2])));
        float a = m[0] + ridge;
        float b = m[1] + ridge;
        float c = m[2] + ridge;
        float d = m[3];
        float e = m[4];
        float f = m[5];
        float c00 = b * c - f * f;
        float c01 = e * f - d * c;
        float c02 = d * f - e * b;
        float determinant = a * c00 + d * c01 + e * c02;
        if (Math.abs(determinant) < 1.0E-9F) {
            return new float[] { 0.0F, 0.0F, 0.0F };
        } else {
            float c11 = a * c - e * e;
            float c12 = e * d - a * f;
            float c22 = a * b - d * d;
            float inv = 1.0F / determinant;
            return new float[] { (c00 * lx + c01 * ly + c02 * lz) * inv, (c01 * lx + c11 * ly + c12 * lz) * inv,
                (c02 * lx + c12 * ly + c22 * lz) * inv };
        }
    }

    private static float limbWhipFor(RigidBody[] parts, int partIndex) {
        float base = .45F;
        float bias = .6F;
        if (!(base <= 0.0F) && !(bias <= 0.0F)) {
            float total = 0.0F;
            int counted = 0;

            for (RigidBody part : parts) {
                float invMass = part.getInvMass();
                if (!(invMass <= 0.0F)) {
                    total += 1.0F / invMass;
                    counted++;
                }
            }

            float invMass = parts[partIndex].getInvMass();
            if (counted != 0 && !(invMass <= 0.0F) && !(total <= 0.0F)) {
                float ratio = Math.max(0.1F, Math.min(10.0F, total / (float) counted * invMass));
                return Math.min(3.0F, base * (float) Math.pow((double) ratio, (double) bias));
            } else {
                return base;
            }
        } else {
            return base;
        }
    }

    private static void applyOffCentreImpulse(RigidBody body, Vector3f impulse, Vector3f impactPoint) {
        if (impactPoint == null) {
            body.applyCentralImpulse(impulse);
        } else {
            Transform at = new Transform();
            body.getWorldTransform(at);
            Vector3f lever = new Vector3f();
            lever.set(impactPoint.x - at.origin.x, impactPoint.y - at.origin.y, impactPoint.z - at.origin.z);
            float radius = partBoundingRadius(body);
            float arm = lever.length();
            if (arm < 1.0E-4F) {
                body.applyCentralImpulse(impulse);
            } else {
                if (arm > radius) {
                    lever.scale(radius / arm);
                }

                body.applyImpulse(impulse, lever);
            }
        }
    }

    private static float partBoundingRadius(RigidBody body) {
        BoxShape shape = (BoxShape) body.getCollisionShape();
        if (shape != null) {
            return shape.getHalfExtentsWithMargin(new Vector3f())
                .length();
        } else {
            return 0.25F;
        }
    }

    static void applyGlobalVelocityKick(RigidBody[] parts, Vector3f velocityKick, Vector3f blastCentre) {
        int count = parts.length;
        Vector3f kick = new Vector3f((float) velocityKick.x, (float) velocityKick.y, (float) velocityKick.z);
        float speed = kick.length();
        if (blastCentre != null && !(speed < 1.0E-4F)) {
            Vector3f heading = new Vector3f(kick);
            heading.scale(1.0F / speed);
            float[] distance = new float[count];
            float[] mass = new float[count];
            Vector3f[] outward = new Vector3f[count];
            float totalMass = 0.0F;
            float massDistance = 0.0F;

            for (int i = 0; i < count; i++) {
                RigidBody body = parts[i];
                Vector3f away = new Vector3f();
                body.getCenterOfMassPosition(away);
                away.sub(blastCentre);
                float length = away.length();
                if (length > 1.0E-4F) {
                    away.scale(1.0F / length);
                } else {
                    away.set(heading);
                }

                outward[i] = away;
                distance[i] = Math.max(0.25F, length);
                float invMass = body.getInvMass();
                mass[i] = invMass > 0.0F ? 1.0F / invMass : 0.0F;
                totalMass += mass[i];
                massDistance += mass[i] * distance[i];
            }

            if (totalMass <= 0.0F) {
                for (RigidBody body : parts) {
                    addLinearVelocity(body, kick);
                }
            } else {
                float meanDistance = massDistance / totalMass;
                float[] share = new float[count];
                float sharedMass = 0.0F;

                for (int i = 0; i < count; i++) {
                    share[i] = Math.max(.5F, Math.min(1.6F, meanDistance / distance[i]));
                    sharedMass += mass[i] * share[i];
                }

                float keepMomentum = sharedMass > 0.0F ? totalMass / sharedMass : 1.0F;
                Vector3f partKick = new Vector3f();

                for (int i = 0; i < count; i++) {
                    partKick.scale(0.65F, heading);
                    partKick.scaleAdd(0.35F, outward[i], partKick);
                    float length = partKick.length();
                    if (length < 1.0E-4F) {
                        partKick.set(heading);
                    } else {
                        partKick.scale(1.0F / length);
                    }

                    partKick.scale(speed * share[i] * keepMomentum);
                    addLinearVelocity(parts[i], partKick);
                }
            }
        } else {
            for (RigidBody body : parts) {
                addLinearVelocity(body, kick);
            }
        }
    }

    private static void addLinearVelocity(RigidBody body, Vector3f delta) {
        Vector3f velocity = new Vector3f();
        body.getLinearVelocity(velocity);
        velocity.add(delta);
        body.setLinearVelocity(velocity);
        body.activate();
    }

    private OriginalHumanoid() {}
}
