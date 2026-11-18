package com.uky.corpses.medicine;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/** Toxilin + any food = the same food, marked. Eating it poisons (Medicine.onLacedEaten). */
public final class LacedRecipe implements IRecipe {

    static final String TAG = "UkyLaced";

    /** The food in the grid if it holds exactly one toxilin and one plain food, else null. */
    private static ItemStack food(InventoryCrafting inv) {
        ItemStack food = null;
        boolean pill = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) continue;
            Item it = s.getItem();
            if (it == Medicine.TOXILIN && !pill) pill = true;
            else if (it instanceof ItemFood && food == null
                && !(s.hasTagCompound() && s.getTagCompound()
                    .getBoolean(TAG)))
                food = s;
            else return null;
        }
        return pill ? food : null;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World w) {
        return food(inv) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack f = food(inv);
        if (f == null) return null;
        ItemStack out = f.copy();
        out.stackSize = 1;
        out.setTagInfo(TAG, new net.minecraft.nbt.NBTTagByte((byte) 1));
        return out;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(Items.apple);
    }
}
