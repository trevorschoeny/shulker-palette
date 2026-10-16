package com.trevorschoeny.shulkerpalette.palette;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import com.trevlar.slotkit.api.window.SlotOperations;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Sneak + right-click into the air opens the shulker in your hand, without placing it: the
 * ordinary shulker screen, control panel included, over the item itself.
 *
 * <p>The screen's container is a copy of the item's contents that writes every change
 * straight back to the item. While it is open, the slot holding the item is inert: a
 * SlotKit veto refuses every operation on it (take, put, swap, drop, collect), on the
 * client before a click is sent and on the server when one arrives, so nothing moves and
 * nothing jumps back. The menu also closes if the item leaves the main hand anyway.
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

    /**
     * Registers the veto that makes the open shulker's slot inert: the selected hotbar slot
     * of the player whose shulker menu was opened from the hand. Both sides read that from
     * the menu's synced palette data, so the client refuses the same clicks the server would.
     */
    public static void register() {
        SlotOperations.veto((ref, operation) ->
                ref.menu() instanceof ShulkerBoxMenu menu
                        && menu instanceof PaletteMenu palette
                        && PanelActions.held(palette.shulkerpalette$packedPalette())
                        && ref.container() instanceof Inventory inventory
                        // the inventory this menu shows (its last slots are the viewer's own), not another player's
                        && menu.slots.get(menu.slots.size() - 1).container == inventory
                        && ref.containerSlot() == inventory.getSelectedSlot());
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
