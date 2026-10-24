package com.trevorschoeny.shulkerpalette.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Shulker Palette's own settings: five look switches, all on by default. Same static-field +
 * JSON shape as the other house mods, saved on every change to
 * {@code config/shulkerpalette/config.json}. Client only: every one changes what this player
 * sees, nothing the server does. The keys live with vanilla's key binds (options.txt).
 */
public final class Config {
    private Config() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // The cycling rainbow outline on the next block, in the random modes.
    private static boolean rainbow = true;
    // The box view above the hotbar while the modifier is held and the wheel turns.
    private static boolean popup = true;
    // In hand, the open box with the next block inside; off shows the next block alone.
    private static boolean openBoxInHand = true;
    // A see-through copy of the next block where a click would place it (survival and creative).
    // Off by default; the repaint one stays on, since repaint replaces blocks and needs the preview.
    private static boolean placementGhost = false;
    // The same over the block repaint would replace (creative).
    private static boolean repaintGhost = true;
    // The palette's icon column on a box opened through Open Anywhere. Off: the palette is
    // handled in hand only (the menu on V), which is the default.
    private static boolean openAnywherePanel = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("shulkerpalette").resolve("config.json");
    }

    /** Reads the file once at client start; a missing or broken file leaves the defaults. */
    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            rainbow = readBool(root, "rainbow", rainbow);
            popup = readBool(root, "popup", popup);
            openBoxInHand = readBool(root, "openBoxInHand", openBoxInHand);
            placementGhost = readBool(root, "placementGhost", placementGhost);
            repaintGhost = readBool(root, "repaintGhost", repaintGhost);
            openAnywherePanel = readBool(root, "openAnywherePanel", openAnywherePanel);
        } catch (Exception e) {
            ShulkerPalette.LOGGER.error("[config] couldn't read {}, using defaults", path, e);
        }
    }

    private static boolean readBool(JsonObject root, String key, boolean fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsBoolean() : fallback;
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.addProperty("rainbow", rainbow);
            root.addProperty("popup", popup);
            root.addProperty("openBoxInHand", openBoxInHand);
            root.addProperty("placementGhost", placementGhost);
            root.addProperty("repaintGhost", repaintGhost);
            root.addProperty("openAnywherePanel", openAnywherePanel);
            Files.writeString(path, GSON.toJson(root));
        } catch (Exception e) {
            ShulkerPalette.LOGGER.error("[config] couldn't write {}, changes won't persist", path, e);
        }
    }

    public static boolean rainbow() { return rainbow; }
    public static void setRainbow(boolean v) { rainbow = v; save(); }

    public static boolean popup() { return popup; }
    public static void setPopup(boolean v) { popup = v; save(); }

    public static boolean openBoxInHand() { return openBoxInHand; }
    public static void setOpenBoxInHand(boolean v) { openBoxInHand = v; save(); }

    public static boolean placementGhost() { return placementGhost; }
    public static void setPlacementGhost(boolean v) { placementGhost = v; save(); }

    public static boolean repaintGhost() { return repaintGhost; }
    public static void setRepaintGhost(boolean v) { repaintGhost = v; save(); }

    public static boolean openAnywherePanel() { return openAnywherePanel; }
    public static void setOpenAnywherePanel(boolean v) { openAnywherePanel = v; save(); }
}
