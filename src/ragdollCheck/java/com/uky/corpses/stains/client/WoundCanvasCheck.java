package com.uky.corpses.stains.client;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;

import com.uky.corpses.stains.DamageCategory;

public final class WoundCanvasCheck {

    public static void run() {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, 0xffffffff);
        image.setRGB(32, 16, 0);
        Set<Integer> patterns = new HashSet<>();
        for (DamageCategory category : DamageCategory.values()) if (category != DamageCategory.NONE) {
            WoundCanvas canvas = new WoundCanvas(64, 32);
            canvas.wound(
                new WoundMask(image),
                (x, y, upright) -> x >= 20 && x < 44 && y >= 8 && y < 28 ? WoundCanvas.spot(x, y, 0) : -1,
                32,
                16,
                3,
                1,
                8,
                category,
                false,
                1,
                0,
                false,
                new WoundCanvas.Pigment(0x8e1111, 0x4e140e, true),
                100,
                new Random(712));
            int[] fresh = canvas.pixels(100, 200, 0), aged = canvas.pixels(400, 200, 0);
            long settled = canvas.settledAt(200);
            assert settled >= 300 && Arrays.equals(
                canvas.pixels(settled, 200, 0),
                canvas.pixels(settled + 200, 200, 0)) : "A settled wound can share a texture across ticks";
            assert canvas.count > 0 : "empty original wound " + category;
            assert fresh[16 * 64 + 32] == 0 : "wound painted transparent skin";
            assert Arrays.stream(canvas.pixels(400, 200, 1))
                .allMatch(pixel -> pixel == 0) : "washed wound survived";
            assert !Arrays.equals(fresh, aged) : "drying and delayed drips did not change wound";
            patterns.add(Arrays.hashCode(aged));
        }
        assert patterns.size() >= 4 : "damage categories collapsed to the same wound";
        TexturedQuad face = new TexturedQuad(
            new PositionTextureVertex[] { new PositionTextureVertex(0, 0, 0, .2F, .3F),
                new PositionTextureVertex(4, 0, 0, .6F, .3F), new PositionTextureVertex(4, 4, 0, .6F, .7F),
                new PositionTextureVertex(0, 4, 0, .2F, .7F) });
        float[] uv = ModelWounds.project(face, 2, 2, 1);
        assert Math.abs(uv[0] - .4F) < .00001 && Math.abs(uv[1] - .5F) < .00001 && Math.abs(uv[6] - 1) < .00001;
        System.out.println(
            "SurfaceBlood: original wound categories, alpha masks, delayed runoff, washing/drying and captured model UVs passed");
    }

    private WoundCanvasCheck() {}
}
