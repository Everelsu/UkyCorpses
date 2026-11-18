package com.uky.corpses.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * A player model with its limbs at given angles (corpse.Poses, mid-Settle, or a downed player's crawl). RenderCorpse
 * places the whole thing through corpse.Skeleton.
 */
public class ModelCorpse extends ModelBiped {

    /** head, right arm, left arm, right leg, left leg; x y z each. */
    float[] ang;

    public ModelCorpse(float expand) {
        super(expand);
        isChild = false; // ModelBase starts as a child; living renderers reset it per frame, nothing does it for us
    }

    public ModelCorpse(float expand, boolean skeletal) {
        this(expand);
        if (!skeletal) return;
        bipedRightArm = new ModelRenderer(this, 40, 16);
        bipedRightArm.addBox(-1, -2, -1, 2, 12, 2, expand);
        bipedRightArm.setRotationPoint(-5, 2, 0);
        bipedLeftArm = new ModelRenderer(this, 40, 16);
        bipedLeftArm.mirror = true;
        bipedLeftArm.addBox(-1, -2, -1, 2, 12, 2, expand);
        bipedLeftArm.setRotationPoint(5, 2, 0);
        bipedRightLeg = new ModelRenderer(this, 0, 16);
        bipedRightLeg.addBox(-1, 0, -1, 2, 12, 2, expand);
        bipedRightLeg.setRotationPoint(-2, 12, 0);
        bipedLeftLeg = new ModelRenderer(this, 0, 16);
        bipedLeftLeg.mirror = true;
        bipedLeftLeg.addBox(-1, 0, -1, 2, 12, 2, expand);
        bipedLeftLeg.setRotationPoint(2, 12, 0);
    }

    @Override
    public void setRotationAngles(float f, float f1, float f2, float f3, float f4, float f5, Entity e) {
        bipedBody.rotateAngleX = bipedBody.rotateAngleY = bipedBody.rotateAngleZ = 0;
        float[] a = ang;
        if (a == null) return;
        set(bipedHead, a[0], a[1], a[2]);
        set(bipedRightArm, a[3], a[4], a[5]);
        set(bipedLeftArm, a[6], a[7], a[8]);
        set(bipedRightLeg, a[9], a[10], a[11]);
        set(bipedLeftLeg, a[12], a[13], a[14]);
        bipedHeadwear.rotateAngleX = bipedHead.rotateAngleX;
        bipedHeadwear.rotateAngleY = bipedHead.rotateAngleY;
        bipedHeadwear.rotateAngleZ = bipedHead.rotateAngleZ;
    }

    private static void set(ModelRenderer r, float x, float y, float z) {
        r.rotateAngleX = x;
        r.rotateAngleY = y;
        r.rotateAngleZ = z;
    }
}
