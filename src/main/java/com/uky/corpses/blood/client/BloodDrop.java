package com.uky.corpses.blood.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

/**
 * A drop of blood in the style of the game's own particles: vanilla's drip sprite, dark red, flung out, falling, and
 * squashed flat where it lands, gone a moment later. A burst of them flies off wherever blood is spilled; the stain on
 * the floor is the splat (BloodClient).
 */
final class BloodDrop extends EntityFX {

    /** Vanilla's drip sprites in particles.png: falling, landed. */
    private static final int FALLING = 112, LANDED = 114;
    private int landedFor = -1;

    private BloodDrop(World w, double x, double y, double z, double mx, double my, double mz, Random r) {
        super(w, x, y, z, 0, 0, 0);
        motionX = mx;
        motionY = my;
        motionZ = mz;
        float dark = r.nextFloat() * 0.25F;
        particleRed = 0.55F - dark;
        particleGreen = 0.02F;
        particleBlue = 0.03F;
        particleScale = 0.7F + r.nextFloat() * 0.6F;
        particleGravity = 1.4F;
        particleMaxAge = 30 + r.nextInt(25);
        setSize(0.02F, 0.02F);
        setParticleTextureIndex(FALLING);
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (particleAge++ >= particleMaxAge || landedFor > 8) {
            setDead();
            return;
        }
        if (landedFor >= 0) { // a flat little splash, then gone
            landedFor++;
            return;
        }
        motionY -= 0.04 * particleGravity;
        moveEntity(motionX, motionY, motionZ);
        motionX *= 0.96;
        motionY *= 0.98;
        motionZ *= 0.96;
        if (onGround || isInWater() || handleLavaMovement()) {
            landedFor = 0;
            motionX = motionY = motionZ = 0;
            setParticleTextureIndex(LANDED);
        }
    }

    /**
     * A burst of drops over a splat at x y z (its floor), as big as the splat; fewer with fewer particles in the
     * video settings, none on minimal or far from the camera.
     */
    static void burst(World w, double x, double y, double z, float size, Random r) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null || mc.gameSettings.particleSetting >= 2
            || mc.renderViewEntity.getDistanceSq(x, y, z) > 32 * 32) return;
        int n = Math.max(2, Math.round(size * 9));
        if (mc.gameSettings.particleSetting == 1) n = (n + 1) / 2;
        double from = y + 0.6 + r.nextDouble() * 0.4; // out of the wound, about waist high
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, out = 0.03 + r.nextDouble() * 0.09 * Math.min(1.5F, size);
            mc.effectRenderer.addEffect(
                new BloodDrop(
                    w,
                    x + (r.nextDouble() - 0.5) * 0.2,
                    from,
                    z + (r.nextDouble() - 0.5) * 0.2,
                    Math.cos(a) * out,
                    0.04 + r.nextDouble() * 0.12,
                    Math.sin(a) * out,
                    r));
        }
    }
}
