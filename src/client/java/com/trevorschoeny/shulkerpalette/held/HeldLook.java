package com.trevorschoeny.shulkerpalette.held;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3fc;

/**
 * How a palette shulker looks in a hand: not the shulker, but its next block, wrapped in a
 * slowly cycling rainbow outline. First person and third person; everywhere else (the
 * ground, an item frame, the inventory) the shulker draws as usual.
 *
 * <p>The outline is an inverted hull: the block's own quads, grown a little about the
 * model's centre, drawn in flat colour with their winding reversed so back-face culling
 * keeps only the faces behind the block. Only a rim past the block's edge stays visible.
 * It works in the first-person pass, which vanilla's glowing outline (a level-only post
 * effect) does not reach.
 */
public final class HeldLook {
    private HeldLook() {}

    /** Marks an item render state that should be outlined (set when the next block is swapped in). */
    public static final RenderStateDataKey<Boolean> RAINBOW = RenderStateDataKey.create(() -> "shulkerpalette:rainbow");

    /** How much bigger the hull is than the block: the outline's thickness. */
    private static final float GROW = 0.07f;
    /** One trip round the colour wheel, in milliseconds. */
    private static final float CYCLE_MILLIS = 4000f;
    /** How much of the wheel spans the block, so the colours run round it as bands. */
    private static final float SPREAD = 0.5f;

    // A flat-colour pipeline like vanilla's debug boxes, but culling back faces and writing
    // depth, so the reversed hull hides behind the block and the block wins where they overlap.
    private static final RenderPipeline HULL_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("shulkerpalette", "pipeline/rainbow_hull"))
            .withCull(true)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .build();
    public static final RenderType HULL = RenderType.create("shulkerpalette_rainbow_hull",
            RenderSetup.builder(HULL_PIPELINE).createRenderSetup());

    /** Is this a hand, where the palette's next block is shown instead of the shulker? */
    public static boolean inHand(ItemDisplayContext context) {
        return context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    /** Writes the hull of these quads, in the current pose (the layer's transform already applied). */
    public static void hull(PoseStack.Pose pose, VertexConsumer out, Iterable<BakedQuad> quads) {
        float time = (Util.getMillis() % (long) CYCLE_MILLIS) / CYCLE_MILLIS;
        float scale = 1 + GROW;
        for (BakedQuad quad : quads) {
            // Reversed vertex order flips the winding, so culling keeps the far side.
            for (int v = 3; v >= 0; v--) {
                Vector3fc p = quad.position(v);
                float x = 0.5f + (p.x() - 0.5f) * scale;
                float y = 0.5f + (p.y() - 0.5f) * scale;
                float z = 0.5f + (p.z() - 0.5f) * scale;
                float hue = time + (p.x() + p.y() + p.z()) * SPREAD / 3f;
                out.addVertex(pose, x, y, z).setColor(Mth.hsvToArgb(hue - Mth.floor(hue), 0.8f, 1f, 255));
            }
        }
    }
}
