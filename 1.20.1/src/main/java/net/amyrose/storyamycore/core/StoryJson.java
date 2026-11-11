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

    /* Optional fields are written only when they differ from their default: tileSize null = untiled, biome null = no biome override,
       minBiomeShare 1.0, litterGrass false, ringRadius equal to StoryEngine.ringRadius, adaptiveRing false (steepnessAdaptationFactor is only written when it is on). */
    public static JsonObject chapterEntry(ChapterCreateInfo info) {
        JsonObject entry = new JsonObject();
        entry.add("size", pos(info.sizeX, info.sizeZ));
        if (info.offsetFromPrevious != null) {
            entry.add("offsetFromPrevious", pos(info.offsetFromPrevious.x, info.offsetFromPrevious.z));
        }
        entry.addProperty("landscapeName", info.landscapeName);
        entry.add("landscapeBlockState", blockStateToJson(info.landscapeBlockState));
        JsonArray forbidden = new JsonArray();
        for (BlockState state : info.forbiddenLandscapeBlocks) {
            forbidden.add(blockStateToJson(state));
        }
        entry.add("forbiddenLandscapeBlocks", forbidden);
        entry.addProperty("environmentName", info.environmentName);
        JsonArray biomes = new JsonArray();
        for (String biome : info.allowedBiomes) {
            biomes.add(biome);
        }
        entry.add("allowedBiomes", biomes);
        entry.addProperty("baseElevation", info.baseElevation);
        if (info.landscapeTileSize != null) {
            entry.add("landscapeTileSize", pos(info.landscapeTileSize.x, info.landscapeTileSize.z));
        }
        if (info.minBiomeShare != 1.0) {
            entry.addProperty("minBiomeShare", info.minBiomeShare);
        }
        if (info.biome != null) {
            entry.addProperty("biome", info.biome);
        }
        if (info.litterGrass) {
            entry.addProperty("litterGrass", true);
        }
        if (info.ringRadius != StoryEngine.ringRadius) {
            entry.addProperty("ringRadius", info.ringRadius);
        }
        if (info.adaptiveRing) {
            entry.addProperty("adaptiveRing", true);
            entry.addProperty("steepnessAdaptationFactor", info.steepnessAdaptationFactor);
        }
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

            /* Optional: absent means every sampled point must be in an allowed biome. */
            double minBiomeShare = 1.0;
            if (json.has("minBiomeShare")) {
                if (!json.get("minBiomeShare").isJsonPrimitive()) {
                    fail(chapterWhere + " 'minBiomeShare' must be a number.");
                }
                minBiomeShare = json.get("minBiomeShare").getAsDouble();
                if (minBiomeShare <= 0 || minBiomeShare > 1) {
                    fail(chapterWhere + " 'minBiomeShare' must be above 0 and at most 1.");
                }
            }

            if (first) {
                story.AddFirstChapter(chapterName, size, landscapeName, landscapeBlockState, forbidden, environmentName, allowedBiomes, baseElevation);
                first = false;
            } else {
                BlockPos2D offsetFromPrevious = getPos(json, "offsetFromPrevious", chapterWhere);
                story.AddChapter(chapterName, size, offsetFromPrevious, landscapeName, landscapeBlockState, forbidden, environmentName, allowedBiomes, baseElevation);
            }
            story.chapters.get(chapterName).minBiomeShare = minBiomeShare;

            /* Optional: how many blocks the blend ring around the landscape spans; a bigger value gives a more gradual transition (each ring step changes the height by about 1 block). */
            if (json.has("ringRadius")) {
                int ringRadius = getInt(json, "ringRadius", chapterWhere);
                if (ringRadius < 1) {
                    fail(chapterWhere + " 'ringRadius' must be at least 1.");
                }
                story.chapters.get(chapterName).ringRadius = ringRadius;
            }

            /* Optional: with adaptiveRing true the ring grows where the vanilla terrain is far from the chapter's edge; steepnessAdaptationFactor (ring blocks per block of height difference) is then required. */
            if (json.has("adaptiveRing")) {
                if (!json.get("adaptiveRing").isJsonPrimitive() || !json.get("adaptiveRing").getAsJsonPrimitive().isBoolean()) {
                    fail(chapterWhere + " 'adaptiveRing' must be true or false.");
                }
                boolean adaptiveRing = json.get("adaptiveRing").getAsBoolean();
                story.chapters.get(chapterName).adaptiveRing = adaptiveRing;
                if (adaptiveRing) {
                    if (!json.has("steepnessAdaptationFactor") || !json.get("steepnessAdaptationFactor").isJsonPrimitive()) {
                        fail(chapterWhere + " 'steepnessAdaptationFactor' (a number above 0) is required when 'adaptiveRing' is true.");
                    }
                    double steepnessAdaptationFactor = json.get("steepnessAdaptationFactor").getAsDouble();
                    if (steepnessAdaptationFactor <= 0) {
                        fail(chapterWhere + " 'steepnessAdaptationFactor' must be above 0.");
                    }
                    story.chapters.get(chapterName).steepnessAdaptationFactor = steepnessAdaptationFactor;
                }
            }

            /* Optional: splits the landscape into templates of this size, named <landscapeName>_<column>_<row>. It must divide the chapter size evenly. */
            if (json.has("landscapeTileSize")) {
                story.chapters.get(chapterName).landscapeTileSize = getPos(json, "landscapeTileSize", chapterWhere);
            }

            /* Optional: true lets grass, flower and mushroom patches be scattered inside the no-features mask too; absent means false. */
            if (json.has("litterGrass")) {
                if (!json.get("litterGrass").isJsonPrimitive() || !json.get("litterGrass").getAsJsonPrimitive().isBoolean()) {
                    fail(chapterWhere + " 'litterGrass' must be true or false.");
                }
                story.chapters.get(chapterName).litterGrass = json.get("litterGrass").getAsBoolean();
            }

            /* Optional: the biome the chapter area is generated as, e.g. "minecraft:forest". */
            if (json.has("biome")) {
                if (!json.get("biome").isJsonPrimitive()) {
                    fail(chapterWhere + " 'biome' must be text.");
                }
                story.chapters.get(chapterName).biomeName = json.get("biome").getAsString();
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
