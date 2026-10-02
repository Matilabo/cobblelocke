package com.cobblelocke.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class Theme {
    public static final int MAIN = 0xFF3466AF;
    public static final int MAIN_DARK = 0xFF1F3F72;
    public static final int MAIN_DEEP = 0xFF142A4D;
    public static final int ACCENT = 0xFFFFCB05;
    public static final int ACCENT_DARK = 0xFFC79A00;

    public static final int PANEL = 0xC8142A4D;
    public static final int PANEL_LIGHT = 0x663466AF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFB9C8E6;
    public static final int TEXT_DISABLED = 0xFF6F7F9E;
    public static final int TEXT_ON_ACCENT = 0xFF14213D;
    public static final int ON = 0xFF7CE08A;
    public static final int OFF = 0xFFB9C8E6;

    private Theme() {
    }

    public static void button(DrawContext context, TextRenderer font, String label, int x, int y, int width,
                              int height, boolean primary, boolean hovered, boolean enabled) {
        int fill;
        int edge;
        int textColour;
        if (!enabled) {
            fill = MAIN_DEEP;
            edge = MAIN_DEEP;
            textColour = TEXT_DISABLED;
        } else if (primary) {
            fill = hovered ? 0xFFFFD83D : ACCENT;
            edge = ACCENT_DARK;
            textColour = TEXT_ON_ACCENT;
        } else {
            fill = hovered ? MAIN : MAIN_DARK;
            edge = MAIN_DEEP;
            textColour = TEXT;
        }
        context.fill(x, y, x + width, y + height, fill);
        context.fill(x, y + height - 2, x + width, y + height, edge);
        context.drawText(font, Text.literal(label), x + (width - font.getWidth(label)) / 2,
                y + (height - 8) / 2 - 1, textColour, false);
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
