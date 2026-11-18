package com.uky.corpses.stains.client;

import java.util.Random;

import net.minecraftforge.common.util.ForgeDirection;

import com.uky.corpses.stains.SurfaceBloodClientConfig;
import com.uky.corpses.stains.client.decal.StainSprites;

public final class StainPlacement {

    public static Stain impact(ForgeDirection face, double x, double y, double z, float vx, float vy, float vz,
        float size, int color, int dried, Surface surface, long now, Random random) {
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed < .0001F) return null;
        float normal = Math.abs(vx * face.offsetX + vy * face.offsetY + vz * face.offsetZ);
        float elongation = Math.max(1, Math.min(5, speed / Math.max(normal, speed * .2F)));
        float ts = face.offsetX != 0 ? vz : vx, tt = face.offsetY != 0 ? vz : vy;
        float travel = (float) Math.atan2(tt, ts);
        float diameter = size * (1.3F + 2.2F * (float) Math.sqrt(Math.min(speed, 1.5F))) * surface.spread();
        boolean crown = speed > .35F && size > .02F && surface != Surface.SLICK, wall = face.offsetY == 0;
        boolean pixel = SurfaceBloodClientConfig.STYLE.get() == StainSprites.Style.PIXEL;
        float angle, scaleU, scaleV, anchorU, anchorV;
        String family;
        StainSprites.Sprite sprite;
        if (pixel) {
            int texels = Math.max(1, Math.round(diameter * 16 * 1.25F));
            if (elongation < (wall ? 2.4F : 1.6F)) {
                family = crown && texels >= 4 ? "splat" : "drop";
                sprite = StainSprites.nearest(family, texels, random);
                angle = random.nextInt(4) * (float) (Math.PI / 2);
                anchorU = sprite.texelWidth / 2;
                anchorV = sprite.texelHeight / 2;
            } else {
                int octant = Math.floorMod(Math.round(travel / (float) (Math.PI / 4)), 8);
                boolean diagonal = (octant & 1) == 1;
                family = diagonal ? "streak_diag" : "streak";
                sprite = StainSprites.nearestStreak(family, texels, texels * elongation, random);
                angle = (diagonal ? octant - 1 : octant) * (float) (Math.PI / 4);
                anchorU = sprite.ax;
                anchorV = sprite.ay;
            }
            scaleU = scaleV = .0625F;
        } else if (elongation < (wall ? 2.2F : 1.4F)) {
            family = crown ? "splat" : "drop";
            sprite = StainSprites.choose(family, random);
            scaleU = scaleV = diameter / sprite.size;
            angle = random.nextFloat() * (float) (Math.PI * 2);
            anchorU = sprite.texelWidth / 2;
            anchorV = sprite.texelHeight / 2;
        } else {
            family = "streak";
            sprite = StainSprites.nearestRatio(family, elongation, random);
            scaleV = diameter / sprite.size;
            scaleU = diameter * elongation / Math.max(sprite.length, 1);
            angle = travel;
            anchorU = sprite.ax;
            anchorV = sprite.ay;
        }
        Stain stain = place(face, x, y, z, angle, sprite, scaleU, scaleV, anchorU, anchorV, pixel);
        float tone = .86F + random.nextFloat() * .24F;
        stain.color = shade(surface.color(color), tone);
        stain.dried = shade(surface.dried(dried), tone);
        stain.opacity = size > .02F ? .95F : .82F;
        stain.born = now;
        stain.family = family;
        stain.surface = surface;
        boolean slick = surface == Surface.SLICK;
        stain.ran = !(wall && !surface.soaks()
            && size >= (slick ? .02F : .035F)
            && SurfaceBloodClientConfig.RUNS.get()
            && random.nextFloat() < (slick ? 1 : .7F));
        return stain;
    }

    static Stain place(ForgeDirection face, double x, double y, double z, float angle, StainSprites.Sprite sprite,
        float scaleU, float scaleV, float anchorU, float anchorV, boolean pixel) {
        float co = (float) Math.cos(angle), si = (float) Math.sin(angle);
        float ou = (anchorU - sprite.texelWidth / 2) * scaleU, ov = (anchorV - sprite.texelHeight / 2) * scaleV;
        double s = face.offsetX != 0 ? z : x, t = face.offsetY != 0 ? z : y;
        s -= co * ou - si * ov;
        t -= si * ou + co * ov;
        if (pixel) {
            float extentS = Math.abs(si) > .5F ? sprite.texelHeight : sprite.texelWidth;
            float extentT = Math.abs(si) > .5F ? sprite.texelWidth : sprite.texelHeight;
            s = (Math.round(s * 16 - extentS / 2) + extentS / 2) / 16;
            t = (Math.round(t * 16 - extentT / 2) + extentT / 2) / 16;
        }
        Stain stain = new Stain();
        stain.face = face;
        stain.x = face.offsetX != 0 ? x : s;
        stain.y = face.offsetY != 0 ? y : t;
        stain.z = face.offsetX != 0 ? s : face.offsetY != 0 ? t : z;
        stain.angle = angle;
        stain.length = sprite.texelWidth * scaleU / 2;
        stain.width = sprite.texelHeight * scaleV / 2;
        stain.sprite = sprite;
        return stain;
    }

    private static int shade(int rgb, float tone) {
        return Math.min(255, (int) ((rgb >> 16 & 255) * tone)) << 16
            | Math.min(255, (int) ((rgb >> 8 & 255) * tone)) << 8
            | Math.min(255, (int) ((rgb & 255) * tone));
    }

    private StainPlacement() {}
}
