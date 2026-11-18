package com.uky.corpses;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.uky.corpses.client.GuiTape;

/** The tape the body's recorder leaves: who, how, where, when, and what was inside. Read-only, right-click to play. */
public class ItemTape extends Item {

    public ItemTape() {
        setUnlocalizedName(UkyCorpses.MODID + ".tape");
        setTextureName(UkyCorpses.MODID + ":tape");
        setMaxStackSize(1);
    }

    static ItemStack create(EntityCorpse body, int dim, int number, IChatComponent cause, List<ItemStack> kept,
        List<ItemStack> lost) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("owner", body.owner());
        t.setInteger("x", MathHelper.floor_double(body.posX));
        t.setInteger("y", MathHelper.floor_double(body.posY));
        t.setInteger("z", MathHelper.floor_double(body.posZ));
        t.setInteger("dim", dim);
        t.setInteger("n", number);
        t.setString("cause", IChatComponent.Serializer.func_150696_a(cause));
        t.setLong("created", body.created);
        t.setLong("lock", body.lockTicks);
        t.setLong("date", System.currentTimeMillis());
        t.setInteger("xp", body.xp);
        t.setTag("items", icons(kept));
        t.setTag("lost", icons(lost));
        ItemStack s = new ItemStack(UkyCorpses.tape);
        s.setTagCompound(t);
        return s;
    }

    /** Just enough to draw the icon: NBT dropped so a backpack full of stuff doesn't bloat the tape. */
    private static NBTTagList icons(List<ItemStack> stacks) {
        NBTTagList list = new NBTTagList();
        for (ItemStack s : stacks) {
            ItemStack c = s.copy();
            c.setTagCompound(null);
            list.appendTag(c.writeToNBT(new NBTTagCompound()));
        }
        return list;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote && stack.hasTagCompound()) GuiTape.open(stack.getTagCompound());
        return stack;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        NBTTagCompound t = stack.getTagCompound();
        if (t == null) return;
        lines.add(
            StatCollector.translateToLocalFormatted("ukycorpses.tape.tip", t.getInteger("n"), t.getString("owner")));
        lines.add("§7" + t.getInteger("x") + " " + t.getInteger("y") + " " + t.getInteger("z"));
    }
}
