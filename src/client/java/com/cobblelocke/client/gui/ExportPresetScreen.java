package com.cobblelocke.client.gui;

import com.cobblelocke.net.ExportPresetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.Locale;

public class ExportPresetScreen extends Screen {
    private static final int MAX_LENGTH = 40;
    private static final int PANEL_W = 300;
    private static final int BUTTON_W = 110;
    private static final int BUTTON_H = 18;

    private final Screen parent;
    private final String configJson;
    private final List<String> existingNames;
    private final Consumer<String> onExported;

    private TextFieldWidget field;

    public ExportPresetScreen(Screen parent, String configJson, List<String> existingNames,
                              Consumer<String> onExported) {
        super(Text.literal("Export Preset"));
        this.parent = parent;
        this.configJson = configJson;
        this.existingNames = existingNames;
        this.onExported = onExported;
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(textRenderer, width / 2 - 110, top() + 52, 220, 20,
                Text.literal("Preset name"));
        field.setMaxLength(MAX_LENGTH);
        addSelectableChild(field);
        setInitialFocus(field);
    }

    private int top() {
        return height / 2 - 70;
    }

    private String name() {
        return field == null ? "" : field.getText().trim();
    }

    private boolean canConfirm() {
        return !name().isEmpty();
    }

    private boolean overwrites() {
        String wanted = name().toLowerCase(Locale.ROOT);
        for (String existing : existingNames) {
            if (existing.toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    private int confirmX() {
        return width / 2 + 6;
    }

    private int cancelX() {
        return width / 2 - BUTTON_W - 6;
    }

    private int buttonY() {
        return top() + 118;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (width - PANEL_W) / 2;
        int top = top();
        int bottom = top + 146;
        context.fill(left, top, left + PANEL_W, bottom, Theme.PANEL);
        context.fill(left, top, left + PANEL_W, top + 22, Theme.MAIN);
        context.fill(left, top + 20, left + PANEL_W, top + 22, Theme.ACCENT);

        String header = Tr.get("ui.export.header", "Export these settings as a preset");
        context.drawText(textRenderer, header, (width - textRenderer.getWidth(header)) / 2, top + 7,
                Theme.TEXT, false);

        String prompt = Tr.get("ui.export.prompt", "Name your preset! It is written to presets.json5 and "
                + "appears in the preset switcher at the top.");
        int promptY = top + 30;
        for (var line : textRenderer.wrapLines(Text.literal(prompt), PANEL_W - 28)) {
            context.drawText(textRenderer, line, left + 14, promptY, Theme.TEXT_MUTED, false);
            promptY += 10;
        }

        field.render(context, mouseX, mouseY, delta);

        String note = overwrites()
                ? "§e" + Tr.get("ui.export.overwrite", "A preset called %s already exists and will be replaced.", name())
                : "§7" + Tr.get("ui.export.tip", "You can replace an existing preset by giving it the same name.");
        int noteY = top + 80;
        for (var line : textRenderer.wrapLines(Text.literal(note), PANEL_W - 28)) {
            context.drawText(textRenderer, line, left + 14, noteY, Theme.TEXT_MUTED, false);
            noteY += 10;
        }

        Theme.button(context, textRenderer, Tr.get("ui.cancel", "Cancel"), cancelX(), buttonY(), BUTTON_W, BUTTON_H, false,
                Theme.inside(mouseX, mouseY, cancelX(), buttonY(), BUTTON_W, BUTTON_H), true);
        Theme.button(context, textRenderer, overwrites() ? Tr.get("ui.replace", "Replace") : Tr.get("ui.export", "Export"),
                confirmX(), buttonY(),
                BUTTON_W, BUTTON_H, true,
                Theme.inside(mouseX, mouseY, confirmX(), buttonY(), BUTTON_W, BUTTON_H), canConfirm());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Theme.inside(mouseX, mouseY, cancelX(), buttonY(), BUTTON_W, BUTTON_H)) {
            back();
            return true;
        }
        if (Theme.inside(mouseX, mouseY, confirmX(), buttonY(), BUTTON_W, BUTTON_H)) {
            confirm();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        if (!canConfirm()) {
            return;
        }
        ClientPlayNetworking.send(new ExportPresetPayload(name(), configJson));
        onExported.accept(name());
        back();
    }

    private void back() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
