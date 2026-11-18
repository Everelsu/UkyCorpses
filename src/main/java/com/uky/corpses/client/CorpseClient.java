package com.uky.corpses.client;

import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.TileCorpse;
import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;

public final class CorpseClient {

    /**
     * This player's last body {x, y, z, dim} and when it arrived (System.currentTimeMillis). Public API: ukyui's death
     * screen reads both through reflection, so keep the names.
     */
    public static volatile int[] lastCorpse;
    public static volatile long lastCorpseAt;

    /** The local player's last death as the server told it (MsgDeath); null before the first. */
    public static volatile Death lastDeath;
    /** The local player's last body and what's in it (MsgCorpse); null before the first. */
    public static volatile Body lastBody;

    /** What killed them (a chat component as JSON; why: "bleed"/"surrender" after lying downed, else empty). */
    public static final class Death {

        public final String cause, why;
        /** MsgDeath.BODY / KEPT / DROPPED. */
        public final int kind;
        /** System.currentTimeMillis when it came. */
        public final long at;

        Death(String cause, String why, int kind) {
            this.cause = cause;
            this.why = why;
            this.kind = kind;
            this.at = System.currentTimeMillis();
        }
    }

    public static final class Body {

        public final int x, y, z, dim, number, items, levels, lost, lock;
        public final long at;

        Body(int x, int y, int z, int dim, int number, int items, int levels, int lost, int lock) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.number = number;
            this.items = items;
            this.levels = levels;
            this.lost = lost;
            this.lock = lock;
            this.at = System.currentTimeMillis();
        }
    }

    /** The one body renderer: the corpse window draws its figure with it. */
    static RenderCorpse renderer;

    public static void init() {
        RenderCorpse body = renderer = new RenderCorpse();
        RenderingRegistry.registerEntityRenderingHandler(EntityCorpse.class, body);
        ClientRegistry.bindTileEntitySpecialRenderer(TileCorpse.class, new RenderCorpseTile(body));
        MinecraftForgeClient.registerItemRenderer(UkyCorpses.tape, new RenderTape());
        FMLCommonHandler.instance()
            .bus()
            .register(new ConfigGui.Changes());
        com.uky.corpses.medicine.client.MedicineClient.init();
        com.uky.corpses.blood.client.BloodClient.init();
        com.uky.corpses.deathcam.DeathCamClient.init();
        DownedClient downed = new DownedClient(body);
        MinecraftForge.EVENT_BUS.register(downed);
        FMLCommonHandler.instance()
            .bus()
            .register(downed);
        CorpsePick.init();
        CrouchClient.init();
        MinecraftForge.EVENT_BUS.register(new CorpsePick());
    }

    /** Netty thread. */
    public static void onDeath(String cause, String why, int kind) {
        lastDeath = new Death(cause, why, kind);
    }

    /** Netty thread. */
    public static void onCorpse(int x, int y, int z, int dim, int number, int items, int levels, int lost, int lock) {
        lastBody = new Body(x, y, z, dim, number, items, levels, lost, lock);
        lastCorpse = new int[] { x, y, z, dim };
        lastCorpseAt = System.currentTimeMillis();
    }
}
