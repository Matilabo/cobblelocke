package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class HeldItemRoller {
    private static final Set<String> ALLOWED_NAMESPACES = Set.of("cobblemon", "mega_showdown");

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "rare_candy", "exp_candy", "exp_share", "medicinal_leek", "tm_material", "link_cable",
            "cracked_pot", "chipped_pot", "sweet_apple", "tart_apple", "galarica_cuff",
            "galarica_wreath", "black_augurite", "peat_block", "auspicious_armor",
            "malicious_armor", "masterpiece_teacup", "unremarkable_teacup", "cornerstone_mask",
            "wellspring_mask", "hearthflame_mask", "metal_alloy", "scroll_of_darkness",
            "potion", "super_potion", "hyper_potion", "max_potion", "full_restore", "revive",
            "max_revive", "ether", "max_ether", "elixir", "max_elixir", "antidote",
            "paralyze_heal", "awakening", "burn_heal", "ice_heal", "full_heal", "fresh_water",
            "soda_pop", "lemonade", "moomoo_milk", "energy_powder", "energy_root", "heal_powder",
            "revival_herb", "vivichoke", "superb_remedy", "fine_remedy", "remedy",
            "hp_up", "protein", "iron", "calcium", "zinc", "carbos", "pp_up", "pp_max",
            "ability_capsule", "ability_patch", "bottle_cap", "gold_bottle_cap", "sacred_ash",
            "smoke_ball", "razor_claw", "razor_fang", "lagging_tail", "prism_scale",
            "data_monitor", "pc", "maxies_glasses", "archies_glasses", "likos_pendant",
            "relic_coin", "relic_coin_pouch", "relic_coin_sack");

    private static final String[] EXCLUDED_FRAGMENTS = {
            "fossil", "_ore", "_block", "evolution_", "_seed", "_spice", "campfire_pot", "mochi",
            "tumblestone", "_shard", "griseous_core", "_wood", "_stairs", "_plaque", "saccharine",
            "_leaf", "aprijuice", "_sweet", "roasted_", "apricorn", "_feather", "_candy",
            "_pouch", "_ring", "gracidea", "unit", "_plate", "_memory", "galarica", "_drive",
            "_crystal", "_tiara", "case", "_bottle", "_pendant", "hearty", "pottery", "_sherd",
            "mega_", "_stone", "pokedex", "zygarde", "mulch", "keystone", "dynamax", "_chest",
            "archie", "sandwich", "ball", "mint"
    };

    private static final String[] EXCLUDED_PREFIXES = {"x_", "power_", "ancient_"};
    private static final String[] EXCLUDED_SUFFIXES = {"_pot", "_rod", "ium_z"};

    private static volatile List<Item> cachedPool = null;

    private HeldItemRoller() {
    }

    public static void invalidate() {
        cachedPool = null;
    }

    public static boolean apply(Pokemon pokemon, Random random,
                                com.cobblelocke.config.CobblelockeConfig config, boolean canDrop) {
        List<Item> pool = new ArrayList<>();
        if (config.heldItemWhitelist != null && !config.heldItemWhitelist.isEmpty()) {
            pool.addAll(HeldItemGroups.resolve(config.heldItemWhitelist));
        } else {
            pool.addAll(pool());
        }
        if (config.heldItemBlacklist != null && !config.heldItemBlacklist.isEmpty()) {
            Set<Item> banned = new java.util.HashSet<>(HeldItemGroups.resolve(config.heldItemBlacklist));
            pool.removeIf(banned::contains);
        }
        if (pool.isEmpty()) {
            return false;
        }
        try {
            Item item = pool.get(random.nextInt(pool.size()));

            pokemon.swapHeldItem(new ItemStack(item, 1), false, canDrop);
            return true;
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not assign a held item: {}", e.toString());
            return false;
        }
    }

    private static List<Item> pool() {
        List<Item> cached = cachedPool;
        if (cached != null) {
            return cached;
        }
        List<Item> built = new ArrayList<>();
        try {
            for (Item item : Registries.ITEM) {
                Identifier id = Registries.ITEM.getId(item);
                if (id == null || !ALLOWED_NAMESPACES.contains(id.getNamespace())) {
                    continue;
                }
                if (isExcluded(id.getPath())) {
                    continue;
                }
                built.add(item);
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not build the held item pool: {}", e.toString());
        }
        cachedPool = built;
        return built;
    }

    private static boolean isExcluded(String path) {
        if (EXCLUDED_PATHS.contains(path)) {
            return true;
        }
        for (String fragment : EXCLUDED_FRAGMENTS) {
            if (path.contains(fragment)) {
                return true;
            }
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (String suffix : EXCLUDED_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
