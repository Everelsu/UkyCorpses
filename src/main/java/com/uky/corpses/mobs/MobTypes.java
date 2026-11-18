package com.uky.corpses.mobs;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityGiantZombie;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;

import com.uky.corpses.mobs.physics.MobRigType;
import com.uky.corpses.mobs.physics.RagdollBodyFactory.BodyProfile;

public final class MobTypes {

    public static MobRigType type(EntityLivingBase entity) {
        if (entity instanceof EntitySlime && ((EntitySlime) entity).getSlimeSize() > 1) return MobRigType.UNSUPPORTED;
        if (entity instanceof EntityPlayer) return MobRigType.UNSUPPORTED;
        if (entity instanceof EntityDragon) return MobRigType.ENDER_DRAGON;
        if (entity instanceof EntityWither) return MobRigType.WITHER;
        if (entity instanceof EntityIronGolem) return MobRigType.IRON_GOLEM;
        if (entity instanceof EntitySnowman) return MobRigType.SNOW_GOLEM;
        if (entity instanceof EntitySkeleton) return MobRigType.HUMANOID_SKELETON;
        if (entity instanceof EntityEnderman) return MobRigType.ENDERMAN;
        if (entity instanceof EntityWitch) return MobRigType.WITCH;
        if (entity instanceof EntityVillager) return MobRigType.ILLAGER;
        if (entity instanceof EntityZombie) return MobRigType.HUMANOID_STANDARD;
        if (entity instanceof EntityGiantZombie) return MobRigType.HUMANOID_STANDARD;
        if (entity instanceof EntityCreeper) return MobRigType.CREEPER;
        if (entity instanceof EntityChicken) return MobRigType.CHICKEN;
        if (entity instanceof EntityCow || entity instanceof EntitySheep
            || entity instanceof EntityPig
            || entity instanceof EntityOcelot) return MobRigType.QUADRUPED;
        if (entity instanceof EntityHorse) return MobRigType.EQUINE;
        if (entity instanceof EntityWolf) return MobRigType.WOLF;
        if (entity instanceof EntityBat) return MobRigType.BAT;
        if (entity instanceof EntityMagmaCube) return MobRigType.MAGMA_CUBE;
        if (entity instanceof EntitySlime) return MobRigType.SLIME;
        if (entity instanceof EntitySilverfish) return MobRigType.SILVERFISH;
        if (entity instanceof EntityBlaze) return MobRigType.BLAZE;
        if (entity instanceof EntitySpider) return MobRigType.SPIDER;
        if (entity instanceof EntityGhast) return MobRigType.GHAST;
        if (entity instanceof EntitySquid) return MobRigType.SQUID;
        return MobRigType.UNSUPPORTED;
    }

    public static BodyProfile profile(EntityLivingBase entity) {
        if (entity instanceof EntityCow) return BodyProfile.COW;
        if (entity instanceof EntitySheep) return BodyProfile.SHEEP;
        if (entity instanceof EntityPig) return BodyProfile.PIG;
        if (entity instanceof EntityOcelot) return BodyProfile.CAT;
        if (entity instanceof EntityChicken) return BodyProfile.CHICKEN;
        if (entity instanceof EntityHorse) {
            int type = ((EntityHorse) entity).getHorseType();
            return type == 1 ? BodyProfile.DONKEY : type == 2 ? BodyProfile.MULE : BodyProfile.HORSE;
        }
        if (entity instanceof EntityWolf) return BodyProfile.WOLF;
        if (entity instanceof EntityMagmaCube) return BodyProfile.MAGMA_CUBE;
        if (entity instanceof EntitySlime) return BodyProfile.SLIME;
        if (entity instanceof EntitySilverfish) return BodyProfile.SILVERFISH;
        if (entity instanceof EntitySnowman) return BodyProfile.SNOW_GOLEM;
        if (entity instanceof EntityBlaze) return BodyProfile.BLAZE;
        if (entity instanceof EntityCaveSpider) return BodyProfile.CAVE_SPIDER;
        if (entity instanceof EntitySpider) return BodyProfile.SPIDER;
        if (entity instanceof EntityGhast) return BodyProfile.GHAST;
        if (entity instanceof EntitySquid) return BodyProfile.SQUID;
        if (entity instanceof EntityWither) return BodyProfile.WITHER;
        if (entity instanceof EntityDragon) return BodyProfile.ENDER_DRAGON;
        return BodyProfile.DEFAULT;
    }

    public static boolean isBaby(EntityLivingBase entity) {
        return entity.isChild();
    }

    public static boolean babyBigHead(EntityLivingBase entity) {
        return entity instanceof EntityVillager;
    }

    public static float scale(EntityLivingBase entity) {
        return entity instanceof EntityGiantZombie ? 6 : 1;
    }

    public static float meshScale(EntityLivingBase entity) {
        return entity instanceof EntitySkeleton && ((EntitySkeleton) entity).getSkeletonType() == 1 ? 1.2F
            : scale(entity);
    }

    private MobTypes() {}
}
