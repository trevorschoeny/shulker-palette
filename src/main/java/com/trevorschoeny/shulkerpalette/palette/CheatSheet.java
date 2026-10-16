package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A survival palette's cheat sheet: what belongs in each slot, loaded from a saved palette.
 * The shulker's empty slots show those items as ghosts until they are filled, so a palette
 * shared as text can be rebuilt from real blocks.
 *
 * <p>It lives on the shulker as its own component next to the palette, so it travels the
 * same way: on the item, among a placed box's leftover components, and onto the drop when
 * the box is broken. A placed box's components never reach the client, so the server sends
 * the open box's cheat sheet when the screen opens and whenever it changes ({@link Sync}).
 */
public final class CheatSheet {
    private CheatSheet() {}

    public static final DataComponentType<List<SavedPalette.Entry>> COMPONENT = DataComponentType.<List<SavedPalette.Entry>>builder()
            .persistent(SavedPalette.Entry.CODEC.listOf(0, Palette.MAX_ROWS * Palette.ROW_WIDTH))
            .networkSynchronized(SavedPalette.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(Palette.MAX_ROWS * Palette.ROW_WIDTH)))
            .cacheEncoding()
            .build();

    /** Server to client: the cheat sheet of the box open in menu {@code containerId} (empty: none). */
    public record Sync(int containerId, List<SavedPalette.Entry> entries) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "cheat_sheet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::containerId,
                SavedPalette.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(Palette.MAX_ROWS * Palette.ROW_WIDTH)), Sync::entries,
                Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "cheat_sheet"), COMPONENT);
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.STREAM_CODEC);
    }

    public static void send(ServerPlayer player, int containerId, PaletteHolder holder) {
        ServerPlayNetworking.send(player, new Sync(containerId, holder.shulkerpalette$cheatSheet()));
    }
}
