package com.uky.corpses;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Body-wide rules: on/off, despawn. The commands are in Commands. */
public final class Corpses {

    /** false: deaths leave the vanilla drops, no body. Check before building one. */
    public static boolean enabled = true;
    /** 0: never. */
    public static long despawnTicks;
    public static boolean dropOnDespawn = true;
    /** An emptied body stays until broken apart. */
    public static boolean keepEmpty = true;
    /** Bodies lie as blocks (BlockCorpse) instead of entities, wherever the grid finds them room. */
    public static boolean asBlock = false;

    private static Configuration cfg;
    private static Property enabledProp;

    public static void config(Configuration c) {
        cfg = c;
        CorpseConfig.Section s = CorpseConfig.section("corpse", "Тела погибших игроков: что остаётся после смерти.");
        enabledProp = s.prop(
            "enabled",
            true,
            "Главный выключатель тел. false — смерть как в ванили, вещи выпадают на землю. Также /corpses on|off.");
        enabled = enabledProp.getBoolean();
        asBlock = false;
        c.get("corpse", "asBlock", false)
            .set(false);
        despawnTicks = 20L
            * s.num("despawnSeconds", 0, 0, 100000000, "Через сколько секунд тело исчезает. 0 — никогда.");
        dropOnDespawn = s
            .on("dropItemsOnDespawn", true, "Тело исчезло — вещи и опыт падают на землю. false — пропадают.");
        keepEmpty = s
            .on("keepEmpty", true, "Пустое тело лежит, пока его не разобьют. false — исчезает, когда его закрыли.");
    }

    public static void init() {}

    public static void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new Commands());
    }

    /** On or off from the chat (/corpses on|off), kept in the file. */
    static void setEnabled(boolean on) {
        enabled = on;
        enabledProp.set(on);
        if (cfg.hasChanged()) cfg.save();
    }

    /** Every loaded body, as an entity or as blocks, in every world; a copy, safe to break them while going through. */
    public static List<EntityCorpse> loaded() {
        List<EntityCorpse> all = new ArrayList<>();
        for (WorldServer w : MinecraftServer.getServer().worldServers) if (w != null) {
            for (Object o : w.loadedEntityList)
                if (o instanceof EntityCorpse && !((EntityCorpse) o).isDead) all.add((EntityCorpse) o);
            for (Object o : w.loadedTileEntityList) if (o instanceof TileCorpse) {
                EntityCorpse b = ((TileCorpse) o).body;
                if (b != null && !b.isDead) all.add(b);
            }
        }
        return all;
    }
}
