package com.trevorschoeny.shulkerpalette.palette;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * The legacy survival cheat sheet: what belonged in each slot, before Hiddenite turned that
 * into a SlotKit {@link Reservation} per slot (a ghost item, strict, capped at the ratio's
 * count) instead of Shulker Palette's own ghost. SlotKit's own ghosts and vetoes take it from
 * there, so nothing here draws or syncs anything any more.
 *
 * <p>{@link #COMPONENT} and {@link #migrate} stay only to carry an old box across the upgrade:
 * a box saved before Hiddenite still has this component, read once (on open, {@link
 * com.trevorschoeny.shulkerpalette.mixin.ShulkerBoxMenuMixin}) and turned into reservations.
 */
public final class CheatSheet {
    private CheatSheet() {}

    public static final DataComponentType<List<SavedPalette.Entry>> COMPONENT = DataComponentType.<List<SavedPalette.Entry>>builder()
            .persistent(SavedPalette.Entry.CODEC.listOf(0, Palette.MAX_ROWS * Palette.ROW_WIDTH))
            .networkSynchronized(SavedPalette.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(Palette.MAX_ROWS * Palette.ROW_WIDTH)))
            .cacheEncoding()
            .build();

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "cheat_sheet"), COMPONENT);
    }

    /** A box still carrying a pre-Hiddenite cheat sheet: its entries become reservations on {@code container}, once. */
    public static void migrate(Container container, PaletteHolder holder) {
        List<SavedPalette.Entry> legacy = holder.shulkerpalette$cheatSheet();
        if (legacy.isEmpty()) return;
        for (SavedPalette.Entry entry : legacy) {
            if (entry.slot() >= container.getContainerSize()) continue;
            Reservations.set(container, entry.slot(), reservationFor(entry));
        }
        holder.shulkerpalette$setCheatSheet(List.of());
    }

    /**
     * Reservations equivalent to {@code entries} over {@code box}'s slots {@code [0, room)}: each
     * entry's item, capped at its saved count; every other slot in {@code room} cleared. What a
     * survival Load applies instead of the old ghost cheat sheet.
     */
    public static void applyAsReservations(Container box, int room, List<SavedPalette.Entry> entries) {
        Map<Integer, SavedPalette.Entry> bySlot = new HashMap<>();
        for (SavedPalette.Entry entry : entries) bySlot.put(entry.slot(), entry);
        for (int i = 0; i < room; i++) {
            SavedPalette.Entry entry = bySlot.get(i);
            Reservations.set(box, i, entry == null ? Reservation.NONE : reservationFor(entry));
        }
    }

    /** {@link #applyAsReservations(Container, int, List)}, for a box held as an item. */
    public static void applyAsReservations(ItemStack box, int room, List<SavedPalette.Entry> entries) {
        Map<Integer, SavedPalette.Entry> bySlot = new HashMap<>();
        for (SavedPalette.Entry entry : entries) bySlot.put(entry.slot(), entry);
        for (int i = 0; i < room; i++) {
            SavedPalette.Entry entry = bySlot.get(i);
            Reservations.set(box, i, entry == null ? Reservation.NONE : reservationFor(entry));
        }
    }

    private static Reservation reservationFor(SavedPalette.Entry entry) {
        ItemStack stack = entry.item().create();
        return Reservation.of(stack).withCap(stack.getCount());
    }
}
