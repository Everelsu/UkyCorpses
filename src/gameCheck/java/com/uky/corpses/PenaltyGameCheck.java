package com.uky.corpses;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.config.Property;

import com.uky.corpses.pc.PcCorpses;

public final class PenaltyGameCheck {

    private static final Map<Property, String> saved = new LinkedHashMap<>();
    private static int xp, respawnCycle;

    public static void clientTick(int phase) {
        Minecraft mc = Minecraft.getMinecraft();
        int cycle = phase >= 180 ? 2 : phase >= 80 ? 1 : 0;
        if (cycle > respawnCycle && mc.thePlayer != null && !mc.thePlayer.isEntityAlive()) {
            mc.thePlayer.respawnPlayer();
            mc.displayGuiScreen(null);
            respawnCycle = cycle;
        }
    }

    private static void set(String category, String key, String value) {
        Property p = CorpseConfig.config()
            .getCategory(category)
            .get(key);
        saved.putIfAbsent(p, p.getString());
        p.set(value);
    }

    public static boolean tick(EntityPlayerMP p, int phase) {
        if (phase == 1) {
            p.worldObj.getGameRules()
                .setOrCreateGameRule("doMobSpawning", "false");
            p.worldObj.getGameRules()
                .setOrCreateGameRule("keepInventory", "false");
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                p.worldObj.setBlock(x, 65, z, Blocks.bedrock);
                for (int y = 66; y <= 73; y++) p.worldObj.setBlockToAir(x, y, z);
            }
        }
        if (phase == 60 || phase == 160 || phase == 260) {
            check(p.isEntityAlive(), "Real player is alive before each death penalty scenario");
            set("penalty", "enabled", phase == 260 ? "false" : "true");
            set("penalty", "mode", "hardcore");
            set("penalty.hardcore", "keepXpPercent", "50");
            set("penalty.hardcore", "durabilityLossPercent", "25");
            set("penalty.hardcore", "itemLossPercent", phase == 60 ? "0" : "100");
            set("corpse.rules", "corpseStoreXp", "true");
            CorpseConfig.sync();
            check(
                CorpseConfig.keepXpPercent == (phase == 260 ? 100 : 50)
                    && CorpseConfig.durabilityLossPercent == (phase == 260 ? 0 : 25)
                    && CorpseConfig.itemLossPercent == (phase == 60 || phase == 260 ? 0 : 100),
                "Selected penalties survive the complete config pipeline, scenario " + phase);
            CorpseConfig.downed = false;
            Reactions.teleported(p);
            p.playerNetServerHandler.setPlayerLocation(.5, 66, .5, 0, 0);
            p.setHealth(20);
            p.hurtResistantTime = 0;
            for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = null;
            for (int i = 0; i < 4; i++) p.inventory.armorInventory[i] = null;
            ItemStack sword = new ItemStack(Items.diamond_sword);
            sword.setTagCompound(new NBTTagCompound());
            sword.getTagCompound()
                .setString("PenaltyQA", "kept");
            p.inventory.mainInventory[0] = sword;
            p.inventory.mainInventory[1] = new ItemStack(Items.diamond, 3);
            p.inventory.armorInventory[3] = new ItemStack(Items.diamond_helmet);
            p.experience = 0;
            p.experienceLevel = 0;
            p.experienceTotal = 0;
            p.addExperience(500);
            xp = UkyCorpses.totalXp(p);
            p.attackEntityFrom(DamageSource.outOfWorld, 1000);
        }
        if (phase == 70 || phase == 170 || phase == 270) {
            EntityCorpse body = null;
            for (Object e : p.worldObj.loadedEntityList) if (e instanceof EntityCorpse) {
                EntityCorpse b = (EntityCorpse) e;
                if (!b.isDead && p.getUniqueID()
                    .equals(b.physicalOwner()) && PcCorpses.isLastDeath(b)) body = b;
            }
            check(body != null, "Real fatal damage creates the indexed corpse, scenario " + phase);
            boolean disabled = phase == 270;
            int retainedXp = body.xp;
            if (phase == 170) for (Object entity : p.worldObj.loadedEntityList)
                if (entity instanceof net.minecraft.entity.item.EntityXPOrb
                    && !((net.minecraft.entity.Entity) entity).isDead)
                    retainedXp += ((net.minecraft.entity.item.EntityXPOrb) entity).xpValue;
            check(
                retainedXp == (disabled ? xp : xp / 2),
                "Death stores the configured fraction of actual XP, scenario " + phase);
            int stacks = 0;
            for (int i = 0; i < body.getSizeInventory(); i++) {
                ItemStack stack = body.getStackInSlot(i);
                if (stack == null) continue;
                stacks++;
                if (stack.isItemStackDamageable()) check(
                    stack.getItemDamage() == (disabled ? 0 : stack.getMaxDamage() / 4),
                    "Corpse tool and armor durability follows the configured penalty, scenario " + phase);
                if (stack.getItem() == Items.diamond_sword) check(
                    "kept".equals(
                        stack.getTagCompound()
                            .getString("PenaltyQA")),
                    "Penalty preserves unrelated item NBT");
            }
            check(
                stacks == (phase == 170 ? 0 : 3),
                "Item loss applies on real death and is completely bypassed when disabled, scenario " + phase);
            check(p.getHealth() <= 0, "Penalty checks use an actual dead player");
        }
        if (phase == 300) {
            for (Map.Entry<Property, String> entry : saved.entrySet()) entry.getKey()
                .set(entry.getValue());
            CorpseConfig.sync();
            return true;
        }
        return false;
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("PENALTYQA {}", text);
    }
}
