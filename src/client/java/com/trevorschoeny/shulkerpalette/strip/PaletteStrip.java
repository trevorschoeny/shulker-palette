package com.trevorschoeny.shulkerpalette.strip;

import com.trevlar.menukit.api.hud.HudCycleStrip;
import com.trevorschoeny.shulkerpalette.palette.Draw;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * The strip beside the hotbar while a pick or gradient palette is held: MenuKit's cycle
 * strip, turning through the choices sneak + scroll moves between.
 *
 * <ul>
 *   <li>Pick: one cell per block type, with its total.</li>
 *   <li>Gradient: one cell per row, showing the row's most plentiful block and the row's total.</li>
 * </ul>
 *
 * <p>The server owns the selection. Between a scroll and the server's answer the strip shows
 * the selection it expects, so it slides once instead of sliding and then jumping.
 */
public final class PaletteStrip implements HudCycleStrip.Source {
    public static final PaletteStrip INSTANCE = new PaletteStrip();

    /** How long to trust our own guess before going back to what the server says. */
    private static final long EXPECT_MILLIS = 1000;
    private static int expected = -1;
    private static long expectedAt;

    private PaletteStrip() {}

    public static void register() {
        HudCycleStrip.register(INSTANCE);
    }

    /** Called right after the client asks the server to scroll: guess the result and slide. */
    public static void scrolled(int steps) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        Palette palette = Palette.activeOn(held);
        if (palette == null) return;
        expected = Scroll.moved(palette, contents(held), steps);
        expectedAt = System.currentTimeMillis();
        HudCycleStrip.slide(INSTANCE, mc.player.getInventory().getSelectedSlot(),
                steps > 0 ? HudCycleStrip.Direction.FORWARD : HudCycleStrip.Direction.BACKWARD);
    }

    @Override
    public HudCycleStrip.@Nullable View viewFor(int hotbarSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || hotbarSlot != mc.player.getInventory().getSelectedSlot()) return null;
        ItemStack held = mc.player.getInventory().getItem(hotbarSlot);
        Palette palette = Palette.activeOn(held);
        if (palette == null || !Scroll.scrolls(palette)) return null;

        List<ItemStack> slots = Draw.slots(contents(held));
        List<ItemStack> ring = palette.mode() == Mode.PICK ? pickRing(slots) : gradientRing(slots, palette.rows());
        return new HudCycleStrip.View(ring, selection(palette));
    }

    /** The server's selection, unless we scrolled a moment ago and it hasn't caught up yet. */
    private static int selection(Palette palette) {
        boolean waiting = expected >= 0 && System.currentTimeMillis() - expectedAt < EXPECT_MILLIS;
        if (waiting && palette.selection() != expected) return expected;
        expected = -1;
        return palette.selection();
    }

    private static List<ItemStack> pickRing(List<ItemStack> slots) {
        List<ItemStack> ring = new ArrayList<>();
        for (Draw.Entry entry : Draw.types(slots)) ring.add(entry.type().copyWithCount(entry.total()));
        return ring;
    }

    private static List<ItemStack> gradientRing(List<ItemStack> slots, int rows) {
        List<ItemStack> ring = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            int from = Math.min(row * Palette.ROW_WIDTH, slots.size());
            int to = Math.min(from + Palette.ROW_WIDTH, slots.size());
            List<Draw.Entry> types = Draw.types(slots.subList(from, to));
            int total = types.stream().mapToInt(Draw.Entry::total).sum();
            ring.add(types.stream().max(Comparator.comparingInt(Draw.Entry::total))
                    .map(top -> top.type().copyWithCount(total)).orElse(ItemStack.EMPTY));
        }
        return ring;
    }

    private static ItemContainerContents contents(ItemStack shulker) {
        return shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    }
}
