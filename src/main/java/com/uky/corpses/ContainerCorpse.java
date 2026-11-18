package com.uky.corpses;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The body's window, laid out like the player it was (as Corpse does it): armor in a row with the empty-slot icons,
 * the spare row next to it, the inventory, the hotbar under it, the rest of the spare slots at the bottom, then your
 * own inventory. The body's 54 boxes (EntityCorpse: 0-35 by player slot, 36-39 armor, 40-53 the rest) are the same as
 * before; only where they're drawn changed.
 */
public class ContainerCorpse extends Container {

    /** Rows of the body's part, y in the window. */
    public static final int TOP = 18, MAIN = 40, HOTBAR = 98, EXTRA = 120, PLAYER = 152, PLAYER_HOTBAR = 210;
    public static final int BODY_SLOTS = 54;

    private final IInventory body;

    public ContainerCorpse(InventoryPlayer player, IInventory body) {
        this.body = body;
        body.openInventory();
        for (int i = 0; i < 4; i++) // helmet first, as the player wears it
            addSlotToContainer(new Armor(body, EntityCorpse.ARMOR_BOX + 3 - i, 8 + i * 18, TOP, i));
        for (int i = 0; i < 5; i++)
            addSlotToContainer(new Slot(body, EntityCorpse.EXTRA_BOX + i, 8 + (4 + i) * 18, TOP));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(body, 9 + r * 9 + c, 8 + c * 18, MAIN + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(body, c, 8 + c * 18, HOTBAR));
        for (int c = 0; c < 9; c++)
            addSlotToContainer(new Slot(body, EntityCorpse.EXTRA_BOX + 5 + c, 8 + c * 18, EXTRA));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++)
            addSlotToContainer(new Slot(player, 9 + r * 9 + c, 8 + c * 18, PLAYER + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(player, c, 8 + c * 18, PLAYER_HOTBAR));
    }

    public IInventory body() {
        return body;
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return body.isUseableByPlayer(p);
    }

    /** Shift-click: from the body into you, from you into the body. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack s = slot.getStack(), copy = s.copy();
        if (index < BODY_SLOTS) {
            if (!mergeItemStack(s, BODY_SLOTS, inventorySlots.size(), true)) return null;
        } else if (!mergeItemStack(s, 4, BODY_SLOTS, false)) return null; // armor row stays as it was worn
        if (s.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }

    @Override
    public void onContainerClosed(EntityPlayer p) {
        super.onContainerClosed(p);
        if (body instanceof EntityCorpse) ((EntityCorpse) body).retrieved(p); // before closing: that may end it
        body.closeInventory();
    }

    /** What was worn: anything may go in, the empty slot shows which piece it was. */
    private static final class Armor extends Slot {

        private final int type;

        Armor(IInventory inv, int index, int x, int y, int type) {
            super(inv, index, x, y);
            this.type = type;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getBackgroundIconIndex() {
            return ItemArmor.func_94602_b(type);
        }
    }
}
