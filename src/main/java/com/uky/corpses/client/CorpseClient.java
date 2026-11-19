package com.uky.corpses.client;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.TileCorpse;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.medicine.Medicine;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class CorpseClient {

    public static final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

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
    public static RenderCorpse renderer;

    private static void registerNeiOrder() {
        if (!cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) return;
        Comparator<ItemStack> order = Comparator.comparingInt(stack -> {
            int index = Medicine.creativeOrder(stack.getItem());
            return index < 0 ? Integer.MAX_VALUE : index;
        });
        try {
            Class.forName("codechicken.nei.api.API")
                .getMethod("addSortOption", String.class, Comparator.class)
                .invoke(null, "ukycorpses.nei.itemsort.creative", order);
        } catch (ReflectiveOperationException | LinkageError failure) {
            UkyCorpses.LOG.warn("Could not register the creative order in NEI", failure);
        }
    }

    public static void init() {
        registerNeiOrder();
        RenderCorpse body = renderer = new RenderCorpse();
        RenderingRegistry.registerEntityRenderingHandler(EntityCorpse.class, body);
        ClientRegistry.bindTileEntitySpecialRenderer(TileCorpse.class, new RenderCorpseTile(body));
        MinecraftForgeClient.registerItemRenderer(UkyCorpses.tape, new RenderTape());
        FMLCommonHandler.instance()
            .bus()
            .register(new ConfigGui.Changes());
        com.uky.corpses.medicine.client.MedicineClient.init();
        com.uky.corpses.blood.client.BloodClient.init();
        DownedClient downed = new DownedClient(body);
        MinecraftForge.EVENT_BUS.register(downed);
        FMLCommonHandler.instance()
            .bus()
            .register(downed);
        CorpsePick.init();
        CrouchClient.init();
        ReactionClient.init();
        ReviveClient.init();
        com.uky.corpses.stains.SurfaceBlood.initClient();
        com.uky.corpses.amp.AmpClient.init();
        com.uky.corpses.mobs.client.RenderMobRagdoll.init();
        MinecraftForge.EVENT_BUS.register(new CorpsePick());
        ClientTicks ticks = new ClientTicks();
        MinecraftForge.EVENT_BUS.register(ticks);
        FMLCommonHandler.instance()
            .bus()
            .register(ticks);
    }

    public static final class ClientTicks {

        private World world;

        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            World current = Minecraft.getMinecraft().theWorld;
            if (world != null && world != current) tasks.clear();
            world = current;
            if (current == null) tasks.clear();
            else for (Runnable task; (task = tasks.poll()) != null;) task.run();
        }

        @SubscribeEvent
        public void unload(WorldEvent.Unload event) {
            if (!event.world.isRemote) return;
            tasks.clear();
            world = null;
        }
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
