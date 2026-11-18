package com.uky.corpses.mobs.physics;

import java.util.List;

import javax.vecmath.Vector3f;

import com.uky.corpses.corpse.PhysicsConfig;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;
import com.uky.corpses.port.physics.PhysicsShape;

/** Original whole-body impulse, limb whip and blast transfer for every authored mob rig. */
public final class MobDynamics {

    public static boolean prepare(List<PhysicsBody> parts, List<PhysicsConstraint> joints, boolean wasAirborne) {
        Vector3f velocity = new Vector3f();
        for (PhysicsBody body : parts) if (body != null && body.getInvMass() > 0) {
            body.getLinearVelocity(velocity);
            float speed = velocity.length();
            if (speed > PhysicsConfig.maxLinearSpeed) {
                velocity.scale(PhysicsConfig.maxLinearSpeed / speed);
                body.setLinearVelocity(velocity);
            }
            body.getAngularVelocity(velocity);
            speed = velocity.length();
            if (speed > PhysicsConfig.maxAngularSpeed) {
                velocity.scale(PhysicsConfig.maxAngularSpeed / speed);
                body.setAngularVelocity(velocity);
            }
        }
        if (parts.isEmpty() || joints.isEmpty()) return wasAirborne;
        parts.get(0)
            .getLinearVelocity(velocity);
        float descent = -velocity.y;
        boolean airborne = descent > (wasAirborne ? 1 : 2);
        if (airborne != wasAirborne)
            RagdollBodyFactory.applyRelaxation(joints, 0, airborne ? PhysicsConfig.airborneScale : 1);
        return airborne;
    }

    private static boolean finite(Vector3f vector) {
        return vector != null && Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }

    public static void hit(List<PhysicsBody> parts, int part, Vector3f impulse, Vector3f impactPoint, float supportY) {
        if (parts == null || part < 0
            || part >= parts.size()
            || !finite(impulse)
            || impactPoint != null && !finite(impactPoint)) return;
        transferToWholeBody(parts, impulse, part, impactPoint, Float.isFinite(supportY), supportY);
        float whip = limbWhipFor(parts, part);
        if (whip > 0) {
            Vector3f local = new Vector3f(impulse);
            local.scale(whip);
            applyOffCentreImpulse(parts.get(part), local, impactPoint);
            parts.get(part)
                .activate();
        }
    }

    private static void transferToWholeBody(List<PhysicsBody> parts, Vector3f impulse, int partIndex,
        Vector3f impactPoint, boolean fromStanding, float supportY) {
        int count = parts.size();
        float[][] centres = new float[count][];
        float[] masses = new float[count];
        float totalMass = 0.0F;
        float cx = 0.0F;
        float cy = 0.0F;
        float cz = 0.0F;
        Vector3f centre = new Vector3f();
        Vector3f halfExtents = new Vector3f();

        for (int i = 0; i < count; i++) {
            PhysicsBody part = parts.get(i);
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
                    PhysicsShape shape = parts.get(ix)
                        .getShape();
                    if (shape != null) {
                        shape.getHalfExtents(halfExtents);
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
                    PhysicsBody part = parts.get(ixx);
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

    private static float limbWhipFor(List<PhysicsBody> parts, int partIndex) {
        float base = .45F;
        float bias = .6F;
        if (!(base <= 0.0F) && !(bias <= 0.0F)) {
            float total = 0.0F;
            int counted = 0;

            for (PhysicsBody part : parts) {
                float invMass = part.getInvMass();
                if (!(invMass <= 0.0F)) {
                    total += 1.0F / invMass;
                    counted++;
                }
            }

            float invMass = parts.get(partIndex)
                .getInvMass();
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

    private static void applyOffCentreImpulse(PhysicsBody body, Vector3f impulse, Vector3f impactPoint) {
        if (impactPoint == null) {
            body.applyCentralImpulse(impulse);
        } else {
            PhysTransform at = new PhysTransform();
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

    private static float partBoundingRadius(PhysicsBody body) {
        PhysicsShape shape = body.getShape();
        if (shape != null) {
            Vector3f half = new Vector3f();
            shape.getHalfExtents(half);
            return half.length();
        } else {
            return 0.25F;
        }
    }

    public static void velocityKick(List<PhysicsBody> parts, Vector3f velocityKick, Vector3f blastCentre) {
        if (parts == null || !finite(velocityKick) || blastCentre != null && !finite(blastCentre)) return;
        int count = parts.size();
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
                PhysicsBody body = parts.get(i);
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
                for (PhysicsBody body : parts) {
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
                    addLinearVelocity(parts.get(i), partKick);
                }
            }
        } else {
            for (PhysicsBody body : parts) {
                addLinearVelocity(body, kick);
            }
        }
    }

    private static void addLinearVelocity(PhysicsBody body, Vector3f delta) {
        if (body == null || body.getInvMass() <= 0) return;
        Vector3f velocity = new Vector3f();
        body.getLinearVelocity(velocity);
        velocity.add(delta);
        body.setLinearVelocity(velocity);
        body.activate();
    }

    private MobDynamics() {}
}
