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
import net.minecraft.world.item.ItemStack;

/**
 * The panel's Save and Load, across the network. The box's contents live on the server
 * (extra rows included, which the client never sees), so:
 * <ul>
 *   <li>Save: the panel presses a menu button; the server snapshots the box and sends the
 *       palette back ({@link Saved}); the client puts it on the clipboard.</li>
 *   <li>Load: the client reads the clipboard and sends the palette ({@link Load}); the server
 *       checks it and applies it to the open box.</li>
 * </ul>
 */
public final class Clipboard {
    private Clipboard() {}

    /** Server to client: the open box's palette, for the clipboard. */
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
     * Load, in creative: the box takes the saved rows, mode and items, replacing what it held
     * (creative items are free). Survival loading becomes a cheat sheet (not built yet), so
     * it is refused for now.
     */
    private static void load(ServerPlayer player, Load payload) {
        if (!payload.palette().valid() || !player.isCreative()) return;
        if (player.containerMenu.containerId != payload.containerId()) return; // the screen changed since
        if (!(player.containerMenu instanceof PaletteMenu menu) || !(menu.shulkerpalette$holder() instanceof Container box)) return;
        PaletteHolder holder = menu.shulkerpalette$holder();

        SavedPalette saved = payload.palette();
        Palette current = holder.shulkerpalette$palette();
        holder.shulkerpalette$setPalette(new Palette(current.on(), saved.mode(), current.repaint(), saved.rows(),
                0, java.util.Optional.empty(), 0));
        for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, ItemStack.EMPTY);
        for (SavedPalette.Entry entry : saved.slots()) box.setItem(entry.slot(), entry.item().create());
        box.setChanged();
        player.containerMenu.broadcastChanges();
    }
}
