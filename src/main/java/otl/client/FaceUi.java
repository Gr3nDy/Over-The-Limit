package otl.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;

/** Shared colours and drawing helpers for the OTL menus. */
final class FaceUi {
    static final int GOLD = 0xFFFFD870;
    static final int GOLD_DIM = 0xFF9C7F38;
    static final int TEXT = 0xFFE9ECF2;
    static final int MUTED = 0xFF9AA3B2;
    static final int DIM = 0xFF626B7A;
    static final int PANEL = 0xE614171E;
    static final int PANEL_BORDER = 0xFF2B323D;
    static final int CARD = 0xE610131A;
    static final int ROW = 0xFF222833;
    static final int ROW_HOVER = 0xFF2E3643;
    static final int ROW_SELECTED = 0xFF3A3220;
    static final int GREEN = 0xFF7BD37B;
    static final int RED = 0xFFD67B7B;
    static final int SHADOW = 0x55000000;

    private FaceUi() {}

    static void panel(int x, int y, int w, int h) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER);
        Gui.drawRect(x, y, x + w, y + h, PANEL);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    static void card(int x, int y, int w, int h) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER);
        Gui.drawRect(x, y, x + w, y + h, CARD);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);
    }

    static void row(FontRenderer font, int x, int y, int w, int h, String label, String value, boolean hover, boolean selected, boolean toggle, boolean on) {
        Gui.drawRect(x + 1, y + 1, x + w + 1, y + h + 1, SHADOW);
        Gui.drawRect(x, y, x + w, y + h, selected ? ROW_SELECTED : hover ? ROW_HOVER : ROW);
        Gui.drawRect(x, y, x + 2, y + h, selected ? GOLD : hover ? GOLD_DIM : 0xFF39414E);
        int textY = y + (h - 8) / 2;
        int valueWidth = font.getStringWidth(value);
        int labelRoom = w - 20 - valueWidth;
        String shown = label;
        if (labelRoom > 8 && font.getStringWidth(label) > labelRoom) {
            shown = font.trimStringToWidth(label, labelRoom - 4) + "...";
        }
        font.drawString(shown, x + 9, textY, selected ? GOLD : TEXT);
        if (value.length() > 0) {
            font.drawString(value, x + w - 9 - valueWidth, textY, toggle ? (on ? GREEN : RED) : GOLD);
        }
    }

    static void header(FontRenderer font, String text, int x, int y) {
        font.drawString(text.toUpperCase(java.util.Locale.ROOT), x, y, DIM);
    }

    static void scrollbar(int x, int y, int h, int thumbY, int thumbH) {
        Gui.drawRect(x, y, x + 3, y + h, 0x30FFFFFF);
        Gui.drawRect(x, y + thumbY, x + 3, y + thumbY + thumbH, GOLD_DIM);
    }
}
