package com.trevorschoeny.shulkerpalette.palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Choosing the next block from a box's contents, and taking it out again. No side effects:
 * every method takes the contents and returns a value, so the server's placement, its
 * upkeep and the client's HUD all read the box the same way.
 *
 * <p>Only block items count. Tools, food and anything else in the box are never chosen and
 * never consumed, so a stray sword doesn't break the palette.
 */
public final class Draw {
    private Draw() {}

    /** One block type in a box, with how many of it there are (across every stack). */
    public record Entry(ItemStack type, int total) {}

    /** Can this stack be placed from a palette? A shulker can't hold another shulker, so any block item. */
    public static boolean eligible(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    /** The box's slots as stacks, in slot order (index i is slot i). */
    public static List<ItemStack> slots(ItemContainerContents contents) {
        return contents.itemCopies().toList();
    }

    /**
     * The slots a draw may use: the whole box, or one row of it in gradient mode. Returned as
     * slot indices so removal can find the same slots again.
     */
    static List<Integer> scope(Palette palette, List<ItemStack> slots) {
        int from = 0;
        int to = slots.size();
        if (palette.mode() == Mode.GRADIENT) {
            from = Math.min(currentRow(palette) * Palette.ROW_WIDTH, slots.size());
            to = Math.min(from + Palette.ROW_WIDTH, slots.size());
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = from; i < to; i++) {
            if (eligible(slots.get(i))) indices.add(i);
        }
        return indices;
    }

    /** The gradient row a palette is on, kept inside the rows the box has. */
    public static int currentRow(Palette palette) {
        return Math.clamp(palette.selection(), 0, palette.rows() - 1);
    }

    /**
     * Every block type in the box, each once, in order of first appearance, with its total.
     * Pick mode's list and equal random's set of types; the popup shows these totals.
     */
    public static List<Entry> types(List<ItemStack> slots) {
        List<Entry> entries = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (!eligible(stack)) continue;
            int found = -1;
            for (int e = 0; e < entries.size(); e++) {
                if (ItemStack.isSameItemSameComponents(entries.get(e).type(), stack)) { found = e; break; }
            }
            if (found < 0) entries.add(new Entry(stack.copyWithCount(1), stack.getCount()));
            else entries.set(found, new Entry(entries.get(found).type(), entries.get(found).total() + stack.getCount()));
        }
        return entries;
    }

    /**
     * The block type pick mode places, as an index into {@link #types}: the selection, kept
     * inside the list. When the picked type runs out the list closes up, so the same index
     * moves on to the next type without rewriting the selection.
     */
    public static int currentPick(Palette palette, List<Entry> types) {
        return types.isEmpty() ? -1 : Math.clamp(palette.selection(), 0, types.size() - 1);
    }

    /**
     * Chooses the next block for this palette from these contents. Empty when nothing is eligible.
     *
     * <p>With {@code shaped} (creative), the palette's shape applies: only materials that come in
     * it take part, keeping their ratios, and the block chosen is that material in that shape. A
     * shape the box no longer offers falls back to a full block. Without it (survival) the box's
     * items are placed as they are, since a shape would need items the box doesn't hold.
     */
    public static Optional<ItemStackTemplate> roll(Palette palette, ItemContainerContents contents, RandomSource random, boolean shaped) {
        List<ItemStack> slots = slots(contents);
        Shape shape = shaped ? Shapes.effective(palette.shape(), slots) : null;
        Predicate<ItemStack> fits = stack -> shape == null || Shapes.variant(stack.getItem(), shape) != null;
        ItemStack chosen = switch (palette.mode()) {
            case RANDOM, GRADIENT -> byCount(slots, scope(palette, slots).stream().filter(i -> fits.test(slots.get(i))).toList(), random);
            case EQUAL -> {
                List<Entry> types = types(slots).stream().filter(e -> fits.test(e.type())).toList();
                yield types.isEmpty() ? ItemStack.EMPTY : types.get(random.nextInt(types.size())).type();
            }
            case PICK -> {
                // The picked material, if it comes in the shape; pick mode doesn't move on by itself.
                List<Entry> types = types(slots);
                int pick = currentPick(palette, types);
                yield pick < 0 || !fits.test(types.get(pick).type()) ? ItemStack.EMPTY : types.get(pick).type();
            }
        };
        if (chosen.isEmpty()) return Optional.empty();
        if (shape == null) return Optional.of(ItemStackTemplate.fromNonEmptyStack(chosen, 1));
        return Optional.of(ItemStackTemplate.fromNonEmptyStack(new ItemStack(Shapes.variant(chosen.getItem(), shape)), 1));
    }

    /** One item, uniformly, from every item in the given slots: each item is one ticket. */
    private static ItemStack byCount(List<ItemStack> slots, List<Integer> scope, RandomSource random) {
        int total = 0;
        for (int i : scope) total += slots.get(i).getCount();
        if (total == 0) return ItemStack.EMPTY;
        int ticket = random.nextInt(total);
        for (int i : scope) {
            ticket -= slots.get(i).getCount();
            if (ticket < 0) return slots.get(i);
        }
        return ItemStack.EMPTY; // unreachable: the tickets add up to total
    }

    /**
     * The contents with one of {@code placed} taken out, from the first slot in scope that
     * holds it. Empty when no slot does, which means the client and server disagreed about
     * the box; the caller treats that as a failed placement.
     */
    public static Optional<ItemContainerContents> removeOne(Palette palette, ItemContainerContents contents, ItemStack placed) {
        List<ItemStack> slots = new ArrayList<>(slots(contents));
        List<Integer> candidates = palette.mode() == Mode.GRADIENT ? scope(palette, slots) : allIndices(slots.size());
        for (int i : candidates) {
            ItemStack stack = slots.get(i);
            if (ItemStack.isSameItemSameComponents(stack, placed)) {
                slots.set(i, stack.copyWithCount(stack.getCount() - 1));
                return Optional.of(ItemContainerContents.fromItems(slots));
            }
        }
        return Optional.empty();
    }

    private static List<Integer> allIndices(int size) {
        List<Integer> all = new ArrayList<>(size);
        for (int i = 0; i < size; i++) all.add(i);
        return all;
    }

    /**
     * A hash of the contents that stays the same across restarts (item ids and counts per
     * slot), unlike {@code ItemContainerContents.hashCode()}, which can lean on identity
     * hashes. A palette stores it next to its next block; a mismatch means the box changed, or
     * whether shapes apply did.
     * Ignores components, so renaming a block inside the box doesn't count as a change.
     */
    public static int fingerprint(ItemContainerContents contents, boolean shaped) {
        // Shapes apply in creative only, so switching game mode chooses the next block again.
        int hash = shaped ? 7 : 1;
        List<ItemStack> slots = slots(contents);
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;
            hash = 31 * hash + i;
            hash = 31 * hash + BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
            hash = 31 * hash + stack.getCount();
        }
        return hash;
    }
}
