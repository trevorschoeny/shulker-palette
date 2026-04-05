package com.trevorschoeny.shulkerpalette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3fc;

import java.util.List;
import java.util.function.Consumer;

/**
 * Composite renderer that renders the open shulker base AND up to 3
 * representative items inside it, in a single submit() call.
 *
 * Because all models render in the same 3D pass (same Z-buffer), items
 * naturally clip against the shulker walls — visible above the rim,
 * hidden behind the walls.
 */
public class ShulkerPaletteCompositeRenderer implements NoDataSpecialModelRenderer {

    /** Position offsets for inventory/hotbar GUI rendering (diagonal layout). */
    private static final float[][] GUI_POSITIONS = {
            {0.15f, 0.75f, 0.15f},  // Item 1 (most common) — front-left
            {0.50f, 0.75f, 0.50f},  // Item 2 — center
            {0.85f, 0.75f, 0.85f},  // Item 3 — back-right
    };

    /** Position offsets for in-game rendering (held item, item frames, etc.). */
    private static final float[][] INGAME_POSITIONS = {
            {0.2f, 0.75f, 0.5f},   // Item 1 (most common) — left
            {0.5f, 0.75f, 0.5f},   // Item 2 — center
            {0.8f, 0.75f, 0.5f},   // Item 3 — right
    };

    private final NoDataSpecialModelRenderer shulkerRenderer;
    private final List<ItemStackRenderState> innerItemStates;
    /** True if the corresponding inner item is a BlockItem (no extra rotation needed). */
    private final List<Boolean> isBlock;

    /**
     * @param shulkerRenderer  The open-lid ShulkerBoxSpecialRenderer.
     * @param innerItemStates  Pre-resolved render states for the representative items (up to 3).
     * @param isBlock          Whether each item is a BlockItem (controls rotation).
     */
    public ShulkerPaletteCompositeRenderer(NoDataSpecialModelRenderer shulkerRenderer,
                                            List<ItemStackRenderState> innerItemStates,
                                            List<Boolean> isBlock) {
        this.shulkerRenderer = shulkerRenderer;
        this.innerItemStates = innerItemStates;
        this.isBlock = isBlock;
    }

    @Override
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack,
                       SubmitNodeCollector collector, int light, int overlay,
                       boolean foil, int tint) {
        // 1. Render the open shulker base.
        shulkerRenderer.submit(displayContext, poseStack, collector, light, overlay, foil, tint);

        // 2. Render each representative item inside.
        float[][] positions = (displayContext == ItemDisplayContext.GUI) ? GUI_POSITIONS : INGAME_POSITIONS;
        for (int i = 0; i < innerItemStates.size() && i < positions.length; i++) {
            float[] pos = positions[i];
            poseStack.pushPose();
            float scale = (displayContext == ItemDisplayContext.GUI)
                    ? ShulkerPaletteState.INNER_ITEM_SCALE_GUI
                    : ShulkerPaletteState.INNER_ITEM_SCALE_INGAME;
            poseStack.translate(pos[0], pos[1], pos[2]);
            poseStack.scale(scale, scale, scale);

            // Non-block items (tools, eggs, etc.) need a Y rotation to face correctly.
            if (i < isBlock.size() && !isBlock.get(i)) {
                if (displayContext == ItemDisplayContext.GUI) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-45.0f));  // spin right 45°
                } else {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));  // spin right 90°
                }
            }

            innerItemStates.get(i).submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, tint);
            poseStack.popPose();
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        shulkerRenderer.getExtents(consumer);
    }
}
