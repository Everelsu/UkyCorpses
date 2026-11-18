package com.uky.corpses.stains.client;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;

import com.uky.corpses.stains.SurfaceBloodClientConfig;

/** SurfaceBlood surface families mapped to the block materials available in 1.7.10. */
public enum Surface {

    PLAIN,
    SOAK,
    SLICK,
    SNOW,
    CLOTH,
    WOOD;

    public static Surface of(Block b) {
        if (!SurfaceBloodClientConfig.SURFACES.get()) return PLAIN;
        Material m = b.getMaterial();
        if (m == Material.snow || m == Material.craftedSnow) return SNOW;
        if (m == Material.ice || m == Material.packedIce || m == Material.glass) return SLICK;
        if (m == Material.cloth || b == Blocks.hay_block || b == Blocks.sponge) return CLOTH;
        if (m == Material.ground || m == Material.grass || m == Material.sand || m == Material.clay) return SOAK;
        return m == Material.wood ? WOOD : PLAIN;
    }

    public boolean soaks() {
        return this == SOAK || this == CLOTH;
    }

    public boolean through() {
        return this == CLOTH || this == WOOD;
    }

    public float spread() {
        switch (this) {
            case SOAK:
            case CLOTH:
                return .85F;
            case SLICK:
                return .75F;
            case SNOW:
                return 1.25F;
            case WOOD:
                return .95F;
            default:
                return 1;
        }
    }

    public int color(int rgb) {
        return this == SNOW ? Stain.blend(rgb, 15914196, .5F)
            : this == SOAK || this == CLOTH ? Stain.blend(rgb, 2755592, .22F)
                : this == WOOD ? Stain.blend(rgb, 2755592, .12F) : rgb;
    }

    public int dried(int rgb) {
        return this == SNOW ? Stain.blend(rgb, 13214624, .45F) : rgb;
    }

    public float pool() {
        switch (this) {
            case SOAK:
            case CLOTH:
                return .6F;
            case SLICK:
                return 1.25F;
            case SNOW:
                return 1.1F;
            case WOOD:
                return .8F;
            default:
                return 1;
        }
    }
}
