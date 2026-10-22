package com.trevorschoeny.shulkerpalette.palette;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * A palette as text you can copy, paste and edit by hand: the box's layout (what sits in each
 * slot, and how many), its number of rows, and its mode. Save puts one on the clipboard; Load
 * reads one back. Items are written in vanilla's own command syntax ({@code /give}), one line
 * per row of the box:
 *
 * <pre>
 * shulker_palette name="Concrete fade" mode=gradient rows=3
 * row 1: white_concrete 30, white_wool 4
 * row 2: light_gray_concrete 30
 * row 3: -, -, gray_concrete 30
 * </pre>
 *
 * Each row lists its slots left to right, separated by commas: an item ({@code id}, or
 * {@code id[components]} as in a command) and an optional count, or {@code -} for an empty
 * slot. Trailing empty slots and empty rows can be left out. Blank lines and lines starting
 * with {@code #} are ignored. The name is optional, and in double quotes when it has spaces.
 *
 * <p>Pasted text is untrusted: {@link #parse} reads items with vanilla's own command parser,
 * says what is wrong and where, and the server checks a parsed palette again ({@link #valid})
 * before it touches a box.
 */
public record SavedPalette(Mode mode, int rows, List<Entry> slots) {

    /** A palette with the name its text gives it (empty when it has none). The server never sees names. */
    public record Named(String name, SavedPalette palette) {}

    /** One filled slot. */
    public record Entry(int slot, ItemStackTemplate item) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, CAPACITY - 1).fieldOf("slot").forGetter(Entry::slot),
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Entry::item)
        ).apply(i, Entry::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::slot, ItemStackTemplate.STREAM_CODEC, Entry::item, Entry::new);
    }

    private static final String HEADER = "shulker_palette";
    private static final String EMPTY = "-";
    private static final int CAPACITY = Palette.MAX_ROWS * Palette.ROW_WIDTH;

    public static final StreamCodec<RegistryFriendlyByteBuf, SavedPalette> STREAM_CODEC = StreamCodec.composite(
            Mode.STREAM_CODEC, SavedPalette::mode,
            ByteBufCodecs.VAR_INT, SavedPalette::rows,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(CAPACITY)), SavedPalette::slots,
            SavedPalette::new);

    /** A snapshot of a box: its palette's mode and rows, and every filled slot. */
    public static SavedPalette of(Palette palette, Container box) {
        List<Entry> slots = new ArrayList<>();
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.getItem(i);
            if (!stack.isEmpty()) slots.add(new Entry(i, ItemStackTemplate.fromNonEmptyStack(stack)));
        }
        return new SavedPalette(palette.mode(), palette.rows(), slots);
    }

    /**
     * Fits the box: rows in range, every slot inside them and named once. A palette that
     * arrives over the network is held to this whatever the client claims.
     */
    public boolean valid() {
        if (rows < Palette.VANILLA_ROWS || rows > Palette.MAX_ROWS) return false;
        Set<Integer> seen = new HashSet<>();
        for (Entry entry : slots) {
            if (entry.slot() < 0 || entry.slot() >= rows * Palette.ROW_WIDTH || !seen.add(entry.slot())) return false;
            // The network form skips the checks the saved form makes: a count the box can't
            // save, or a shulker inside a shulker, would break the box it lands in.
            ItemStack stack = entry.item().create();
            if (stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize() || !stack.getItem().canFitInsideContainerItems()) return false;
        }
        return true;
    }

    // ---- writing ----

    public String write(HolderLookup.Provider registries) {
        return write("", registries);
    }

    /** The text form, with {@code name} in the header unless it is blank. */
    public String write(String name, HolderLookup.Provider registries) {
        String named = name.isBlank() ? "" : " name=\"" + name.replace("\"", "'") + "\"";
        StringBuilder text = new StringBuilder(HEADER + named + " mode=" + mode.getSerializedName() + " rows=" + rows + "\n");
        ItemStackTemplate[] bySlot = new ItemStackTemplate[rows * Palette.ROW_WIDTH];
        for (Entry entry : slots) bySlot[entry.slot()] = entry.item();
        for (int row = 0; row < rows; row++) {
            int last = -1;
            for (int col = 0; col < Palette.ROW_WIDTH; col++) if (bySlot[row * Palette.ROW_WIDTH + col] != null) last = col;
            if (last < 0) continue; // an empty row is left out
            List<String> cells = new ArrayList<>();
            for (int col = 0; col <= last; col++) {
                ItemStackTemplate item = bySlot[row * Palette.ROW_WIDTH + col];
                cells.add(item == null ? EMPTY : itemText(item, registries));
            }
            text.append("row ").append(row + 1).append(": ").append(String.join(", ", cells)).append('\n');
        }
        return text.toString();
    }

    /** {@code stone 30}, {@code oak_planks[custom_name='"Floor"'] 12}: the id short when it is vanilla's. */
    private static String itemText(ItemStackTemplate item, HolderLookup.Provider registries) {
        Identifier id = item.item().unwrapKey().orElseThrow().identifier();
        StringBuilder text = new StringBuilder(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString());
        if (!item.components().isEmpty()) {
            Tag encoded = DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), item.components()).getOrThrow();
            List<String> parts = new ArrayList<>();
            if (encoded instanceof CompoundTag compound) {
                for (String key : compound.keySet()) {
                    String shortKey = key.replace("minecraft:", "");
                    parts.add(shortKey.startsWith("!") ? shortKey : shortKey + "=" + compound.get(key));
                }
            }
            text.append('[').append(String.join(",", parts)).append(']');
        }
        if (item.count() != 1) text.append(' ').append(item.count());
        return text.toString();
    }

    // ---- reading ----

    /**
     * The palette in this text, or what is wrong with it ({@code "Line 3: unknown item stnoe"}).
     * Anything at all may be on the clipboard, so "not a palette" is an error like any other.
     */
    public static DataResult<SavedPalette> parse(String text, HolderLookup.Provider registries) {
        return parseNamed(text, registries).map(Named::palette);
    }

    /** {@link #parse}, keeping the name from the header. */
    public static DataResult<Named> parseNamed(String text, HolderLookup.Provider registries) {
        String name = "";
        List<String> lines = text.lines().map(String::strip).toList();
        Mode mode = null;
        int rows = -1;
        List<Entry> slots = new ArrayList<>();
        Set<Integer> rowsSeen = new HashSet<>();
        ItemParser items = new ItemParser(registries);
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n);
            String where = "Line " + (n + 1) + ": ";
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (mode == null) {
                // The header comes first: shulker_palette mode=... rows=...
                List<String> words = headerWords(line);
                if (!words.get(0).equals(HEADER)) return DataResult.error(() -> "Not a palette (it starts with " + HEADER + ")");
                mode = Mode.RANDOM;
                rows = Palette.VANILLA_ROWS;
                for (int w = 1; w < words.size(); w++) {
                    String word = words.get(w);
                    String[] pair = word.split("=", 2);
                    if (pair.length != 2) return DataResult.error(() -> where + "expected key=value, got " + word);
                    switch (pair[0]) {
                        case "mode" -> {
                            Mode found = null;
                            for (Mode m : Mode.values()) if (m.getSerializedName().equals(pair[1])) found = m;
                            if (found == null) return DataResult.error(() -> where + "unknown mode " + pair[1] + " (random, equal, pick, gradient)");
                            mode = found;
                        }
                        case "rows" -> {
                            try { rows = Integer.parseInt(pair[1]); } catch (NumberFormatException e) { rows = -1; }
                            if (rows < Palette.VANILLA_ROWS || rows > Palette.MAX_ROWS)
                                return DataResult.error(() -> where + "rows must be " + Palette.VANILLA_ROWS + " to " + Palette.MAX_ROWS);
                        }
                        case "name" -> name = pair[1];
                        default -> { return DataResult.error(() -> where + "unknown setting " + pair[0]); }
                    }
                }
                continue;
            }
            // A row: row N: item, item, -, ...
            int colon = line.indexOf(':');
            if (!line.startsWith("row ") || colon < 0) return DataResult.error(() -> where + "expected row N: ...");
            int parsedRow;
            try { parsedRow = Integer.parseInt(line.substring(4, colon).strip()) - 1; } catch (NumberFormatException e) { parsedRow = -1; }
            int row = parsedRow;
            int finalRows = rows;
            if (row < 0 || row >= rows) return DataResult.error(() -> where + "row number must be 1 to " + finalRows);
            if (!rowsSeen.add(row)) return DataResult.error(() -> where + "row " + (row + 1) + " is listed twice");
            List<String> cells = splitCells(line.substring(colon + 1));
            if (cells.size() > Palette.ROW_WIDTH) return DataResult.error(() -> where + "a row holds at most " + Palette.ROW_WIDTH + " slots");
            for (int col = 0; col < cells.size(); col++) {
                String cell = cells.get(col);
                if (cell.equals(EMPTY) || cell.isEmpty()) continue;
                try {
                    StringReader reader = new StringReader(cell);
                    ItemInput input = items.parse(reader);
                    reader.skipWhitespace();
                    int count = reader.canRead() ? reader.readInt() : 1;
                    reader.skipWhitespace();
                    if (reader.canRead()) throw new IllegalArgumentException("unexpected " + reader.getRemaining());
                    if (count < 1) throw new IllegalArgumentException("count must be at least 1");
                    ItemStack stack = input.createItemStack(count); // vanilla's checks: stack size, valid components
                    slots.add(new Entry(row * Palette.ROW_WIDTH + col, ItemStackTemplate.fromNonEmptyStack(stack)));
                } catch (CommandSyntaxException | IllegalArgumentException e) {
                    String problem = e instanceof CommandSyntaxException c ? c.getRawMessage().getString() : e.getMessage();
                    return DataResult.error(() -> where + cell + ": " + problem);
                }
            }
        }
        if (mode == null) return DataResult.error(() -> "Not a palette (it starts with " + HEADER + ")");
        return DataResult.success(new Named(name, new SavedPalette(mode, rows, slots)));
    }

    /** The header's words, split at spaces outside double quotes, with the quotes taken off. */
    private static List<String> headerWords(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') quoted = !quoted;
            else if (Character.isWhitespace(c) && !quoted) {
                if (!word.isEmpty()) words.add(word.toString());
                word.setLength(0);
            } else word.append(c);
        }
        if (!word.isEmpty()) words.add(word.toString());
        return words;
    }

    /** Splits a row at its commas, but not commas inside an item's [components], {...} or quotes. */
    private static List<String> splitCells(String row) {
        List<String> cells = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        StringBuilder cell = new StringBuilder();
        for (char c : row.toCharArray()) {
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[' || c == '{' || c == '(') {
                depth++;
            } else if (c == ']' || c == '}' || c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
                continue;
            }
            cell.append(c);
        }
        if (!cell.isEmpty() || !cells.isEmpty()) cells.add(cell.toString().strip());
        return cells;
    }
}
