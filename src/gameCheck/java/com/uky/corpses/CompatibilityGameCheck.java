package com.uky.corpses;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.uky.corpses.stains.client.GearBlood;

import cpw.mods.fml.common.Loader;

public final class CompatibilityGameCheck {

    public static void setup(EntityPlayerMP player) throws Exception {
        com.uky.corpses.pc.LegacyStoreGameCheck.run(player);
        java.util.Map<?, ?> commands = net.minecraft.server.MinecraftServer.getServer()
            .getCommandManager()
            .getCommands();
        Object command = commands.get(UkyCorpses.COMMAND);
        check(
            command instanceof Commands && !commands.containsKey("corpses") && commands.get("ukycorpse") == command,
            "Canonical /ukycorpses is registered and /corpses is absent");
        try {
            ((Commands) command).processCommand(player, new String[] { "retrieve", "invalid-uuid" });
            throw new AssertionError("retrieve was not routed");
        } catch (net.minecraft.command.WrongUsageException expected) {
            check(
                expected.getMessage()
                    .contains("UUID"),
                "Unified retrieve subcommand reaches the existing recovery handler");
        }

        java.lang.reflect.Method wear = UkyCorpses.class.getDeclaredMethod("wear", ItemStack.class);
        wear.setAccessible(true);
        int saved = CorpseConfig.durabilityLossPercent;
        ItemStack tool = new ItemStack(net.minecraft.init.Items.diamond_pickaxe);
        try {
            CorpseConfig.durabilityLossPercent = 10;
            wear.invoke(null, tool);
            check(tool.getItemDamage() == tool.getMaxDamage() / 10, "Death penalty applies configured tool wear");
            CorpseConfig.durabilityLossPercent = 0;
            int before = tool.getItemDamage();
            wear.invoke(null, tool);
            check(tool.getItemDamage() == before, "Disabled durability penalty preserves the item");
            float bar = player.experience;
            int level = player.experienceLevel;
            player.experienceLevel = 20;
            player.experience = .5F;
            check(
                UkyCorpses.totalXp(player) > 100,
                "Death XP accounting includes the full level total beyond vanilla drop cap");
            player.experienceLevel = level;
            player.experience = bar;
        } finally {
            CorpseConfig.durabilityLossPercent = saved;
        }
        net.minecraftforge.common.config.Property toggle = CorpseConfig.config()
            .getCategory("penalty")
            .get("enabled");
        boolean enabled = toggle.getBoolean();
        try {
            toggle.set(false);
            CorpseConfig.sync();
            check(
                CorpseConfig.keepXpPercent == 100 && CorpseConfig.durabilityLossPercent == 0
                    && CorpseConfig.itemLossPercent == 0,
                "penalty.enabled=false disables XP loss, durability wear and item deletion together");
        } finally {
            toggle.set(enabled);
            CorpseConfig.sync();
        }
        if (CorpseConfig.penaltyEnabled) {
            net.minecraftforge.common.config.ConfigCategory selected = CorpseConfig.config()
                .getCategory("penalty." + CorpseConfig.mode);
            check(
                CorpseConfig.keepXpPercent == selected.get("keepXpPercent")
                    .getInt() && CorpseConfig.durabilityLossPercent
                        == selected.get("durabilityLossPercent")
                            .getInt()
                    && CorpseConfig.itemLossPercent == selected.get("itemLossPercent")
                        .getInt(),
                "Full config sync preserves all three enabled death penalties");
        }
        if (!Loader.isModLoaded("hbm")) return;
        Item gun = (Item) Class.forName("com.hbm.items.ModItems")
            .getField("gun_pepperbox")
            .get(null);
        check(!GearBlood.canCoat(new ItemStack(gun)), "Actual HBM ranged weapon is excluded from blood coating");
        Item item = (Item) Class.forName("com.hbm.items.ModItems")
            .getField("hev_plate")
            .get(null);
        ItemStack armor = new ItemStack(item);
        armor.setTagCompound(new NBTTagCompound());
        armor.getTagCompound()
            .setInteger("UkyCompatMarker", 5853);
        EntityCorpse corpse = new EntityCorpse(player.worldObj);
        corpse.fill(java.util.Collections.singletonList(new EntityCorpse.Entry(EntityCorpse.ARMOR + 2, armor.copy())));
        NBTTagCompound tag = new NBTTagCompound();
        corpse.writeEntityToNBT(tag);
        EntityCorpse restored = new EntityCorpse(player.worldObj);
        restored.readEntityFromNBT(tag);
        check(
            ItemStack.areItemStacksEqual(armor, restored.armor(2)),
            "Actual HBM HEV armor retains item, metadata and complete NBT through corpse save/load");
        player.inventory.armorInventory[2] = armor;
        player.inventoryContainer.detectAndSendChanges();
    }

    public static void recovered(EntityCorpse corpse) {
        if (!Loader.isModLoaded("hbm")) return;
        ItemStack stack = corpse.armor(2);
        check(
            stack != null && Item.itemRegistry.getNameForObject(stack.getItem())
                .toString()
                .toLowerCase(java.util.Locale.ROOT)
                .contains("hev")
                && stack.hasTagCompound()
                && stack.getTagCompound()
                    .getInteger("UkyCompatMarker") == 5853,
            "Real player death retains the HBM armor identity and custom NBT in the corpse");
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        UkyCorpses.LOG.info("COMPATQA {}", text);
    }

    private CompatibilityGameCheck() {}
}
