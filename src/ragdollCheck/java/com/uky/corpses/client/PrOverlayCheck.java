package com.uky.corpses.client;

/** Installed PR final-shut timings and desaturation endpoints, without a GL context. */
public final class PrOverlayCheck {

    public static void run() {
        assert PrOverlay.saturation(0, 1200) == 1;
        assert PrOverlay.saturation(.4F, 1200) == 1;
        assert Math.abs(PrOverlay.saturation(1, 1200) - .12F) < .000001;
        assert PrOverlay.saturation(1, 30) == 0;
        assert Math.abs(PrOverlay.closure(1, 160, 1200, 1000) - .33F) < .000001;
        assert Math.abs(PrOverlay.closure(0, 100, 1200, 1000) - .9F) < .000001;
        assert PrOverlay.closure(0, 30, 1200, 1000) == 1;
        for (int tick = 0; tick <= 1200; tick++) {
            float progress = tick / 1200F, remaining = 1200 - tick;
            float saturation = PrOverlay.saturation(progress, remaining);
            assert Float.isFinite(saturation) && saturation >= 0 && saturation <= 1;
            for (double time : new double[] { tick, tick + .5, tick + 1000000.75 }) {
                float closure = PrOverlay.closure(progress, remaining, 1200, time);
                assert Float.isFinite(closure) && closure >= 0 && closure <= 1;
                assert closure == PrOverlay.closure(progress, remaining, 1200, time);
            }
        }
        System.out
            .println("Revival overlay: original eyelid timings, deterministic blink and desaturation bounds passed");
    }

    private PrOverlayCheck() {}
}
