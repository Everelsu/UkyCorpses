package com.uky.corpses.compat;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.uky.corpses.CorpsePart;
import com.uky.corpses.EntityCorpse;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaEntityAccessor;
import mcp.mobius.waila.api.IWailaEntityProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/** Owner, contents, age and lock under the crosshair. Data comes from the body's DataWatcher. */
public class WailaCompat implements IWailaEntityProvider {

    public static void register(IWailaRegistrar r) {
        WailaCompat w = new WailaCompat();
        r.registerBodyProvider(w, EntityCorpse.class);
        r.registerOverrideEntityProvider(w, CorpsePart.class); // the crosshair is on a limb: show the body
    }

    @Override
    public List<String> getWailaBody(Entity e, List<String> lines, IWailaEntityAccessor acc, IWailaConfigHandler cfg) {
        if (!(e instanceof EntityCorpse)) return lines;
        EntityCorpse c = (EntityCorpse) e;
        lines.add(StatCollector.translateToLocalFormatted("ukycorpses.waila.owner", c.owner()));
        lines.add(StatCollector.translateToLocalFormatted("ukycorpses.waila.items", c.count(), c.xpShown()));
        int min = c.ageMinutes();
        lines.add(
            StatCollector.translateToLocalFormatted(
                "ukycorpses.waila.ago",
                min < 120 ? StatCollector.translateToLocalFormatted("ukycorpses.waila.min", min)
                    : StatCollector.translateToLocalFormatted("ukycorpses.waila.hours", min / 60)));
        int lock = c.lockMinutes();
        lines.add(
            lock < 0 ? StatCollector.translateToLocal("ukycorpses.waila.ownerOnly")
                : lock > 0 ? StatCollector.translateToLocalFormatted("ukycorpses.waila.locked", lock)
                    : StatCollector.translateToLocal("ukycorpses.waila.open"));
        return lines;
    }

    @Override
    public Entity getWailaOverride(IWailaEntityAccessor acc, IWailaConfigHandler cfg) {
        Entity e = acc.getEntity();
        return e instanceof CorpsePart ? ((CorpsePart) e).body : null;
    }

    @Override
    public List<String> getWailaHead(Entity e, List<String> lines, IWailaEntityAccessor acc, IWailaConfigHandler cfg) {
        return lines;
    }

    @Override
    public List<String> getWailaTail(Entity e, List<String> lines, IWailaEntityAccessor acc, IWailaConfigHandler cfg) {
        return lines;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, Entity e, NBTTagCompound tag, World world) {
        return tag;
    }
}
