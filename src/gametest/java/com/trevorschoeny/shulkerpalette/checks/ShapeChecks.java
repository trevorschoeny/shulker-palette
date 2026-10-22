package com.trevorschoeny.shulkerpalette.checks;

import com.trevorschoeny.shulkerpalette.palette.Draw;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Repaint;
import com.trevorschoeny.shulkerpalette.palette.Shape;
import com.trevorschoeny.shulkerpalette.palette.Shapes;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import testgallery.SceneWorld;

/** Checks for the shapes scene (test-gallery/scenes/shulker-palette/shapes.json). */
public class ShapeChecks implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulker-palette", "shapes")) {
            String problem = ctx.computeOnClient(mc -> shapes());
            if (problem != null) throw new AssertionError(problem);
        }
    }

    /**
     * Station "shulkerpalette:shapes": stone, oak and wool come in the shapes vanilla makes of
     * them (cushions included); a stone and dirt box offers only stone's shapes; a roll in stairs
     * places only stairs, and only of materials that have them; survival rolls ignore the shape;
     * and repaint carries a stair's facing and half onto the new stair.
     */
    private static String shapes() {
        if (Shapes.variant(Items.STONE, Shape.STAIRS) != Items.STONE_STAIRS) return "stone has no stairs";
        if (Shapes.variant(Items.STONE, Shape.BUTTON) != Items.STONE_BUTTON) return "stone has no button";
        if (Shapes.variant(Items.OAK_PLANKS, Shape.DOOR) != Items.OAK_DOOR) return "oak has no door";
        if (Shapes.variant(Items.OAK_STAIRS, Shape.SLAB) != Items.OAK_SLAB) return "oak stairs don't count as oak";
        if (Shapes.variant(Items.WOOL.white(), Shape.CARPET) != Items.CARPET.white()) return "white wool has no carpet";
        if (Shapes.variant(Items.WOOL.white(), Shape.CUSHION) != Items.CUSHION.white()) return "white wool has no cushion";
        if (Shapes.variant(Items.WOOL.white(), Shape.STAIRS) != Items.WOOL_STAIRS.white()) return "white wool has no stairs";
        if (Shapes.variant(Items.DIRT, Shape.STAIRS) != null) return "dirt has stairs";
        if (Shapes.variant(Items.DIRT, Shape.BLOCK) != Items.DIRT) return "dirt isn't its own block";

        List<ItemStack> stoneAndDirt = List.of(new ItemStack(Items.STONE, 32), new ItemStack(Items.DIRT, 32));
        List<Shape> offered = Shapes.available(stoneAndDirt);
        if (!offered.contains(Shape.STAIRS) || offered.contains(Shape.FENCE) || offered.get(0) != Shape.BLOCK) return "stone and dirt offer " + offered;

        Palette stairs = new Palette(true, Mode.RANDOM, false, 3, 0, Shape.STAIRS, Optional.empty(), 0);
        ItemContainerContents contents = ItemContainerContents.fromItems(stoneAndDirt);
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 50; i++) {
            Item rolled = Draw.roll(stairs, contents, random, true).orElseThrow().item().value();
            if (rolled != Items.STONE_STAIRS) return "a stairs roll gave " + rolled;
        }
        boolean dirt = false;
        for (int i = 0; i < 50; i++) dirt |= Draw.roll(stairs, contents, random, false).orElseThrow().item().value() == Items.DIRT;
        if (!dirt) return "survival rolls never gave dirt, so the shape applied";
        Palette fence = stairs.withShape(Shape.FENCE);
        for (int i = 0; i < 20; i++) {
            Item rolled = Draw.roll(fence, contents, random, true).orElseThrow().item().value();
            if (rolled != Items.STONE && rolled != Items.DIRT) return "an unoffered shape gave " + rolled + ", not a full block";
        }

        BlockState old = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
        BlockState carried = Repaint.carry(old, Blocks.STONE_STAIRS.defaultBlockState());
        if (carried.getValue(StairBlock.FACING) != Direction.EAST || carried.getValue(StairBlock.HALF) != Half.TOP) return "repaint lost the stair's facing: " + carried;
        return null;
    }
}
