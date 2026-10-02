package com.cobblelocke.compat;

import com.cobblelocke.Cobblelocke;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GymLeaders {
    private static final String MOB_PATH = "mobs/trainers/single";

    public static final int GYMS_PER_REGION = 8;

    public static final int ELITES_PER_REGION = 4;

    public enum Role {
        GYM,
        ELITE,
        CHAMPION
    }

    public record Slot(Role role, int number) {
    }

    private static volatile Map<String, Slot> slots = null;

    private GymLeaders() {
    }

    public static void invalidate() {
        slots = null;
    }

    public static Slot slotFor(String trainerId) {
        if (trainerId == null || trainerId.isBlank()) {
            return null;
        }
        return lookup().get(trainerId.toLowerCase(Locale.ROOT));
    }

    public static int gymNumber(String trainerId) {
        Slot slot = slotFor(trainerId);
        return slot != null && slot.role() == Role.GYM ? slot.number() : 0;
    }

    public static boolean isGymLeader(String trainerId) {
        return slotFor(trainerId) != null;
    }

    public static int known() {
        return lookup().size();
    }

    public static int known(Role role) {
        return count(lookup(), role);
    }

    private static int count(Map<String, Slot> found, Role role) {
        int count = 0;
        for (Slot slot : found.values()) {
            if (slot.role() == role) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, Slot> lookup() {
        Map<String, Slot> cached = slots;
        if (cached != null) {
            return cached;
        }
        Map<String, Slot> built = new HashMap<>();
        MinecraftServer server = Cobblelocke.getServer();
        if (server == null) {
            return built;
        }
        try {
            Map<String, Mob> mobs = readMobs(server);
            Map<String, Map<Role, List<String>>> regions = byRegion(mobs);
            for (Map.Entry<String, Map<Role, List<String>>> region : regions.entrySet()) {
                assign(region.getValue(), mobs, built);
            }
            slots = built;
            if (!built.isEmpty()) {
                Cobblelocke.LOGGER.info("Found {} gym leaders, {} elite four members and {} champions "
                                + "across {} regions",
                        count(built, Role.GYM), count(built, Role.ELITE), count(built, Role.CHAMPION),
                        regions.size());
            } else {
                Cobblelocke.LOGGER.info("No Radical Cobblemon Trainers gym data found, so the per-gym "
                        + "settings will not apply");
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not read the gym leader data: {}", e.toString());
            slots = built;
        }
        return built;
    }

    private static void assign(Map<Role, List<String>> region, Map<String, Mob> mobs,
                               Map<String, Slot> into) {
        List<String> gyms = chain(region.getOrDefault(Role.GYM, List.of()), mobs);
        List<String> elites = chain(region.getOrDefault(Role.ELITE, List.of()), mobs);
        List<String> champions = chain(region.getOrDefault(Role.CHAMPION, List.of()), mobs);

        if (elites.isEmpty() && gyms.size() > GYMS_PER_REGION) {
            elites = new ArrayList<>(gyms.subList(GYMS_PER_REGION, gyms.size()));
        }

        for (int i = 0; i < Math.min(GYMS_PER_REGION, gyms.size()); i++) {
            into.put(gyms.get(i), new Slot(Role.GYM, i + 1));
        }
        for (int i = 0; i < Math.min(ELITES_PER_REGION, elites.size()); i++) {
            into.put(elites.get(i), new Slot(Role.ELITE, i + 1));
        }
        for (String champion : champions) {
            into.put(champion, new Slot(Role.CHAMPION, 1));
        }
    }

    private record Mob(String series, Role role, List<String> mustBeat) {
    }

    private static Map<String, Mob> readMobs(MinecraftServer server) {
        Map<String, Mob> mobs = new LinkedHashMap<>();
        Map<Identifier, Resource> files = server.getResourceManager()
                .findResources(MOB_PATH, id -> id.getPath().endsWith(".json")
                        && "rctmod".equals(id.getNamespace()));
        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            Mob mob = readMob(entry.getValue());
            if (mob != null) {
                String path = entry.getKey().getPath();
                String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                mobs.put(name.toLowerCase(Locale.ROOT), mob);
            }
        }
        return mobs;
    }

    private static Mob readMob(Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray series = root.getAsJsonArray("series");
            if (series == null || series.isEmpty() || !root.has("signatureItem")) {
                return null;
            }
            String home = series.get(0).getAsString().toLowerCase(Locale.ROOT);
            String type = root.has("type") ? root.get("type").getAsString().toLowerCase(Locale.ROOT) : "";

            Role role;
            if (type.equals(home)) {
                role = Role.GYM;
            } else if (type.equals(home + "_league")) {
                role = Role.ELITE;
            } else if (type.equals(home + "_champion")) {
                role = Role.CHAMPION;
            } else {
                return null;
            }

            List<String> mustBeat = new ArrayList<>();
            JsonElement required = root.get("requiredDefeats");
            if (required != null && required.isJsonArray()) {
                for (JsonElement group : required.getAsJsonArray()) {
                    if (group.isJsonArray()) {
                        for (JsonElement id : group.getAsJsonArray()) {
                            mustBeat.add(id.getAsString().toLowerCase(Locale.ROOT));
                        }
                    } else {
                        mustBeat.add(group.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
            }
            return new Mob(home, role, mustBeat);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Map<Role, List<String>>> byRegion(Map<String, Mob> mobs) {
        Map<String, Map<Role, List<String>>> regions = new LinkedHashMap<>();
        mobs.forEach((name, mob) -> regions
                .computeIfAbsent(mob.series(), key -> new LinkedHashMap<>())
                .computeIfAbsent(mob.role(), key -> new ArrayList<>())
                .add(name));
        return regions;
    }

    private static List<String> chain(List<String> members, Map<String, Mob> mobs) {
        Set<String> group = new HashSet<>(members);
        List<String> order = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        List<String> waiting = new ArrayList<>(members);
        waiting.sort(String::compareTo);

        while (!waiting.isEmpty()) {
            String next = null;
            for (String candidate : waiting) {
                boolean ready = true;
                for (String required : mobs.get(candidate).mustBeat()) {
                    if (group.contains(required) && !placed.contains(required)) {
                        ready = false;
                        break;
                    }
                }
                if (ready) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                next = waiting.get(0);
            }
            order.add(next);
            placed.add(next);
            waiting.remove(next);
        }
        return order;
    }
}
