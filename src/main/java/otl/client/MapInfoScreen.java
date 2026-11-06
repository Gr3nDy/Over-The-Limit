package otl.client;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Replaces the server's map book with a card built from map.xml, bundled or downloaded. */
final class MapInfoScreen extends GuiScreen {
    private final String query, command;
    private final long opened = System.currentTimeMillis();
    private volatile MapInfo info;
    private volatile boolean failed;
    private int x, y, w, h, banner;

    MapInfoScreen(String query, String command) {
        this.query = query;
        this.command = command;
        info = MapInfo.findLocal(query);
        if (info == null) MapRemote.lookup(query, found -> {
            if (found == null) failed = true; else info = found;
        });
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    public void initGui() {
        w = Math.min(372, width - 20);
        h = Math.min(230, height - 16);
        if (h < 168) h = height - 8;
        x = (width - w) / 2;
        y = (height - h) / 2;
        banner = Math.max(60, Math.min(92, h / 3));
        buttonList.clear();
        int row = y + h - 30;
        buttonList.add(new NeutralButton(1, x + w - 118, row, 104, 19, "Server book", false));
        buttonList.add(new NeutralButton(2, x + w - 198, row, 72, 19, "Close", false));
    }

    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            if (mc.thePlayer != null) mc.thePlayer.sendChatMessage(command == null || command.isEmpty() ? "/map " + query : command);
            mc.displayGuiScreen(null);
            return;
        }
        if (b.id == 2) mc.displayGuiScreen(null);
    }

    protected void keyTyped(char typed, int key) throws IOException {
        if (key == 1) mc.displayGuiScreen(null);
    }

    public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        drawRect(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);
        drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3C3C3C);
        drawRect(x, y, x + w, y + h, 0xF5141414);
        drawBanner();
        if (info != null) drawBody(); else drawStatus();
        super.drawScreen(mx, my, partial);
    }

    private void drawBanner() {
        int bx = x + 8, by = y + 8, bw = w - 16;
        drawRect(bx, by, bx + bw, by + banner, 0xFF1D1D1D);
        ResourceLocation texture = info == null ? null : info.texture();
        if (texture != null) {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(texture);
        drawModalRectWithCustomSizedTexture(bx, by, 0, 0, bw, banner, bw, banner);
            for (int i = 0; i < banner / 2; i++) {
                int alpha = (int) (i * 3.2f);
                drawRect(bx, by + banner - banner / 2 + i, bx + bw, by + banner - banner / 2 + i + 1, alpha << 24);
            }
        }
        String title = info == null ? query : info.name;
        String pill = info == null ? "LOOKUP" : info.gamemode + (info.version.isEmpty() ? "" : "  " + info.version);
        int pillWidth = fontRendererObj.getStringWidth(pill) + 12;
        drawRect(bx + bw - pillWidth - 6, by + 6, bx + bw - 6, by + 20, 0xCC0F0F0F);
        drawRect(bx + bw - pillWidth - 6, by + 6, bx + bw - pillWidth - 5, by + 20, 0xFFFFC94A);
        drawString(fontRendererObj, pill, bx + bw - pillWidth, by + 9, 0xFFFFC94A);
        GlStateManager.pushMatrix();
        GlStateManager.translate(bx + 8, by + banner - 24, 0);
        GlStateManager.scale(1.6f, 1.6f, 1f);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(title, (int) ((bw - 24) / 1.6f)), 0, 0, 0xFFFFFFFF, true);
        GlStateManager.popMatrix();
    }

    private void drawBody() {
        int top = y + 8 + banner + 6;
        drawRect(x + 8, top, x + w - 8, top + 1, 0xFFFFC94A);
        int row = top + 7;
        if (!info.objective.isEmpty()) row = block("Objective", info.objective, row, 2);
        drawString(fontRendererObj, "Created", x + 14, row, 0xFF7A7A7A);
        drawString(fontRendererObj, fontRendererObj.trimStringToWidth(info.created.isEmpty() ? "-" : info.created, 110), x + 78, row, 0xFFE8E8E8);
        drawString(fontRendererObj, "Players", x + w - 104, row, 0xFF7A7A7A);
        drawString(fontRendererObj, info.players > 0 ? String.valueOf(info.players) : "-", x + w - 52, row, 0xFFE8E8E8);
        row += 12;
        row = block("Author", info.authors.isEmpty() ? "-" : info.authors, row, 1);
        row = block("Credits", info.contributors.isEmpty() ? "-" : info.contributors, row, 2);
        row = block("Teams", info.teams.isEmpty() ? "-" : info.teams, row, 1);
        drawString(fontRendererObj, fontRendererObj.trimStringToWidth("XML   " + info.dir, w - 28), x + 14, y + h - 44, 0xFF6E6E6E);
    }

    private int block(String label, String value, int row, int maxLines) {
        drawString(fontRendererObj, label, x + 14, row, 0xFF7A7A7A);
        List<String> lines = fontRendererObj.listFormattedStringToWidth(value, x + w - 14 - (x + 78));
        int shown = Math.min(maxLines, lines.size());
        for (int i = 0; i < shown; i++) drawString(fontRendererObj, lines.get(i), x + 78, row + i * 10, 0xFFE8E8E8);
        if (lines.size() > shown && shown > 0) {
            String last = lines.get(shown - 1);
            drawString(fontRendererObj, "...", x + 78 + fontRendererObj.getStringWidth(last) + 2, row + (shown - 1) * 10, 0xFF9A9A9A);
        }
        return row + Math.max(1, shown) * 10 + 2;
    }

    private void drawStatus() {
        int top = y + 8 + banner + 14;
        if (failed) {
            drawString(fontRendererObj, "No public map matches \"" + query + "\".", x + 14, top, 0xFFE8E8E8);
            drawString(fontRendererObj, "880 maps are bundled; the rest are looked up from", x + 14, top + 18, 0xFF9A9A9A);
            drawString(fontRendererObj, "github.com/OvercastCommunity/PublicMaps", x + 14, top + 30, 0xFF9A9A9A);
            drawString(fontRendererObj, "Check the spelling, or open the server book.", x + 14, top + 48, 0xFF9A9A9A);
        } else {
            int dots = (int) ((System.currentTimeMillis() - opened) / 350) % 4;
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < dots; i++) suffix.append('.');
            drawString(fontRendererObj, "Looking up \"" + query + "\"" + suffix, x + 14, top, 0xFFE8E8E8);
            drawString(fontRendererObj, "Bundled previews first, then the public map repo.", x + 14, top + 18, 0xFF9A9A9A);
        }
    }
}
