package com.uky.corpses.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.uky.corpses.ContainerCorpse;
import com.uky.corpses.EntityCorpse;
import com.uky.corpses.MsgTakeAll;
import com.uky.corpses.UkyCorpses;

/**
 * The body's window, as small as a double chest and laid out like a player's inventory: a header with whose body it is
 * (their face, how long it has lain there; the rest under the face) and "take all"; then what was worn with the spare
 * slots that hold something beside it, the backpack, the hotbar, and your own inventory under a line.
 *
 * With UkyUI it's drawn as part of its interface, the way the tape is: a dark panel with the gold rail, the header
 * coming in letter by letter, dark slots (what was worn underlined in gold, what holds something a shade lighter, the
 * one under the mouse framed). Without it, a vanilla window.
 */
public class GuiCorpse extends GuiContainer {

    private static final int HEADER = 24, SLOT = 18, GAP = 4;
    /** Container slots of the body's spare boxes (EntityCorpse.EXTRA_BOX..53), in box order. */
    private static final int[] EXTRA = { 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53 };

    private final ContainerCorpse box;
    private final IInventory chest;
    private final EntityCorpse body;
    /** Where the player's own part starts; rows of spare slots under the armor: set by layout(). */
    private int playerTop, extraRows;
    /** When the window opened (the header comes in from it). */
    private final long opened = System.nanoTime();
    private final Fancy header = new Fancy();

    public GuiCorpse(InventoryPlayer player, IInventory chest, EntityCorpse body) {
        this(new ContainerCorpse(player, chest), chest, body);
    }

    private GuiCorpse(ContainerCorpse box, IInventory chest, EntityCorpse body) {
        super(box);
        this.box = box;
        this.chest = chest;
        this.body = body;
        xSize = 176;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        layout();
        super.initGui();
        String label = StatCollector.translateToLocal("ukycorpses.gui.takeAll");
        int w = fontRendererObj.getStringWidth(label) + 10;
        buttonList.add(
            Palette.uky() ? new Flat(0, guiLeft + xSize - 7 - w, guiTop + 4, w, 16, label)
                : new GuiButton(0, guiLeft + xSize - 7 - w, guiTop + 3, w, 18, label));
    }

