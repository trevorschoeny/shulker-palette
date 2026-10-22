package com.trevorschoeny.shulkerpalette.library;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.ElementConstants;
import com.trevlar.menukit.api.element.RenderContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One palette in the Load list: a flat, full-width button showing the palette's block strip
 * and name, washed lighter while hovered and lighter still while pressed, for the dark list.
 * A file that doesn't parse shows its name in red with its problem as the tooltip, and can't
 * be clicked. Built on Button's two drawing extension points, so clicks, focus, the sound
 * and narration stay MenuKit's.
 */
final class PaletteRow extends Button {
    static final int HEIGHT = 20;

    private static final int HOVER = 0x30FFFFFF;
    private static final int PRESSED = 0x50FFFFFF;
    private static final int RED = 0xFFFF5555;
    private static final int ITEM = 16;

    private final List<ItemStack> strip;
    private final Component name;
    private final boolean broken;

    private PaletteRow(Builder b, Library.Entry entry, int stripLength) {
        super(b);
        this.broken = entry.palette().isError();
        this.strip = broken ? List.of() : LibraryRows.strip(entry.palette().getOrThrow(), stripLength);
        this.name = Component.literal(entry.name());
    }

    static PaletteRow of(Library.Entry entry, int width, int stripLength, Runnable onClick) {
        Builder b = Button.builder().size(width, HEIGHT).onClick(onClick);
        if (entry.palette().isError()) {
            b.tooltip(Component.literal(entry.palette().error().map(e -> e.message()).orElse(""))).disabledWhen(() -> true);
        }
        return new PaletteRow(b, entry, stripLength);
    }

    @Override
    protected void renderBackground(RenderContext ctx, int sx, int sy, Look look) {
        if (look.disabled() || !look.hovered()) return;
        ctx.graphics().fill(sx, sy, sx + getWidth(), sy + getHeight(), look.pressed() ? PRESSED : HOVER);
    }

    @Override
    protected void renderContent(RenderContext ctx, int sx, int sy, Look look) {
        int x = sx + 2;
        int itemY = sy + (getHeight() - ITEM) / 2;
        for (ItemStack type : strip) {
            ctx.graphics().item(type, x, itemY);
            x += ITEM + 1;
        }
        int textY = sy + (getHeight() - 8) / 2;
        ctx.graphics().text(Minecraft.getInstance().font, name, x + 3, textY, broken ? RED : ElementConstants.TEXT_LIGHT, true);
    }
}
