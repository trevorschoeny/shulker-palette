package com.trevorschoeny.shulkerpalette.palette;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jspecify.annotations.Nullable;

/**
 * Everything that makes a shulker box a palette, kept on the shulker itself as one data
 * component. It travels with the item and, because a block entity keeps the components it
 * doesn't consume and hands them back on break, with the placed box too. Two boxes in one
 * chest can differ.
 *
 * <p>The server owns it. Clients read it to draw the held block and to predict placement,
 * and only ever change it by asking the server.
 *
 * @param on          palette mode; off makes it an ordinary shulker box
 * @param mode        how the next block is chosen
 * @param repaint     right-click replaces the targeted block instead of placing (creative only)
 * @param rows        rows the box has, 3 to 16 (extra rows are creative only)
 * @param selection   the picked block type (pick) or the current row (gradient); unused otherwise
 * @param next        the block the next click places, chosen in advance so it can be shown
 * @param fingerprint the contents {@link #next} was chosen from; a mismatch means the box
 *                    changed since, so the next block is chosen again
 */
public record Palette(boolean on, Mode mode, boolean repaint, int rows, int selection,
                      Optional<ItemStackTemplate> next, int fingerprint) {

    public static final int VANILLA_ROWS = 3;
    public static final int MAX_ROWS = 16;
    public static final int ROW_WIDTH = 9;

    /** A shulker that has never been a palette reads as this. */
    public static final Palette OFF = new Palette(false, Mode.RANDOM, false, VANILLA_ROWS, 0, Optional.empty(), 0);

    // Every field is optional in the saved form, so a command or a scene can write a partial
    // palette ({on:true}) and get the defaults for the rest.
    public static final Codec<Palette> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("on", false).forGetter(Palette::on),
            Mode.CODEC.optionalFieldOf("mode", Mode.RANDOM).forGetter(Palette::mode),
            Codec.BOOL.optionalFieldOf("repaint", false).forGetter(Palette::repaint),
            Codec.intRange(VANILLA_ROWS, MAX_ROWS).optionalFieldOf("rows", VANILLA_ROWS).forGetter(Palette::rows),
            Codec.INT.optionalFieldOf("selection", 0).forGetter(Palette::selection),
            ItemStackTemplate.CODEC.optionalFieldOf("next").forGetter(Palette::next),
            Codec.INT.optionalFieldOf("fingerprint", 0).forGetter(Palette::fingerprint)
    ).apply(i, Palette::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Palette> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Palette::on,
            Mode.STREAM_CODEC, Palette::mode,
            ByteBufCodecs.BOOL, Palette::repaint,
            ByteBufCodecs.VAR_INT, Palette::rows,
            ByteBufCodecs.VAR_INT, Palette::selection,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), Palette::next,
            ByteBufCodecs.VAR_INT, Palette::fingerprint,
            Palette::new);

    public static final DataComponentType<Palette> COMPONENT = DataComponentType.<Palette>builder()
            .persistent(CODEC)
            .networkSynchronized(STREAM_CODEC)
            .cacheEncoding()
            .build();

    /** Registers the component. Called once from the common initializer. */
    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "palette"), COMPONENT);
    }

    /** Is this stack a shulker box (vanilla, dyed, or any modded box built on vanilla's block)? */
    public static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
    }

    /** The stack's palette if it is a shulker with palette mode on, else null. */
    public static @Nullable Palette activeOn(ItemStack stack) {
        if (!isShulker(stack)) return null;
        Palette palette = stack.get(COMPONENT);
        return palette != null && palette.on() ? palette : null;
    }

    public Palette withNext(Optional<ItemStackTemplate> next, int fingerprint) {
        return new Palette(on, mode, repaint, rows, selection, next, fingerprint);
    }
}
