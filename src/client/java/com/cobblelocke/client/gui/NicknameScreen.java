package com.cobblelocke.client.gui;

import com.cobblelocke.net.SetNicknamePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class NicknameScreen extends Screen {
    private static final int MAX_LENGTH = 12;

    private final UUID pokemonId;
    private final String species;
    private TextFieldWidget field;

    public NicknameScreen(UUID pokemonId, String species) {
        super(Text.literal("Nickname"));
        this.pokemonId = pokemonId;
        this.species = species;
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(textRenderer, width / 2 - 80, height / 2 - 6, 160, 20, Text.literal("Nickname"));
        field.setMaxLength(MAX_LENGTH);
        addSelectableChild(field);
        setInitialFocus(field);
    }

    private boolean canConfirm() {
        return field != null && !field.getText().trim().isEmpty();
    }

    private int buttonX() {
        return width / 2 - 50;
    }

    private int buttonY() {
        return height / 2 + 24;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int panelW = 240;
        int left = (width - panelW) / 2;
        int top = height / 2 - 58;
        context.fill(left, top, left + panelW, height / 2 + 52, Theme.PANEL);
        context.fill(left, top, left + panelW, top + 22, Theme.MAIN);
        context.fill(left, top + 20, left + panelW, top + 22, Theme.ACCENT);
        String header = "You caught " + species + "!";
        context.drawText(textRenderer, header, (width - textRenderer.getWidth(header)) / 2, top + 7, Theme.TEXT, false);
        String prompt = "Give it a nickname (Nuzlocke rule)";
        context.drawText(textRenderer, prompt, (width - textRenderer.getWidth(prompt)) / 2, top + 32,
                Theme.TEXT_MUTED, false);
        field.render(context, mouseX, mouseY, delta);
        Theme.button(context, textRenderer, "Confirm", buttonX(), buttonY(), 100, 18, true,
                Theme.inside(mouseX, mouseY, buttonX(), buttonY(), 100, 18), canConfirm());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Theme.inside(mouseX, mouseY, buttonX(), buttonY(), 100, 18)) {
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        if (!canConfirm()) {
            return;
        }
        ClientPlayNetworking.send(new SetNicknamePayload(pokemonId, field.getText().trim()));
        if (client != null) {
            client.setScreen(null);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
