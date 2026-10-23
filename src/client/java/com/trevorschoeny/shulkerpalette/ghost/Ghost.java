package com.trevorschoeny.shulkerpalette.ghost;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Repaint;
import com.trevorschoeny.shulkerpalette.config.Config;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A see-through copy of the palette's next block where a click would put it.
 * <ul>
 *   <li><b>Placing</b> (survival and creative, on by default): in the spot the block would land,
 *       in the orientation it would get, only where it can actually be placed.</li>
 *   <li><b>Repainting</b> (creative): over the block you look at, as the block it would become,
 *       the old block's facing and half carried over as repaint does.</li>
 * </ul>
 * Each has its own switch in the settings. A door shows both halves.
 *
 * <p>Drawn through Fabric's level render event and vanilla's translucent block render type
 * (no raw OpenGL), the way Sandboxes draws its overlay, so it runs on every backend and
 * alongside Sodium.
 */
public final class Ghost {
    private Ghost() {}

    /** How opaque the ghost is (of 255). */
    private static final int ALPHA = 0x99;
    /**
     * The ghost is drawn scaled toward the camera by this much. Scaling about the eye leaves
     * the picture on screen unchanged but brings every face of the ghost slightly in front of
     * the world's, so it never flickers against the block it covers, a stair's step or a slab's
     * top included (growing it about the block's own center left those faces where they were).
     */
    private static final float TOWARD_EYE = 0.995f;
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** A block state to draw at a position. */
    private record Shown(BlockPos pos, BlockState state) {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            Palette palette = Palette.activeOn(mc.player.getMainHandItem());
            if (palette == null) return;
            ItemStack next = palette.next().map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
            if (!(next.getItem() instanceof BlockItem block)) return;

            BlockPlaceContext placing = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, next, hit);
            Shown shown = Repaint.active(palette, mc.player)
                    ? (Config.repaintGhost() ? repainted(mc.level, hit.getBlockPos(), block, placing) : null)
                    : (Config.placementGhost() ? placed(mc.level, block, placing) : null);
            if (shown == null) return;

            Vec3 camera = context.levelState().cameraRenderState.pos;
            submit(mc, context, camera, shown.pos(), shown.state());
            // A two-block-tall block (a door) shows its top half too.
            if (shown.state().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                submit(mc, context, camera, shown.pos().above(),
                        shown.state().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            }
        });
    }

    /** Where and how the block would land, or null when a click wouldn't place it. */
    private static @Nullable Shown placed(Level level, BlockItem block, BlockPlaceContext placing) {
        if (!placing.canPlace()) return null;
        BlockPos pos = placing.getClickedPos();
        BlockState state = block.getBlock().getStateForPlacement(placing);
        if (state == null || !state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, CollisionContext.empty())) return null;
        return new Shown(pos, state);
    }

    /** What the looked-at block would become, or null when it can't be repainted. */
    private static @Nullable Shown repainted(Level level, BlockPos pos, BlockItem block, BlockPlaceContext placing) {
        BlockState old = level.getBlockState(pos);
        if (!Repaint.canRepaint(level, pos, old)) return null;
        BlockState state = block.getBlock().getStateForPlacement(placing);
        if (state == null) return null;
        return new Shown(pos, Block.updateFromNeighbourShapes(Repaint.carry(old, state), level, pos));
    }

    private static void submit(Minecraft mc, LevelRenderContext context, Vec3 camera, BlockPos pos, BlockState state) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(state.getSeed(pos)), parts);
        PoseStack poses = context.poseStack();
        poses.pushPose();
        poses.scale(TOWARD_EYE, TOWARD_EYE, TOWARD_EYE);
        poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.translucentMovingBlock(),
                (pose, out) -> draw(pose, out, parts));
        poses.popPose();
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer out, List<BlockStateModelPart> parts) {
        QuadInstance look = new QuadInstance();
        look.setColor(ALPHA << 24 | 0xFFFFFF);
        look.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) out.putBakedQuad(pose, quad, look);
            }
        }
    }
}
