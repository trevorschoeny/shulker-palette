package com.trevorschoeny.shulkerpalette.palette;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.SlotRef;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Fill shulker box: tops up {@code target}'s reserved slots, up to each one's cap, from the
 * player's inventory and any other container open alongside it (a chest, say). Survival;
 * creative's load already fills the box outright, so this is a no-op there.
 */
public final class Fill {
    private Fill() {}

    public static void fill(ServerPlayer player, Target target) {
        List<Container> sources = sources(player, target);
        if (target.slots() != null) {
            for (Slot slot : target.slots()) {
                Reservation r = Reservations.get(SlotRef.of(slot.container, slot.getContainerSlot(), null));
                if (!r.isNone()) topUp(slot.container, slot.getContainerSlot(), r, sources);
            }
            player.containerMenu.broadcastChanges();
        } else {
            fillHeld(target.stack(), sources);
        }
    }

    /** The player's inventory, then every other container open on the player's menu right now, box excluded. */
    private static List<Container> sources(ServerPlayer player, Target target) {
        Container box = target.slots() == null ? null : target.slots().getFirst().container;
        List<Container> sources = new ArrayList<>();
        sources.add(player.getInventory());
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != box && !sources.contains(slot.container)) sources.add(slot.container);
        }
        return sources;
    }

    /** A box in hand: its contents live in the item's container component, not a live {@link Container}. */
    private static void fillHeld(ItemStack box, List<Container> sources) {
        List<ItemStack> items = new ArrayList<>(box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList());
        // The component stores only up to the last filled slot (an empty box has none), so the
        // snapshot spans the box's rows, not just what's stored.
        int rows = box.getOrDefault(Palette.COMPONENT, Palette.OFF).rows();
        SimpleContainer snapshot = new SimpleContainer(Math.max(items.size(), rows * Palette.ROW_WIDTH));
        for (int i = 0; i < items.size(); i++) snapshot.setItem(i, items.get(i));
        boolean changed = false;
        for (int i = 0; i < snapshot.getContainerSize(); i++) {
            Reservation r = Reservations.get(box, i);
            if (!r.isNone()) changed |= topUp(snapshot, i, r, sources);
        }
        if (changed) box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(snapshot.getItems()));
    }

    /** Tops up {@code container}'s reserved slot {@code index} from {@code sources}, up to {@code r}'s cap. */
    private static boolean topUp(Container container, int index, Reservation r, List<Container> sources) {
        int need = r.limit(r.item()) - container.getItem(index).getCount();
        boolean changed = false;
        for (Container source : sources) {
            if (need <= 0) break;
            for (int s = 0; s < source.getContainerSize() && need > 0; s++) {
                ItemStack stack = source.getItem(s);
                if (stack.isEmpty() || !r.matches(stack)) continue;
                int take = Math.min(need, stack.getCount());
                ItemStack there = container.getItem(index);
                container.setItem(index, there.isEmpty() ? stack.copyWithCount(take) : there.copyWithCount(there.getCount() + take));
                stack.shrink(take);
                source.setItem(s, stack);
                need -= take;
                changed = true;
            }
        }
        if (changed) container.setChanged();
        return changed;
    }
}
