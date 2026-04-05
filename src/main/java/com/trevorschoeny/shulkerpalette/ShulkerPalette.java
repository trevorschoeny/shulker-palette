package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.inventoryplus.ContainerPeekClient;
import com.trevorschoeny.inventoryplus.network.PeekS2CPayload;
import com.trevorschoeny.menukit.MKButton;
import com.trevorschoeny.menukit.MKButtonDef;
import com.trevorschoeny.menukit.MKContainerType;
import com.trevorschoeny.menukit.MKGroupChild;
import com.trevorschoeny.menukit.MKInventory;
import com.trevorschoeny.menukit.MenuKit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/**
 * Server-side entry point for the Shulker Palette feature.
 *
 * Registers the C2S toggle packet handler so players can mark a shulker
 * box as a "palette" — whether it's a placed block (via ShulkerBoxMenu)
 * or an item in their inventory (via peek container).
 */
public class ShulkerPalette {

    /** NBT key stored in the shulker box block entity to mark it as a palette. */
    public static final String PALETTE_TAG = "trevormod_palette";

    // ── Sprite identifiers for the toggle button ────────────────────────────
    private static final Identifier ICON_OFF =
            Identifier.fromNamespaceAndPath(ShulkerPaletteMod.MOD_ID, "palette_off");
    private static final Identifier ICON_ON =
            Identifier.fromNamespaceAndPath(ShulkerPaletteMod.MOD_ID, "palette_on");

    /**
     * Called from ShulkerPaletteMod.onInitialize() — server-safe init.
     * Registers the C2S packet type and its server handler.
     */
    public static void init() {
        // Register the C2S payload type.
        PayloadTypeRegistry.playC2S().register(
                ShulkerPaletteTogglePayload.TYPE,
                ShulkerPaletteTogglePayload.CODEC
        );

        // Handle toggle requests from the client.
        ServerPlayNetworking.registerGlobalReceiver(
                ShulkerPaletteTogglePayload.TYPE,
                (payload, context) -> {
                    context.server().execute(() -> {
                        var player = context.player();

                        if (payload.unifiedPos() >= 0) {
                            // ── Item toggle (peek path) ─────────────────────
                            // Toggle the palette flag directly on the item's
                            // CUSTOM_DATA component at the given inventory position.
                            ItemStack stack = MKInventory.getPlayerItem(player, payload.unifiedPos());
                            if (!isShulkerBox(stack)) return;

                            boolean wasPalette = isShulkerPalette(stack);
                            toggleItemPalette(stack, !wasPalette);
                            player.inventoryMenu.broadcastChanges();
                            ShulkerPaletteMod.LOGGER.debug("[ShulkerPalette] Toggled item palette at pos {}: {}",
                                    payload.unifiedPos(), !wasPalette);
                        } else {
                            // ── Block entity toggle (ShulkerBoxMenu path) ───
                            if (!(player.containerMenu instanceof ShulkerBoxMenu menu)) return;
                            if (!(menu instanceof ShulkerPaletteMenuAccessor menuAccessor)) return;
                            var paletteSlot = menuAccessor.trevorMod$getPaletteSlot();
                            if (paletteSlot != null) {
                                paletteSlot.set(paletteSlot.get() == 0 ? 1 : 0);
                                ShulkerPaletteMod.LOGGER.debug("[ShulkerPalette] Toggled block palette: {}",
                                        paletteSlot.get() != 0);
                            }
                        }
                    });
                }
        );
    }

    /**
     * Initialises the client side. Called from ShulkerPaletteClient.onInitializeClient().
     *
     * Registers the palette toggle button attachment — shows on:
     *   - ShulkerBoxScreen (placed shulker, region "mk:shulker")
     *   - Peek container (item shulker, region "peek_shulker")
     */
    public static void initClient(net.minecraft.client.KeyMapping.Category category) {
        MenuKit.buttonAttachment("palette_toggle")
                .forContainerType(MKContainerType.SIMPLE)
                .above()
                .gap(2)
                .overlayOffset(1, -2)
                .buttons(regionName -> {
                    Minecraft mc = Minecraft.getInstance();

                    // ── Peek shulker: always show ────────────────────────
                    if ("peek_shulker".equals(regionName)) {
                        return List.of(peekPaletteButton());
                    }

                    // ── ShulkerBoxScreen: show on the shulker region only ─
                    if (mc.screen instanceof ShulkerBoxScreen
                            && "mk:shulker".equals(regionName)) {
                        return List.of(screenPaletteButton());
                    }

                    // All other regions: no button
                    return List.of();
                })
                .register();
    }

    // ── Button Factories ────────────────────────────────────────────────────

