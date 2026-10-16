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

    /**
     * The selection after scrolling {@code steps}, wrapping round at both ends: from block
     * type to block type in pick mode, from row to
     * row in gradient mode (rows holding no blocks skipped). -1 when there is nothing to
     * choose. The client runs this too, to move the popup before the server's answer
     * arrives.
     */
    public static int moved(Palette palette, ItemContainerContents contents, int steps) {
        List<ItemStack> slots = Draw.slots(contents);
        if (palette.mode() == Mode.GRADIENT) {
            // Row to row, skipping rows that hold no blocks (the popup leaves them out too).
            int row = Draw.currentRow(palette);
            int direction = Integer.signum(steps);
            boolean any = false;
            for (int r = 0; r < palette.rows(); r++) any |= rowHasBlocks(slots, r);
            if (!any) return -1;
            for (int moves = Math.abs(steps); moves > 0; moves--) {
                do row = Math.floorMod(row + direction, palette.rows());
                while (!rowHasBlocks(slots, row));
            }
            return row;
        }
        List<Draw.Entry> types = Draw.types(slots);
        int pick = Draw.currentPick(palette, types);
        return pick < 0 ? -1 : Math.floorMod(pick + steps, types.size());
    }

    private static boolean rowHasBlocks(List<ItemStack> slots, int row) {
        for (int i = row * Palette.ROW_WIDTH; i < Math.min((row + 1) * Palette.ROW_WIDTH, slots.size()); i++) {
            if (Draw.eligible(slots.get(i))) return true;
        }
        return false;
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
        int selection = moved(palette, contents, steps);
        if (selection < 0) return;

        Palette moved = new Palette(palette.on(), palette.mode(), palette.repaint(), palette.rows(), selection,
                palette.next(), palette.fingerprint());
        Placement.rollNext(shulker, moved, contents, player.level());

        // The strip by the hotbar shows the choices; the action bar names the one chosen,
        // the way vanilla names a newly held item.
        Component said = palette.mode() == Mode.PICK
                ? Component.translatable("shulkerpalette.scroll.pick", types.get(selection).type().getHoverName(), types.get(selection).total())
                : Component.translatable("shulkerpalette.scroll.row", selection + 1, palette.rows());
        player.sendOverlayMessage(said);
    }
}
