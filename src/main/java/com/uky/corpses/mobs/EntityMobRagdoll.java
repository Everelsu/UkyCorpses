package com.uky.corpses.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.uky.corpses.UkyCorpses;
import com.uky.corpses.amp.AmputationState;
import com.uky.corpses.corpse.PhysicsConfig;
import com.uky.corpses.corpse.RagdollWorld;
import com.uky.corpses.mobs.physics.MobRigType;
import com.uky.corpses.mobs.physics.RagdollBodyFactory;
import com.uky.corpses.mobs.physics.RagdollBodyFactory.BodyProfile;
import com.uky.corpses.port.physics.PhysTransform;
import com.uky.corpses.port.physics.PhysicsBody;
import com.uky.corpses.port.physics.PhysicsConstraint;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/** A non-loot visual body. Vanilla death, drops and XP remain attached to the original mob. */
public final class EntityMobRagdoll extends Entity implements IEntityAdditionalSpawnData {

    private MobRigType type = MobRigType.UNSUPPORTED;
    private BodyProfile profile = BodyProfile.DEFAULT;
    private float scale = 1, mesh = 1, yaw;
    private boolean baby, bigHead, airborne;
    public int hiddenMask;
    private long cleanupBorn = -1;
    private int sourceEntityId = -1, age, lifetime = 600;
    private String sourceType = "";
    private NBTTagCompound visualTag = new NBTTagCompound();
    private EntityLivingBase visual;
    private double originX, originY, originZ;
    private long generation = (UUID.randomUUID()
        .getLeastSignificantBits() & Long.MAX_VALUE) | 1L, sequence, lastSequence = -1;
    private float[] before, state;
    private float[] halfExtents = new float[0];
    private final java.util.Map<java.util.UUID, Integer> lastHit = new java.util.HashMap<>();
    private long receivedAt;
    private RagdollWorld.RigScene scene;
    private final List<PhysicsBody> parts = new ArrayList<>();
    private final List<PhysicsConstraint> joints = new ArrayList<>();
    private final Vector3f initialVelocity = new Vector3f();

    public EntityMobRagdoll(World world) {
        super(world);
        setSize(.6F, .6F);
        ignoreFrustumCheck = true;
    }

    @Override
    protected void entityInit() {}

    static EntityMobRagdoll from(EntityLivingBase victim, Vector3f inherited) {
        EntityMobRagdoll body = new EntityMobRagdoll(victim.worldObj);
        body.type = MobTypes.type(victim);
        body.profile = MobTypes.profile(victim);
        if (body.type == MobRigType.UNSUPPORTED || body.type == MobRigType.GENERIC) return null;
        body.scale = MobTypes.scale(victim);
        body.mesh = MobTypes.meshScale(victim);
        body.baby = MobTypes.isBaby(victim);
        body.bigHead = MobTypes.babyBigHead(victim);
        body.sourceEntityId = victim.getEntityId();
        body.sourceType = EntityList.getEntityString(victim);
        if (body.sourceType == null) return null;
        victim.writeToNBT(body.visualTag);
        body.visualTag.setString("id", body.sourceType);
        body.visualTag.setShort("DeathTime", (short) 0);
        body.visualTag.setShort("HurtTime", (short) 0);
        body.visualTag.setShort("Fire", (short) 0);
        body.visualTag.setFloat("HealF", Math.max(1, victim.getMaxHealth()));
        body.visualTag.setShort("Health", (short) Math.max(1, victim.getMaxHealth()));
        body.originX = victim.posX;
        body.originY = victim.boundingBox.minY
            + MobBirth.torsoHeight(body.type, body.profile, body.baby, body.scale, body.mesh);
        body.originZ = victim.posZ;
        body.yaw = victim.renderYawOffset;
        body.rotationYaw = body.yaw;
        body.hiddenMask = MobBirth.missingParts(AmputationState.mask(victim));
        body.lifetime = Math.max(20, PhysicsConfig.ragdollLifetime);
        body.initialVelocity.set(inherited);
        body.initialVelocity.scale(PhysicsConfig.initialVelocityScale);
        body.airborne = !victim.onGround;
        body.setPosition(body.originX, body.originY, body.originZ);
        if (!body.ensurePhysics()) return null;
        return body;
    }

