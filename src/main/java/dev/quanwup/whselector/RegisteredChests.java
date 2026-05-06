package dev.quanwup.whselector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory cache of registered containers (from warehouse-agent data), keyed by dimension.
 * Loaded either directly from chests.json (file mode) or from /api/chests (API mode).
 */
public class RegisteredChests {
    /** dim -> list of positions. "unknown" if entry has no dim field. */
    private static volatile Map<String, List<Entry>> byDim = new HashMap<>();
    private static volatile long lastLoadMs = 0L;
    private static volatile String lastMode = "none";

    public record Entry(BlockPos pos, String type) {}

    public static Map<String, List<Entry>> getAll() {
        return byDim;
    }

    /**
     * Return entries for the given dim plus any entries with no/unknown dim
     * (old records stored before we added the dim field). Unknowns still show up
     * everywhere — that's better than not showing at all.
     */
    public static List<Entry> forDim(String dim) {
        List<Entry> same = byDim.getOrDefault(dim, Collections.emptyList());
        List<Entry> unknown = byDim.getOrDefault("unknown", Collections.emptyList());
        if (unknown.isEmpty()) return same;
        if (same.isEmpty()) return unknown;
        List<Entry> combined = new ArrayList<>(same.size() + unknown.size());
        combined.addAll(same);
        combined.addAll(unknown);
        return combined;
    }

    public static int total() {
        int n = 0;
        for (List<Entry> v : byDim.values()) n += v.size();
        return n;
    }

    public static long ageSeconds() {
        if (lastLoadMs == 0) return -1;
        return (System.currentTimeMillis() - lastLoadMs) / 1000;
    }

    public static String mode() {
        return lastMode;
    }

    public static void reload() throws Exception {
        Config cfg = WhSelectorClient.CONFIG;
        Map<String, List<Entry>> next = new HashMap<>();
        if (!cfg.hasJsonFile()) {
            byDim = next;
            lastLoadMs = System.currentTimeMillis();
            lastMode = "file(unset)";
            return;
        }
        Path p = Path.of(cfg.jsonFile);
        if (!Files.exists(p)) {
            byDim = next;
            lastLoadMs = System.currentTimeMillis();
            lastMode = "file(empty)";
            return;
        }
        String raw = Files.readString(p, StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            byDim = next;
            lastLoadMs = System.currentTimeMillis();
            lastMode = "file(empty)";
            return;
        }
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        loadDim(root, next, "overworld", "minecraft:overworld");
        loadDim(root, next, "end", "minecraft:the_end");
        loadDim(root, next, "nether", "minecraft:the_nether");
        byDim = next;
        lastLoadMs = System.currentTimeMillis();
        lastMode = "file";
    }

    private static void loadDim(JsonObject root, Map<String, List<Entry>> next, String key, String dim) {
        JsonElement el = root.get(key);
        if (el == null || !el.isJsonArray()) return;
        for (JsonElement e : el.getAsJsonArray()) {
            if (!e.isJsonArray()) continue;
            JsonArray pos = e.getAsJsonArray();
            if (pos.size() < 3) continue;
            int x = pos.get(0).getAsInt();
            int y = pos.get(1).getAsInt();
            int z = pos.get(2).getAsInt();
            next.computeIfAbsent(dim, k -> new ArrayList<>()).add(new Entry(new BlockPos(x, y, z), "container"));
        }
    }

    /** Fire and forget; swallows exceptions. Safe for auto-reload after /wh add /wh rm. */
    public static void reloadAsync() {
        new Thread(() -> {
            try { reload(); }
            catch (Exception e) { WhSelectorClient.LOGGER.warn("reload registered chests failed: {}", e.getMessage()); }
        }, "wh-reload").start();
    }
}
