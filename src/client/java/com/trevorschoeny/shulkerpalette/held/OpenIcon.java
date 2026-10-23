package com.trevorschoeny.shulkerpalette.held;

import com.trevorschoeny.shulkerpalette.mixin.ItemStackRenderStateAccessor;
import com.trevorschoeny.shulkerpalette.mixin.LayerRenderStateAccessor;
import com.trevorschoeny.shulkerpalette.mixin.ShulkerBoxSpecialRendererAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import org.joml.Matrix4f;

/**
 * A palette shulker as an open box: its lid gone (in the inventory, lifted out of the icon's
 * fixed 16 x 16 cell; in a hand, sent far away) and the palette's next block, small,
 * sitting inside it.
 */
public final class OpenIcon {
    private OpenIcon() {}

    /**
     * Lid lift: the lid rises 8 px per unit of openness. In the inventory 4 puts it two
     * blocks up, out of the icon's cell. In a hand nothing clips it, so it goes so far up
     * it is past the far plane.
     * The lid is sent away rather than left out; a lidless model would be cleaner
     * if the shulker model ever exposes one.
     */
    private static final float ICON_OPENNESS = 4f;
    private static final float HAND_OPENNESS = 10_000f;
    /** The inner block's size, as a fraction of a block, and how high it sits off the box's floor. */
    private static final float INNER_SCALE = 0.55f;
    private static final float INNER_FLOOR = 0.12f;
    private static final Matrix4f INSIDE = new Matrix4f()
            .translate(0.5f - INNER_SCALE / 2, INNER_FLOOR, 0.5f - INNER_SCALE / 2)
            .scale(INNER_SCALE);

    /** An opened copy of each shulker renderer, made once per box color and per place. */
    private static final Map<ShulkerBoxSpecialRenderer, ShulkerBoxSpecialRenderer> ICON_OPENED = new IdentityHashMap<>();
    private static final Map<ShulkerBoxSpecialRenderer, ShulkerBoxSpecialRenderer> HAND_OPENED = new IdentityHashMap<>();
    /** Marks the icon's identity, so vanilla's icon cache keeps an open box apart from a closed one. */
    private static final Object OPEN = new Object();

    /** Opens every shulker layer drawn so far; returns how many layers that was. */
    public static int openLid(ItemStackRenderState output, boolean hand) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
        int count = state.shulkerpalette$activeLayerCount();
        for (int i = 0; i < count; i++) {
            ItemStackRenderState.LayerRenderState layer = state.shulkerpalette$layers()[i];
            Object special = ((LayerRenderStateAccessor) layer).shulkerpalette$specialRenderer();
            if (special instanceof ShulkerBoxSpecialRenderer closed) {
                layer.setupSpecialModel(hand
                        ? HAND_OPENED.computeIfAbsent(closed, c -> opened(c, HAND_OPENNESS))
                        : ICON_OPENED.computeIfAbsent(closed, c -> opened(c, ICON_OPENNESS)), null);
            }
        }
        output.appendModelIdentityElement(OPEN);
        return count;
    }

    /** Shrinks every layer from {@code from} on (the inner block's) and sets it inside the box. */
    public static void shrinkInside(ItemStackRenderState output, int from) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
        for (int i = from; i < state.shulkerpalette$activeLayerCount(); i++) {
            state.shulkerpalette$layers()[i].setLocalTransform(INSIDE);
        }
    }

    private static ShulkerBoxSpecialRenderer opened(ShulkerBoxSpecialRenderer closed, float openness) {
        ShulkerBoxSpecialRendererAccessor parts = (ShulkerBoxSpecialRendererAccessor) closed;
        return new ShulkerBoxSpecialRenderer(parts.shulkerpalette$renderer(), openness, parts.shulkerpalette$sprite());
    }
}
