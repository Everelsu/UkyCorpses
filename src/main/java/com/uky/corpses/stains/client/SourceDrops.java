// SurfaceBlood 0.1.5-beta Droplets entry points adapted to the legacy world/render bridge.
package com.uky.corpses.stains.client;

class SourceDrops {

    private int source = -1;

    void emitting(int entity) {
        source = entity;
    }

    void spawn(double x, double y, double z, float vx, float vy, float vz, float size, int color, int dried, int flags,
        float shade, int life) {
        SurfaceBloodClient.spawnSource(x, y, z, vx, vy, vz, size, color, dried, flags, shade, life, source, 0, 0, 0, 0);
    }

    void mist(double x, double y, double z, float vx, float vy, float vz, float microns, float ax, float ay, float az,
        int color, int dried, boolean marks, float shade, int life) {
        float diameter = microns / 100;
        SurfaceBloodClient.spawnSource(
            x,
            y,
            z,
            vx,
            vy,
            vz,
            diameter * .008F,
            color,
            dried,
            marks ? 5 : 4,
            shade,
            life,
            source,
            Math.max(.02F, .65F * diameter * diameter),
            ax,
            ay,
            az);
    }

    void burst(double x, double y, double z, boolean big, boolean flip, int color) {
        SurfaceBloodClient.burst(x, y, z, big, flip, color);
    }

    static BloodVec aim(BloodVec from, BloodVec to, float size, int ticks) {
        float drag = 1 - Math.min(.5F, .0022F / size);
        double reach, fall;
        if (drag > .9999F) {
            reach = ticks;
            fall = .055F * ticks * (ticks + 1) * .5;
        } else {
            reach = drag * (1 - Math.pow(drag, ticks)) / (1 - drag);
            fall = .055F * drag / (1 - drag) * (ticks - reach);
        }
        BloodVec span = to.subtract(from);
        return new BloodVec(span.x / reach, (span.y + fall) / reach, span.z / reach);
    }

    static BloodVec exit(net.minecraft.util.AxisAlignedBB box, BloodVec entry, BloodVec direction) {
        double reach = ((box.maxX - box.minX) + (box.maxY - box.minY) + (box.maxZ - box.minZ)) / 3 * 2 + 1;
        BloodVec far = entry.add(direction.scale(reach));
        net.minecraft.util.MovingObjectPosition hit = box.expand(.02, .02, .02)
            .calculateIntercept(
                net.minecraft.util.Vec3.createVectorHelper(far.x, far.y, far.z),
                net.minecraft.util.Vec3.createVectorHelper(entry.x, entry.y, entry.z));
        return hit == null ? entry : new BloodVec(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
    }

    static int pose(int id, float[] positions, float[] rotations) {
        net.minecraft.world.World world = SurfaceBloodClient.world();
        if (world == null) return 0;
        for (Object object : world.loadedEntityList) if (object instanceof com.uky.corpses.mobs.EntityMobRagdoll) {
            com.uky.corpses.mobs.EntityMobRagdoll body = (com.uky.corpses.mobs.EntityMobRagdoll) object;
            if (body.sourceEntityId() != id) continue;
            com.uky.corpses.port.physics.PhysTransform[] parts = body.physicalTransforms(1);
            int count = Math.min(16, parts.length);
            javax.vecmath.Quat4f q = new javax.vecmath.Quat4f();
            for (int p = 0; p < count; p++) {
                if (parts[p] == null) {
                    positions[p * 3] = Float.NaN;
                    continue;
                }
                positions[p * 3] = (float) (body.posX + parts[p].origin.x);
                positions[p * 3 + 1] = (float) (body.posY + parts[p].origin.y);
                positions[p * 3 + 2] = (float) (body.posZ + parts[p].origin.z);
                parts[p].getRotation(q);
                rotations[p * 4] = q.x;
                rotations[p * 4 + 1] = q.y;
                rotations[p * 4 + 2] = q.z;
                rotations[p * 4 + 3] = q.w;
            }
            return count;
        }
        net.minecraft.entity.Entity entity = world.getEntityByID(id);
        com.uky.corpses.corpse.Skeleton.Shape shape = entity == null ? null
            : com.uky.corpses.Reactions.physicalShape(entity, 1);
        if (shape == null) return 0;
        float[] matrix = new float[16];
        for (int p = 0; p < 6; p++) {
            shape.parts[p].toColumns(matrix);
            positions[p * 3] = (float) (entity.posX + matrix[12]);
            positions[p * 3 + 1] = (float) (entity.posY + matrix[13]);
            positions[p * 3 + 2] = (float) (entity.posZ + matrix[14]);
            javax.vecmath.Matrix3f basis = new javax.vecmath.Matrix3f(
                matrix[0],
                matrix[4],
                matrix[8],
                matrix[1],
                matrix[5],
                matrix[9],
                matrix[2],
                matrix[6],
                matrix[10]);
            basis.normalize();
            javax.vecmath.Quat4f q = new javax.vecmath.Quat4f();
            q.set(basis);
            rotations[p * 4] = q.x;
            rotations[p * 4 + 1] = q.y;
            rotations[p * 4 + 2] = q.z;
            rotations[p * 4 + 3] = q.w;
        }
        return 6;
    }
}
