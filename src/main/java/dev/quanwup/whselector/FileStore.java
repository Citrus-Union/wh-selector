package dev.quanwup.whselector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Directly reads & writes warehouse-agent's data/chests.json file.
 * Schema must match packages/inventory-agent/src/stores.js ChestStore.
 */
public class FileStore {
    // Must match CONTAINER_BLOCKS in packages/inventory-agent/src/containers.js
    public static final Set<String> CONTAINER_BLOCKS = Set.of(
        "chest", "trapped_chest", "barrel",
        "shulker_box", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box",
        "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box",
        "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box",
        "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box",
        "black_shulker_box",
        "hopper", "dispenser", "dropper", "brewing_stand"
    );

    private final Path chestsFile;

    public FileStore(String jsonFile) {
        this.chestsFile = Path.of(jsonFile);
    }

    /** @return the chests.json JSON object, creating `{chests: []}` if absent. */
    private JsonObject load() throws IOException {
        if (!Files.exists(chestsFile)) {
            JsonObject root = new JsonObject();
            root.add("overworld", new JsonArray());
            root.add("end", new JsonArray());
            root.add("nether", new JsonArray());
            return root;
        }
        String raw = Files.readString(chestsFile, StandardCharsets.UTF_8);
        JsonElement el = com.google.gson.JsonParser.parseString(raw);
        if (!el.isJsonObject()) {
            JsonObject root = new JsonObject();
            root.add("overworld", new JsonArray());
            root.add("end", new JsonArray());
            root.add("nether", new JsonArray());
            return root;
        }
        JsonObject root = el.getAsJsonObject();
        ensureDim(root, "overworld");
        ensureDim(root, "end");
        ensureDim(root, "nether");
        return root;
    }

    private void save(JsonObject root) throws IOException {
        Path parent = chestsFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(chestsFile, format(root), StandardCharsets.UTF_8);
    }

    /**
     * Scan the client's loaded world for container blocks inside [min, max], and
     * upsert them into chests.json. Returns the number of entries added (new, not already present).
     */
    public AddResult addFromClient(BlockPos min, BlockPos max, Set<String> excludeTypes, String dim) throws IOException {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) {
            throw new IOException("Not in a world");
        }

        JsonObject root = load();
        JsonArray chests = root.getAsJsonArray(dimKey(dim));

        Set<String> existing = new HashSet<>();
        for (JsonElement e : chests) {
            int[] pos = readPos(e);
            if (pos != null) existing.add(key(pos[0], pos[1], pos[2]));
        }

        int added = 0, scanned = 0, skippedExcluded = 0, updated = 0;
        //#if MC>=260000
        //$$ BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        //#else
        BlockPos.Mutable cur = new BlockPos.Mutable();
        //#endif
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cur.set(x, y, z);
                    BlockState state = world.getBlockState(cur);
                    if (state.isAir()) continue;
                    Block block = state.getBlock();
                    //#if MC>=260000
                    //$$ Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                    //#else
                    Identifier id = Registries.BLOCK.getId(block);
                    //#endif
                    String type = id.getPath(); // e.g. "chest"
                    if (!CONTAINER_BLOCKS.contains(type)) continue;
                    scanned++;
                    if (excludeTypes.contains(type)) { skippedExcluded++; continue; }

                    String k = key(x, y, z);
                    if (!existing.contains(k)) {
                        JsonArray entry = new JsonArray();
                        entry.add(x);
                        entry.add(y);
                        entry.add(z);
                        chests.add(entry);
                        existing.add(k);
                        added++;
                    }
                }
            }
        }

        if (added > 0 || updated > 0) save(root);
        return new AddResult(added, updated, scanned, skippedExcluded);
    }

    /** Remove all chests whose pos falls inside [min, max] inclusive. */
    public int removeInAabb(BlockPos min, BlockPos max) throws IOException {
        return removeInAabb(min, max, "minecraft:overworld");
    }

    public int removeInAabb(BlockPos min, BlockPos max, String dim) throws IOException {
        JsonObject root = load();
        String dimKey = dimKey(dim);
        JsonArray chests = root.getAsJsonArray(dimKey);
        JsonArray kept = new JsonArray();
        int removed = 0;
        for (JsonElement e : chests) {
            int[] pos = readPos(e);
            if (pos == null) { kept.add(e); continue; }
            int x = pos[0], y = pos[1], z = pos[2];
            boolean inside = x >= min.getX() && x <= max.getX()
                && y >= min.getY() && y <= max.getY()
                && z >= min.getZ() && z <= max.getZ();
            if (inside) removed++;
            else kept.add(e);
        }
        if (removed > 0) {
            root.add(dimKey, kept);
            save(root);
        }
        return removed;
    }

    /** Total stored chest count. */
    public int count() throws IOException {
        JsonObject root = load();
        return root.getAsJsonArray("overworld").size()
            + root.getAsJsonArray("end").size()
            + root.getAsJsonArray("nether").size();
    }

    private static void ensureDim(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) root.add(key, new JsonArray());
    }

    private static String dimKey(String dim) {
        if (dim == null) return "overworld";
        if (dim.endsWith(":the_end") || dim.equals("end")) return "end";
        if (dim.endsWith(":the_nether") || dim.equals("nether")) return "nether";
        return "overworld";
    }

    private static int[] readPos(JsonElement e) {
        if (!e.isJsonArray()) return null;
        JsonArray arr = e.getAsJsonArray();
        if (arr.size() < 3) return null;
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()};
    }

    private static String format(JsonObject root) {
        return "{\n"
            + "  \"overworld\": " + formatDim(root.getAsJsonArray("overworld")) + ",\n"
            + "  \"end\": " + formatDim(root.getAsJsonArray("end")) + ",\n"
            + "  \"nether\": " + formatDim(root.getAsJsonArray("nether")) + "\n"
            + "}\n";
    }

    private static String formatDim(JsonArray arr) {
        if (arr == null || arr.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < arr.size(); i++) {
            int[] pos = readPos(arr.get(i));
            if (pos == null) continue;
            sb.append("    [").append(pos[0]).append(",").append(pos[1]).append(",").append(pos[2]).append("]");
            if (i < arr.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    public record AddResult(int added, int updated, int scanned, int skippedExcluded) {}
}
