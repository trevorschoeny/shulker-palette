package com.trevorschoeny.shulkerpalette.library;

import com.mojang.serialization.DataResult;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The palette library: saved palettes as text files, one per palette, in the same readable
 * form the clipboard used ({@link SavedPalette}), so they can be edited, copied and shared
 * as files. Client only: loading still goes through the server, which checks every palette.
 *
 * <p>Two shelves. All worlds lives in the config folder. This world lives inside the save in
 * singleplayer (copying a world takes its palettes along), and in a folder per server
 * address in multiplayer, since a client has no world folder there.
 *
 * <p>Nothing is cached: every read lists the folder again, so a file edited by hand shows up
 * the next time the library or the Load list opens.
 */
public final class Library {
    private Library() {}

    public enum Shelf { WORLD, GLOBAL;
        public Shelf other() { return this == WORLD ? GLOBAL : WORLD; }
    }

    /**
     * One file on a shelf. {@code key} is the file name without ".txt", unique on its shelf
     * and stable while the file exists. {@code palette} is an error for a file that doesn't
     * parse; such an entry is shown, with its problem, but never loaded.
     */
    public record Entry(Shelf shelf, String key, String name, DataResult<SavedPalette> palette) {
        /** Unique across both shelves, for a list that shows both. */
        public String id() { return shelf + "/" + key; }
    }

    private static final String EXTENSION = ".txt";
    private static final int MAX_KEY = 48;

    /** The shelf's folder, or null when there is none (This world while no world is open). */
    public static @Nullable Path folder(Shelf shelf) {
        Path config = FabricLoader.getInstance().getConfigDir().resolve(ShulkerPalette.MOD_ID);
        if (shelf == Shelf.GLOBAL) return config.resolve("palettes");
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve(ShulkerPalette.MOD_ID).resolve("palettes");
        }
        ServerData server = mc.getCurrentServer();
        if (server != null && mc.level != null) return config.resolve("servers").resolve(safe(server.ip.toLowerCase(Locale.ROOT))).resolve("palettes");
        return null;
    }

    /** Every palette on the shelf, by name. Empty when the shelf has no folder yet. */
    public static List<Entry> list(Shelf shelf) {
        Path folder = folder(shelf);
        if (folder == null || !Files.isDirectory(folder)) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(f -> f.toString().endsWith(EXTENSION)).toList()) {
                String key = file.getFileName().toString();
                key = key.substring(0, key.length() - EXTENSION.length());
                entries.add(read(shelf, key, file));
            }
        } catch (IOException e) {
            ShulkerPalette.LOGGER.error("[library] couldn't list {}", folder, e);
        }
        entries.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return entries;
    }

    private static Entry read(Shelf shelf, String key, Path file) {
        try {
            DataResult<SavedPalette.Named> parsed = SavedPalette.parseNamed(Files.readString(file), registries());
            // A file with no name in it goes by its file name.
            String name = parsed.result().map(SavedPalette.Named::name).filter(n -> !n.isBlank()).orElse(key);
            return new Entry(shelf, key, name, parsed.map(SavedPalette.Named::palette));
        } catch (IOException e) {
            return new Entry(shelf, key, key, DataResult.error(() -> "Couldn't read the file: " + e.getMessage()));
        }
    }

    /** Is a palette with this name (or one that makes the same file name) on the shelf? */
    public static boolean exists(Shelf shelf, String name) {
        Path folder = folder(shelf);
        return folder != null && Files.exists(folder.resolve(key(name) + EXTENSION));
    }

    /** Writes the palette under its name, replacing one with the same name. False if it couldn't. */
    public static boolean save(Shelf shelf, String name, SavedPalette palette) {
        Path folder = folder(shelf);
        if (folder == null) return false;
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(key(name) + EXTENSION), palette.write(name, registries()));
            return true;
        } catch (IOException e) {
            ShulkerPalette.LOGGER.error("[library] couldn't save {} to {}", name, folder, e);
            return false;
        }
    }

    /** Renames a palette: the name inside the file, and the file itself. */
    public static boolean rename(Entry entry, String name) {
        if (entry.palette().isError() || name.isBlank()) return false;
        if (!key(name).equals(entry.key()) && exists(entry.shelf(), name)) return false;
        return delete(entry) && save(entry.shelf(), name, entry.palette().getOrThrow());
    }

    /** Moves a palette to the other shelf, unless one with its name is already there. */
    public static boolean move(Entry entry) {
        Shelf to = entry.shelf().other();
        if (entry.palette().isError() || exists(to, entry.name())) return false;
        return save(to, entry.name(), entry.palette().getOrThrow()) && delete(entry);
    }

    public static boolean delete(Entry entry) {
        Path folder = folder(entry.shelf());
        if (folder == null) return false;
        try {
            Files.deleteIfExists(folder.resolve(entry.key() + EXTENSION));
            return true;
        } catch (IOException e) {
            ShulkerPalette.LOGGER.error("[library] couldn't delete {}", entry.key(), e);
            return false;
        }
    }

    /** The first unused "Palette N" on the shelf, for a box with no name of its own. */
    public static String freshName(Shelf shelf) {
        for (int n = 1; ; n++) if (!exists(shelf, "Palette " + n)) return "Palette " + n;
    }

    /** A name as a file name: lowercase letters, digits and hyphens. */
    static String key(String name) {
        String key = safe(name.strip().toLowerCase(Locale.ROOT));
        return key.isEmpty() ? "palette" : key.substring(0, Math.min(key.length(), MAX_KEY));
    }

    private static String safe(String text) {
        return text.replaceAll("[^a-z0-9.]+", "-").replaceAll("^-+|-+$", "");
    }

    /**
     * Item lookups for reading and writing files: the world's, which know every enchantment
     * and other data-driven entry, or outside a world the built-in registries, which read
     * every plain block (a component naming a data-driven entry then shows as an error).
     */
    static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}
