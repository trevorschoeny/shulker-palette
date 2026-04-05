package com.trevorschoeny.shulkerpalette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone config for Shulker Palette. Persisted as JSON in the config dir.
 * YACL UI bindings are registered in ShulkerPaletteClient.
 */
public class ShulkerPaletteConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("shulker-palette.json");

    /** Global enable/disable for all shulker palettes. */
    public boolean enabled = true;

    /**
     * What shift+right-click does when holding a shulker palette.
     *   PLACE_SHULKER  — bypasses palette and places the shulker box normally.
     *   STILL_PALETTE  — shift+click still uses the palette (no way to place without toggling off).
     */
    public ShiftBehavior shiftBehavior = ShiftBehavior.PLACE_SHULKER;

    public enum ShiftBehavior {
        PLACE_SHULKER,
        STILL_PALETTE
    }

    private static ShulkerPaletteConfig instance;

    public static ShulkerPaletteConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                instance = GSON.fromJson(Files.readString(CONFIG_PATH), ShulkerPaletteConfig.class);
            } catch (IOException e) {
                instance = new ShulkerPaletteConfig();
            }
        } else {
            instance = new ShulkerPaletteConfig();
        }
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(get()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