    private boolean ensurePhysics() {
        if (scene != null) return true;
        if (worldObj.isRemote || type == MobRigType.UNSUPPORTED || type == MobRigType.GENERIC) return false;
        try {
            scene = new RagdollWorld.RigScene(worldObj, originX, originY, originZ);
            scene.own(parts, joints);
            Quat4f base = new Quat4f();
            base.set(new AxisAngle4f(0, 1, 0, (float) Math.toRadians(180 - yaw)));
            RagdollBodyFactory.build(
                scene.physics(),
                parts,
                joints,
                type,
                scene.offset(),
                base,
                scale,
                initialVelocity,
                null,
                profile,
                baby,
                bigHead,
                null,
                mesh);
            for (int i = 0; i < parts.size(); i++) {
                parts.get(i)
                    .setSleepingAllowed(false);
                if ((hiddenMask & (1 << i)) != 0) scene.hidePart(i);
            }
            if (state != null && !scene.restore(state))
                throw new IllegalArgumentException("Saved mob rig body count or pose is invalid");
            halfExtents = new float[parts.size() * 3];
            Vector3f half = new Vector3f();
            for (int i = 0; i < parts.size(); i++) {
                parts.get(i)
                    .getShape()
                    .getHalfExtents(half);
                halfExtents[i * 3] = half.x;
                halfExtents[i * 3 + 1] = half.y;
                halfExtents[i * 3 + 2] = half.z;
            }
            state = scene.snapshot();
            before = state.clone();
            scene.refresh(this);
            return true;
        } catch (RuntimeException error) {
            UkyCorpses.LOG.warn("Cannot create ragdoll for {}", sourceType, error);
            disposePhysics();
            return false;
        }
    }

