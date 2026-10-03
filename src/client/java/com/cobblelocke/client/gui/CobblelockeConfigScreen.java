package com.cobblelocke.client.gui;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.config.ConfigOptions;
import com.cobblelocke.eventlock.EncounterAnimations;
import com.cobblelocke.config.ConfigOptions.Kind;
import com.cobblelocke.config.ConfigOptions.Spec;
import com.cobblelocke.config.ConfigOptions.Tab;
import com.cobblelocke.net.SaveConfigPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CobblelockeConfigScreen extends Screen {
    private static final Identifier TITLE = Cobblelocke.id("textures/gui/title.png");
    private static final int TITLE_W = 1015;
    private static final int TITLE_H = 171;

    private static final int HEADER_H = 44;
    private static final int TAB_H = 20;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 20;
    private static final int VALUE_W = 150;

    private record Preset(String name, String description, JsonObject rules) {
    }

    private final CobblelockeConfig config;
    private final boolean canEdit;
    private final boolean raidDensInstalled;
    private final List<Preset> presets = new ArrayList<>();

    private Tab tab = Tab.NUZLOCKE;
    private final List<Spec> rows = new ArrayList<>();
    private int scroll = 0;
    private int selected = -1;
    private Spec dragging = null;
    private boolean titleFiltered = false;

    private String editingKey = null;
    private StringBuilder editBuffer = new StringBuilder();

    private boolean onHighHalf = false;

    public CobblelockeConfigScreen(CobblelockeConfig config, String presetsJson, boolean canEdit,
                                   boolean raidDensInstalled) {
        super(Text.literal("Cobblelocke"));
        this.config = config;
        this.canEdit = canEdit;
        this.raidDensInstalled = raidDensInstalled;
        try {
            JsonArray array = JsonParser.parseString(presetsJson).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject preset = element.getAsJsonObject();
                presets.add(new Preset(
                        preset.get("name").getAsString(),
                        preset.has("description") ? preset.get("description").getAsString() : "",
                        preset.has("rules") ? preset.getAsJsonObject("rules") : new JsonObject()));
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not read presets: {}", e.toString());
        }
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        for (Spec spec : ConfigOptions.all()) {
            if (spec.tab() == tab && isVisible(spec)) {
                rows.add(spec);
            }
        }
        clampScroll();
        if (selected >= rows.size()) {
            selected = rows.size() - 1;
        }
    }

    private boolean isVisible(Spec spec) {
        if (spec.parent() == null) {
            return true;
        }
        boolean negate = spec.parent().startsWith("!");
        String parentKey = negate ? spec.parent().substring(1) : spec.parent();
        boolean parentOn = getBool(parentKey);
        if (negate == parentOn) {
            return false;
        }
        Spec parent = ConfigOptions.byKey(parentKey);
        return parent == null || isVisible(parent);
    }

    private int depth(Spec spec) {
        int depth = 0;
        String parent = spec.parent();
        while (parent != null) {
            depth++;
            Spec up = ConfigOptions.byKey(parent.startsWith("!") ? parent.substring(1) : parent);
            parent = up == null ? null : up.parent();
        }
        return depth;
    }

    private boolean getBool(String key) {
        try {
            Field field = field(key);
            if (field.getType() == boolean.class) {
                return field.getBoolean(config);
            }
            if (field.getType() == int.class) {
                return field.getInt(config) != 0;
            }
            Object value = field.get(config);
            return value != null && !value.toString().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private String getString(String key) {
        try {
            Object value = field(key).get(config);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private int getInt(String key) {
        try {
            return field(key).getInt(config);
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(String key) {
        try {
            return (List<String>) field(key).get(config);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void set(String key, Object value) {
        try {
            field(key).set(config, value);
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not set {}: {}", key, e.toString());
        }
    }

    private static Field field(String key) throws NoSuchFieldException {
        return CobblelockeConfig.class.getField(key);
    }

    private void animationsChanged(String key) {
        if ("animationPreset".equals(key)) {
            config.applyAnimationPreset(getString(key));
        } else if (ConfigOptions.ANIMATION_KEYS.contains(key)) {
            config.animationPreset = "custom";
        }
    }

    private void edited() {
        config.preset = "Custom";
        rebuildRows();
        playClick();
    }

    private void step(Spec spec, int direction) {
        if (!canEdit || spec.isHeading()) {
            return;
        }
        switch (spec.kind()) {
            case TOGGLE -> set(spec.key(), !getBool(spec.key()));
            case CHOICE -> {
                int size = spec.labels().size();
                set(spec.key(), ((getInt(spec.key()) + direction) % size + size) % size);
            }
            case OPTION -> {
                int size = spec.ids().size();
                int next = ((spec.indexOfId(getString(spec.key())) + direction) % size + size) % size;
                set(spec.key(), spec.ids().get(next));
                animationsChanged(spec.key());
            }
            case SLIDER -> {
                int index = Math.max(0, Math.min(spec.values().size() - 1, sliderIndex(spec) + direction));
                set(spec.key(), spec.values().get(index));
            }
            case RANGE -> {
                String key = rangeKey(spec, onHighHalf);
                int index = Math.max(0, Math.min(spec.values().size() - 1,
                        indexOfValue(spec, getInt(key)) + direction));
                set(key, spec.values().get(index));
                clampRange(spec);
            }
            case LIST -> {
                openListEditor(spec);
                return;
            }
            default -> {
                return;
            }
        }
        edited();
    }

    private static String rangeKey(Spec spec, boolean high) {
        return spec.ids().get(high && spec.ids().size() > 1 ? 1 : 0);
    }

    private void clampRange(Spec spec) {
        int low = getInt(rangeKey(spec, false));
        int high = getInt(rangeKey(spec, true));
        if (low <= 0 || high <= 0 || low <= high) {
            return;
        }
        set(rangeKey(spec, !onHighHalf), onHighHalf ? high : low);
    }

    private int indexOfValue(Spec spec, int value) {
        int exact = spec.values().indexOf(value);
        if (exact >= 0) {
            return exact;
        }
        int best = 0;
        for (int i = 0; i < spec.values().size(); i++) {
            if (Math.abs(spec.values().get(i) - value) < Math.abs(spec.values().get(best) - value)) {
                best = i;
            }
        }
        return best;
    }

    private String halfLabel(Spec spec, boolean high) {
        int value = getInt(rangeKey(spec, high));
        int index = spec.values().indexOf(value);
        return index >= 0 && !spec.labels().isEmpty() ? spec.labels().get(index) : String.valueOf(value);
    }

    private int sliderIndex(Spec spec) {
        int value = getInt(spec.key());
        int exact = spec.values().indexOf(value);
        if (exact >= 0) {
            return exact;
        }
        int best = 0;
        for (int i = 0; i < spec.values().size(); i++) {
            if (Math.abs(spec.values().get(i) - value) < Math.abs(spec.values().get(best) - value)) {
                best = i;
            }
        }
        return best;
    }

    private String valueLabel(Spec spec) {
        return switch (spec.kind()) {
            case TOGGLE -> getBool(spec.key()) ? "On" : "Off";
            case CHOICE -> {
                int index = getInt(spec.key());
                yield index >= 0 && index < spec.labels().size() ? spec.labels().get(index) : "?";
            }
            case OPTION -> spec.labels().get(spec.indexOfId(getString(spec.key())));
            case SLIDER -> {
                int value = getInt(spec.key());
                int index = spec.values().indexOf(value);
                if (index >= 0 && !spec.labels().isEmpty()) {
                    yield spec.labels().get(index);
                }
                yield switch (spec.unit()) {
                    case "in" -> value <= 1 ? "Always" : "1 in " + value;
                    case "s" -> value <= 0 ? "Off" : duration(value);
                    default -> String.valueOf(value);
                };
            }
            case LIST -> {
                int size = getList(spec.key()).size();
                yield size == 0 ? "Empty ›" : size + (size == 1 ? " item ›" : " items ›");
            }
            default -> "";
        };
    }

    private static String duration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int rest = seconds % 60;
        if (hours > 0) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        if (minutes > 0) {
            return minutes + "m" + (rest > 0 ? " " + rest + "s" : "");
        }
        return rest + "s";
    }

    private void applyPreset(Preset preset) {
        CobblelockeConfig fromPreset = CobblelockeConfig.fromPreset(preset.name(), preset.rules());
        fromPreset.keepServerSettings(config);
        config.copyFrom(fromPreset);
        rebuildRows();
        playClick();
    }

    private void cyclePreset(int direction) {
        if (!canEdit || presets.isEmpty()) {
            return;
        }
        int current = -1;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name().equals(config.preset)) {
                current = i;
            }
        }
        int next = current < 0
                ? (direction > 0 ? 0 : presets.size() - 1)
                : ((current + direction) % presets.size() + presets.size()) % presets.size();
        applyPreset(presets.get(next));
    }

    private Preset currentPreset() {
        for (Preset preset : presets) {
            if (preset.name().equals(config.preset)) {
                return preset;
            }
        }
        return null;
    }

    private void beginEditing(Spec spec) {
        beginEditing(spec, spec.key());
    }

    private void beginEditing(Spec spec, String key) {
        if (!canEdit) {
            return;
        }
        editingKey = key;
        editBuffer = new StringBuilder();
        playClick();
    }

    private void commitEditing() {
        if (editingKey == null) {
            return;
        }
        String key = editingKey;
        String typed = editBuffer.toString();
        editingKey = null;
        editBuffer = new StringBuilder();
        if (typed.isEmpty()) {
            return;
        }
        try {
            set(key, Math.max(0, Integer.parseInt(typed)));
            Spec spec = ConfigOptions.byKey(key);
            if (spec != null && spec.kind() == Kind.RANGE) {
                clampRange(spec);
            }
            edited();
        } catch (NumberFormatException e) {
        }
    }

    private void cancelEditing() {
        editingKey = null;
        editBuffer = new StringBuilder();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingKey != null) {
            if (chr >= '0' && chr <= '9' && editBuffer.length() < 9) {
                editBuffer.append(chr);
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void previewAnimation(Spec spec) {
        String clip = EncounterAnimations.resolve(getString(spec.key()),
                EncounterAnimations.environmentFor(spec.key()), new java.util.Random());
        com.cobblelocke.client.cutscene.CutscenePlayer.play(clip);
        playClick();
    }

    private void openListEditor(Spec spec) {
        if (client != null) {
            client.setScreen(new ListEditScreen(this, spec.label(), spec.description(), getList(spec.key()),
                    values -> {
                        set(spec.key(), values);
                        edited();
                    }));
        }
    }

    private void openExport() {
        if (client == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (Preset preset : presets) {
            names.add(preset.name());
        }
        client.setScreen(new ExportPresetScreen(this, config.toJson(), names));
    }

    private void save(boolean startRun) {
        ClientPlayNetworking.send(new SaveConfigPayload(config.toJson(), startRun));
        close();
    }

    private int listLeft() {
        return 14;
    }

    private int listRight() {
        return Math.max(listLeft() + 260, (int) (width * 0.60));
    }

    private int listTop() {
        return HEADER_H + TAB_H + 8;
    }

    private int listBottom() {
        return height - FOOTER_H - 8;
    }

    private int valueLeft() {
        return listRight() - VALUE_W - 8;
    }

    private int valueRight() {
        return listRight() - 8;
    }

    private int numberBoxLeft() {
        return valueRight() - 58;
    }

    private int sliderBarRight() {
        return numberBoxLeft() - 6;
    }

    private static final int RANGE_BOX_W = 34;

    private int rangeBoxLeft() {
        return valueRight() - RANGE_BOX_W * 2 - 4;
    }

    private int previewButtonX() {
        return valueLeft() - 24;
    }

    private static boolean isAnimationRow(Spec spec) {
        return spec.key() != null && ConfigOptions.ANIMATION_KEYS.contains(spec.key());
    }

    private int presetBoxWidth() {
        return Math.min(220, width / 3);
    }

    private int presetBoxX() {
        return (width - presetBoxWidth()) / 2;
    }

    private void clampScroll() {
        int max = Math.max(0, rows.size() * ROW_H - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private record FooterButton(String label, boolean primary, int x, int y, int width, int height, Runnable action) {
    }

    private List<FooterButton> footerButtons() {
        List<FooterButton> buttons = new ArrayList<>();
        int y = height - FOOTER_H + 6;
        int h = 18;
        int x = width - 12;
        String[] labels = canEdit
                ? new String[]{"Cancel", "Export", "Save", "Start Run!"}
                : new String[]{"Close"};
        for (String label : labels) {
            int w = textRenderer.getWidth(label) + 28;
            x -= w;
            Runnable action = switch (label) {
                case "Export" -> this::openExport;
                case "Save" -> () -> save(false);
                case "Start Run!" -> () -> save(true);
                default -> this::close;
            };
            buttons.add(new FooterButton(label, label.equals("Start Run!"), x, y, w, h, action));
            x -= 8;
        }
        return buttons;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        drawHeader(context, mouseX, mouseY);
        drawTabs(context, mouseX, mouseY);
        drawList(context, mouseX, mouseY);
        drawDescription(context);
        drawFooter(context, mouseX, mouseY);
    }

    private void drawHeader(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, HEADER_H, Theme.MAIN);
        context.fill(0, HEADER_H - 3, width, HEADER_H, Theme.ACCENT);

        if (!titleFiltered && client != null) {
            client.getTextureManager().getTexture(TITLE).setFilter(true, false);
            titleFiltered = true;
        }
        int titleH = HEADER_H - 10;
        int titleW = titleH * TITLE_W / TITLE_H;
        RenderSystem.enableBlend();
        context.drawTexture(TITLE, 10, 3, titleW, titleH, 0.0f, 0.0f, TITLE_W, TITLE_H, TITLE_W, TITLE_H);

        int boxW = presetBoxWidth();
        int boxX = presetBoxX();
        int boxY = 12;
        boolean hovered = Theme.inside(mouseX, mouseY, boxX, boxY, boxW, 20);
        context.fill(boxX, boxY, boxX + boxW, boxY + 20, hovered && canEdit ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
        context.fill(boxX, boxY + 18, boxX + boxW, boxY + 20, Theme.ACCENT);
        context.drawText(textRenderer, "◀", boxX + 6, boxY + 6, Theme.ACCENT, false);
        context.drawText(textRenderer, "▶", boxX + boxW - 12, boxY + 6, Theme.ACCENT, false);
        String label = textRenderer.trimToWidth(config.preset, boxW - 40);
        context.drawText(textRenderer, label, boxX + (boxW - textRenderer.getWidth(label)) / 2, boxY + 6,
                Theme.TEXT, false);
        String caption = "PRESET";
        context.drawText(textRenderer, caption, boxX + (boxW - textRenderer.getWidth(caption)) / 2, 2,
                Theme.TEXT_MUTED, false);
        if (!canEdit) {
            String readOnly = "Read only";
            context.drawText(textRenderer, readOnly, width - textRenderer.getWidth(readOnly) - 12,
                    boxY + 6, Theme.ACCENT, false);
        }
    }

    private void drawTabs(DrawContext context, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        int left = listLeft();
        int total = listRight() - left;
        int tabW = total / tabs.length;
        int y = HEADER_H + 4;
        for (int i = 0; i < tabs.length; i++) {
            int x = left + i * tabW;
            boolean active = tabs[i] == tab;
            boolean hovered = Theme.inside(mouseX, mouseY, x, y, tabW - 2, TAB_H - 2);
            int fill = active ? Theme.ACCENT : hovered ? Theme.MAIN : Theme.MAIN_DARK;
            context.fill(x, y, x + tabW - 2, y + TAB_H - 2, fill);
            context.fill(x, y + TAB_H - 4, x + tabW - 2, y + TAB_H - 2, active ? Theme.ACCENT_DARK : Theme.MAIN_DEEP);
            String title = tabs[i].title;
            context.drawText(textRenderer, title, x + (tabW - 2 - textRenderer.getWidth(title)) / 2, y + 5,
                    active ? Theme.TEXT_ON_ACCENT : Theme.TEXT, false);
        }
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int left = listLeft();
        int right = listRight();
        int top = listTop();
        int bottom = listBottom();
        context.fill(left - 4, top - 4, right + 4, bottom + 4, Theme.PANEL);
        context.fill(left - 4, top - 4, right + 4, top - 3, 0x55FFFFFF);
        context.fill(left - 4, bottom + 3, right + 4, bottom + 4, 0x55FFFFFF);

        context.enableScissor(left - 4, top - 2, right + 4, bottom + 2);
        int y = top - scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_H) {
            if (y + ROW_H < top || y > bottom) {
                continue;
            }
            Spec spec = rows.get(i);
            if (spec.isHeading()) {
                drawHeading(context, spec, left, y, right);
                continue;
            }
            boolean isSelected = i == selected;
            drawRow(context, spec, left, y, right, isSelected, mouseX, mouseY);
        }
        context.disableScissor();

        int content = rows.size() * ROW_H;
        int view = bottom - top;
        if (content > view) {
            int trackX = right + 8;
            context.fill(trackX, top, trackX + 2, bottom, 0x44FFFFFF);
            int thumb = Math.max(18, view * view / content);
            int thumbY = top + (view - thumb) * scroll / Math.max(1, content - view);
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumb, Theme.TEXT);
        }
    }

    private void drawHeading(DrawContext context, Spec spec, int left, int y, int right) {
        context.drawText(textRenderer, spec.label().toUpperCase(), left + 4, y + 8, Theme.ACCENT, false);
        int lineX = left + 10 + textRenderer.getWidth(spec.label().toUpperCase());
        context.fill(lineX, y + 11, right - 4, y + 12, 0x44FFCB05);
    }

    private void drawRow(DrawContext context, Spec spec, int left, int y, int right, boolean isSelected,
                         int mouseX, int mouseY) {
        int indent = depth(spec) * 12;
        boolean disabled = spec.key().equals("disableRaidCatch") && !raidDensInstalled;
        if (isSelected) {
            context.fill(left + indent, y + 1, right, y + ROW_H - 1, Theme.ACCENT);
            context.fill(left + indent, y + ROW_H - 3, right, y + ROW_H - 1, Theme.ACCENT_DARK);

            context.drawText(textRenderer, "▶", left + indent - 9, y + 6, Theme.ACCENT, false);
        }
        int labelColour = isSelected ? Theme.TEXT_ON_ACCENT : disabled ? Theme.TEXT_DISABLED : Theme.TEXT;
        if (indent > 0 && !isSelected) {
            context.fill(left + indent - 6, y + 9, left + indent - 3, y + 10, 0x66FFFFFF);
        }

        int labelWidth = valueLeft() - left - indent - 12 - (isAnimationRow(spec) ? 26 : 0);
        String label = textRenderer.trimToWidth(spec.label(), labelWidth);
        context.drawText(textRenderer, label, left + indent + 6, y + 6, labelColour, false);

        int valueColour = isSelected ? Theme.TEXT_ON_ACCENT
                : spec.kind() == Kind.TOGGLE ? (getBool(spec.key()) ? Theme.ON : Theme.OFF)
                : Theme.TEXT_MUTED;
        if (spec.kind() == Kind.SLIDER) {
            drawSlider(context, spec, y, isSelected, valueColour);
            return;
        }
        if (spec.kind() == Kind.RANGE) {
            drawRange(context, spec, y, isSelected, valueColour);
            return;
        }
        String value = valueLabel(spec);
        int center = (valueLeft() + valueRight()) / 2;
        context.drawText(textRenderer, value, center - textRenderer.getWidth(value) / 2, y + 6, valueColour, false);
        if (isAnimationRow(spec)) {
            drawPreviewButton(context, y, Theme.inside(mouseX, mouseY, previewButtonX(), y + 4, 16, ROW_H - 8));
        }
        if (isSelected && canEdit && spec.kind() != Kind.LIST) {
            drawArrow(context, valueLeft() + 4, y, true);
            drawArrow(context, valueRight() - 14, y, false);
        }
    }

    private void drawSlider(DrawContext context, Spec spec, int y, boolean isSelected, int valueColour) {
        int barLeft = valueLeft() + 16;
        int barRight = sliderBarRight();
        int barY = y + 9;
        int index = sliderIndex(spec);
        int steps = Math.max(1, spec.values().size() - 1);
        int knob = barLeft + (barRight - barLeft) * index / steps;
        int track = isSelected ? Theme.MAIN_DEEP : 0x88FFFFFF;
        context.fill(barLeft, barY, barRight, barY + 2, track);
        context.fill(barLeft, barY, knob, barY + 2, isSelected ? Theme.MAIN_DARK : Theme.TEXT);
        context.fill(knob - 1, barY - 4, knob + 2, barY + 6, isSelected ? Theme.MAIN_DEEP : Theme.TEXT);
        drawNumberBox(context, spec, y, isSelected, valueColour);
        if (isSelected && canEdit) {
            drawArrow(context, valueLeft() + 2, y, true);
        }
    }

    private void drawNumberBox(DrawContext context, Spec spec, int y, boolean isSelected, int valueColour) {
        int left = numberBoxLeft();
        int right = valueRight();
        boolean editing = spec.key().equals(editingKey);
        context.fill(left, y + 3, right, y + ROW_H - 3, editing ? Theme.MAIN_DEEP : 0x33000000);
        if (editing) {
            context.fill(left, y + ROW_H - 5, right, y + ROW_H - 3, Theme.ACCENT);
        }
        String text = editing ? editBuffer + "_" : valueLabel(spec);
        text = textRenderer.trimToWidth(text, right - left - 6);
        context.drawText(textRenderer, text, right - 3 - textRenderer.getWidth(text), y + 6,
                editing ? Theme.TEXT : valueColour, false);
    }

    private void drawRange(DrawContext context, Spec spec, int y, boolean isSelected, int valueColour) {
        int barLeft = valueLeft() + 16;
        int barRight = rangeBoxLeft() - 6;
        int barY = y + 9;
        int steps = Math.max(1, spec.values().size() - 1);
        int lowKnob = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, false))) / steps;
        int highKnob = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, true))) / steps;

        context.fill(barLeft, barY, barRight, barY + 2, isSelected ? Theme.MAIN_DEEP : 0x88FFFFFF);
        context.fill(Math.min(lowKnob, highKnob), barY, Math.max(lowKnob, highKnob), barY + 2,
                isSelected ? Theme.MAIN_DARK : Theme.TEXT);
        for (int knob : new int[]{lowKnob, highKnob}) {
            context.fill(knob - 1, barY - 4, knob + 2, barY + 6, isSelected ? Theme.MAIN_DEEP : Theme.TEXT);
        }
        drawHalfBox(context, spec, false, y, isSelected, valueColour);
        drawHalfBox(context, spec, true, y, isSelected, valueColour);
    }

    private void drawHalfBox(DrawContext context, Spec spec, boolean high, int y, boolean isSelected,
                             int valueColour) {
        int left = high ? rangeBoxLeft() + RANGE_BOX_W + 4 : rangeBoxLeft();
        int right = left + RANGE_BOX_W;
        String key = rangeKey(spec, high);
        boolean editing = key.equals(editingKey);
        boolean active = isSelected && onHighHalf == high;
        context.fill(left, y + 3, right, y + ROW_H - 3, editing ? Theme.MAIN_DEEP : 0x33000000);
        if (editing || active) {
            context.fill(left, y + ROW_H - 5, right, y + ROW_H - 3, Theme.ACCENT);
        }
        String text = editing ? editBuffer + "_" : halfLabel(spec, high);
        text = textRenderer.trimToWidth(text, right - left - 6);
        context.drawText(textRenderer, text, right - 3 - textRenderer.getWidth(text), y + 6,
                editing ? Theme.TEXT : valueColour, false);
    }

    private void drawPreviewButton(DrawContext context, int y, boolean hovered) {
        int x = previewButtonX();
        context.fill(x, y + 4, x + 16, y + ROW_H - 4, hovered ? Theme.ACCENT_DARK : Theme.MAIN_DEEP);
        context.drawText(textRenderer, "▶", x + 5, y + 6, hovered ? Theme.TEXT_ON_ACCENT : Theme.ACCENT, false);
    }

    private void drawArrow(DrawContext context, int x, int y, boolean left) {
        context.fill(x, y + 4, x + 11, y + 15, Theme.MAIN_DEEP);
        context.fill(x + 1, y + 3, x + 10, y + 16, Theme.MAIN_DEEP);
        context.drawText(textRenderer, left ? "◀" : "▶", x + 2, y + 6, Theme.TEXT, false);
    }

    private void drawDescription(DrawContext context) {
        int left = listRight() + 20;
        int right = width - 14;
        int top = listTop() + 6;
        if (right - left < 80) {
            return;
        }
        String title;
        String body;
        Spec spec = selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
        if (spec != null && !spec.isHeading()) {
            title = spec.label();
            body = spec.description();
            if (spec.key().equals("disableRaidCatch") && !raidDensInstalled) {
                body += " Cobblemon Raid Dens is not installed, so this has no effect.";
            }
        } else {
            Preset preset = currentPreset();
            title = preset != null ? preset.name() : "Custom rules";
            body = preset != null ? preset.description()
                    : "Hover or use the arrow keys to pick an option. Q and E switch tabs.";
        }
        context.fill(left, top, left + 3, top + 12, Theme.ACCENT);
        context.drawText(textRenderer, title, left + 9, top + 2, Theme.TEXT, true);
        int y = top + 22;
        for (var line : textRenderer.wrapLines(Text.literal(body), right - left - 9)) {
            context.drawText(textRenderer, line, left + 9, y, Theme.TEXT_MUTED, true);
            y += 11;
        }
    }

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, height - FOOTER_H, width, height, Theme.MAIN);
        context.fill(0, height - FOOTER_H, width, height - FOOTER_H + 2, Theme.ACCENT);
        context.drawText(textRenderer, "Q/E: tabs   ◀ ▶: change   ↑ ↓: select", 12, height - FOOTER_H + 11,
                Theme.TEXT_MUTED, false);
        for (FooterButton button : footerButtons()) {
            Theme.button(context, textRenderer, button.label(), button.x(), button.y(), button.width(),
                    button.height(), button.primary(),
                    Theme.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height()), true);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && !rows.get(index).isHeading()) {
            selected = index;
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft() || mouseX >= listRight() || mouseY < listTop() || mouseY >= listBottom()) {
            return -1;
        }
        int index = ((int) mouseY - listTop() + scroll) / ROW_H;
        return index >= 0 && index < rows.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (editingKey != null) {
            commitEditing();
        }
        for (FooterButton footer : footerButtons()) {
            if (Theme.inside(mouseX, mouseY, footer.x(), footer.y(), footer.width(), footer.height())) {
                playClick();
                footer.action().run();
                return true;
            }
        }
        int boxX = presetBoxX();
        if (Theme.inside(mouseX, mouseY, boxX, 12, presetBoxWidth(), 20)) {
            cyclePreset(mouseX < boxX + presetBoxWidth() / 2.0 ? -1 : 1);
            return true;
        }
        Tab[] tabs = Tab.values();
        int tabW = (listRight() - listLeft()) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            if (Theme.inside(mouseX, mouseY, listLeft() + i * tabW, HEADER_H + 4, tabW - 2, TAB_H - 2)) {
                switchTab(tabs[i]);
                return true;
            }
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || !canEdit) {
            return false;
        }
        Spec spec = rows.get(index);
        if (spec.isHeading()) {
            return false;
        }
        selected = index;
        clickValue(spec, mouseX);
        return true;
    }

    private void clickValue(Spec spec, double mouseX) {
        if (isAnimationRow(spec) && mouseX >= previewButtonX() && mouseX < previewButtonX() + 16) {
            previewAnimation(spec);
            return;
        }
        switch (spec.kind()) {
            case SLIDER -> {
                if (mouseX >= numberBoxLeft()) {
                    beginEditing(spec);
                } else if (mouseX < valueLeft() + 14) {
                    step(spec, -1);
                } else {
                    dragging = spec;
                    setSliderFromMouse(spec, mouseX);
                }
            }
            case RANGE -> {
                if (mouseX >= rangeBoxLeft()) {
                    onHighHalf = mouseX >= rangeBoxLeft() + RANGE_BOX_W + 4;
                    beginEditing(spec, rangeKey(spec, onHighHalf));
                } else {
                    onHighHalf = nearerHalfIsHigh(spec, mouseX);
                    dragging = spec;
                    setRangeFromMouse(spec, mouseX);
                }
            }
            case CHOICE, OPTION -> step(spec, mouseX < valueLeft() + VALUE_W / 3.0 ? -1 : 1);
            default -> step(spec, 1);
        }
    }

    private void setSliderFromMouse(Spec spec, double mouseX) {
        int barLeft = valueLeft() + 16;
        int barRight = sliderBarRight();
        double fraction = Math.max(0.0, Math.min(1.0, (mouseX - barLeft) / (double) (barRight - barLeft)));
        int index = (int) Math.round(fraction * (spec.values().size() - 1));
        int value = spec.values().get(index);
        if (value != getInt(spec.key())) {
            set(spec.key(), value);
            edited();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging == null) {
            return false;
        }
        if (dragging.kind() == Kind.RANGE) {
            setRangeFromMouse(dragging, mouseX);
        } else {
            setSliderFromMouse(dragging, mouseX);
        }
        return true;
    }

    private boolean nearerHalfIsHigh(Spec spec, double mouseX) {
        int barLeft = valueLeft() + 16;
        int barRight = rangeBoxLeft() - 6;
        int steps = Math.max(1, spec.values().size() - 1);
        int low = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, false))) / steps;
        int high = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, true))) / steps;
        return Math.abs(mouseX - high) <= Math.abs(mouseX - low);
    }

    private void setRangeFromMouse(Spec spec, double mouseX) {
        int barLeft = valueLeft() + 16;
        int barRight = rangeBoxLeft() - 6;
        double fraction = Math.max(0.0, Math.min(1.0, (mouseX - barLeft) / (double) (barRight - barLeft)));
        int value = spec.values().get((int) Math.round(fraction * (spec.values().size() - 1)));
        String key = rangeKey(spec, onHighHalf);
        if (value != getInt(key)) {
            set(key, value);
            clampRange(spec);
            edited();
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) (verticalAmount * ROW_H);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingKey != null) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> cancelEditing();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> commitEditing();
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (editBuffer.length() > 0) {
                        editBuffer.setLength(editBuffer.length() - 1);
                    }
                }
                default -> {
                    return true;
                }
            }
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                selectedSpec(spec -> step(spec, -1));
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> {
                selectedSpec(spec -> step(spec, 1));
                return true;
            }
            case GLFW.GLFW_KEY_Q -> {
                switchTab(Tab.values()[(tab.ordinal() + Tab.values().length - 1) % Tab.values().length]);
                return true;
            }
            case GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_TAB -> {
                switchTab(Tab.values()[(tab.ordinal() + 1) % Tab.values().length]);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void selectedSpec(java.util.function.Consumer<Spec> action) {
        if (selected >= 0 && selected < rows.size() && !rows.get(selected).isHeading()) {
            action.accept(rows.get(selected));
        }
    }

    private void moveSelection(int direction) {
        if (rows.isEmpty()) {
            return;
        }
        int index = selected;
        for (int i = 0; i < rows.size(); i++) {
            index = Math.floorMod(index + direction, rows.size());
            if (!rows.get(index).isHeading()) {
                break;
            }
        }
        selected = index;
        int rowTop = index * ROW_H;
        int view = listBottom() - listTop();
        if (rowTop < scroll) {
            scroll = rowTop;
        } else if (rowTop + ROW_H > scroll + view) {
            scroll = rowTop + ROW_H - view;
        }
        clampScroll();
    }

    private void switchTab(Tab next) {
        if (next != tab) {
            tab = next;
            scroll = 0;
            selected = -1;
            rebuildRows();
            playClick();
        }
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