    /**
     * Creates the palette toggle button for the peek context.
     * Reads palette state from the peeked item's CUSTOM_DATA.
     * Sends toggle packet with the peeked inventory position.
     */
    private static MKGroupChild peekPaletteButton() {
        return new MKGroupChild.Button(new MKButtonDef(
                0, 0, 9, 9,
                ICON_OFF,                               // icon (off state)
                ICON_ON,                                 // toggledIcon (on state)
                9,                                       // iconSize
                Component.empty(),                       // label (none — icon only)
                true,                                    // toggleMode
                false,                                   // initialPressed
                null,                                    // groupName
                btn -> {                                 // onClick: send toggle packet
                    int pos = ContainerPeekClient.getPeekedSlot();
                    if (pos >= 0) {
                        ClientPlayNetworking.send(new ShulkerPaletteTogglePayload(pos));
                    }
                },
                null,                                    // onToggle
                null,                                    // tooltip (set via pressedWhen-aware supplier below)
                null, null, null,                        // opensScreenName, opensScreenFactory, togglesPanelName
                MKButton.ButtonStyle.NONE,               // buttonStyle
                false,                                   // disabled
                null,                                    // disabledWhen
                () -> {                                  // pressedWhen: read item palette state
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !ContainerPeekClient.isPeeking()) return false;
                    if (ContainerPeekClient.getSourceType() != PeekS2CPayload.SOURCE_ITEM_CONTAINER) return false;
                    ItemStack stack = MKInventory.getPlayerItem(mc.player, ContainerPeekClient.getPeekedSlot());
                    return isShulkerPalette(stack);
                },
                () -> {                                  // tooltipSupplier: dynamic "Palette: On/Off"
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !ContainerPeekClient.isPeeking()) return Component.literal("Palette: Off");
                    ItemStack stack = MKInventory.getPlayerItem(mc.player, ContainerPeekClient.getPeekedSlot());
                    return Component.literal(isShulkerPalette(stack) ? "Palette: On" : "Palette: Off");
                }
        ), "palette_toggle:peek");
    }

    /**
     * Creates the palette toggle button for the ShulkerBoxScreen context.
     * Reads palette state from the menu's synced DataSlot.
     * Sends toggle packet with -1 (block entity path).
     */
    private static MKGroupChild screenPaletteButton() {
        return new MKGroupChild.Button(new MKButtonDef(
                0, 0, 9, 9,
                ICON_OFF,                               // icon (off state)
                ICON_ON,                                 // toggledIcon (on state)
                9,                                       // iconSize
                Component.empty(),                       // label (none — icon only)
                true,                                    // toggleMode
                false,                                   // initialPressed
                null,                                    // groupName
                btn -> {                                 // onClick: send toggle packet (-1 = block entity)
                    ClientPlayNetworking.send(new ShulkerPaletteTogglePayload());
                },
                null,                                    // onToggle
                null,                                    // tooltip
                null, null, null,                        // opensScreenName, opensScreenFactory, togglesPanelName
                MKButton.ButtonStyle.NONE,               // buttonStyle
                false,                                   // disabled
                null,                                    // disabledWhen
                () -> {                                  // pressedWhen: read DataSlot
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return false;
                    if (!(mc.player.containerMenu instanceof ShulkerPaletteMenuAccessor accessor)) return false;
                    var slot = accessor.trevorMod$getPaletteSlot();
                    return slot != null && slot.get() != 0;
                },
                () -> {                                  // tooltipSupplier: dynamic "Palette: On/Off"
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return Component.literal("Palette: Off");
                    if (!(mc.player.containerMenu instanceof ShulkerPaletteMenuAccessor accessor))
                        return Component.literal("Palette: Off");
                    var slot = accessor.trevorMod$getPaletteSlot();
                    boolean on = slot != null && slot.get() != 0;
                    return Component.literal(on ? "Palette: On" : "Palette: Off");
                }
        ), "palette_toggle:screen");
    }

    // ── Item Palette Toggle ─────────────────────────────────────────────────

    /**
     * Sets or clears the palette flag on a shulker item's CUSTOM_DATA.
     * Used by the server handler for the peek toggle path.
     */
    private static void toggleItemPalette(ItemStack stack, boolean palette) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = existing.copyTag();
        if (palette) {
            tag.putBoolean(PALETTE_TAG, true);
        } else {
            tag.remove(PALETTE_TAG);
        }
        // If the tag is now empty, remove the component entirely to stay clean
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    /**
     * Returns true if the given ItemStack is a shulker box marked as a palette.
     * Checks the CUSTOM_DATA component for the trevormod_palette flag.
     */
    public static boolean isShulkerPalette(ItemStack stack) {
        if (!isShulkerBox(stack)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean(PALETTE_TAG).orElse(false);
    }

    /** Returns true if the ItemStack is any color of shulker box. */
    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    /** Returns the global enabled setting from config. */
    public static boolean isEnabled() {
        return ShulkerPaletteConfig.get().enabled;
    }

    /** Returns the configured shift-click behavior. */
    public static ShulkerPaletteConfig.ShiftBehavior getShiftBehavior() {
        return ShulkerPaletteConfig.get().shiftBehavior;
    }
}
