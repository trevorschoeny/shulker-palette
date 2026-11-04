package com.trevorschoeny.shulkerpalette.held;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
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
 * <p>A block is outlined with an inverted hull: its own quads, grown a little about the
 * model's center, drawn in flat color with their winding reversed so back-face culling keeps
 * only the faces behind the block. Only a rim past the block's edge stays visible. A flat item
 * (a door, which holds as a picture, not a block) is outlined the way a sprite is: its picture
 * drawn again in flat color half a pixel off in each of eight directions, just behind itself.
 * Either way the color only fills where the texture is opaque, so a door's empty corners and
 * glass's clear panes stay clear. It works in the first-person pass, which vanilla's glowing
 * outline (a level-only post effect) does not reach.
 */
public final class HeldLook {
    private HeldLook() {}

    /** Marks an item render state that should be outlined (set when the next block is swapped in). */
    public static final RenderStateDataKey<Boolean> RAINBOW = RenderStateDataKey.create(() -> "shulkerpalette:rainbow");

    /** How much bigger the hull is than the block: the outline's thickness. */
    private static final float GROW = 0.07f;
    /** One trip round the color wheel, in milliseconds. */
    private static final float CYCLE_MILLIS = 4000f;
    /** How much of the wheel spans the block, so the colors run round it as bands. */
    private static final float SPREAD = 0.5f;

    /** One of a flat item's 16 pixels. */
    private static final float PIXEL = 1 / 16f;
    /**
     * A flat item's outline thickness: half a pixel, about the hull's rim on a block
     * ({@link #GROW}), and thin enough that a door's small windows keep their middle clear.
     */
    private static final float RIM = PIXEL / 2;
    /** How far a flat item's outline copies sit behind its faces, so the item draws over them. */
    private static final float BEHIND = 0.01f;

    // Vanilla's entity-outline shader (flat vertex color, but discarding where the texture is
    // clear), drawn into the normal target, culling back faces and writing depth, so the hull
    // hides behind the item and the item wins where they overlap.
    private static final RenderPipeline HULL_PIPELINE = RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("shulkerpalette", "pipeline/rainbow_hull"))
            .withCull(true)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .build();
    /** The outline's render type for quads from this texture atlas (blocks or items). */
    public static final Function<Identifier, RenderType> HULL = Util.memoize(atlas -> RenderType.create("shulkerpalette_rainbow_hull",
            RenderSetup.builder(HULL_PIPELINE).withTexture("Sampler0", atlas).createRenderSetup()));

    /** Is this a hand, where the palette's next block is shown instead of the shulker? */
    public static boolean inHand(ItemDisplayContext context) {
        return context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    /**
     * Writes the outline of these quads, in the current pose (the layer's transform already
     * applied): a sprite outline for a flat item, a hull for anything with depth.
     */
    public static void outline(PoseStack.Pose pose, VertexConsumer out, List<BakedQuad> quads) {
        float time = (Util.getMillis() % (long) CYCLE_MILLIS) / CYCLE_MILLIS;
        if (flat(quads)) {
            for (BakedQuad quad : quads) {
                // Only the picture's two faces; the thin edge strips between them add nothing.
                if (quad.direction() != Direction.SOUTH && quad.direction() != Direction.NORTH) continue;
                float back = quad.direction() == Direction.SOUTH ? -BEHIND : BEHIND; // toward the middle
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    for (int v = 0; v < 4; v++) {
                        Vector3fc p = quad.position(v);
                        vertex(out, pose, quad, v, p.x() + dx * RIM, p.y() + dy * RIM, p.z() + back, time);
                    }
                }
            }
            return;
        }
        float scale = 1 + GROW;
        for (BakedQuad quad : quads) {
            // Reversed vertex order flips the winding, so culling keeps the far side.
            for (int v = 3; v >= 0; v--) {
                Vector3fc p = quad.position(v);
                vertex(out, pose, quad, v, 0.5f + (p.x() - 0.5f) * scale, 0.5f + (p.y() - 0.5f) * scale, 0.5f + (p.z() - 0.5f) * scale, time);
            }
        }
    }

    /**
     * Is this a flat item (a picture with a sliver of thickness, as vanilla builds a door or a
     * stick to hold) rather than a block? Its every corner sits within a pixel of the middle
     * plane front to back.
     */
    private static boolean flat(List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            for (int v = 0; v < 4; v++) if (Math.abs(quad.position(v).z() - 0.5f) > PIXEL) return false;
        }
        return true;
    }

    /** One outline corner: at (x, y, z), sampling the quad's texture there, its rainbow color by position. */
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, BakedQuad quad, int v, float x, float y, float z, float time) {
        long uv = quad.packedUV(v);
        float hue = time + (x + y + z) * SPREAD / 3f;
        out.addVertex(pose, x, y, z).setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
                .setColor(Mth.hsvToArgb(hue - Mth.floor(hue), 0.8f, 1f, 255));
    }
}
