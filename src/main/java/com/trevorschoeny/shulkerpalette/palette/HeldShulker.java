package com.trevorschoeny.shulkerpalette.palette;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Sneak + right-click into the air opens the shulker in your hand, without placing it: the
 * ordinary shulker screen, control panel included, over the item itself.
 *
 * <p>The screen's container is a copy of the item's contents that writes every change
 * straight back to the item. The menu closes the moment that item leaves the main hand,
 * and the item's own slot can't be clicked while it is open, so it can't be moved out from
 * under its own screen.
 */
public final class HeldShulker extends SimpleContainer implements PaletteHolder {
    private final ItemStack stack;

    private HeldShulker(ItemStack stack) {
        super(Palette.VANILLA_ROWS * Palette.ROW_WIDTH);
        this.stack = stack;
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
    }

    public static void open(ServerPlayer player, ItemStack stack) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new ShulkerBoxMenu(id, inventory, new HeldShulker(stack)),
                stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.translatable("container.shulkerBox"))));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHULKER_BOX_OPEN, SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    /** Is this the item the screen is open over? Its slot is locked while it is. */
    public boolean holds(ItemStack other) {
        return other == stack;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
    }

    @Override
    public boolean stillValid(Player player) {
        return !stack.isEmpty() && player.getMainHandItem() == stack;
    }

    @Override
    public Palette shulkerpalette$palette() {
        Palette palette = stack.get(Palette.COMPONENT);
        return palette != null ? palette : Palette.OFF;
    }

    @Override
    public void shulkerpalette$setPalette(Palette palette) {
        stack.set(Palette.COMPONENT, palette);
    }
}