    /**
     * Armor first, the spare boxes that hold something next to it (more of them in rows of their own only when there
     * are), the backpack, the hotbar, then yours; the window grows and shrinks with that.
     */
    private void layout() {
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < EXTRA.length; i++) if (slot(EXTRA[i]).getHasStack() || i == 0) used.add(EXTRA[i]);
        int beside = 4; // room next to the armor
        extraRows = used.size() > beside ? 1 + (used.size() - beside - 1) / 9 : 0;
        int top = HEADER;
        for (int i = 0; i < 4; i++) place(i, 8 + i * SLOT, top);
        for (int c : EXTRA) place(c, -2000, -2000);
        for (int i = 0; i < used.size(); i++) {
            if (i < beside) place(used.get(i), 8 + (5 + i) * SLOT, top);
            else {
                int j = i - beside;
                place(used.get(i), 8 + (j % 9) * SLOT, top + SLOT + GAP + (j / 9) * SLOT);
            }
        }
        int main = top + SLOT + GAP + extraRows * SLOT + (extraRows > 0 ? GAP : 0);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) place(9 + r * 9 + c, 8 + c * SLOT, main + r * SLOT);
        for (int c = 0; c < 9; c++) place(36 + c, 8 + c * SLOT, main + 3 * SLOT + GAP);
        playerTop = main + 4 * SLOT + GAP + 14;
        int p = ContainerCorpse.BODY_SLOTS;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) place(p + r * 9 + c, 8 + c * SLOT, playerTop + r * SLOT);
        for (int c = 0; c < 9; c++) place(p + 27 + c, 8 + c * SLOT, playerTop + 3 * SLOT + GAP);
        ySize = playerTop + 4 * SLOT + GAP + 6;
    }

    private Slot slot(int i) {
        return (Slot) box.inventorySlots.get(i);
    }

    private void place(int i, int x, int y) {
        Slot s = slot(i);
        s.xDisplayPosition = x;
        s.yDisplayPosition = y;
    }

    /** Something landed in another spare box (or left it): the window follows. */
    @Override
    public void updateScreen() {
        super.updateScreen();
        int rows = extraRows;
        layout();
        if (rows != extraRows) {
            buttonList.clear();
            initGui();
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) UkyCorpses.net.sendToServer(new MsgTakeAll());
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        String name = body != null ? body.owner() : chest.getInventoryName();
        String inv = StatCollector.translateToLocal("container.inventory");
        if (Palette.uky()) {
            float t = (System.nanoTime() - opened) / 1e9F;
            header.set(t * 2.5F, 0, 0, t < 0.25F ? 0.6F * (1 - t / 0.25F) : 0)
                .draw(fontRendererObj, name, 28, 5, Palette.text(), 1);
            if (body != null) header.set(t * 2.5F - 0.3F, 0, 0, 0)
                .draw(fontRendererObj, fontRendererObj.trimStringToWidth(ago(), room()), 28, 14, Palette.dim(), 0.9F);
            String caps = inv.toUpperCase();
            Fancy.text(fontRendererObj, caps, 8, playerTop - 12, Palette.accent(), 0.85F, false);
            int after = 12 + fontRendererObj.getStringWidth(caps);
            Fancy.gradH(after, playerTop - 8, xSize - 7, playerTop - 7, Palette.accent(), 0.5F, 0);
        } else {
            fontRendererObj.drawString(EnumChatFormatting.BOLD + name, 27, 4, 0x303030);
            if (body != null) fontRendererObj.drawString(ago(), 27, 14, 0x707070);
            fontRendererObj.drawString(inv, 8, playerTop - 11, 0x404040);
        }
        if (body != null && func_146978_c(5, 3, 18, 18, mx, my)) { // the rest of it under the face
            List<String> tip = new ArrayList<>();
            tip.add(StatCollector.translateToLocalFormatted("ukycorpses.waila.owner", body.owner()));
            tip.add(StatCollector.translateToLocalFormatted("ukycorpses.waila.items", body.count(), body.xpShown()));
            tip.add(lockLine());
            func_146283_a(tip, mx - guiLeft, my - guiTop);
        }
    }

    /** Width the header has before the "take all" button (the rest of it is in the tooltip on the face). */
    private int room() {
        int button = fontRendererObj.getStringWidth(StatCollector.translateToLocal("ukycorpses.gui.takeAll")) + 10;
        return xSize - 7 - button - 4 - 28;
    }

    /** "Lying 9 min", short enough for the header. */
    private String ago() {
        int min = body.ageMinutes();
        String ago = min < 120 ? StatCollector.translateToLocalFormatted("ukycorpses.waila.min", min)
            : StatCollector.translateToLocalFormatted("ukycorpses.waila.hours", min / 60);
        return StatCollector.translateToLocalFormatted("ukycorpses.gui.lies", ago);
    }

    private String lockLine() {
        int lock = body.lockMinutes();
        return lock < 0 ? StatCollector.translateToLocal("ukycorpses.gui.cardOwner")
            : lock > 0 ? StatCollector.translateToLocalFormatted("ukycorpses.gui.cardLocked", lock)
                : StatCollector.translateToLocal("ukycorpses.gui.cardOpen");
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        if (Palette.uky()) ukyBackground(mx, my);
        else vanillaBackground();
    }

    private void ukyBackground(int mx, int my) {
        int x = guiLeft, y = guiTop, accent = Palette.accent(), surface = Palette.get("surface", 0x14141A);
        // the panel: UkyUI's surface going a shade darker down it, the header band lighter, a hairline round it, the
        // gold rail with its glow, faint scanlines
        drawGradientRect(x, y, x + xSize, y + ySize, 0xF4000000 | surface, 0xF4000000 | darker(surface));
        drawRect(x + 2, y, x + xSize, y + HEADER - 2, 0x0AFFFFFF);
        drawRect(x, y, x + xSize, y + 1, LINE);
        drawRect(x, y + ySize - 1, x + xSize, y + ySize, LINE);
        drawRect(x + xSize - 1, y, x + xSize, y + ySize, LINE);
        Fancy.gradH(x + 2, y, x + 12, y + ySize, accent, 0.18F, 0);
        drawRect(x, y, x + 2, y + ySize, 0xFF000000 | accent);
        for (int r = y; r < y + ySize; r += 2) drawRect(x + 2, r, x + xSize - 1, r + 1, 0x0A000000);
        Fancy.gradH(x + 5, y + HEADER - 2, x + xSize - 5, y + HEADER - 1, accent, 0.55F, 0);

        if (body != null) {
            drawRect(x + 5, y + 3, x + 23, y + 21, 0xFF000000 | accent);
            drawRect(x + 6, y + 4, x + 22, y + 20, 0xFF000000);
            face(x + 6, y + 4);
        }
        Slot hot = null;
        for (int i = 0; i < box.inventorySlots.size(); i++) {
            Slot s = slot(i);
            if (s.xDisplayPosition < 0) continue;
            int sx = x + s.xDisplayPosition - 1, sy = y + s.yDisplayPosition - 1;
            drawRect(sx, sy, sx + 18, sy + 18, LINE);
            drawRect(sx + 1, sy + 1, sx + 17, sy + 17, s.getHasStack() ? 0xC818181E : SLOT_FILL);
            if (i < 4) { // what was worn: a gold line under it
                drawRect(sx + 1, sy + 1, sx + 17, sy + 17, 0x14000000 | accent);
                drawRect(sx + 1, sy + 16, sx + 17, sy + 17, 0xC0000000 | accent);
            }
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) hot = s;
        }
        if (hot != null) {
            int sx = x + hot.xDisplayPosition - 1, sy = y + hot.yDisplayPosition - 1;
            Fancy.frame(sx - 1, sy - 1, sx + 19, sy + 19, accent, 0.9F);
        }
    }

    /** The vanilla window: light grey, white upper-left edge, dark lower-right, black outline; grey slots. */
    private void vanillaBackground() {
        int x = guiLeft, y = guiTop, w = xSize, h = ySize;
        drawRect(x + 1, y, x + w - 1, y + 1, 0xFF000000);
        drawRect(x + 1, y + h - 1, x + w - 1, y + h, 0xFF000000);
        drawRect(x, y + 1, x + 1, y + h - 1, 0xFF000000);
        drawRect(x + w - 1, y + 1, x + w, y + h - 1, 0xFF000000);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        drawRect(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
        drawRect(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        drawRect(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        if (body != null) face(x + 6, y + 4);
        for (int i = 0; i < box.inventorySlots.size(); i++) {
            Slot s = slot(i);
            if (s.xDisplayPosition < 0) continue;
            int sx = x + s.xDisplayPosition - 1, sy = y + s.yDisplayPosition - 1;
            drawRect(sx, sy, sx + 18, sy + 18, 0xFF373737);
            drawRect(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
            drawRect(sx + 1, sy + 1, sx + 17, sy + 17, i < 4 ? 0xFF7D7D8E : 0xFF8B8B8B);
        }
        drawRect(x + 7, y + playerTop - 15, x + xSize - 7, y + playerTop - 14, 0xFF8B8B8B);
    }

    private static int darker(int rgb) {
        return (rgb >> 16 & 255) * 3 / 4 << 16 | (rgb >> 8 & 255) * 3 / 4 << 8 | (rgb & 255) * 3 / 4;
    }

    private static final int LINE = 0x22FFFFFF, SLOT_FILL = 0xB00A0A0E;

    /** The owner's face off their skin, the hat layer over it, at twice the size. */
    private void face(int x, int y) {
        mc.getTextureManager()
            .bindTexture(RenderCorpse.skin(body.owner()));
        GL11.glColor4f(0.85F, 0.84F, 0.82F, 1);
        int height = (int) (32 / RenderCorpse.boundSkinV());
        func_152125_a(x, y, 8, 8, 8, 8, 16, 16, 64, height);
        GL11.glEnable(GL11.GL_BLEND);
        func_152125_a(x, y, 40, 8, 8, 8, 16, 16, 64, height);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** UkyUI's flat button: dark, a hairline that turns gold under the mouse with a wash of gold behind the label. */
    public static final class Flat extends GuiButton {

        public Flat(int id, int x, int y, int w, int h, String label) {
            super(id, x, y, w, h, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) return;
            field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            boolean hot = field_146123_n && enabled;
            int accent = Palette.accent();
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, hot ? 0xFF000000 | accent : LINE);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, 0xE00C0C10);
            if (hot) Fancy.gradH(
                xPosition + 1,
                yPosition + 1,
                xPosition + width - 1,
                yPosition + height - 1,
                accent,
                0.25F,
                0.05F);
            drawCenteredString(
                mc.fontRenderer,
                displayString,
                xPosition + width / 2,
                yPosition + (height - 8) / 2,
                !enabled ? Palette.dim() : hot ? Palette.get("textHover", 0xFFFFFF) : Palette.text());
        }
    }
}
