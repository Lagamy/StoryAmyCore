package net.amyrose.storyamycore.nbt;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;

public class ServerDataManager {
    private static final String FILENAME = "story_data.dat";

    private final MinecraftServer server;
    private final File dataFile;
    private worldDataNBT data;

    public ServerDataManager(MinecraftServer server, File saveDir) {
        this.server = server;
        this.dataFile = new File(saveDir, FILENAME);
    }

    public void load() {
        if (dataFile.exists()) {
            try {
                NbtCompound compound = NbtIo.readCompressed(dataFile);
                data = worldDataNBT.fromNbt(compound);
                // load into StoryEngine here
            } catch (IOException e) {
                e.printStackTrace();
                data = new worldDataNBT();
                data.wasCreated = true;
            }
        } else {
            data = new worldDataNBT();
            data.wasCreated = true;
        }

        if (data.wasCreated) {
            StoryEngine.RandomizeStories();
            save(); // save new data immediately
        }
    }

    public void save() {
        if (data == null) return;
        try {
            NbtCompound compound = new NbtCompound();
            data.writeNbt(compound);
            NbtIo.writeCompressed(compound, dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public worldDataNBT getData() {
        return data;
    }
}