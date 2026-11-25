package com.uky.corpses.pc;

import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.client.GuiCorpse;
import com.uky.corpses.client.Palette;
import com.uky.corpses.client.RenderCorpse;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Installed Corpse storagex166 compass panel and recovery-command copy interaction. */
@SideOnly(Side.CLIENT)
final class CompassScreen extends GuiScreen {

    private static final float[] PREVIEW_TINT = { 1, 1, 1 };
    private NBTTagCompound target;
    private final ItemStack compass;
    private final UUID corpseId;
    private int left, top, idX, idY, idW;
    private long copiedUntil;
    private final net.minecraft.client.renderer.entity.RenderItem itemRenderer = new net.minecraft.client.renderer.entity.RenderItem();
    private final ItemStack[] armor = new ItemStack[4];

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
        String done = StatCollector.translateToLocal("gui.done");
        buttonList.add(
            Palette.uky() ? new GuiCorpse.Flat(0, left + 186, top + 140, 54, 20, done)
                : new GuiButton(0, left + 186, top + 140, 54, 20, done));
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
        boolean themed = Palette.uky();
        int text = themed ? Palette.text() : 0x404040;
        int dimText = themed ? Palette.text() & 0xFEFEFE : 0x606060;
        int accent = themed ? Palette.accent() : 0x2020A0;
        if (themed) {
            drawRect(left, top, left + 248, top + 166, 0xF4000000 | Palette.get("surface", 0x14141A));
            drawRect(left, top, left + 2, top + 166, 0xFF000000 | accent);
            drawRect(left + 2, top, left + 248, top + 20, 0x0AFFFFFF);
            drawRect(left + 8, top + 19, left + 240, top + 20, 0x60000000 | accent);
            drawRect(left + 98, top + 28, left + 99, top + 136, 0x30FFFFFF);
        } else {
            drawRect(left, top, left + 248, top + 166, 0xFFC6C6C6);
            drawRect(left, top, left + 248, top + 2, 0xFFFFFFFF);
            drawRect(left, top, left + 2, top + 166, 0xFFFFFFFF);
            drawRect(left + 246, top + 2, left + 248, top + 166, 0xFF555555);
            drawRect(left + 2, top + 164, left + 246, top + 166, 0xFF555555);
            drawRect(left + 98, top + 28, left + 99, top + 136, 0x403F3F3F);
        }
        String name = target.getString("Name");
        if (name.isEmpty()) name = "Unknown";
        fontRendererObj.drawString(
            StatCollector.translateToLocalFormatted("gui.ukycorpses_compass.corpse_compass.title", name),
            left + 8,
            top + 6,
            text);
        body(partial, text);
        int x = left + 108, y = top + 24, line = 12;
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.ukycorpses_compass.corpse_compass.location"),
            x,
            y,
            dimText);
        y += line + 1;
        for (String axis : new String[] { "X", "Y", "Z" }) {
            fontRendererObj.drawString(axis + ": " + (int) Math.floor(target.getDouble(axis)), x, y, text);
            y += line;
        }
        y += 2;
        int dim = target.getInteger("Dim");
        String dimension = StatCollector.translateToLocal("ukycorpses.compass.dim." + dim);
        if (dimension.startsWith("ukycorpses.")) dimension = "ID " + dim;
        fontRendererObj.drawString(dimension, x, y, dimText);
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
        fontRendererObj.drawString(label, idX, idY, hover ? accent : dimText);
        if (hover) drawRect(idX, idY + 9, idX + idW, idY + 10, 0xFF000000 | accent);
        if (System.currentTimeMillis() < copiedUntil) fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.ukycorpses_compass.corpse_compass.copied"),
            x,
            y + line + 2,
            themed ? Palette.OK : 3046706);
        super.drawScreen(mouseX, mouseY, partial);
        if (hover) drawHoveringText(
            java.util.Collections
                .singletonList(StatCollector.translateToLocal("gui.ukycorpses_compass.corpse_compass.copy.tooltip")),
            mouseX,
            mouseY,
            fontRendererObj);
    }

    private void body(float partial, int color) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        EntityCorpse corpse = PcClient.body(compass);
        if (corpse != null && !corpse.pending() && CorpseClient.renderer != null) preview(corpse, partial);
        else {
            org.lwjgl.opengl.GL11.glPushMatrix();
            try {
                org.lwjgl.opengl.GL11.glTranslatef(left + 24, top + 42, 0);
                org.lwjgl.opengl.GL11.glScalef(3, 3, 3);
                itemRenderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), compass, 0, 0);
            } finally {
                org.lwjgl.opengl.GL11.glPopMatrix();
            }
        }
        String text = target.getInteger("Dim") == player.dimension ? StatCollector.translateToLocalFormatted(
            "ukycorpses.compass.distance",
            Math.round(
                Math.sqrt(player.getDistanceSq(target.getDouble("X"), target.getDouble("Y"), target.getDouble("Z")))))
            : StatCollector.translateToLocal("ukycorpses.compass.other_dimension");
        fontRendererObj.drawString(text, left + 50 - fontRendererObj.getStringWidth(text) / 2, top + 118, color);
    }

    private void preview(EntityCorpse corpse, float partial) {
        Skeleton.Shape shape = corpse.drawnShape(partial);
        if (shape == null) return;
        double[] center = shape.parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
        double span = 1;
        for (int part = 0; part < Skeleton.PARTS; part++) {
            if ((corpse.ampHiddenMask() & 1 << part) != 0) continue;
            double[] bounds = shape.bounds(part);
            for (int axis = 0; axis < 3; axis++) span = Math
                .max(span, Math.max(Math.abs(bounds[axis] - center[axis]), Math.abs(bounds[axis + 3] - center[axis])));
        }
        for (int slot = 0; slot < armor.length; slot++) armor[slot] = corpse.armor(slot);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(left + 49, top + 72, 80);
            double scale = 24 / span;
            GL11.glScaled(scale, -scale, scale);
            GL11.glRotatef(28, 1, 0, 0);
            GL11.glRotatef(35, 0, 1, 0);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            CorpseClient.renderer.drawPhysical(
                corpse,
                RenderCorpse.skin(corpse.owner()),
                armor,
                null,
                shape,
                PREVIEW_TINT,
                -center[0],
                -center[1],
                -center[2]);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
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
