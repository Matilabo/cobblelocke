package com.cobblelocke.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListEditScreen extends Screen {
    private final Screen parent;
    private final String description;
    private final List<String> initial;
    private final Consumer<List<String>> onDone;
    private TextFieldWidget field;

    public ListEditScreen(Screen parent, String title, String description, List<String> initial,
                          Consumer<List<String>> onDone) {
        super(Text.literal(title));
        this.parent = parent;
        this.description = description;
        this.initial = initial;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(420, width - 40);
        field = new TextFieldWidget(textRenderer, (width - fieldWidth) / 2, height / 2 - 10, fieldWidth, 20,
                Text.literal("ids"));
        field.setMaxLength(4000);
        field.setText(String.join(", ", initial));
        addSelectableChild(field);
        setInitialFocus(field);
    }

    private int buttonY() {
        return height / 2 + 24;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int panelW = Math.min(460, width - 20);
        int left = (width - panelW) / 2;
        int top = height / 2 - 70;
        context.fill(left, top, left + panelW, height / 2 + 52, Theme.PANEL);
        context.fill(left, top, left + panelW, top + 22, Theme.MAIN);
        context.fill(left, top + 20, left + panelW, top + 22, Theme.ACCENT);
        context.drawText(textRenderer, title, left + 10, top + 7, Theme.TEXT, false);
        int y = top + 30;
        for (var line : textRenderer.wrapLines(Text.literal(description + " "
                + Tr.get("ui.list.separate", "Separate ids with commas.")), panelW - 20)) {
            context.drawText(textRenderer, line, left + 10, y, Theme.TEXT_MUTED, false);
            y += 10;
        }
        field.render(context, mouseX, mouseY, delta);
        int center = width / 2;
        Theme.button(context, textRenderer, Tr.get("ui.done", "Done"), center - 84, buttonY(), 80, 18, true,
                Theme.inside(mouseX, mouseY, center - 84, buttonY(), 80, 18), true);
        Theme.button(context, textRenderer, Tr.get("ui.cancel", "Cancel"), center + 4, buttonY(), 80, 18, false,
                Theme.inside(mouseX, mouseY, center + 4, buttonY(), 80, 18), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int center = width / 2;
        if (Theme.inside(mouseX, mouseY, center - 84, buttonY(), 80, 18)) {
            finish(true);
            return true;
        }
        if (Theme.inside(mouseX, mouseY, center + 4, buttonY(), 80, 18)) {
            finish(false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finish(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void finish(boolean keep) {
        if (keep) {
            List<String> values = new ArrayList<>();
            for (String part : field.getText().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            onDone.accept(values);
        }
        close();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
