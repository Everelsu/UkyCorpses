package com.uky.corpses.client;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.TileCorpse;

/** A block body: the same drawing as an entity body, placed from the tile. */
public class RenderCorpseTile extends TileEntitySpecialRenderer {

    private final RenderCorpse body;

    RenderCorpseTile(RenderCorpse body) {
        this.body = body;
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        EntityCorpse b = ((TileCorpse) te).body;
        if (b == null) return;
        body.doRender(b, x + b.posX - te.xCoord, y + b.posY - te.yCoord, z + b.posZ - te.zCoord, 0, partial);
    }
}
