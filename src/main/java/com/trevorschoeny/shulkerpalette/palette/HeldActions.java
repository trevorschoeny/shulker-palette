package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * What the palette menu and the palette keys do to the shulker in the main hand. No container
 * menu is open, so these travel as their own packets; the server applies each one to the
 * stack in hand with the same rules as everywhere else (repaint creative only, mode only while
 * on, every loaded palette checked).
 *
 * <ul>
 *   <li>{@link Act}: power, mode, repaint, save (answered with the box's palette for the
 *       library), clear the cheat sheet.</li>
 *   <li>{@link LoadHeld}: a palette from the library. Creative fills the box with it; survival
 *       sets the box's cheat sheet, shown in its slots wherever the box is opened.</li>
 * </ul>
 */
public final class HeldActions {
    private HeldActions() {}

    /** Client to server: press {@code action} ({@link PanelActions}) for the shulker in the main hand. */
    public record Act(int action) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "held_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Act::action, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: load this palette into the shulker in the main hand. */
    public record LoadHeld(SavedPalette palette) implements CustomPacketPayload {
        public static final Type<LoadHeld> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "held_load"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LoadHeld> STREAM_CODEC =
                StreamCodec.composite(SavedPalette.STREAM_CODEC, LoadHeld::palette, LoadHeld::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LoadHeld.TYPE, LoadHeld.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Act.TYPE,
                (payload, context) -> context.server().execute(() -> act(context.player(), payload.action())));
        ServerPlayNetworking.registerGlobalReceiver(LoadHeld.TYPE,
                (payload, context) -> context.server().execute(() -> load(context.player(), payload.palette())));
    }

    private static void act(ServerPlayer player, int action) {
        ItemStack shulker = player.getMainHandItem();
        if (!Palette.isShulker(shulker)) return; // the hand changed since the press
        // Any shulker can be switched on, so one with no palette yet counts as off.
        Palette palette = shulker.getOrDefault(Palette.COMPONENT, Palette.OFF);
        switch (action) {
            case PanelActions.POWER, PanelActions.MODE, PanelActions.REPAINT -> {
                Palette changed = PanelActions.apply(palette, action, player);
                if (changed == palette) return;
                shulker.set(Palette.COMPONENT, changed);
                if (action == PanelActions.POWER) say(player, changed.on() ? "shulkerpalette.key.power.on" : "shulkerpalette.key.power.off");
                if (action == PanelActions.REPAINT) say(player, changed.repaint() ? "shulkerpalette.key.repaint.on" : "shulkerpalette.key.repaint.off");
            }
            case PanelActions.SAVE -> ServerPlayNetworking.send(player, new PaletteTransfer.Saved(SavedPalette.of(palette, box(shulker, palette))));
            case PanelActions.CLEAR -> shulker.remove(CheatSheet.COMPONENT);
            default -> { } // anything else from the wire is ignored
        }
    }

    /**
     * Load into the held box. Creative takes the saved rows, mode and items, replacing what the box
     * held; survival takes the mode and a cheat sheet, keeping the box's own rows (slots past them
     * are left off).
     */
    private static void load(ServerPlayer player, SavedPalette saved) {
        ItemStack shulker = player.getMainHandItem();
        if (!Palette.isShulker(shulker) || !saved.valid()) return;
        Palette current = shulker.getOrDefault(Palette.COMPONENT, Palette.OFF);
        int rows = player.isCreative() ? saved.rows() : current.rows();
        shulker.set(Palette.COMPONENT, new Palette(current.on(), saved.mode(), current.repaint(), rows, 0, current.shape(), Optional.empty(), 0));
        int room = rows * Palette.ROW_WIDTH;
        List<SavedPalette.Entry> fitting = saved.slots().stream().filter(e -> e.slot() < room).toList();
        if (player.isCreative()) {
            List<ItemStack> items = new ArrayList<>(java.util.Collections.nCopies(Palette.MAX_ROWS * Palette.ROW_WIDTH, ItemStack.EMPTY));
            for (SavedPalette.Entry entry : fitting) items.set(entry.slot(), entry.item().create());
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            shulker.remove(CheatSheet.COMPONENT);
        } else if (fitting.isEmpty()) {
            shulker.remove(CheatSheet.COMPONENT);
        } else {
            shulker.set(CheatSheet.COMPONENT, fitting);
        }
    }

    /** The held box's items as a container sized to its rows, for a snapshot. */
    private static SimpleContainer box(ItemStack shulker, Palette palette) {
        SimpleContainer box = new SimpleContainer(palette.rows() * Palette.ROW_WIDTH);
        List<ItemStack> items = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList();
        for (int i = 0; i < Math.min(items.size(), box.getContainerSize()); i++) box.setItem(i, items.get(i));
        return box;
    }

    private static void say(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable(key));
    }
}
