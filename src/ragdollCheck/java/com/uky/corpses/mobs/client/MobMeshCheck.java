package com.uky.corpses.mobs.client;

import java.util.List;

import javax.vecmath.Vector3f;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBat;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBlaze;
import net.minecraft.client.model.ModelChicken;
import net.minecraft.client.model.ModelCow;
import net.minecraft.client.model.ModelCreeper;
import net.minecraft.client.model.ModelDragon;
import net.minecraft.client.model.ModelEnderman;
import net.minecraft.client.model.ModelGhast;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.client.model.ModelIronGolem;
import net.minecraft.client.model.ModelMagmaCube;
import net.minecraft.client.model.ModelOcelot;
import net.minecraft.client.model.ModelPig;
import net.minecraft.client.model.ModelSheep1;
import net.minecraft.client.model.ModelSilverfish;
import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.client.model.ModelSlime;
import net.minecraft.client.model.ModelSnowMan;
import net.minecraft.client.model.ModelSpider;
import net.minecraft.client.model.ModelSquid;
import net.minecraft.client.model.ModelVillager;
import net.minecraft.client.model.ModelWitch;
import net.minecraft.client.model.ModelWither;
import net.minecraft.client.model.ModelWolf;
import net.minecraft.client.model.ModelZombie;

import com.uky.corpses.mobs.physics.MobRigType;

/** Actual 1.7 model boxes and source part ordering, without opening a GL context. */
public final class MobMeshCheck {

    public static void run() {
        check(new ModelBiped(), MobRigType.HUMANOID_STANDARD, 7, 6);
        check(new ModelSkeleton(), MobRigType.HUMANOID_SKELETON, 7, 6);
        check(new ModelEnderman(), MobRigType.ENDERMAN, 7, 6);
        check(new ModelZombie(), MobRigType.HUMANOID_STANDARD, 7, 6);
        check(new ModelVillager(0), MobRigType.ILLAGER, 5, 4);
        check(new ModelWitch(0), MobRigType.WITCH, 5, 4);
        check(new ModelCreeper(), MobRigType.CREEPER, 6, 6);
        check(new ModelCow(), MobRigType.QUADRUPED, 6, 6);
        check(new ModelPig(), MobRigType.QUADRUPED, 6, 6);
        check(new ModelSheep1(), MobRigType.QUADRUPED, 6, 6);
        check(new ModelChicken(), MobRigType.CHICKEN, 8, 6);
        check(new ModelIronGolem(), MobRigType.IRON_GOLEM, 6, 6);
        check(new ModelWolf(), MobRigType.WOLF, 8, 6);
        check(new ModelOcelot(), MobRigType.QUADRUPED, 8, 6);
        check(new ModelHorse(), MobRigType.EQUINE, 23, 6);
        check(new ModelBat(), MobRigType.BAT, 4, 4);
        check(new ModelSpider(), MobRigType.SPIDER, 11, 10);
        check(new ModelGhast(), MobRigType.GHAST, 10, 10);
        check(new ModelSquid(), MobRigType.SQUID, 9, 9);
        check(new ModelSnowMan(), MobRigType.SNOW_GOLEM, 5, 3);
        check(new ModelBlaze(), MobRigType.BLAZE, 1, 1);
        check(new ModelSlime(16), MobRigType.SLIME, 4, 1);
        check(new ModelMagmaCube(), MobRigType.MAGMA_CUBE, 9, 1);
        check(new ModelSilverfish(), MobRigType.SILVERFISH, 10, 6);
        check(new ModelWither(), MobRigType.WITHER, 6, 5);
        check(new ModelDragon(0), MobRigType.ENDER_DRAGON, 26, 10);
        Vector3f head = MobMeshes.center(new ModelBiped().bipedHead);
        assert head.x == 0 && head.y == -4 && head.z == 0;
        Vector3f arm = MobMeshes.center(new ModelBiped().bipedRightArm);
        assert arm.x == -1 && arm.y == 4 && arm.z == 0;
        System.out.println(
            "Mob meshes: actual vanilla models, original body indices, independent parts and centering passed");
    }

    private static void check(ModelBase model, MobRigType type, int count, int bodies) {
        List<MobMeshes.Mesh> mesh = MobMeshes.layout(model, type, false, 1, null);
        assert mesh.size() == count : model.getClass() + " mesh count " + mesh.size();
        java.util.Set<Integer> unique = new java.util.HashSet<>();
        for (MobMeshes.Mesh part : mesh) {
            unique.add(part.body);
            Vector3f center = MobMeshes.center(part.anchor);
            assert Float.isFinite(center.x) && Float.isFinite(center.y) && Float.isFinite(center.z);
        }
        assert unique.size() == bodies : model.getClass() + " physical body count " + unique.size();
    }

    private MobMeshCheck() {}
}
