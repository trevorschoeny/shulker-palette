package com.trevorschoeny.shulkerpalette.palette;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * A broken palette box still drops a palette. Vanilla's shulker loot table copies a fixed
 * list of components from the block entity to the dropped item (name, contents, lock,
 * loot), so any other component, the palette included, stays behind. This adds the palette
 * to the copy on every shulker box's table, the same way vanilla copies the contents.
 */
public final class Drops {
    private Drops() {}

    public static void register() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            boolean shulker = BuiltInRegistries.BLOCK.stream().anyMatch(block ->
                    block instanceof ShulkerBoxBlock && block.getLootTable().filter(key::equals).isPresent());
            if (!shulker) return;
            table.modifyPools(pool -> pool.apply(
                    CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(Palette.COMPONENT)));
        });
    }
}
