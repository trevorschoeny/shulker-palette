package com.trevorschoeny.shulkerpalette.compat;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.ResolvedSlotGroup;
import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.group.SlotGroups;
import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.openanywhere.api.ShownItem;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.menu.PaletteControls;
import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Shulker Palette on a box opened through Open Anywhere: the palette's icon column beside the
 * box's section, its library dialogs on those screens, and the cheat sheet's ghosts in the box's
 * empty slots. Loaded only when Open Anywhere is installed.
 *
 * <p>The box is the one whose section shows now ({@link ShownItem#get()}); the controls name it
 * to the server by its menu slot, and the server checks it is still open there.
 */
public final class OpenAnywhereClient {
    private OpenAnywhereClient() {}

    /** The shulker the panel is for: the shown opened item, while it's a shulker box. */
    private static final PaletteControls.For SHOWN = new PaletteControls.For(
            () -> {
                OpenItems.OpenItem item = ShownItem.get();
                return item != null && Palette.isShulker(item.stack()) ? item.stack() : ItemStack.EMPTY;
            },
            () -> {
                OpenItems.OpenItem item = ShownItem.get();
                return item == null ? -1 : item.menuSlot();
            });

    /** The screens Open Anywhere opens items on, where the library dialogs can come up. */
    @SuppressWarnings("unchecked")
    private static final Class<? extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>>[] SCREENS =
            new Class[] { InventoryScreen.class, ContainerScreen.class, CreativeModeInventoryScreen.class };

    private static @Nullable Screen lastScreen;

    public static void register() {
        // The icon column, left of the box's section and moving with it; hidden with no shulker shown.
        Panel panel = Panel.builder(ShulkerPalette.MOD_ID + ":open_anywhere")
                .in(ModeScope.BOTH, Placement.around(ShownItem.SECTION, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
                // A vertical Flow: what doesn't apply is hidden and the rest close up.
                .elements(List.of(Flow.builder().vertical().gap(0, 2).addAll(PaletteControls.icons(SHOWN)).build()))
                .build()
                .visibleWhen(() -> !SHOWN.stack().get().isEmpty());
        new AnchorPanelAdapter(panel);

        for (Panel dialog : LibraryClient.panels("open_anywhere_", SHOWN)) new ScreenPanelAdapter(dialog).on(SCREENS);
        // A newly opened screen starts with no dialog up; a resize (same screen) keeps it.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen != lastScreen && isHost(screen)) LibraryClient.reset();
            lastScreen = screen;
        });
    }

    private static boolean isHost(Screen screen) {
        for (Class<?> host : SCREENS) if (host.isInstance(screen)) return true;
        return false;
    }

    // The shown box's slots on the menu, by slot, rebuilt when the menu, the box or SlotKit's groups change.
    private static @Nullable AbstractContainerMenu cachedMenu;
    private static @Nullable SlotGroupId cachedGroup;
    private static long cachedGeneration = -1;
    private static Map<Slot, Integer> cachedIndex = Map.of();

    /**
     * The cheat sheet's ghost for this slot, if it is an empty slot of the shown box and the
     * box's cheat sheet names something for it; else empty. Read from the box itself, which
     * carries its cheat sheet while open.
     */
    public static ItemStack ghost(AbstractContainerMenu menu, Slot slot) {
        OpenItems.OpenItem item = ShownItem.get();
        if (item == null) return ItemStack.EMPTY;
        List<SavedPalette.Entry> sheet = item.stack().get(CheatSheet.COMPONENT);
        if (sheet == null) return ItemStack.EMPTY;
        Integer index = index(menu, item.group()).get(slot);
        if (index == null) return ItemStack.EMPTY;
        for (SavedPalette.Entry entry : sheet) if (entry.slot() == index) return entry.item().create();
        return ItemStack.EMPTY;
    }

    private static Map<Slot, Integer> index(AbstractContainerMenu menu, SlotGroupId group) {
        long generation = SlotGroups.generation();
        if (menu != cachedMenu || !group.equals(cachedGroup) || generation != cachedGeneration) {
            Map<Slot, Integer> index = new IdentityHashMap<>();
            for (ResolvedSlotGroup resolved : SlotGroupCategories.groups(menu)) {
                if (!resolved.id().equals(group)) continue;
                for (int i = 0; i < resolved.slots().size(); i++) index.put(resolved.slots().get(i), i);
            }
            cachedMenu = menu;
            cachedGroup = group;
            cachedGeneration = generation;
            cachedIndex = index;
        }
        return cachedIndex;
    }
}
