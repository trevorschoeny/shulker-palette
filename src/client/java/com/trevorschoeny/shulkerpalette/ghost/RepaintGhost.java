package com.trevorschoeny.shulkerpalette.ghost;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Repaint;
import java.util.ArrayList;
import java.util.List;
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
 * With repaint on, a see-through copy of the palette's next block sits over the block you
 * look at: what a click would turn it into.
 *
 * <p>Drawn through Fabric's level render event and vanilla's translucent block render type
 * (no raw OpenGL), the way Sandboxes draws its overlay, so it runs on every backend and
 * alongside Sodium.
 */
public final class RepaintGhost {
    private RepaintGhost() {}

    /** How opaque the ghost is (of 255). */
    private static final int ALPHA = 0x99;
    /** Grows the ghost a hair past the block under it, so the two faces don't flicker. */
    private static final float INFLATE = 0.004f;
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            Palette palette = Palette.activeOn(mc.player.getMainHandItem());
            if (palette == null || !Repaint.active(palette, mc.player)) return;
            BlockPos pos = hit.getBlockPos();
            if (!Repaint.canRepaint(mc.level, pos, mc.level.getBlockState(pos))) return;
            ItemStack next = palette.next().map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
            if (!(next.getItem() instanceof BlockItem block)) return;

            // ponytail: shows the block's default state, so a log or stairs may face another
            // way than the click will place them; tinted blocks (grass, leaves) show untinted.
            BlockState state = block.getBlock().defaultBlockState();
            List<BlockStateModelPart> parts = new ArrayList<>();
            mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(state.getSeed(pos)), parts);

            Vec3 camera = context.levelState().cameraRenderState.pos;
            PoseStack poses = context.poseStack();
            poses.pushPose();
            poses.translate(pos.getX() - camera.x - INFLATE, pos.getY() - camera.y - INFLATE, pos.getZ() - camera.z - INFLATE);
            poses.scale(1 + 2 * INFLATE, 1 + 2 * INFLATE, 1 + 2 * INFLATE);
            context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.translucentMovingBlock(),
                    (pose, out) -> draw(pose, out, parts));
            poses.popPose();
        });
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
