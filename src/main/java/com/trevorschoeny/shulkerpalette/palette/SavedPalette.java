package com.trevorschoeny.shulkerpalette.palette;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * A palette as text you can copy and paste: the box's layout (what sits in each slot, and how
 * many), its number of rows, and its mode. Save puts one on the clipboard; Load reads one back.
 *
 * <p>On the clipboard it is {@code shulkerpalette:} followed by the layout as base64 JSON,
 * one token that survives being pasted into chat or a document. Pasted text is untrusted:
 * {@link #decode} rejects anything malformed, and the server checks a decoded palette again
 * ({@link #valid}) before it touches a box.
 */
public record SavedPalette(Mode mode, int rows, List<Entry> slots) {

    /** One filled slot. */
    public record Entry(int slot, ItemStackTemplate item) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, CAPACITY - 1).fieldOf("slot").forGetter(Entry::slot),
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Entry::item)
        ).apply(i, Entry::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::slot, ItemStackTemplate.STREAM_CODEC, Entry::item, Entry::new);
    }

    private static final String PREFIX = "shulkerpalette:";
    private static final int CAPACITY = Palette.MAX_ROWS * Palette.ROW_WIDTH;

    static final Codec<SavedPalette> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mode.CODEC.fieldOf("mode").forGetter(SavedPalette::mode),
            Codec.intRange(Palette.VANILLA_ROWS, Palette.MAX_ROWS).fieldOf("rows").forGetter(SavedPalette::rows),
            Entry.CODEC.listOf(0, CAPACITY).fieldOf("slots").forGetter(SavedPalette::slots)
    ).apply(i, SavedPalette::new));

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
        }
        return true;
    }

    public String encode(HolderLookup.Provider registries) {
        String json = CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), this).getOrThrow().toString();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** The palette in this text, or empty if it isn't one (anything at all may be on the clipboard). */
    public static Optional<SavedPalette> decode(String text, HolderLookup.Provider registries) {
        String trimmed = text.strip();
        if (!trimmed.startsWith(PREFIX)) return Optional.empty();
        try {
            String json = new String(Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length())), StandardCharsets.UTF_8);
            return CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json))
                    .result().filter(SavedPalette::valid);
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }
}
