package com.uky.corpses.amp;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector3f;

import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.uky.corpses.corpse.PhysicsConfig;
import com.uky.corpses.corpse.RagdollWorld;
import com.uky.corpses.port.physics.BodyProperties;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Loose limbs participate in the main ragdoll solver, including body-to-limb collisions. */
public final class LoosePhysics {

    private static final Map<EntityDetachedPart, RagdollWorld.LooseBody> bodies = new IdentityHashMap<>();

    static void ensure(EntityDetachedPart limb) {
        if (bodies.containsKey(limb)) return;
        if (bodies.size() >= 512) {
            EntityDetachedPart oldest = null;
            for (EntityDetachedPart old : bodies.keySet())
                if (oldest == null || old.ticksExisted > oldest.ticksExisted) oldest = old;
            if (oldest != null) oldest.setDead();
        }
        float half = limb.part == 0 ? .25F : .125F, height = limb.part == 0 ? .25F : .375F;
        BodyProperties properties = new BodyProperties();
        properties.mass = (limb.part == 0 ? 2.4F : limb.part >= 4 ? 4.6F : 1.4F) * PhysicsConfig.massScale
            * PhysicsConfig.weight[limb.part];
        properties.friction = PhysicsConfig.friction;
        properties.restitution = PhysicsConfig.restitution;
        properties.linearDamping = PhysicsConfig.linearDamping;
        properties.angularDamping = PhysicsConfig.angularDamping;
        properties.allowSleeping = true;
        properties.ccdMotionThreshold = .08F;
        properties.ccdSweptSphereRadius = half * .8F;
        properties.linearVelocity.set(limb.velocity);
        RagdollWorld.LooseBody body = new RagdollWorld.LooseBody(
            limb.worldObj,
            limb.posX,
            limb.posY,
            limb.posZ,
            new Vector3f(half, height, half),
            limb.rotation,
            properties,
            limb.angular,
            PhysicsConfig.partGravity[limb.part]);
        try {
            body.refresh(limb);
            bodies.put(limb, body);
        } catch (Throwable error) {
            body.close();
            throw error;
        }
    }

    static void remove(EntityDetachedPart limb) {
        RagdollWorld.LooseBody body = bodies.remove(limb);
        if (body != null) {
            double[] at = new double[3];
            body.snapshot(at, limb.rotation, limb.velocity, limb.angular);
            body.close();
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (EntityDetachedPart limb : new ArrayList<>(bodies.keySet())) {
            if (limb.isDead) {
                remove(limb);
                continue;
            }
            RagdollWorld.LooseBody body = bodies.get(limb);
            double[] at = new double[3];
            body.snapshot(at, limb.rotation, limb.velocity, limb.angular);
            limb.setPosition(at[0], at[1], at[2]);
            limb.syncRotation();
            body.refresh(limb);
        }
    }

    @SubscribeEvent
    public void worldUnload(WorldEvent.Unload event) {
        for (EntityDetachedPart limb : new ArrayList<>(bodies.keySet())) if (limb.worldObj == event.world) remove(limb);
    }

    @SubscribeEvent
    public void chunkUnload(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        for (List<?> slice : event.getChunk().entityLists)
            for (Object entity : slice) if (entity instanceof EntityDetachedPart) remove((EntityDetachedPart) entity);
    }
}
