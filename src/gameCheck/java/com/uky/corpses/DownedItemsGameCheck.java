package com.uky.corpses;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;

import com.uky.corpses.medicine.Medicine;

public final class DownedItemsGameCheck {

    private static int invulnerable, alone;
    private static boolean self;
    private static ItemStack slot, armor;
    private static float health;

    public static boolean tick(EntityPlayerMP p, int phase) throws Exception {
        if (phase == 1) {
            invulnerable = OriginalGameplay.downedInvulnerableTicks;
            alone = CorpseConfig.aloneSeconds;
            self = Medicine.selfAdrenaline;
            slot = p.inventory.mainInventory[20];
            armor = p.inventory.armorInventory[2];
            health = p.getHealth();
            OriginalGameplay.downedInvulnerableTicks = 0;
            CorpseConfig.aloneSeconds = 60;
            Medicine.selfAdrenaline = true;
            p.capabilities.isCreativeMode = false;
            p.removePotionEffect(Medicine.STUN.id);
            p.removePotionEffect(Medicine.ADRENALINE.id);
            EntityZombie target = new EntityZombie(p.worldObj);
            target.setPosition(p.posX + 1, p.posY, p.posZ);
            target.setHealth(20);
            p.attackTargetEntityWithCurrentItem(target);
            check(target.getHealth() < 20, "Respawned player can immediately attack");
            target.setDead();
            Downed.down(p, DamageSource.generic);
            p.inventory.armorInventory[2] = new ItemStack(Items.diamond_chestplate);
            p.hurtResistantTime = 0;
            float before = knocked(p);
            p.attackEntityFrom(DamageSource.causeMobDamage(target), 6);
            float after = knocked(p);
            check(
                after < before && before - after < 6 && p.hurtTime > 0,
                "Downed hits respect armor and vanilla hurt animation");
            p.attackEntityFrom(DamageSource.causeMobDamage(target), 6);
            check(knocked(p) == after, "Repeated downed hits respect vanilla hit cooldown");
            p.inventory.mainInventory[20] = new ItemStack(Medicine.INJECTOR, 2);
            Downed.selfAdrenaline(p, -1);
        }
        if (phase == 3) {
            p.hurtResistantTime = 0;
            p.attackEntityFrom(DamageSource.generic, 2);
            check(field(p, "selfItem") == null, "A hit interrupts self injection without consuming the item");
            Downed.selfAdrenaline(p, 20);
        }
        if (phase == 28) {
            check(
                !Downed.is(p) && p.inventory.mainInventory[20].stackSize == 1,
                "Inventory injector revives self and consumes exactly one item");
            Downed.down(p, DamageSource.generic);
            p.inventory.mainInventory[20] = new ItemStack(Medicine.SYRINGE, 1);
            Downed.selfAdrenaline(p, 20);
        }
        if (phase == 114) {
            check(
                !Downed.is(p) && p.inventory.mainInventory[20] == null,
                "Inventory syringe revives self after its configured duration");
            boolean playerPhysics = com.uky.corpses.corpse.PhysicsConfig.playerRagdolls;
            com.uky.corpses.corpse.PhysicsConfig.playerRagdolls = true;
            EntityCorpse body = UkyCorpses.instance.layOut(
                p,
                new double[] { p.posX, p.boundingBox.minY, p.posZ, p.rotationYaw, EntityCorpse.PRONE, 1 },
                0,
                false,
                false,
                null,
                p.rotationYaw,
                -1,
                Collections.emptyList(),
                0,
                0);
            check(body.ragdollActive(), "Downed death creates a physical corpse");
            NBTTagCompound tag = new NBTTagCompound();
            body.writeEntityToNBT(tag);
            net.minecraft.nbt.NBTTagList state = tag.getTagList("ragdollState", 5);
            float qx = state.func_150308_e(16), qz = state.func_150308_e(18);
            check(
                Math.abs(1 - 2 * (qx * qx + qz * qz)) < .5F,
                "Corpse starts with horizontal torso instead of standing");
            body.setDead();
            com.uky.corpses.corpse.PhysicsConfig.playerRagdolls = playerPhysics;
            p.inventory.mainInventory[20] = slot;
            p.inventory.armorInventory[2] = armor;
            p.setHealth(health);
            OriginalGameplay.downedInvulnerableTicks = invulnerable;
            CorpseConfig.aloneSeconds = alone;
            Medicine.selfAdrenaline = self;
            return true;
        }
        return false;
    }

    private static Object field(EntityPlayerMP p, String name) throws Exception {
        Field f = Downed.class.getDeclaredField("downed");
        f.setAccessible(true);
        Object state = ((Map<?, ?>) f.get(null)).get(p.getUniqueID());
        Field value = state.getClass()
            .getDeclaredField(name);
        value.setAccessible(true);
        return value.get(state);
    }

    private static float knocked(EntityPlayerMP p) throws Exception {
        return (Float) field(p, "knocked");
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("ITEMSQA {}", text);
    }

    private DownedItemsGameCheck() {}
}
