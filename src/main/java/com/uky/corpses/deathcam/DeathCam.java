package com.uky.corpses.deathcam;

import net.minecraftforge.common.config.Configuration;

import com.uky.corpses.CorpseConfig;

/** Death camera. Common side holds only config; everything else is in DeathCamClient. */
public final class DeathCam {

    /** Камера смерти вместо мгновенного экрана «Вы умерли». */
    public static boolean enabled = true;
    /** Цитата в верхней полосе. */
    public static boolean quotes = true;
    /**
     * Back to life on a click (or by itself after `seconds`); off: the vanilla death screen instead. Not in hardcore.
     */
    public static boolean autoRespawn = true;
    /** Seconds after the dying is over (the body still, the heart stopped) until it happens by itself; 0: never. */
    public static int seconds = 3;
    /** Look around with the mouse while lying there. */
    public static boolean lookAround = true;
    /** The world's sounds go dull and quiet. */
    public static boolean muffle = true;
    /** Sight blurs and greys a little (a vanilla post shader). */
    public static boolean blur = true;

    public static void config(Configuration c) {
        CorpseConfig.Section s = CorpseConfig
            .section("deathcam", "Камера смерти: остаёшься в своём теле, видишь причину и где тело.");
        enabled = s.on("enabled", true, "false — сразу экран возрождения.");
        autoRespawn = s
            .on("clickToRespawn", true, "ЛКМ — сразу возродиться, без экрана «Вы умерли». В хардкоре — обычный экран.");
        seconds = s.num(
            "autoRespawnSeconds",
            3,
            0,
            600,
            "Через сколько секунд после затишья возродиться самому. 0 — только по клику.");
        lookAround = s.on("lookAround", true, "Осматриваться мышью.");
        quotes = s.on("quotes", true, "Цитата в верхней полосе.");
        muffle = s.on("muffle", true, "Звуки мира глохнут.");
        blur = s.on("blur", true, "Размытие и обесцвечивание. Выключи, если шейдерпак с ним не дружит.");
    }

    public static void preInit() {}
}
