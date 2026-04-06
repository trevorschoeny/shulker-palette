package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.inventoryplus.ContainerPeekClient;
import com.trevorschoeny.inventoryplus.network.PeekS2CPayload;
import com.trevorschoeny.menukit.MKButton;
import com.trevorschoeny.menukit.MKButtonDef;
import com.trevorschoeny.menukit.MKGroupChild;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory Plus compatibility — provides the peek palette toggle button.
 *
 * Isolated in its own class so that importing ContainerPeekClient and
 * PeekS2CPayload only happens when this class is actually loaded.
 * ShulkerPalette guards access with FabricLoader.isModLoaded("inventory-plus").
 */
final class ShulkerPalettePeekCompat {

    private ShulkerPalettePeekCompat() {}

    /**
     * Reads the item at the currently peeked menu slot. Returns EMPTY
     * if not peeking or the slot is invalid.
     */
    private static ItemStack getPeekedItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ContainerPeekClient.isPeeking()) return ItemStack.EMPTY;
        int idx = ContainerPeekClient.getPeekedSlot();
        var menu = mc.player.containerMenu;
        if (menu == null || idx < 0 || idx >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.slots.get(idx).getItem();
    }

    /**
     * Creates the palette toggle button for the peek context.
     * Reads palette state from the peeked item's CUSTOM_DATA.
     * Sends toggle packet with the peeked menu slot index.
     */
    static MKGroupChild peekPaletteButton() {
        return new MKGroupChild.Button(new MKButtonDef(
                0, 0, 9, 9,
                ShulkerPalette.ICON_OFF,
                ShulkerPalette.ICON_ON,
                9,
                Component.empty(),
                true,
                false,
                null,
                btn -> {
                    int pos = ContainerPeekClient.getPeekedSlot();
                    if (pos >= 0) {
                        ClientPlayNetworking.send(new ShulkerPaletteTogglePayload(pos));
                    }
                },
                null,
                null,
                null, null, null,
                MKButton.ButtonStyle.NONE,
                false,
                null,
                () -> {
                    if (ContainerPeekClient.getSourceType() != PeekS2CPayload.SOURCE_ITEM_CONTAINER) return false;
                    return ShulkerPalette.isShulkerPalette(getPeekedItem());
                },
                () -> {
                    ItemStack stack = getPeekedItem();
                    return Component.literal(ShulkerPalette.isShulkerPalette(stack) ? "Palette: On" : "Palette: Off");
                },
                null
        ), "palette_toggle:peek");
    }
}