    void applyBirthMotion(Vector3f impulse, Vector3f kick, double[] blastCenter, boolean grounded) {
        if (scene == null || parts.isEmpty()) return;
        if (kick != null) {
            Vector3f center = blastCenter == null ? null : scene.offset();
            if (center != null) center.add(
                new Vector3f(
                    (float) (blastCenter[0] - originX),
                    (float) (blastCenter[1] - originY),
                    (float) (blastCenter[2] - originZ)));
            com.uky.corpses.mobs.physics.MobDynamics.velocityKick(parts, kick, center);
        } else if (impulse != null) {
            float support = grounded ? scene.offset().y - MobBirth.torsoHeight(type, profile, baby, scale, mesh)
                : Float.NaN;
            com.uky.corpses.mobs.physics.MobDynamics.hit(parts, 0, impulse, null, support);
        }
        airborne = com.uky.corpses.mobs.physics.MobDynamics.prepare(parts, joints, airborne);
        state = scene.snapshot();
        before = state.clone();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) return;
        if (cleanupBorn < 0) cleanupBorn = worldObj.getTotalWorldTime();
        if (!PhysicsConfig.mobRagdolls || PhysicsConfig.expired(worldObj.getTotalWorldTime(), cleanupBorn)
            || ++age >= lifetime
            || !ensurePhysics()) {
            setDead();
            return;
        }
        scene.refresh(this);
        airborne = com.uky.corpses.mobs.physics.MobDynamics.prepare(parts, joints, airborne);
        float[] next = scene.snapshot();
        if (!RagdollWorld.RigScene.validState(next)) {
            setDead();
            return;
        }
        before = state;
        state = next;
        setPosition(originX + state[0], originY + state[1], originZ + state[2]);
        motionX = motionY = motionZ = 0;
        if (posY < -128) {
            setDead();
            return;
        }
        if ((age & 1) == 0) UkyCorpses.net.sendToAllAround(
            new MsgMobRagdoll(this, ++sequence),
            new NetworkRegistry.TargetPoint(dimension, posX, posY, posZ, 128));
    }

    public MobRigType rigType() {
        return type;
    }

    public float meshScale() {
        return mesh;
    }

    public int sourceEntityId() {
        return sourceEntityId;
    }

    public String sourceType() {
        return sourceType;
    }

    public long physicalGeneration() {
        return generation;
    }

    public double[] birthOrigin() {
        return new double[] { originX, originY, originZ };
    }

    public float[] snapshot() {
        return state == null ? null : state.clone();
    }

    public int loadedAge() {
        return age;
    }

    public EntityLivingBase visualEntity() {
        if (!worldObj.isRemote) return null;
        if (visual == null && !visualTag.getString("id")
            .isEmpty()) {
            try {
                Entity entity = EntityList.createEntityFromNBT((NBTTagCompound) visualTag.copy(), worldObj);
                if (entity instanceof EntityLivingBase) {
                    visual = (EntityLivingBase) entity;
                    visual.setHealth(visual.getMaxHealth());
                    visual.deathTime = 0;
                    visual.hurtTime = 0;
                }
            } catch (RuntimeException badVisual) {
                UkyCorpses.LOG.warn("Cannot recreate visual mob {}", sourceType, badVisual);
                visualTag.removeTag("id");
            }
        }
        return visual;
    }

    public PhysTransform[] physicalTransforms(float partial) {
        if (state == null) return new PhysTransform[0];
        float alpha = worldObj.isRemote ? snapshotFraction(partial) : Math.max(0, Math.min(1, partial));
        float[] old = before == null || before.length != state.length ? state : before;
        PhysTransform[] result = new PhysTransform[state.length / 13];
        for (int i = 0; i < result.length; i++) {
            if ((hiddenMask & (1 << i)) != 0) continue;
            int j = i * 13;
            PhysTransform at = new PhysTransform();
            at.origin.set(
                (float) (originX - posX) + old[j] + (state[j] - old[j]) * alpha,
                (float) (originY - posY) + old[j + 1] + (state[j + 1] - old[j + 1]) * alpha,
                (float) (originZ - posZ) + old[j + 2] + (state[j + 2] - old[j + 2]) * alpha);
            Quat4f a = new Quat4f(old[j + 3], old[j + 4], old[j + 5], old[j + 6]),
                b = new Quat4f(state[j + 3], state[j + 4], state[j + 5], state[j + 6]);
            a.interpolate(b, alpha);
            a.normalize();
            at.setRotation(a);
            result[i] = at;
        }
        return result;
    }

    private float snapshotFraction(float partial) {
        return Math.max(0, Math.min(1, (worldObj.getTotalWorldTime() - receivedAt + partial) / 2F));
    }

    public void acceptSnapshot(long life, long order, int serverAge, double x, double y, double z, float[] incoming) {
        if (!worldObj.isRemote || generation != life
            || order <= lastSequence
            || serverAge < 0
            || !MsgMobRagdoll.validAnchor(x, y, z)
            || !RagdollWorld.RigScene.validState(incoming)
            || state != null && state.length != incoming.length) return;
        if (originX != x || originY != y || originZ != z) return;
        if (state == null) before = incoming.clone();
        else {
            float alpha = snapshotFraction(0);
            if (before == null || alpha >= 1) before = state;
            else {
                Quat4f start = new Quat4f(), end = new Quat4f();
                for (int offset = 0; offset < state.length; offset += 13) {
                    for (int axis = 0; axis < 3; axis++) {
                        int index = offset + axis;
                        before[index] += (state[index] - before[index]) * alpha;
                    }
                    start.set(before[offset + 3], before[offset + 4], before[offset + 5], before[offset + 6]);
                    end.set(state[offset + 3], state[offset + 4], state[offset + 5], state[offset + 6]);
                    start.interpolate(end, alpha);
                    start.normalize();
                    before[offset + 3] = start.x;
                    before[offset + 4] = start.y;
                    before[offset + 5] = start.z;
                    before[offset + 6] = start.w;
                }
            }
        }
        state = incoming.clone();
        lastSequence = order;
        age = serverAge;
        receivedAt = worldObj.getTotalWorldTime();
        setPosition(originX + state[0], originY + state[1], originZ + state[2]);
        lastTickPosX = prevPosX = posX;
        lastTickPosY = prevPosY = posY;
        lastTickPosZ = prevPosZ = posZ;
    }

    @Override
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int steps) {
        if (state == null) super.setPositionAndRotation2(x, y, z, yaw, pitch, steps);
    }

    public float[] partHalfExtents() {
        return halfExtents.clone();
    }

    public int ray(double[] worldEye, double[] direction, double reach, double[] worldHit) {
        double[] eye = { worldEye[0] - posX, worldEye[1] - posY, worldEye[2] - posZ }, hit = new double[3];
        int part = MobRay.part(physicalTransforms(1), halfExtents, eye, direction, reach, hit);
        if (part >= 0 && worldHit != null) {
            worldHit[0] = hit[0] + posX;
            worldHit[1] = hit[1] + posY;
            worldHit[2] = hit[2] + posZ;
        }
        return part;
    }

    @Override
    public boolean hitByEntity(Entity actor) {
        if (worldObj.isRemote || isDead || !(actor instanceof net.minecraft.entity.player.EntityPlayer)) return true;
        net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) actor;
        Integer last = lastHit.get(player.getUniqueID());
        if (last != null && ticksExisted - last < 2) return true;
        net.minecraft.util.Vec3 look = player.getLookVec();
        double[] point = new double[3];
        double reach = player.capabilities.isCreativeMode ? 5 : 3;
        net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3
            .createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        net.minecraft.util.MovingObjectPosition wall = worldObj
            .rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        if (wall != null) reach = Math.min(reach, eye.distanceTo(wall.hitVec));
        int part = ray(
            new double[] { eye.xCoord, eye.yCoord, eye.zCoord },
            new double[] { look.xCoord, look.yCoord, look.zCoord },
            reach,
            point);
        if (part < 0 || !ensurePhysics()) return true;
        lastHit.put(player.getUniqueID(), ticksExisted);
        double damage = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.attackDamage)
            .getAttributeValue();
        if (!Double.isFinite(damage)) return true;
        float strength = (float) Math.min(500, 75 * Math.sqrt(Math.max(1, damage) / 5) * (part == 1 ? 1.25 : 1));
        Vector3f impulse = new Vector3f(
            (float) look.xCoord * strength,
            (float) look.yCoord * strength + strength * .18F,
            (float) look.zCoord * strength);
        Vector3f at = scene.offset();
        at.add(new Vector3f((float) (point[0] - originX), (float) (point[1] - originY), (float) (point[2] - originZ)));
        Vector3f center = new Vector3f();
        parts.get(part)
            .getPosition(center);
        Vector3f lever = new Vector3f(at);
        lever.sub(center);
        if (lever.length() > .45F) {
            lever.scale(.45F / lever.length());
            at.add(center, lever);
        }
        com.uky.corpses.mobs.physics.MobDynamics.hit(parts, part, impulse, at, Float.NaN);
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage) {
        if (!worldObj.isRemote && source.isProjectile()
            && source.getSourceOfDamage() != null
            && Float.isFinite(damage)
            && damage > 0
            && ensurePhysics()) {
            Entity projectile = source.getSourceOfDamage();
            Vector3f direction = new Vector3f(
                (float) projectile.motionX,
                (float) projectile.motionY,
                (float) projectile.motionZ);
            if (direction.lengthSquared() > 1e-6F) {
                direction.normalize();
                direction.scale((float) Math.min(50, 75 * Math.sqrt(Math.max(1, damage) / 5)));
                direction.y += direction.length() * .18F;
                com.uky.corpses.mobs.physics.MobDynamics.hit(parts, 0, direction, null, Float.NaN);
            }
        }
        return false;
    }

    public void disposePhysics() {
        if (scene != null) {
            scene.close();
            scene = null;
        }
        parts.clear();
        joints.clear();
    }

    @Override
    public void setDead() {
        disposePhysics();
        super.setDead();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        if (scene != null) state = scene.snapshot();
        tag.setTag("Visual", visualTag.copy());
        tag.setString("RigType", type.name());
        tag.setString("Profile", profile.name());
        tag.setFloat("Scale", scale);
        tag.setFloat("MeshScale", mesh);
        tag.setFloat("BirthYaw", yaw);
        tag.setBoolean("Baby", baby);
        tag.setBoolean("BigHead", bigHead);
        tag.setInteger("SourceId", sourceEntityId);
        tag.setString("SourceType", sourceType);
        tag.setInteger("Hidden", hiddenMask);
        tag.setInteger("Age", age);
        tag.setLong("CleanupBorn", cleanupBorn < 0 ? worldObj.getTotalWorldTime() : cleanupBorn);
        tag.setInteger("Lifetime", lifetime);
        tag.setLong("Generation", generation);
        tag.setLong("Sequence", sequence);
        tag.setDouble("OriginX", originX);
        tag.setDouble("OriginY", originY);
        tag.setDouble("OriginZ", originZ);
        NBTTagList halves = new NBTTagList();
        for (float value : halfExtents) halves.appendTag(new NBTTagFloat(value));
        tag.setTag("HalfExtents", halves);
        if (state != null) {
            NBTTagList list = new NBTTagList();
            for (float value : state) list.appendTag(new NBTTagFloat(value));
            tag.setTag("State", list);
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        disposePhysics();
        visual = null;
        visualTag = tag.getCompoundTag("Visual");
        try {
            type = MobRigType.valueOf(tag.getString("RigType"));
            profile = BodyProfile.valueOf(tag.getString("Profile"));
        } catch (IllegalArgumentException invalid) {
            setDead();
            return;
        }
        scale = boundedScale(tag.getFloat("Scale"));
        mesh = boundedScale(tag.getFloat("MeshScale"));
        yaw = tag.getFloat("BirthYaw");
        baby = tag.getBoolean("Baby");
        bigHead = tag.getBoolean("BigHead");
        sourceEntityId = tag.getInteger("SourceId");
        sourceType = tag.getString("SourceType");
        hiddenMask = tag.getInteger("Hidden") & 62;
        age = Math.max(0, tag.getInteger("Age"));
        cleanupBorn = tag.hasKey("CleanupBorn") ? tag.getLong("CleanupBorn")
            : Math.max(0, worldObj.getTotalWorldTime() - age);
        lifetime = Math.max(1, Math.min(2400000, tag.getInteger("Lifetime")));
        generation = tag.getLong("Generation");
        sequence = Math.max(0, tag.getLong("Sequence"));
        originX = tag.getDouble("OriginX");
        originY = tag.getDouble("OriginY");
        originZ = tag.getDouble("OriginZ");
        NBTTagList list = tag.getTagList("State", 5);
        state = list.tagCount() > 64 * 13 ? null : new float[list.tagCount()];
        if (state != null) for (int i = 0; i < state.length; i++) state[i] = list.func_150308_e(i);
        if (generation <= 0 || !Float.isFinite(yaw)
            || !MsgMobRagdoll.validAnchor(originX, originY, originZ)
            || !RagdollWorld.RigScene.validState(state)
            || type == MobRigType.UNSUPPORTED
            || type == MobRigType.GENERIC) {
            state = null;
            setDead();
            return;
        }
        NBTTagList halves = tag.getTagList("HalfExtents", 5);
        halfExtents = halves.tagCount() == state.length / 13 * 3 ? new float[halves.tagCount()] : new float[0];
        for (int i = 0; i < halfExtents.length; i++) {
            halfExtents[i] = halves.func_150308_e(i);
            if (!Float.isFinite(halfExtents[i]) || halfExtents[i] <= 0 || halfExtents[i] > 64) {
                halfExtents = new float[0];
                break;
            }
        }
        before = state.clone();
        receivedAt = worldObj.getTotalWorldTime();
        lastSequence = sequence;
    }

    static float boundedScale(float value) {
        return Float.isFinite(value) ? Math.max(.01F, Math.min(32, value)) : 1;
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        if (!worldObj.isRemote) ensurePhysics();
        NBTTagCompound tag = new NBTTagCompound();
        writeEntityToNBT(tag);
        ByteBufUtils.writeTag(buffer, tag);
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        NBTTagCompound tag = ByteBufUtils.readTag(buffer);
        if (tag == null) {
            setDead();
            return;
        }
        readEntityFromNBT(tag);
    }
}
