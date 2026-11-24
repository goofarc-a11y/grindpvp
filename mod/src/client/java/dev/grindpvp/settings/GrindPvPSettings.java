package dev.grindpvp.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GrindPvPSettings {
    private static final System.Logger LOGGER = System.getLogger(GrindPvPSettings.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("grindpvp.json");
    private static final GrindPvPSettings INSTANCE = load();

    public boolean animationsEnabled = true;
    public boolean soundsEnabled = true;
    public boolean hoverSoundsEnabled = false;
    public boolean nametagPingEnabled = true;
    public boolean nametagTierEnabled = true;
    public boolean nametagRankingEnabled = true;
    public boolean nametagRoleEnabled = true;
    public int guiSoundVolume = 25;
    public int clickSoundVolume = 30;
    public int guiOpacity = 255;
    public int guiCornerRadius;

    private GrindPvPSettings() {
    }

    public static GrindPvPSettings get() {
        return INSTANCE;
    }

    public boolean save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
            return true;
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save GrindPvP settings to " + FILE, error);
            return false;
        }
    }

    private static GrindPvPSettings load() {
        if (!Files.exists(FILE)) {
            return new GrindPvPSettings();
        }
        try {
            GrindPvPSettings loaded = GSON.fromJson(Files.readString(FILE), GrindPvPSettings.class);
            if (loaded == null) {
                throw new IOException("Settings file contained no configuration.");
            }
            loaded.guiSoundVolume = clamp(loaded.guiSoundVolume);
            loaded.clickSoundVolume = clamp(loaded.clickSoundVolume);
            loaded.guiOpacity = Math.max(1, Math.min(255, loaded.guiOpacity));
            loaded.guiCornerRadius = Math.max(0, Math.min(20, loaded.guiCornerRadius));
            return loaded;
        } catch (IOException | RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load GrindPvP settings from " + FILE, error);
            return new GrindPvPSettings();
        }
    }

    private static int clamp(int volume) {
        return Math.max(0, Math.min(100, volume));
    }
}
