package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;

public final class WoundImageCheck {

    public static void run() {
        BufferedImage skin = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(0, 0, 0x7f123456);
        skin.setRGB(1, 1, 0xffabcdef);
        BufferedImage first = SkinBlood.copyImage(skin);
        assert first != skin && first.getRGB(0, 0) == 0x7f123456 && first.getRGB(1, 1) == 0xffabcdef;
        first.setRGB(0, 0, 0xffff0000);
        assert skin.getRGB(0, 0) == 0x7f123456 : "wound painting mutated the cached base skin";
        BufferedImage second = SkinBlood.copyImage(skin);
        assert second.getRGB(0, 0) == 0x7f123456 : "new wound revision inherited old paint";
        System.out.println("SurfaceBlood wound images: base skin and alpha survive independent wound revisions");
    }

    private WoundImageCheck() {}
}
