package com.uky.corpses;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.uky.corpses.client.GuiCorpse;

import cpw.mods.fml.common.network.IGuiHandler;

/** The body's chest. Gui 0: an entity body, x is its id. Gui 1: a block body at x y z. */
public class CorpseGui implements IGuiHandler {

    private static EntityCorpse body(int id, World w, int x, int y, int z) {
        if (id == 1) {
            TileCorpse t = BlockCorpse.master(w, x, y, z);
            return t == null ? null : t.body;
        }
        Entity e = w.getEntityByID(x);
        return e instanceof EntityCorpse ? (EntityCorpse) e : null;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer p, World w, int x, int y, int z) {
        EntityCorpse b = body(id, w, x, y, z);
        return b == null ? null : new ContainerCorpse(p.inventory, b);
    }

    // the server never calls this, so client classes are never loaded there
    @Override
    public Object getClientGuiElement(int id, EntityPlayer p, World w, int x, int y, int z) {
        EntityCorpse b = body(id, w, x, y, z);
        if (b == null) return null;
        String title = StatCollector.translateToLocal("entity.ukycorpses.corpse.name") + " " + b.owner();
        return new GuiCorpse(p.inventory, new InventoryBasic(title, true, ContainerCorpse.BODY_SLOTS), b);
    }
}
