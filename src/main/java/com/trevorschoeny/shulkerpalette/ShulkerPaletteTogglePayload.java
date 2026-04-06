package com.trevorschoeny.shulkerpalette;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S packet: tells the server to toggle the Shulker Palette flag.
 *
 * Two modes:
 *   - menuSlotIndex = -1 → toggle via the open ShulkerBoxMenu (placed block)
 *   - menuSlotIndex >= 0 → toggle the item's CUSTOM_DATA at that menu slot (peek)
 */
public record ShulkerPaletteTogglePayload(int menuSlotIndex) implements CustomPacketPayload {

    /** Convenience: toggle the currently open ShulkerBoxMenu (placed block). */
    public ShulkerPaletteTogglePayload() {
        this(-1);
    }

    public static final CustomPacketPayload.Type<ShulkerPaletteTogglePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShulkerPaletteMod.MOD_ID, "shulker_palette_toggle"));

    public static final StreamCodec<FriendlyByteBuf, ShulkerPaletteTogglePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ShulkerPaletteTogglePayload::menuSlotIndex,
                    ShulkerPaletteTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
