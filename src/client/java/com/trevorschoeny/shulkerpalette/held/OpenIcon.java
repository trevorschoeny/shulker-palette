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
 * A palette shulker's inventory icon: the box with its lid lifted clean out of the icon
 * (vanilla renders inventory icons into fixed 16 x 16 cells, so anything above is cut off)
 * and the palette's next block, small, sitting inside it.
 */
public final class OpenIcon {
    private OpenIcon() {}

    /** Lid lift: the lid rises 8 px per unit of openness, so 4 puts it two blocks up, out of the cell. */
    private static final float OPENNESS = 4f;
    /** The inner block's size, as a fraction of a block, and how high it sits off the box's floor. */
    private static final float INNER_SCALE = 0.55f;
    private static final float INNER_FLOOR = 0.12f;
    private static final Matrix4f INSIDE = new Matrix4f()
            .translate(0.5f - INNER_SCALE / 2, INNER_FLOOR, 0.5f - INNER_SCALE / 2)
            .scale(INNER_SCALE);

    /** An opened copy of each shulker renderer, made once (one per box colour). */
    private static final Map<ShulkerBoxSpecialRenderer, ShulkerBoxSpecialRenderer> OPENED = new IdentityHashMap<>();
    /** Marks the icon's identity, so vanilla's icon cache keeps an open box apart from a closed one. */
    private static final Object OPEN = new Object();

    /** Opens every shulker layer drawn so far; returns how many layers that was. */
    public static int openLid(ItemStackRenderState output) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
        int count = state.shulkerpalette$activeLayerCount();
        for (int i = 0; i < count; i++) {
            ItemStackRenderState.LayerRenderState layer = state.shulkerpalette$layers()[i];
            Object special = ((LayerRenderStateAccessor) layer).shulkerpalette$specialRenderer();
            if (special instanceof ShulkerBoxSpecialRenderer closed) {
                layer.setupSpecialModel(OPENED.computeIfAbsent(closed, OpenIcon::opened), null);
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

    private static ShulkerBoxSpecialRenderer opened(ShulkerBoxSpecialRenderer closed) {
        ShulkerBoxSpecialRendererAccessor parts = (ShulkerBoxSpecialRendererAccessor) closed;
        return new ShulkerBoxSpecialRenderer(parts.shulkerpalette$renderer(), OPENNESS, parts.shulkerpalette$sprite());
    }
}
