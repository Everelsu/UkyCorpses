package com.uky.corpses.core;

import net.minecraft.entity.player.EntityPlayer;

import com.uky.corpses.Reactions;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Dragging movement restrictions shared by client and server. */
public final class HookEvents {

    @SubscribeEvent
    public void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (event.entityLiving instanceof EntityPlayer && Reactions.isDragging((EntityPlayer) event.entityLiving))
            event.entityLiving.motionY *= .45;
    }

}
