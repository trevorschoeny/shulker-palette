package com.trevorschoeny.shulkerpalette.menu;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.ElementConstants;
import com.trevlar.menukit.api.element.RenderContext;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One of the palette menu's buttons: MenuKit's button with the action's icon at its left and a
 * label read every frame ("Palette: On", "Mode: Gradient"), so one button shows the state it
 * changes. Built on Button's drawing extension point; clicks, focus, the sound and the raised
 * look stay MenuKit's.
 */
final class MenuRow extends Button {
    static final int WIDTH = 150;
    static final int HEIGHT = 20;

    private static final int ICON = 10;
    private static final int TEXT_GRAY = 0xFFA0A0A0;

    private final Supplier<Identifier> icon;
    private final Supplier<Component> label;

    private MenuRow(Builder b, Supplier<Identifier> icon, Supplier<Component> label) {
        super(b);
        this.icon = icon;
        this.label = label;
    }

    /** A row whose look is set on the builder (tooltip, disabledWhen, visibleWhen) before it's built. */
    static MenuRow of(Supplier<Identifier> icon, Supplier<Component> label, Runnable onClick, java.util.function.UnaryOperator<Builder> extra) {
        return new MenuRow(extra.apply(Button.builder().size(WIDTH, HEIGHT).onClick(onClick)), icon, label);
    }

    @Override
    protected void renderContent(RenderContext ctx, int sx, int sy, Look look) {
        int iconY = sy + (getHeight() - ICON) / 2;
        ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, icon.get(), sx + 5, iconY, ICON, ICON, look.disabled() ? 0.4f : 1f);
        int textY = sy + (getHeight() - 8) / 2;
        ctx.graphics().text(Minecraft.getInstance().font, label.get(), sx + 5 + ICON + 6, textY,
                look.disabled() ? TEXT_GRAY : ElementConstants.TEXT_LIGHT, true);
    }
}
