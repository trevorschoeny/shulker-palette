package com.trevorschoeny.shulkerpalette;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles rolling a random placeable block from a shulker box's contents.
 *
 * Selection is weighted by stack count: a stack of 32 stone has 32× the chance
 * of a stack of 1 oak plank. This gives natural weighting — the more of an item
 * you have, the more often it gets placed.
 */
public final class ShulkerPaletteRoll {

    private ShulkerPaletteRoll() {}

    /**
     * Result of a shulker palette roll.
     *
     * @param itemId           Registry ID of the block to place (e.g. "minecraft:stone").
     * @param internalSlot     Slot index (0–26) inside the shulker to decrement.
     */
    public record Result(String itemId, int internalSlot) {}

    /**
     * Rolls a random item from the shulker box item's contents.
     *
     * @param shulkerStack  The shulker box ItemStack (must have CONTAINER component).
     * @return A roll result, or null if the shulker is empty.
     */
    public static Result roll(ItemStack shulkerStack) {
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return null;

        // Extract all 27 slots from the shulker.
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);

        // Build a list of candidates: (slot index, item ID, weight = count).
        // No filtering — any item in the shulker can be part of the palette.
        record Candidate(int slot, String itemId, int weight) {}
        List<Candidate> candidates = new ArrayList<>();
        int totalWeight = 0;

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            candidates.add(new Candidate(i, id.toString(), stack.getCount()));
            totalWeight += stack.getCount();
        }

        if (candidates.isEmpty() || totalWeight <= 0) return null;

        // Weighted random selection.
        int pick = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (Candidate c : candidates) {
            cumulative += c.weight;
            if (pick < cumulative) {
                return new Result(c.itemId, c.slot);
            }
        }

        // Shouldn't reach here, but fallback to the last candidate.
        Candidate last = candidates.get(candidates.size() - 1);
        return new Result(last.itemId, last.slot);
    }

    /**
     * Returns the most common item inside the shulker box.
     * "Most common" = highest total stack count across all slots of that item type.
     *
     * @param shulkerStack  The shulker box ItemStack.
     * @return The most common item stack (count=1), or EMPTY if none found.
     */
    public static ItemStack mostCommonItem(ItemStack shulkerStack) {
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return ItemStack.EMPTY;

        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);

        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        String bestId = null;
        int bestCount = 0;

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int total = counts.merge(id, stack.getCount(), Integer::sum);
            if (total > bestCount) {
                bestCount = total;
                bestId = id;
            }
        }

        return bestId != null ? stackForId(bestId) : ItemStack.EMPTY;
    }

    /**
     * Returns the top N most common items inside the shulker box,
     * ordered by total stack count (most common first).
     *
     * @param shulkerStack  The shulker box ItemStack.
     * @param maxItems      Maximum number of items to return.
     * @return List of ItemStacks (count=1 each), up to maxItems. May be shorter if fewer types exist.
     */
    public static List<ItemStack> topNItems(ItemStack shulkerStack, int maxItems) {
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return List.of();

        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);

        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }

        // Sort by count descending and take top N.
        return counts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxItems)
                .map(e -> stackForId(e.getKey()))
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Creates a fresh ItemStack from a registry item ID string.
     *
     * @param itemId  e.g. "minecraft:stone"
     * @return The ItemStack, or EMPTY if the ID is unknown.
     */
    public static ItemStack stackForId(String itemId) {
        Identifier id = Identifier.parse(itemId);
        if (id == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
