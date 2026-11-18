package com.uky.corpses.port.physics.jbullet;

import javax.vecmath.Vector3f;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.uky.corpses.port.physics.PhysicsShape;

final class JBulletShape implements PhysicsShape {

    final CollisionShape shape;
    private final BoxShape box;

    JBulletShape(CollisionShape shape) {
        this.shape = shape;
        this.box = shape instanceof BoxShape b ? b : null;
    }

    @Override
    public void getHalfExtents(Vector3f out) {
        if (this.box != null) {
            this.box.getHalfExtentsWithMargin(out);
        } else {
            out.set(0.0F, 0.0F, 0.0F);
        }
    }

    @Override
    public boolean isBox() {
        return this.box != null;
    }
}
