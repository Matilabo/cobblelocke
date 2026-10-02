package com.cobblelocke.util;

import com.cobblelocke.Cobblelocke;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RegionalDex {
    private static final String PATH = "/data/cobblelocke/regional_dex.json";

    private static volatile Map<String, List<String>> regions;

    private RegionalDex() {
    }

    public static List<String> speciesOf(String region) {
        return load().getOrDefault(region.toLowerCase(Locale.ROOT), List.of());
    }

    public static boolean knows(String region) {
        return load().containsKey(region.toLowerCase(Locale.ROOT));
    }

    private static Map<String, List<String>> load() {
        Map<String, List<String>> cached = regions;
        if (cached != null) {
            return cached;
        }
        Map<String, List<String>> built = new LinkedHashMap<>();
        try (InputStream stream = RegionalDex.class.getResourceAsStream(PATH)) {
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
                    List<String> names = new ArrayList<>(array.size());
                    for (JsonElement element : array) {
                        names.add(element.getAsString().toLowerCase(Locale.ROOT));
                    }
                    built.put(entry.getKey().toLowerCase(Locale.ROOT), Collections.unmodifiableList(names));
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not read the regional dex listings: {}", e.toString());
        }
        regions = built;
        return built;
    }
}
