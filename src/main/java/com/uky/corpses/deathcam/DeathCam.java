package com.uky.corpses.deathcam;

import net.minecraftforge.common.config.Configuration;

/** Death camera. Common side holds only config; everything else is in DeathCamClient. */
public final class DeathCam {

    /** Камера смерти вместо мгновенного экрана «Вы умерли». */
    public static boolean enabled = false;
    /** Цитата в верхней полосе. */
    public static boolean quotes = true;
    /**
     * Back to life on a click (or by itself after `seconds`); off: the vanilla death screen instead. Not in hardcore.
     */
    public static boolean autoRespawn = false;
    /** Seconds after the dying is over (the body still, the heart stopped) until it happens by itself; 0: never. */
    public static int seconds = 3;
    /** Look around with the mouse while lying there. */
    public static boolean lookAround = true;
    /** The world's sounds go dull and quiet. */
    public static boolean muffle = true;
    /** Sight blurs and greys a little (a vanilla post shader). */
    public static boolean blur = true;

    public static void config(Configuration c) {
        enabled = autoRespawn = quotes = muffle = blur = false;
        seconds = 0;
        c.get("deathcam", "enabled", false)
            .set(false);
        c.get("deathcam", "clickToRespawn", false)
            .set(false);
        c.get("deathcam", "autoRespawnSeconds", 0)
            .set(0);
    }

    public static void preInit() {}
}
