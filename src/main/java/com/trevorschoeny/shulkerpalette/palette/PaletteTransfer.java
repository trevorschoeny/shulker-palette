package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * The panel's Save and Load, across the network. The box's contents live on the server
 * (extra rows included, which the client never sees), so:
 * <ul>
 *   <li>Save: the panel presses a menu button; the server snapshots the box and sends the
 *       palette back ({@link Saved}); the client files it in its palette library.</li>
 *   <li>Load: the client picks a palette from its library and sends it ({@link Load}); the
 *       server checks it and applies it to the open box.</li>
 * </ul>
 * The library itself is client-side files, so the server stores nothing.
 */
public final class PaletteTransfer {
    private PaletteTransfer() {}

    /** Server to client: the open box's palette, for the library. */
    public record Saved(SavedPalette palette) implements CustomPacketPayload {
        public static final Type<Saved> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "saved"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Saved> STREAM_CODEC =
                StreamCodec.composite(SavedPalette.STREAM_CODEC, Saved::palette, Saved::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: load this palette into the box open in menu {@code containerId}. */
    public record Load(int containerId, SavedPalette palette) implements CustomPacketPayload {
        public static final Type<Load> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "load"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Load> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Load::containerId, SavedPalette.STREAM_CODEC, Load::palette, Load::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Saved.TYPE, Saved.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Load.TYPE, Load.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Load.TYPE,
                (payload, context) -> context.server().execute(() -> load(context.player(), payload)));
    }

    /** Save: snapshot the open box and send it to its player. */
    public static void save(ServerPlayer player, PaletteHolder holder) {
        if (holder instanceof Container box) {
            ServerPlayNetworking.send(player, new Saved(SavedPalette.of(holder.shulkerpalette$palette(), box)));
        }
    }

    /**
     * Load. In creative the box takes the saved rows, mode and items, replacing what it held
     * (creative items are free). In survival it takes the mode and a cheat sheet: ghosts of
     * the saved items in the empty slots, for the player to fill from real blocks. Survival
     * keeps the box's own rows, so slots past them are left off the sheet.
     */
    private static void load(ServerPlayer player, Load payload) {
        if (!payload.palette().valid()) return;
        if (player.containerMenu.containerId != payload.containerId()) return; // the screen changed since
        if (!(player.containerMenu instanceof PaletteMenu menu) || !(menu.shulkerpalette$holder() instanceof Container box)) return;
        PaletteHolder holder = menu.shulkerpalette$holder();
        SavedPalette saved = payload.palette();
        Palette current = holder.shulkerpalette$palette();

        // Creative takes the saved rows (extra rows are creative); survival keeps the box's own,
        // and slots past them are left off the sheet.
        int rows = player.isCreative() ? saved.rows() : current.rows();
        holder.shulkerpalette$setPalette(new Palette(current.on(), saved.mode(), current.repaint(), rows, 0, current.shape(), Optional.empty(), 0));
        int room = rows * Palette.ROW_WIDTH;
        List<SavedPalette.Entry> fitting = saved.slots().stream().filter(e -> e.slot() < room).toList();
        if (player.isCreative()) {
            // Every slot of the room, not just the rows reported, so no old extra row hides items.
            for (int i = 0; i < Palette.MAX_ROWS * Palette.ROW_WIDTH; i++) box.setItem(i, ItemStack.EMPTY);
            for (SavedPalette.Entry entry : fitting) box.setItem(entry.slot(), entry.item().create());
            holder.shulkerpalette$setCheatSheet(List.of());
        } else {
            holder.shulkerpalette$setCheatSheet(fitting);
        }
        box.setChanged();
        CheatSheet.send(player, payload.containerId(), holder);
        player.containerMenu.broadcastChanges();
    }
}
