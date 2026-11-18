package com.uky.corpses.pc;

import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Installed Corpse storagex166 compass panel and recovery-command copy interaction. */
@SideOnly(Side.CLIENT)
final class CompassScreen extends GuiScreen {

    private NBTTagCompound target;
    private final ItemStack compass;
    private final UUID corpseId;
    private int left, top, idX, idY, idW;
    private long copiedUntil;

    CompassScreen(ItemStack stack) {
        compass = stack;
        target = CorpseCompass.target(stack);
        corpseId = CorpseCompass.id(stack);

    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        left = (width - 248) / 2;
        top = (height - 166) / 2;
        buttonList.clear();
        buttonList.add(
            new GuiButton(0, left + 248 - 8 - 54, top + 166 - 26, 54, 20, StatCollector.translateToLocal("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        target = PcClient.target(compass);
        drawDefaultBackground();
        drawRect(left, top, left + 248, top + 166, 0xFFC6C6C6);
        drawRect(left, top, left + 248, top + 2, 0xFFFFFFFF);
        drawRect(left, top, left + 2, top + 166, 0xFFFFFFFF);
        drawRect(left + 246, top + 2, left + 248, top + 166, 0xFF555555);
        drawRect(left + 2, top + 164, left + 246, top + 166, 0xFF555555);
        drawRect(left + 98, top + 28, left + 99, top + 136, 0x403F3F3F);
        String name = target.getString("Name");
        if (name.isEmpty()) name = "Unknown";
        fontRendererObj.drawString(
            StatCollector.translateToLocalFormatted("gui.ukycorpses_compass.corpse_compass.title", name),
            left + 8,
            top + 6,
            0x404040);
        body(mouseX, mouseY);
        int x = left + 108, y = top + 22, line = 12;
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.ukycorpses_compass.corpse_compass.location"),
            x,
            y,
            0x606060);
        y += line + 1;
        for (String axis : new String[] { "X", "Y", "Z" }) {
            fontRendererObj.drawString(axis + ": " + (int) Math.floor(target.getDouble(axis)), x, y, 0x404040);
            y += line;
        }
        y += 2;
        int dim = target.getInteger("Dim");
        String dimension = StatCollector.translateToLocal("ukycorpses.compass.dim." + dim);
        if (dimension.startsWith("ukycorpses.")) dimension = "ID " + dim;
        fontRendererObj.drawString(dimension, x, y, 0x606060);
        y += line + 6;
        String label = StatCollector.translateToLocalFormatted(
            "gui.ukycorpses_compass.corpse_compass.id",
            corpseId == null ? "unknown"
                : corpseId.toString()
                    .substring(0, 8) + "\u2026");
        idX = x;
        idY = y;
        idW = fontRendererObj.getStringWidth(label);
        boolean hover = corpseId != null && inside(mouseX, mouseY);
        fontRendererObj.drawString(label, idX, idY, hover ? 0x2020A0 : 0x606060);
        if (hover) drawRect(idX, idY + 9, idX + idW, idY + 10, -14979149);
        if (System.currentTimeMillis() < copiedUntil) fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.ukycorpses_compass.corpse_compass.copied"),
            x,
            y + line + 2,
            3046706);
        super.drawScreen(mouseX, mouseY, partial);
        if (hover) drawHoveringText(
            java.util.Collections
                .singletonList(StatCollector.translateToLocal("gui.ukycorpses_compass.corpse_compass.copy.tooltip")),
            mouseX,
            mouseY,
            fontRendererObj);
    }

    private void body(int mouseX, int mouseY) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glTranslatef(left + 24, top + 42, 0);
            org.lwjgl.opengl.GL11.glScalef(3, 3, 3);
            new net.minecraft.client.renderer.entity.RenderItem()
                .renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), compass, 0, 0);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
        String text = target.getInteger("Dim") == player.dimension ? StatCollector.translateToLocalFormatted(
            "ukycorpses.compass.distance",
            Math.round(
                Math.sqrt(player.getDistanceSq(target.getDouble("X"), target.getDouble("Y"), target.getDouble("Z")))))
            : StatCollector.translateToLocal("ukycorpses.compass.other_dimension");
        fontRendererObj.drawString(text, left + 50 - fontRendererObj.getStringWidth(text) / 2, top + 104, 0x404040);
    }

    private boolean inside(int x, int y) {
        return x >= idX && x <= idX + idW && y >= idY && y <= idY + 9;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (button == 0 && corpseId != null && inside(x, y)) {
            setClipboardString("/" + com.uky.corpses.UkyCorpses.COMMAND + " retrieve " + corpseId);
            copiedUntil = System.currentTimeMillis() + 1600;
        } else super.mouseClicked(x, y, button);
    }

}
