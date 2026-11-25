package com.uky.corpses.movement;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.config.Configuration;

import com.uky.corpses.CorpseConfig;
import com.uky.corpses.Downed;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Sneaking as newer Minecraft has it: a crouching player is 1.5 tall (gets under a slab-and-a-half gap), the eyes come
 * down to 1.27, and with no room to stand they stay crouched until there is. Server half: the box and the eyes it
 * checks suffocation with; the client half (CrouchClient) moves the camera and keeps sneak held where it must.
 */
public final class Crouch {

    public static final float HEIGHT = 1.5F, DROP = 0.35F;
    public static boolean enabled = true;

    public static void config(Configuration c) {
        CorpseConfig.Section s = CorpseConfig.section("movement", "Движение игрока.");
        enabled = s.on("modernSneak", true, "Присед как в новых версиях: высота 1.5 блока, плавная камера.");
    }

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new Crouch());
    }

    public static boolean bypassCollision(EntityPlayer player) {
        return player.capabilities.isFlying || player.noClip;
    }

    /** Crouching: 1.5 tall; standing: 1.8 again (callers check there's room). Never touches a box someone else set. */
    public static void set(EntityPlayer p, boolean crouch) {
        float want = crouch ? HEIGHT : Downed.TALL;
        if (p.height != Downed.TALL && p.height != HEIGHT || p.height == want) return;
        p.height = want;
        p.boundingBox.maxY = p.boundingBox.minY + want;
        // the server's eyes are at their real height (no suffocating under the gap); the client's camera has its own
        if (!p.worldObj.isRemote) p.eyeHeight = crouch ? p.getDefaultEyeHeight() - DROP : p.getDefaultEyeHeight();
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (!enabled || e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.isPlayerSleeping() || Downed.is(p))
            return;
        if (bypassCollision(p)) {
            set(p, false);
            return;
        }
        if (p.isSneaking()) set(p, true);
        else if (p.height == HEIGHT && Downed.room(p)) set(p, false);
    }
}
