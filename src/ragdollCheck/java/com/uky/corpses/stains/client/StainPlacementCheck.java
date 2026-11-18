package com.uky.corpses.stains.client;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import net.minecraftforge.common.util.ForgeDirection;

import com.uky.corpses.stains.client.decal.StainSprites;

public final class StainPlacementCheck {

    public static void run() {
        try (InputStreamReader json = new InputStreamReader(
            StainPlacementCheck.class.getResourceAsStream("/assets/ukycorpses_blood/textures/stain/pixel.json"),
            StandardCharsets.UTF_8)) {
            StainSprites.read(StainSprites.Style.PIXEL, json);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
        StainSprites.Sprite streak = StainSprites.nearest("streak", 3, new Random(7));
        assert streak.size == 3 && streak.texelWidth > streak.size
            && streak.ax != streak.texelWidth / 2 : "original width/length/anchor metadata lost";
        for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            Stain stain = StainPlacement.impact(
                face,
                2,
                -3,
                -5,
                face.offsetX * .5F,
                face.offsetY * .5F,
                face.offsetZ * .5F,
                .04F,
                0x8e1111,
                0x4e140e,
                Surface.PLAIN,
                100,
                new Random(41));
            assert stain != null;
            assert face.offsetX == 0 || stain.x == 2 : "pixel snapping moved wall normal X";
            assert face.offsetY == 0 || stain.y == -3 : "pixel snapping moved floor normal Y";
            assert face.offsetZ == 0 || stain.z == -5 : "pixel snapping moved wall normal Z";
            double turns = stain.angle / (Math.PI / 2);
            assert Math.abs(turns - Math.rint(turns)) < .00001 : "pixel stain rotated between texels";
            assert Math.abs(stain.length * 32 - stain.sprite.texelWidth) < .00001
                && Math.abs(stain.width * 32 - stain.sprite.texelHeight)
                    < .00001 : "pixel stain stretched off source texel density";
        }
        Stain glancing = StainPlacement.impact(
            ForgeDirection.NORTH,
            0,
            2,
            -5,
            .5F,
            -.5F,
            .02F,
            .04F,
            0x8e1111,
            0x4e140e,
            Surface.PLAIN,
            100,
            new Random(21));
        assert glancing.family.equals("streak_diag") && glancing.z == -5;
        Stain spread = new Stain();
        spread.spread(0, 0, 100);
        spread.spread(1, -50, 100);
        assert spread.level(125) == .5F && spread.level(150) == 1;
        spread.spread(0, 50, 150);
        assert spread.level(175) == .25F;
        PoolSpots pools = new PoolSpots();
        Stain drop = StainPlacement.impact(
            ForgeDirection.UP,
            4,
            65,
            4,
            0,
            -.2F,
            0,
            .03F,
            0x8e1111,
            0x4e140e,
            Surface.PLAIN,
            100,
            new Random(1));
        assert !pools.catchDrop(drop, .03F, false, 100, new Random(1)) : "ordinary spray instantly became a pool";
        assert !pools.catchDrop(drop, .03F, true, 101, new Random(1));
        assert !pools.catchDrop(drop, .03F, true, 102, new Random(1));
        assert pools.catchDrop(drop, .03F, true, 103, new Random(1)) : "three nearby drips did not gather";
        SurfaceBloodClient.stains.clear();
        pools.clear();
        StainSprites.clear();
        System.out.println(
            "SurfaceBlood: original sprite metrics, all six preserved planes, pixel density/angles, diagonal streaks, spread and 64-slot drip gathering passed");
    }

    private StainPlacementCheck() {}
}
