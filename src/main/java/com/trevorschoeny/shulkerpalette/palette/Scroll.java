package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Sneak + scroll moves a held palette's selection: the picked block type in pick mode, the
 * row in gradient mode. The client only reports the scroll; the server moves the selection
 * on the shulker in hand, chooses the next block, and says what is selected.
 */
public final class Scroll {
    private Scroll() {}

    /** Client to server: the player scrolled this many steps (positive is down, onwards). */
    public record Payload(int steps) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "scroll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Payload::steps, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Does scrolling move this palette's selection? Only pick and gradient have one. */
    public static boolean scrolls(Palette palette) {
        return palette.mode() == Mode.PICK || palette.mode() == Mode.GRADIENT;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE,
                (payload, context) -> context.server().execute(() -> apply(context.player(), payload.steps())));
    }

    private static void apply(ServerPlayer player, int steps) {
        ItemStack shulker = player.getMainHandItem();
        Palette palette = Palette.activeOn(shulker);
        if (palette == null || !scrolls(palette)) return; // the hand changed since the client scrolled

        ItemContainerContents contents = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        List<Draw.Entry> types = Draw.types(Draw.slots(contents));
        // The selection wraps round at both ends.
        int choices = palette.mode() == Mode.PICK ? types.size() : palette.rows();
        if (choices == 0) return;
        int selection = Math.floorMod(palette.selection() + steps, choices);

        Palette moved = new Palette(palette.on(), palette.mode(), palette.repaint(), palette.rows(), selection,
                palette.next(), palette.fingerprint());
        Placement.rollNext(shulker, moved, contents, player.level());

        // ponytail: the action bar says what is selected until the HUD strip lands (phase 3).
        Component said = palette.mode() == Mode.PICK
                ? Component.translatable("shulkerpalette.scroll.pick", types.get(selection).type().getHoverName(), types.get(selection).total())
                : Component.translatable("shulkerpalette.scroll.row", selection + 1, palette.rows());
        player.sendOverlayMessage(said);
    }
}
