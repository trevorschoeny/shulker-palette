package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The server's half of the palette keys.
 *
 * <p>The palette modifier (Option by default, rebindable) replaced sneak for every palette
 * gesture, since builders sneak all the time (Trev, 2026-10-15): modifier + scroll moves the
 * selection, modifier + right-click on a block places the box itself, modifier + right-click
 * into the air opens it. The server can't see a key, so the client reports the modifier
 * whenever it goes down or up, before any click it applies to (one connection keeps order).
 *
 * <p>The repaint key flips repaint on the palette in hand, as the panel's button does.
 */
public final class Keys {
    private Keys() {}

    /**
     * Players holding the modifier right now. Shared by both sides in one JVM: the client
     * marks its own player as it sends, so the client half of a click agrees with the server.
     */
    private static final Set<UUID> HOLDING = ConcurrentHashMap.newKeySet();

    /** Client to server: the modifier went down (true) or up (false). */
    public record Modifier(boolean held) implements CustomPacketPayload {
        public static final Type<Modifier> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "modifier"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Modifier> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Modifier::held, Modifier::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: flip repaint on the palette in the main hand. */
    public record ToggleRepaint() implements CustomPacketPayload {
        public static final Type<ToggleRepaint> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "toggle_repaint"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleRepaint> STREAM_CODEC = StreamCodec.unit(new ToggleRepaint());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Is this player holding the palette modifier? */
    public static boolean modifierHeld(Player player) {
        return HOLDING.contains(player.getUUID());
    }

    /** Records the modifier for a player; the client calls this for itself as it reports. */
    public static void setModifier(UUID player, boolean held) {
        if (held) HOLDING.add(player);
        else HOLDING.remove(player);
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Modifier.TYPE, Modifier.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleRepaint.TYPE, ToggleRepaint.STREAM_CODEC);
        // Applied on the network thread, not queued: a click right behind it is handled on the
        // server thread, and must already see the modifier.
        ServerPlayNetworking.registerGlobalReceiver(Modifier.TYPE,
                (payload, context) -> setModifier(context.player().getUUID(), payload.held()));
        ServerPlayNetworking.registerGlobalReceiver(ToggleRepaint.TYPE,
                (payload, context) -> context.server().execute(() -> toggleRepaint(context.player())));
        // A player who leaves with the key down never sends the release.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> setModifier(handler.player.getUUID(), false));
    }

    private static void toggleRepaint(ServerPlayer player) {
        ItemStack shulker = player.getMainHandItem();
        Palette palette = Palette.activeOn(shulker);
        if (palette == null) return; // the hand changed since the key was pressed
        // The same rule as the panel's button: palette on, creative only in v1.
        Palette changed = PanelActions.apply(palette, PanelActions.REPAINT, player);
        if (changed == palette) return;
        shulker.set(Palette.COMPONENT, changed);
        player.sendOverlayMessage(Component.translatable(changed.repaint() ? "shulkerpalette.key.repaint.on" : "shulkerpalette.key.repaint.off"));
    }
}
