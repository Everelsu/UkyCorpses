package com.uky.corpses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.Downed;
import com.uky.corpses.movement.Crouch;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client half of the modern sneak: your box is a crouch's while you sneak, the camera eases down to crouching eyes
 * (through ySize, the step-up smoothing the camera already follows: vanilla's own little sneak dip works the same
 * way), and under something low sneak stays held until you're out. Your model in third person is lifted back by what
 * the camera dropped.
 */
public final class CrouchClient {

    /** ySize is cut to 0.4 in moveEntity before the camera reads it. */
    private static final float YSIZE = 0.4F;
    /** How far down the eyes are now, eased. */
    private float drop, lastDrop;
    private boolean pushed;

    /** Sneak held for you while there's no room to stand. */
    private static final class Held extends MovementInputFromOptions {

        Held(GameSettings s) {
            super(s);
        }

        @Override
        public void updatePlayerMoveState() {
            super.updatePlayerMoveState();
            if (!sneak) { // vanilla slows a sneak down in there, so only for the key it reads: as slow as held
                moveStrafe *= 0.3F;
                moveForward *= 0.3F;
            }
            sneak = true;
            jump = false;
        }
    }

    static void init() {
        CrouchClient c = new CrouchClient();
        MinecraftForge.EVENT_BUS.register(c);
        FMLCommonHandler.instance()
            .bus()
            .register(c);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase != TickEvent.Phase.START || e.player != mc.thePlayer) return;
        EntityPlayerSP p = (EntityPlayerSP) e.player;
        boolean down = Downed.is(p) || p.isPlayerSleeping();
        boolean bypass = Crouch.bypassCollision(p);
        if (down || bypass || !Crouch.enabled) {
            if (p.movementInput instanceof Held) p.movementInput = new MovementInputFromOptions(mc.gameSettings);
            if (!down && p.height == Crouch.HEIGHT && (bypass || Downed.room(p))) Crouch.set(p, false);
            drop = lastDrop = 0;
            return;
        }
        if (p.movementInput.getClass() != MovementInputFromOptions.class && !(p.movementInput instanceof Held)) {
            drop = lastDrop = 0;
            return;
        }
        boolean sneaking = p.movementInput.sneak;
        if (sneaking) Crouch.set(p, true);
        else if (p.height == Crouch.HEIGHT && Downed.room(p)) Crouch.set(p, false);
        boolean stuck = p.height == Crouch.HEIGHT && !Downed.room(p);
        if (stuck != p.movementInput instanceof Held)
            p.movementInput = stuck ? new Held(mc.gameSettings) : new MovementInputFromOptions(mc.gameSettings);
        lastDrop = drop;
        drop += ((p.height == Crouch.HEIGHT ? Crouch.DROP : 0) - drop) * 0.45F;
        if (drop < 0.001F) drop = 0;
        if (drop > 0) p.ySize = Math.max(p.ySize, drop / YSIZE);
    }

    /** Your own model, seen in third person: up by what the camera came down, so the feet stay on the floor. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderPre(RenderPlayerEvent.Pre e) {
        if (e.entityPlayer != Minecraft.getMinecraft().thePlayer || drop <= 0) return;
        // vanilla's own sneak already dips the camera (and the model with it) by 0.08
        float d = lastDrop + (drop - lastDrop) * e.partialRenderTick - 0.08F;
        if (d <= 0) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(0, d, 0);
        pushed = true;
    }

    @SubscribeEvent
    public void onRenderPost(RenderPlayerEvent.Post e) {
        if (!pushed) return;
        pushed = false;
        GL11.glPopMatrix();
    }
}
