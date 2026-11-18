package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class GearPaintCheck {

    public static void run() {
        assert !GearBlood.canCoat(new net.minecraft.item.ItemStack(new net.minecraft.item.ItemBow()));
        assert GearBlood.canCoat(
            new net.minecraft.item.ItemStack(
                new net.minecraft.item.ItemSword(net.minecraft.item.Item.ToolMaterial.EMERALD)));

        assert GearBlood.fade(300 * 20L, 300, 30) == 0;
        assert GearBlood.fade(315 * 20L, 300, 30) == .5F;
        assert GearBlood.fade(330 * 20L, 300, 30) == 1;
        BufferedImage source = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) source.setRGB(x, y, 0xffb0b0b0);
        source.setRGB(0, 0, 0);
        int[] before = pixels(source);
        BufferedImage wet = GearPaint.flat(source, 123, 0xa70000, 0, 1, 0, 1, 0);
        BufferedImage same = GearPaint.flat(source, 123, 0xa70000, 0, 1, 0, 1, 0);
        BufferedImage dry = GearPaint.flat(source, 123, 0xa70000, 1, 1, 0, 1, 0);
        BufferedImage partial = GearPaint.flat(source, 123, 0xa70000, 0, .1F, 0, 1, 0);
        assert Arrays.equals(before, pixels(source)) : "Weapon painting changed the resource image";
        assert Arrays.equals(pixels(wet), pixels(same)) : "Seeded coating changed between renders";
        assert !Arrays.equals(pixels(wet), pixels(dry)) : "Original wet highlights did not dry";
        assert wet.getRGB(0, 0) == 0 : "Blood painted an empty sprite pixel";
        assert count(wet) > count(partial) : "Coverage did not extend the coating";
        assert (wet.getRGB(15, 0) >>> 24) != 0
            && wet.getRGB(0, 15) == 0 : "Coating no longer grows from the radial weapon tip";
        System.out.println(
            "SurfaceBlood gear: original seeded radial coating, transparency, drying and source isolation passed");
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, 16, 16, null, 0, 16);
    }

    private static int count(BufferedImage image) {
        int count = 0;
        for (int pixel : pixels(image)) if ((pixel >>> 24) != 0) count++;
        return count;
    }
}
