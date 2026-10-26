package com.trevorschoeny.shulkerpalette.panel;

import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.compat.OpenAnywhereClient;
import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The cheat sheet of the box whose screen is open, as the server last sent it, keyed by the
 * menu it belongs to (it can arrive just before the screen opens). Drawn on empty box slots as
 * a MenuKit slot decoration: the item, faded, with its count and a matching tooltip.
 */
public final class CheatSheetClient {
    private CheatSheetClient() {}

    /** Vanilla's ghost-item look (the recipe book's): the item, then this over it. */
    private static final int GHOST = 0x808B8B8B;

    private static int containerId = -1;
    private static Map<Integer, ItemStack> bySlot = Map.of();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CheatSheet.Sync.TYPE, (payload, context) -> {
            Map<Integer, ItemStack> sheet = new HashMap<>();
            for (SavedPalette.Entry entry : payload.entries()) sheet.put(entry.slot(), entry.item().create());
            containerId = payload.containerId();
            bySlot = sheet;
        });
        SlotDecorations.under(ShulkerPalette.MOD_ID + ":cheat_sheet", new SlotDecorations.Decoration() {
            @Override
            public void draw(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y) {
                ItemStack ghost = ghostFor(screen.getMenu(), slot);
                if (ghost.isEmpty()) return;
                graphics.item(ghost, x, y);
                graphics.fill(x, y, x + 16, y + 16, GHOST);
                // The count over the fade, so it reads clearly: how many go here.
                graphics.itemDecorations(Minecraft.getInstance().font, ghost, x, y);
            }

            @Override
            public @Nullable List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int mouseX, int mouseY) {
                ItemStack ghost = ghostFor(screen.getMenu(), slot);
                return ghost.isEmpty() ? null : tooltipFor(ghost);
            }
        });
    }

    /** What belongs in this box slot of menu {@code menuId}, or empty. */
    public static ItemStack ghost(int menuId, int slot) {
        return menuId == containerId ? bySlot.getOrDefault(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static boolean has(int menuId) {
        return menuId == containerId && !bySlot.isEmpty();
    }

    /**
     * The cheat sheet's ghost for an empty box slot of {@code menu}: the server's sheet for a
     * placed box, else the box's own sheet through Open Anywhere (when it's installed and has
     * this slot's box shown). Empty for a filled slot or one with nothing to show.
     */
    public static ItemStack ghostFor(AbstractContainerMenu menu, Slot slot) {
        if (slot.hasItem()) return ItemStack.EMPTY;
        ItemStack sheet = ItemStack.EMPTY;
        if (menu instanceof ShulkerBoxMenu && !(slot.container instanceof Inventory)) sheet = ghost(menu.containerId, slot.getContainerSlot());
        if (sheet.isEmpty() && FabricLoader.getInstance().isModLoaded("openanywhere")) sheet = OpenAnywhereClient.ghost(menu, slot);
        return sheet;
    }

    /** What to put where, and how many: the tooltip a cheat sheet ghost shows on hover. */
    public static List<Component> tooltipFor(ItemStack ghost) {
        return List.of(ghost.getHoverName(),
                Component.translatable("shulkerpalette.ghost.place", ghost.getCount()).withStyle(ChatFormatting.GRAY));
    }
}
