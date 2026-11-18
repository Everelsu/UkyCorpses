package com.uky.corpses.client;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.DimensionManager;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Playback of the tape. With UkyUI it is part of its interface and plays like a tape: a camcorder's viewfinder corners,
 * a band rolling down, now and then a tracking error that tears the picture and shakes the title, the running time
 * next to REC, the lines coming in one after another letter by letter (Fancy). Its colours are UkyUI's live ones
 * (Palette). Without UkyUI, a plain vanilla window.
 */
public class GuiTape extends GuiScreen {

    private static final int W = 232, PAD = 10, CELL = 18, COLS = (W - 2 * PAD) / CELL;

    private final NBTTagCompound t;
    private final List<ItemStack> items, lost;
    private final String cause;
    private ItemStack hovered;
    private final long opened = System.nanoTime();
    private final Fancy fx = new Fancy();
    private final java.util.Random noise = new java.util.Random();

    // this frame: UkyUI or vanilla, its colours, seconds since opened, the tracking error (0 none, else how far in)
    private boolean uky;
    private int SURFACE, ACCENT, TEXT, DIM, DANGER, LINE;
    private float time, glitch;
    /** Lines drawn so far this frame: each comes in a little after the one before. */
    private int shown;

    public static void open(NBTTagCompound t) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiTape(t));
    }

    private GuiTape(NBTTagCompound t) {
        this.t = t;
        items = read(t.getTagList("items", 10));
        lost = read(t.getTagList("lost", 10));
        String c;
        try {
            c = IChatComponent.Serializer.func_150699_a(t.getString("cause"))
                .getFormattedText();
        } catch (Exception e) {
            c = "";
        }
        cause = c;
    }

    private static List<ItemStack> read(NBTTagList list) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    private static int gridHeight(List<ItemStack> l) {
        return l.isEmpty() ? 0 : 12 + (l.size() + COLS - 1) / COLS * CELL + 4;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void drawScreen(int mx, int my, float partialTicks) {
        uky = Palette.uky();
        time = (System.nanoTime() - opened) / 1e9F;
        // a tracking error every few seconds, a tenth of a second long
        float cycle = time % 3.7F;
        glitch = uky && time > 1 && cycle < 0.12F ? cycle / 0.12F : 0;
        if (uky) {
            SURFACE = Palette.get("surface", 0x14141A);
            ACCENT = Palette.accent();
            TEXT = Palette.text();
            DIM = Palette.dim();
            DANGER = Palette.danger();
            LINE = 0xFFFFFF;
        } else {
            SURFACE = 0xC6C6C6;
            ACCENT = 0x404040;
            TEXT = 0x303030;
            DIM = 0x707070;
            DANGER = 0xAA0000;
            LINE = 0x8B8B8B;
        }
        shown = 0;
        drawRect(0, 0, width, height, 0xA0000000);
        List<String> causeLines = fontRendererObj.listFormattedStringToWidth(cause, W - 2 * PAD);
        int xp = t.getInteger("xp");
        int h = 26 + 10
            + causeLines.size() * 10
            + 4
            + 20
            + (xp > 0 ? 10 : 0)
            + 6
            + gridHeight(items)
            + gridHeight(lost)
            + 14
            + PAD;
        int left = (width - W) / 2, top = Math.max(4, (height - h) / 2), right = left + W;
        if (uky) ukyPanel(left, top, right, top + h);
        else vanillaPanel(left, top, W, h);

        int x = left + PAD, y = top + 8;
        String title = StatCollector.translateToLocalFormatted("ukycorpses.gui.record", t.getInteger("n"));
        if (uky) fx.set(time * 2.5F, glitch > 0 ? 1.5F : 0, 0, glitch > 0 ? 2 : 0.3F)
            .draw(fontRendererObj, title, x + (glitch > 0 ? 2 : 0), y, ACCENT, 1);
        else fontRendererObj.drawString(title, x, y, ACCENT);
        int rw = fontRendererObj.getStringWidth("REC");
        text("REC", right - PAD - rw, y, DANGER);
        if (Minecraft.getSystemTime() / 600 % 2 == 0)
            drawRect(right - PAD - rw - 7, y + 1, right - PAD - rw - 2, y + 6, 0xFF000000 | DANGER);
        if (uky) {
            int sec = (int) time;
            String code = String.format("%02d:%02d", sec / 60, sec % 60);
            Fancy.text(
                fontRendererObj,
                code,
                right - PAD - rw - 12 - fontRendererObj.getStringWidth(code),
                y,
                DIM,
                0.8F,
                false);
        }
        y += 14;
        rule(x, y, right - PAD);
        y += 6;

        line(t.getString("owner"), x, y, TEXT);
        y += 10;
        for (String l : causeLines) {
            line(l, x, y, DIM);
            y += 10;
        }
        y += 4;
        line(
            t.getInteger(
                "x") + " " + t.getInteger("y") + " " + t.getInteger("z") + "  \u00b7  " + dimName(t.getInteger("dim")),
            x,
            y,
            TEXT);
        y += 10;
        String date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(t.getLong("date")));
        line(
            StatCollector.translateToLocalFormatted("ukycorpses.gui.day", t.getLong("created") / 24000 + 1)
                + "  \u00b7  "
                + date,
            x,
            y,
            DIM);
        y += 10;
        if (xp > 0) {
            line(StatCollector.translateToLocalFormatted("ukycorpses.gui.xp", xp), x, y, DIM);
            y += 10;
        }
        y += 6;

        hovered = null;
        y = grid(items, "ukycorpses.gui.inside", DIM, false, x, y, mx, my, right - PAD);
        y = grid(lost, "ukycorpses.gui.lost", DANGER, true, x, y, mx, my, right - PAD);

        line(lockText(), x, y + 2, DIM);
        if (uky) tears(left, top, right, top + h);
        if (hovered != null) renderToolTip(hovered, mx, my);
    }

    /** UkyUI's panel as a tape: its surface, the gold rail and its glow, scanlines, a band rolling down, corners. */
    private void ukyPanel(int left, int top, int right, int bottom) {
        drawGradientRect(left, top, right, bottom, 0xF4000000 | SURFACE, 0xF4000000 | darker(SURFACE));
        drawRect(left, top, right, top + 1, 0x22FFFFFF);
        drawRect(left, bottom - 1, right, bottom, 0x22FFFFFF);
        drawRect(right - 1, top, right, bottom, 0x22FFFFFF);
        Fancy.gradH(left + 2, top, left + 14, bottom, ACCENT, 0.18F, 0);
        drawRect(left, top, left + 2, bottom, 0xFF000000 | ACCENT);
        for (int y = top; y < bottom; y += 2) drawRect(left + 2, y, right - 1, y + 1, 0x0C000000);
        float band = top + (time * 0.25F % 1.3F - 0.15F) * (bottom - top), bh = 14;
        if (band > top - bh && band < bottom) {
            float b0 = Math.max(top, band), b1 = Math.min(bottom, band + bh);
            Fancy.rect(left + 2, b0, right - 1, b1, ACCENT, 0.035F);
        }
        // a camcorder's viewfinder
        int c = 6, i = 4;
        float a = 0.55F;
        Fancy.rect(left + i + 2, top + i, left + i + 2 + c, top + i + 1, ACCENT, a);
        Fancy.rect(left + i + 2, top + i, left + i + 3, top + i + c, ACCENT, a);
        Fancy.rect(right - i - c, top + i, right - i, top + i + 1, ACCENT, a);
        Fancy.rect(right - i - 1, top + i, right - i, top + i + c, ACCENT, a);
        Fancy.rect(left + i + 2, bottom - i - 1, left + i + 2 + c, bottom - i, ACCENT, a);
        Fancy.rect(left + i + 2, bottom - i - c, left + i + 3, bottom - i, ACCENT, a);
        Fancy.rect(right - i - c, bottom - i - 1, right - i, bottom - i, ACCENT, a);
        Fancy.rect(right - i - 1, bottom - i - c, right - i, bottom - i, ACCENT, a);
    }

    /** During a tracking error: a few bright tears across the picture. */
    private void tears(int left, int top, int right, int bottom) {
        if (glitch <= 0) return;
        noise.setSeed((long) (time * 30));
        for (int k = 0; k < 6; k++) {
            float y = top + noise.nextFloat() * (bottom - top), w = 20 + noise.nextFloat() * (right - left) * 0.6F,
                x = left + noise.nextFloat() * (right - left - w);
            Fancy.rect(x, y, x + w, y + 1, 0xFFFFFF, 0.08F + 0.1F * noise.nextFloat());
        }
    }

    /** The vanilla window: light grey, white upper-left edge, dark lower-right, black outline. */
    private static void vanillaPanel(int x, int y, int w, int h) {
        drawRect(x + 1, y, x + w - 1, y + 1, 0xFF000000);
        drawRect(x + 1, y + h - 1, x + w - 1, y + h, 0xFF000000);
        drawRect(x, y + 1, x + 1, y + h - 1, 0xFF000000);
        drawRect(x + w - 1, y + 1, x + w, y + h - 1, 0xFF000000);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        drawRect(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
        drawRect(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        drawRect(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    /** A line of the record: with UkyUI it comes in letter by letter, a little after the one before. */
    private void line(String s, int x, int y, int color) {
        if (uky) fx.set((time - 0.05F - 0.025F * shown++) * 2.5F, 0, 0, 0)
            .draw(fontRendererObj, s, x, y, color, 1);
        else fontRendererObj.drawString(s, x, y, color);
    }

    private void text(String s, int x, int y, int color) {
        if (uky) Fancy.text(fontRendererObj, s, x, y, color, 1, false);
        else fontRendererObj.drawString(s, x, y, color);
    }

    /** Under a heading: UkyUI's gold rule fading to the right, or a plain grey line. */
    private void rule(int x0, int y, int x1) {
        if (uky) Fancy.gradH(x0, y, x1, y + 1, ACCENT, 0.6F, 0);
        else drawRect(x0, y, x1, y + 1, 0xFF000000 | LINE);
    }

    private static int darker(int rgb) {
        return (rgb >> 16 & 255) * 3 / 4 << 16 | (rgb >> 8 & 255) * 3 / 4 << 8 | (rgb & 255) * 3 / 4;
    }

    private int grid(List<ItemStack> list, String title, int color, boolean burnt, int x, int y, int mx, int my,
        int right) {
        if (list.isEmpty()) return y;
        String caption = StatCollector.translateToLocalFormatted(title, list.size());
        line(caption, x, y, color);
        if (uky) Fancy.gradH(x + fontRendererObj.getStringWidth(caption) + 4, y + 4, right, y + 5, color, 0.35F, 0);
        y += 12;
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        for (int i = 0; i < list.size(); i++) {
            int ix = x + i % COLS * CELL, iy = y + i / COLS * CELL;
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), list.get(i), ix, iy);
            itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), list.get(i), ix, iy);
            if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) hovered = list.get(i);
        }
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        if (burnt) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            for (int i = 0; i < list.size(); i++) {
                int ix = x + i % COLS * CELL, iy = y + i / COLS * CELL;
                drawRect(ix, iy, ix + 16, iy + 16, uky ? 0x99141418 : 0x66C6C6C6);
                drawRect(ix, iy + 15, ix + 16, iy + 16, 0xFF000000 | DANGER);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        return y + (list.size() + COLS - 1) / COLS * CELL + 4;
    }

    private String lockText() {
        long lock = t.getLong("lock");
        if (lock < 0) return StatCollector.translateToLocal("ukycorpses.gui.ownerOnly");
        long left = t.getLong("created") + lock - mc.theWorld.getTotalWorldTime();
        return left > 0 ? StatCollector.translateToLocalFormatted("ukycorpses.gui.locked", (left + 1199) / 1200)
            : StatCollector.translateToLocal("ukycorpses.gui.open");
    }

    private static String dimName(int dim) {
        try {
            return DimensionManager.createProviderFor(dim)
                .getDimensionName();
        } catch (Throwable e) { // dimension from a removed mod
            return "DIM " + dim;
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == 1 || key == mc.gameSettings.keyBindInventory.getKeyCode()) mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
