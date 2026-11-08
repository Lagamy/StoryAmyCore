package net.amyrose.storyamycore.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* Reads and writes <gameDir>/Stories/<modId>/<storyName>.json. The file is created once and entries are only ever appended,
   so values the user edited are never reset. Chapter order is the JSON object order (Gson keeps it). */
public final class StoryJson {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StoryJson() {}

    public static Path storyPath(String modId, String storyName) {
        return FabricLoader.getInstance().getGameDir().resolve("Stories").resolve(modId).resolve(storyName + ".json");
    }

    /* Creates the file only if it is missing. Returns baseMaxDistanceRadius from the file, so a user edit wins over the value passed by code. */
    public static int ensureStoryFile(String modId, String storyName, int baseMaxDistanceRadius) {
        Path path = storyPath(modId, storyName);
        if (!Files.exists(path)) {
            JsonObject root = new JsonObject();
            root.addProperty("baseMaxDistanceRadius", baseMaxDistanceRadius);
            root.add("chapters", new JsonObject());
            write(path, root);
            return baseMaxDistanceRadius;
        }
        return getInt(read(path), "baseMaxDistanceRadius", path.toString());
    }

    /* Adds the entry only if the chapter name is not in the file yet. An existing entry is never touched. */
    public static void ensureChapterEntry(String modId, String storyName, String chapterName, String previousChapterName, JsonObject entry) {
        Path path = storyPath(modId, storyName);
        JsonObject root = read(path);
        JsonObject chapters = getObject(root, "chapters", path.toString());
        if (chapters.has(chapterName)) {
            return;
        }
        /* A missing entry goes right after the chapter registered before it (null = first chapter, goes to the front), so it returns to its place in the script and the first JSON entry stays the first chapter. */
        JsonObject reordered = new JsonObject();
        if (previousChapterName == null) {
            reordered.add(chapterName, entry);
        }
        for (Map.Entry<String, JsonElement> existing : chapters.entrySet()) {
            reordered.add(existing.getKey(), existing.getValue());
            if (existing.getKey().equals(previousChapterName)) {
                reordered.add(chapterName, entry);
            }
        }
        if (!reordered.has(chapterName)) {
            reordered.add(chapterName, entry);
        }
        root.add("chapters", reordered);
        write(path, root);
    }

    public static JsonObject chapterEntry(int sizeX, int sizeZ, BlockPos2D offsetFromPrevious, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int baseElevation) {
        JsonObject entry = new JsonObject();
        entry.add("size", pos(sizeX, sizeZ));
        if (offsetFromPrevious != null) {
            entry.add("offsetFromPrevious", pos(offsetFromPrevious.x, offsetFromPrevious.z));
        }
        entry.addProperty("landscapeName", landscapeName);
        entry.add("landscapeBlockState", blockStateToJson(landscapeBlockState));
        JsonArray forbidden = new JsonArray();
        for (BlockState state : forbiddenLandscapeBlocks) {
            forbidden.add(blockStateToJson(state));
        }
        entry.add("forbiddenLandscapeBlocks", forbidden);
        entry.addProperty("environmentName", environmentName);
        JsonArray biomes = new JsonArray();
        for (String biome : allowedBiomes) {
            biomes.add(biome);
        }
        entry.add("allowedBiomes", biomes);
        entry.addProperty("baseElevation", baseElevation);
        return entry;
    }

    /* Rebuilds the story's chapters from its file. Called at server start (after registration closed), so chapters the user added by hand
       are loaded too and edits are picked up on every world load. */
    public static void loadStory(Story story) {
        Path path = storyPath(story.ModId, story.name);
        String where = path.toString();
        JsonObject root = read(path);
        story.baseMaxDistanceRadius = getInt(root, "baseMaxDistanceRadius", where);
        story.chapters.clear();

        boolean first = true;
        for (Map.Entry<String, JsonElement> chapterEntry : getObject(root, "chapters", where).entrySet()) {
            String chapterName = chapterEntry.getKey();
            String chapterWhere = where + " chapter " + chapterName;
            if (!chapterEntry.getValue().isJsonObject()) {
                fail(chapterWhere + " must be an object.");
            }
            JsonObject json = chapterEntry.getValue().getAsJsonObject();

            BlockPos2D size = getPos(json, "size", chapterWhere);
            String landscapeName = getString(json, "landscapeName", chapterWhere);
            BlockState landscapeBlockState = blockStateFromJson(json.get("landscapeBlockState"), chapterWhere + " landscapeBlockState");
            Set<BlockState> forbidden = new HashSet<>();
            for (JsonElement element : getArray(json, "forbiddenLandscapeBlocks", chapterWhere)) {
                forbidden.add(blockStateFromJson(element, chapterWhere + " forbiddenLandscapeBlocks"));
            }
            String environmentName = getString(json, "environmentName", chapterWhere);
            Set<String> allowedBiomes = new HashSet<>();
            for (JsonElement element : getArray(json, "allowedBiomes", chapterWhere)) {
                allowedBiomes.add(element.getAsString());
            }
            int baseElevation = getInt(json, "baseElevation", chapterWhere);

            if (first) {
                story.AddFirstChapter(chapterName, size, landscapeName, landscapeBlockState, forbidden, environmentName, allowedBiomes, baseElevation);
                first = false;
            } else {
                BlockPos2D offsetFromPrevious = getPos(json, "offsetFromPrevious", chapterWhere);
                story.AddChapter(chapterName, size, offsetFromPrevious, landscapeName, landscapeBlockState, forbidden, environmentName, allowedBiomes, baseElevation);
            }
        }
    }

    private static JsonObject pos(int x, int z) {
        JsonObject pos = new JsonObject();
        pos.addProperty("x", x);
        pos.addProperty("z", z);
        return pos;
    }

    private static JsonElement blockStateToJson(BlockState state) {
        return BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).resultOrPartial(LOGGER::error).orElseThrow(() -> new RuntimeException("Could not write block state " + state));
    }

    private static BlockState blockStateFromJson(JsonElement element, String where) {
        if (element == null) {
            fail(where + " is missing.");
        }
        return BlockState.CODEC.parse(JsonOps.INSTANCE, element).resultOrPartial(LOGGER::error).orElseThrow(() -> new RuntimeException("Invalid block state in " + where));
    }

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.error("Could not read {}", path, e);
            throw new RuntimeException("Could not read " + path, e);
        }
    }

    private static void write(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("Could not write {}", path, e);
            throw new RuntimeException("Could not write " + path, e);
        }
    }

    private static int getInt(JsonObject json, String key, String where) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            fail(where + " is missing number '" + key + "'.");
        }
        return json.get(key).getAsInt();
    }

    private static String getString(JsonObject json, String key, String where) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            fail(where + " is missing text '" + key + "'.");
        }
        return json.get(key).getAsString();
    }

    private static JsonObject getObject(JsonObject json, String key, String where) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            fail(where + " is missing object '" + key + "'.");
        }
        return json.getAsJsonObject(key);
    }

    private static JsonArray getArray(JsonObject json, String key, String where) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            fail(where + " is missing list '" + key + "'.");
        }
        return json.getAsJsonArray(key);
    }

    private static BlockPos2D getPos(JsonObject json, String key, String where) {
        JsonObject pos = getObject(json, key, where);
        return new BlockPos2D(getInt(pos, "x", where + " " + key), getInt(pos, "z", where + " " + key));
    }

    private static void fail(String message) {
        LOGGER.error(message);
        throw new RuntimeException(message);
    }
}
