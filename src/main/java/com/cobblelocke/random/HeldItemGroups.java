package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HeldItemGroups {
    private static final String PATH = "/data/cobblelocke/held_item_groups.json";

    private static volatile Map<String, List<String>> groups = null;

    private HeldItemGroups() {
    }

    public static List<Item> resolve(List<String> entries) {
        Set<Item> items = new LinkedHashSet<>();
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.startsWith("#")) {
                expand(entry.substring(1), items);
            } else {
                Item item = byId(entry);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return new ArrayList<>(items);
    }

    private static void expand(String name, Set<Item> into) {
        List<String> shipped = load().get(name);
        if (shipped != null) {
            for (String id : shipped) {
                Item item = byId(id);
                if (item != null) {
                    into.add(item);
                }
            }
            return;
        }

        Identifier id = Identifier.tryParse(name);
        if (id == null) {
            return;
        }
        try {
            Registries.ITEM.getEntryList(TagKey.of(RegistryKeys.ITEM, id))
                    .ifPresent(list -> list.forEach(entry -> into.add(entry.value())));
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("No item tag {}: {}", name, e.toString());
        }
    }

    private static Item byId(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null || !Registries.ITEM.containsId(id)) {
            return null;
        }
        Item item = Registries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    private static Map<String, List<String>> load() {
        Map<String, List<String>> cached = groups;
        if (cached != null) {
            return cached;
        }
        Map<String, List<String>> built = new LinkedHashMap<>();
        try (InputStream stream = HeldItemGroups.class.getResourceAsStream(PATH)) {
            if (stream == null) {
                Cobblelocke.LOGGER.error("{} is missing from the mod jar", PATH);
            } else {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (!entry.getValue().isJsonArray()) {
                        continue;
                    }
                    JsonArray array = entry.getValue().getAsJsonArray();
                    List<String> ids = new ArrayList<>(array.size());
                    for (JsonElement element : array) {
                        ids.add(element.getAsString().toLowerCase(Locale.ROOT));
                    }
                    built.put(entry.getKey().toLowerCase(Locale.ROOT), ids);
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not read the held item groups: {}", e.toString());
        }
        groups = built;
        return built;
    }

    public static Set<String> names() {
        return load().keySet();
    }
}
