package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;

final class WoundMask {

    private final int width, height;
    private final boolean[] solid;

    WoundMask(BufferedImage image) {
        width = image.getWidth();
        height = image.getHeight();
        solid = new boolean[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) solid[y * width + x] = image.getRGB(x, y) >>> 24 >= 128;
    }

    int fine() {
        return 1;
    }

    WoundMask base() {
        return this;
    }

    boolean solid(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && solid[y * width + x];
    }
}
