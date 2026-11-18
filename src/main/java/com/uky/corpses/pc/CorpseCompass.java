package com.uky.corpses.pc;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Original CorpseTarget component is stored in ordinary 1.7 stack NBT. */
public final class CorpseCompass extends Item {

    public CorpseCompass() {
        setUnlocalizedName("ukycorpses_compass.corpse_compass");
        setTextureName("minecraft:compass");
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return StatCollector.translateToLocal("item.ukycorpses_compass.corpse_compass.name");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister atlas) {
        PcClient.register(atlas);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraft.util.IIcon getIconIndex(ItemStack stack) {
        return PcClient.icon(stack, net.minecraft.client.Minecraft.getMinecraft().thePlayer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraft.util.IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraft.util.IIcon getIcon(ItemStack stack, int pass, EntityPlayer player, ItemStack using,
        int remaining) {
        return PcClient.icon(stack, player);
    }

    public static ItemStack create(NBTTagCompound target) {
        ItemStack stack = new ItemStack(PcCorpses.compass);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound copy = (NBTTagCompound) target.copy();
        copy.removeTag("Body");
        tag.setTag("CorpseTarget", copy);
        stack.setTagCompound(tag);
        return stack;
    }

    public static NBTTagCompound target(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("CorpseTarget", 10) ? stack.getTagCompound()
                    .getCompoundTag("CorpseTarget") : null;
    }

    public static UUID id(ItemStack stack) {
        NBTTagCompound t = target(stack);
        return t == null ? null : PcStore.uuid(t, "CorpseId");
    }

    public static boolean valid(NBTTagCompound t) {
        return t != null && PcStore.uuid(t, "CorpseId") != null
            && Double.isFinite(t.getDouble("X"))
            && Double.isFinite(t.getDouble("Y"))
            && Double.isFinite(t.getDouble("Z"))
            && Math.abs(t.getDouble("X")) <= 30000001
            && Math.abs(t.getDouble("Z")) <= 30000001
            && Math.abs(t.getDouble("Y")) <= 4096;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) PcClient.open(stack);
        return stack;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.getTotalWorldTime() % 10 == 0) PcCorpses.refreshCompass(stack, world);
        if (world.getTotalWorldTime() % 10 == 0 && PcCorpses.spent(stack, world)) {
            stack.stackSize = 0;
            if (entity instanceof EntityPlayer) ((EntityPlayer) entity).inventory.markDirty();
        }
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entity) {
        if (entity.worldObj.getTotalWorldTime() % 10 != 0 || !PcCorpses.spent(entity.getEntityItem(), entity.worldObj))
            return false;
        entity.setDead();
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        NBTTagCompound t = target(stack);
        if (!valid(t)) return;
        if (!t.getString("Name")
            .isEmpty())
            lines.add(
                "\u00a77" + StatCollector
                    .translateToLocalFormatted("item.ukycorpses_compass.corpse_compass.owner", t.getString("Name")));
        lines.add(
            "\u00a78" + StatCollector.translateToLocalFormatted(
                "item.ukycorpses_compass.corpse_compass.pos",
                (int) Math.floor(t.getDouble("X")),
                (int) Math.floor(t.getDouble("Y")),
                (int) Math.floor(t.getDouble("Z"))));
    }
}
