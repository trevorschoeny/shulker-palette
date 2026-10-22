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
 * Scrolling with a palette in hand moves one of its two axes:
 * <ul>
 *   <li><b>Material</b> (palette modifier + scroll): the picked block type in pick mode, the
 *       row in gradient mode.</li>
 *   <li><b>Shape</b> (shape modifier + scroll, creative only): full block, stairs, slab and so on,
 *       through the shapes the box's materials come in.</li>
 * </ul>
 * The client only reports the scroll; the server moves the axis on the shulker in hand, chooses
 * the next block, and says what is selected.
 */
public final class Scroll {
    private Scroll() {}

    /** Client to server: the player scrolled this many steps (positive is down, onwards), on the material axis or the shape axis. */
    public record Payload(int steps, boolean shape) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "scroll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Payload::steps, ByteBufCodecs.BOOL, Payload::shape, Payload::new);

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

    /**
     * The shape after scrolling {@code steps} through the shapes the box offers, wrapping round.
     * A shape the box no longer offers counts as a full block. The client runs this too.
     */
    public static Shape movedShape(Palette palette, ItemContainerContents contents, int steps) {
        List<Shape> shapes = Shapes.available(Draw.slots(contents));
        if (shapes.isEmpty()) return palette.shape();
        int at = Math.max(0, shapes.indexOf(Shapes.effective(palette.shape(), Draw.slots(contents))));
        return shapes.get(Math.floorMod(at + steps, shapes.size()));
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
                (payload, context) -> context.server().execute(() -> {
                    if (payload.shape()) applyShape(context.player(), payload.steps());
                    else apply(context.player(), payload.steps());
                }));
    }

    private static void apply(ServerPlayer player, int steps) {
        // The client sends any int; more steps than a box has slots only loops round, and an
        // unbounded count would keep the server thread busy for minutes.
        steps = Math.clamp(steps, -Palette.MAX_ROWS * Palette.ROW_WIDTH, Palette.MAX_ROWS * Palette.ROW_WIDTH);
        ItemStack shulker = player.getMainHandItem();
        Palette palette = Palette.activeOn(shulker);
        if (palette == null || !scrolls(palette)) return; // the hand changed since the client scrolled

        ItemContainerContents contents = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        List<Draw.Entry> types = Draw.types(Draw.slots(contents));
        int selection = moved(palette, contents, steps);
        if (selection < 0) return;

        Placement.rollNext(shulker, palette.withSelection(selection), contents, player.level(), Placement.shaped(player));

        // The strip by the hotbar shows the choices; the action bar names the one chosen,
        // the way vanilla names a newly held item.
        Component said = palette.mode() == Mode.PICK
                ? Component.translatable("shulkerpalette.scroll.pick", types.get(selection).type().getHoverName(), types.get(selection).total())
                : Component.translatable("shulkerpalette.scroll.row", selection + 1, palette.rows());
        player.sendOverlayMessage(said);
    }

    private static void applyShape(ServerPlayer player, int steps) {
        ItemStack shulker = player.getMainHandItem();
        Palette palette = Palette.activeOn(shulker);
        if (palette == null || !Placement.shaped(player)) return; // shapes are creative only
        steps = Math.clamp(steps, -Shape.values().length, Shape.values().length);
        ItemContainerContents contents = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        Shape shape = movedShape(palette, contents, steps);
        Placement.rollNext(shulker, palette.withShape(shape), contents, player.level(), true);
        player.sendOverlayMessage(Component.translatable("shulkerpalette.scroll.shape",
                Component.translatable("shulkerpalette.shape." + shape.getSerializedName())));
    }
}
