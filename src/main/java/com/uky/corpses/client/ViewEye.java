package com.uky.corpses.client;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

/**
 * The eye the world is drawn from when the camera isn't the player's own (the death camera, the crawl camera): put in
 * renderViewEntity, never in the world, placed every frame. Fog, blindness, night vision and the hurt shake come from
 * the player it stands in for.
 */
public final class ViewEye extends EntityLiving {

    EntityLivingBase of;
    /**
     * The death camera's: no hurt jolt (vanilla's would tilt the view 14 degrees), and of the potions only night
     * vision (blindness from lying downed would lift at some random moment mid-scene).
     */
    private final boolean calm;
    private boolean originalCamera;

    public ViewEye(World w, EntityLivingBase of) {
        this(w, of, false);
    }

    public ViewEye(World w, EntityLivingBase of, boolean calm) {
        super(w);
        this.of = of;
        this.calm = calm;
        yOffset = 1.62F; // orientCamera puts the view at posY - (yOffset - 1.62)
    }

    public ViewEye(World w, EntityLivingBase of, boolean calm, boolean originalCamera) {
        this(w, of, calm);
        this.originalCamera = originalCamera;
    }

    @Override
    public float getEyeHeight() {
        return 0;
    }

    @Override
    public boolean isPotionActive(Potion p) {
        return of != null && (originalCamera || !calm || p == Potion.nightVision) && of.isPotionActive(p);
    }

    @Override
    public PotionEffect getActivePotionEffect(Potion p) {
        return of == null || !originalCamera && calm && p != Potion.nightVision ? null : of.getActivePotionEffect(p);
    }

    /** Exactly there this frame, nothing to interpolate. */
    public void place(double x, double y, double z, float yaw, float pitch) {
        setPosition(x, y, z);
        prevPosX = lastTickPosX = x;
        prevPosY = lastTickPosY = y;
        prevPosZ = lastTickPosZ = z;
        rotationYaw = prevRotationYaw = rotationYawHead = prevRotationYawHead = yaw;
        rotationPitch = prevRotationPitch = pitch;
        if (originalCamera && of != null) setHealth(com.uky.corpses.Downed.is(of) ? getMaxHealth() : of.getHealth());
        if (of != null && (originalCamera ? !com.uky.corpses.Downed.is(of) : !calm)) {
            if (originalCamera) deathTime = of.deathTime;
            hurtTime = of.hurtTime;
            maxHurtTime = of.maxHurtTime;
            attackedAtYaw = of.attackedAtYaw;
        }
    }
}
