package com.cobblelocke.client.cutscene;

import com.cobblelocke.Cobblelocke;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CutsceneClip(
        String id,
        int fps,
        int width,
        int height,
        boolean transparent,
        boolean smooth,
        int durationMs,
        int battleAtMs,
        int coverAtMs,
        int fadeOutMs,
        List<Frame> frames) {
    public record Frame(byte[] colour, byte[] alpha) {
    }

    public int frameCount() {
        return frames.size();
    }

    public static CutsceneClip load(String id) throws IOException {
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
        String base = "cutscenes/" + id + "/";

        JsonObject meta;
        try (InputStream stream = open(resources, Cobblelocke.id(base + "meta.json"))) {
            meta = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        JsonArray names = meta.getAsJsonArray("frames");
        List<Frame> frames = new ArrayList<>(names.size());
        Map<String, byte[]> cache = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String[] parts = names.get(i).getAsString().split("\\|");
            byte[] colour = read(resources, cache, base + parts[0]);
            byte[] alpha = parts.length > 1 ? read(resources, cache, base + parts[1]) : null;
            frames.add(new Frame(colour, alpha));
        }

        int fps = meta.get("fps").getAsInt();
        return new CutsceneClip(
                id,
                fps,
                meta.get("width").getAsInt(),
                meta.get("height").getAsInt(),
                meta.get("transparent").getAsBoolean(),
                meta.get("smooth").getAsBoolean(),
                meta.has("durationMs") ? meta.get("durationMs").getAsInt() : frames.size() * 1000 / fps,
                meta.get("battleAtMs").getAsInt(),
                meta.has("coverAtMs") ? meta.get("coverAtMs").getAsInt()
                        : meta.get("battleAtMs").getAsInt(),
                meta.has("fadeOutMs") ? meta.get("fadeOutMs").getAsInt() : 0,
                frames);
    }

    private static byte[] read(ResourceManager resources, Map<String, byte[]> cache, String path)
            throws IOException {
        byte[] cached = cache.get(path);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = open(resources, Cobblelocke.id(path))) {
            byte[] bytes = stream.readAllBytes();
            cache.put(path, bytes);
            return bytes;
        }
    }

    private static InputStream open(ResourceManager resources, Identifier id) throws IOException {
        Optional<Resource> resource = resources.getResource(id);
        if (resource.isEmpty()) {
            throw new IOException("Missing cutscene resource " + id);
        }
        return resource.get().getInputStream();
    }
}
