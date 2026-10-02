package com.cobblelocke.random;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class CustomLearnset {
    private final TreeMap<Integer, String> levelUpMoves = new TreeMap<>();
    private final List<String> tmMoves = new ArrayList<>();
    private final List<String> eggMoves = new ArrayList<>();

    public Map<Integer, String> getLevelUpMoves() {
        return levelUpMoves;
    }

    public List<String> getTmMoves() {
        return tmMoves;
    }

    public List<String> getEggMoves() {
        return eggMoves;
    }

    public void addLevelUpMove(int level, String move) {
        if (move != null && !move.isBlank()) {
            levelUpMoves.put(level, move);
        }
    }

    public void addTmMove(String move) {
        if (move != null && !move.isBlank()) {
            tmMoves.add(move);
        }
    }

    public void addEggMove(String move) {
        if (move != null && !move.isBlank()) {
            eggMoves.add(move);
        }
    }

    public boolean isEmpty() {
        return levelUpMoves.isEmpty() && tmMoves.isEmpty() && eggMoves.isEmpty();
    }

    public List<String> movesUpToLevel(int level) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : levelUpMoves.entrySet()) {
            if (entry.getKey() <= level) {
                out.add(entry.getValue());
            }
        }
        return out;
    }

    public Learnset toLearnset() {
        Learnset learnset = new Learnset();
        for (Map.Entry<Integer, String> entry : levelUpMoves.entrySet()) {
            MoveTemplate move = lookup(entry.getValue());
            if (move != null) {
                learnset.getLevelUpMoves()
                        .computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                        .add(move);
            }
        }
        for (String name : tmMoves) {
            MoveTemplate move = lookup(name);
            if (move != null) {
                learnset.getTmMoves().add(move);
            }
        }
        for (String name : eggMoves) {
            MoveTemplate move = lookup(name);
            if (move != null) {
                learnset.getEggMoves().add(move);
            }
        }
        return learnset;
    }

    private static MoveTemplate lookup(String name) {
        try {
            return Moves.getByName(name.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        NbtCompound levels = new NbtCompound();
        for (Map.Entry<Integer, String> entry : levelUpMoves.entrySet()) {
            levels.putString(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("LevelUpMoves", levels);
        tag.put("TmMoves", toNbtList(tmMoves));
        tag.put("EggMoves", toNbtList(eggMoves));
        return tag;
    }

    private static NbtList toNbtList(List<String> values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value));
        }
        return list;
    }

    public static CustomLearnset fromNbt(NbtCompound tag) {
        CustomLearnset learnset = new CustomLearnset();
        if (tag == null) {
            return learnset;
        }
        NbtCompound levels = tag.getCompound("LevelUpMoves");
        for (String key : levels.getKeys()) {
            try {
                learnset.addLevelUpMove(Integer.parseInt(key), levels.getString(key));
            } catch (NumberFormatException ignored) {
            }
        }
        NbtList tms = tag.getList("TmMoves", NbtList.STRING_TYPE);
        for (int i = 0; i < tms.size(); i++) {
            learnset.addTmMove(tms.getString(i));
        }
        NbtList eggs = tag.getList("EggMoves", NbtList.STRING_TYPE);
        for (int i = 0; i < eggs.size(); i++) {
            learnset.addEggMove(eggs.getString(i));
        }
        return learnset;
    }
}
