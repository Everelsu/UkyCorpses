package com.uky.corpses.port.physics.jolt;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.SixDofConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;

final class JoltConstraint implements PhysicsConstraint {

    final TwoBodyConstraint constraint;
    private final TwoBodyConstraintRef reference;
    private final JoltBody bodyA;
    private final JoltBody bodyB;
    private boolean keepsCollision;
    boolean inWorld;
    private static final EAxis[] ROTATION_AXES = new EAxis[] { EAxis.RotationX, EAxis.RotationY, EAxis.RotationZ };

    JoltConstraint(TwoBodyConstraint constraint, JoltBody bodyA, JoltBody bodyB) {
        this.constraint = constraint;
        this.reference = constraint.toRef();
        this.bodyA = bodyA;
        this.bodyB = bodyB;
    }

    @Override
    public PhysicsBody bodyA() {
        return this.bodyA;
    }

    @Override
    public PhysicsBody bodyB() {
        return this.bodyB;
    }

    @Override
    public void driveToOrientation(float qx, float qy, float qz, float qw, float frequency, float damping,
        float maxTorque) {
        if (this.constraint instanceof SixDofConstraint sixDof) {
            if (frequency <= 0.0F) {
                for (EAxis axis : ROTATION_AXES) {
                    sixDof.setMotorState(axis, EMotorState.Off);
                }
            } else {
                for (EAxis axis : ROTATION_AXES) {
                    MotorSettings motor = sixDof.getMotorSettings(axis);
                    motor.getSpringSettings()
                        .setFrequency(frequency);
                    motor.getSpringSettings()
                        .setDamping(damping);
                    motor.setTorqueLimits(-maxTorque, maxTorque);
                    sixDof.setMotorState(axis, EMotorState.Position);
                }

                sixDof.setTargetOrientationCs(new Quat(qx, qy, qz, qw));
            }
        }
    }

    @Override
    public boolean keepsCollision() {
        return this.keepsCollision;
    }

    @Override
    public void setKeepsCollision(boolean keepsCollision) {
        this.keepsCollision = keepsCollision;
    }

    TwoBodyConstraintRef reference() {
        return this.reference;
    }
}
