package dev.skirmish.module.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skirmish.debug.DebugLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves {@link BaseData} ({@code config/skirmish/base.json}); saves are batched through a dirty flag. */
final class BaseStore {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private BaseData data = new BaseData();
    private boolean dirty;

    BaseStore(Path file) {
        this.file = file;
    }

    BaseData data() {
        return data;
    }

    void load() {
        try {
            if (Files.exists(file)) {
                BaseData read = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), BaseData.class);
                if (read != null && read.servers != null) {
                    data = read;
                }
            }
        } catch (IOException | RuntimeException e) {
            DebugLog.error(BaseModule.ID, "base.json unreadable", e);
        }
    }

    void markDirty() {
        dirty = true;
    }

    void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    void save() {
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            DebugLog.error(BaseModule.ID, "base.json not saved", e);
        }
    }
}
