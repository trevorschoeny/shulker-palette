package com.trevorschoeny.shulkerpalette.library;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.ItemDisplay;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.layout.CrossAlign;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** What the library page and the Load list both draw for a palette: its block strip and its name. */
final class LibraryRows {
    private LibraryRows() {}

    private static final int RED = 0xFFFF5555;

    /** Up to {@code max} of the palette's block types, in box order, one of each. */
    static List<ItemStack> strip(SavedPalette palette, int max) {
        List<ItemStack> types = new ArrayList<>();
        for (SavedPalette.Entry entry : palette.slots()) {
            ItemStack stack = entry.item().create();
            if (types.stream().noneMatch(t -> ItemStack.isSameItemSameComponents(t, stack))) types.add(stack.copyWithCount(1));
            if (types.size() == max) break;
        }
        return types;
    }

    /**
     * A row's start: the block strip then the name, or for a file that doesn't parse, the name
     * in red with the problem as its tooltip text beside it.
     */
    static Flow.Builder start(Library.Entry entry, int stripLength) {
        Flow.Builder row = Flow.builder().gap(2).crossAlign(CrossAlign.CENTER);
        if (entry.palette().isError()) {
            String problem = entry.palette().error().map(e -> e.message()).orElse("");
            return row.add(TextLabel.builder().text(Component.literal(entry.name())).color(RED).build())
                    .add(TextLabel.builder().text(Component.literal(problem).withStyle(ChatFormatting.GRAY)).build());
        }
        for (ItemStack type : strip(entry.palette().getOrThrow(), stripLength)) {
            row.add(ItemDisplay.builder().item(type).hideCount().build());
        }
        return row.add(TextLabel.builder().text(Component.literal(entry.name())).build());
    }
}
