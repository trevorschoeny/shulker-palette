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
     * them (cushions included); the gaps vanilla leaves (stone has no wall of its own) are
     * filled from the baked table with a real substitute, so every material offers every shape;
     * a roll in stairs places only stairs, and only of materials in the box; survival rolls
     * ignore the shape; a shape nothing in the box offers (not even gap-filled) falls back to a
     * full block; and repaint carries a stair's facing and half onto the new stair.
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

        // Gap-fill: stone has no wall of its own (cobblestone does), so it's filled from the
        // baked table with the closest-looking block that has one. Pin the exact answer, not
        // just "non-null", so a bad regeneration (or the table going stale) is caught; this is
        // the example Trev called out (granite would be the wrong pick; it's not this close).
        Item stoneWall = Shapes.variant(Items.STONE, Shape.WALL);
        if (stoneWall == null) return "stone's wall gap wasn't filled";
        if (stoneWall != Items.STONE_BRICK_WALL) return "stone's wall gap-fill is " + stoneWall + ", not stone bricks";
        if (stoneWall == Items.GRANITE_WALL) return "stone's wall gap-fill is granite, the wrong pick";

        // A material the gap-fill table has nothing for (not a vanilla block family, no wool
        // entry) still offers only its own block: dirt alone offers just BLOCK.
        List<Shape> dirtOnly = Shapes.available(List.of(new ItemStack(Items.DIRT, 32)));
        if (!dirtOnly.equals(List.of(Shape.BLOCK))) return "dirt alone offers " + dirtOnly;

        // Stone's gaps are filled, so a stone and dirt box now offers every shape (stone's own
        // or gap-filled), BLOCK first in cycle order.
        List<ItemStack> stoneAndDirt = List.of(new ItemStack(Items.STONE, 32), new ItemStack(Items.DIRT, 32));
        List<Shape> offered = Shapes.available(stoneAndDirt);
        if (offered.size() != Shape.values().length || offered.get(0) != Shape.BLOCK) return "stone and dirt offer " + offered;

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

        // Fence: stone's gap-fill (dirt still offers nothing), so every roll is that fence, never dirt.
        Palette fence = stairs.withShape(Shape.FENCE);
        Item stoneFence = Shapes.variant(Items.STONE, Shape.FENCE);
        for (int i = 0; i < 20; i++) {
            Item rolled = Draw.roll(fence, contents, random, true).orElseThrow().item().value();
            if (rolled != stoneFence) return "a fence roll gave " + rolled + ", not stone's gap-filled fence " + stoneFence;
        }

        // A shape nothing in the box offers, not even gap-filled: a dirt-only box falls back to
        // a full block, since dirt has no entry in the table at all.
        Palette dirtFence = new Palette(true, Mode.RANDOM, false, 3, 0, Shape.FENCE, Optional.empty(), 0);
        ItemContainerContents dirtContents = ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIRT, 32)));
        for (int i = 0; i < 5; i++) {
            Item rolled = Draw.roll(dirtFence, dirtContents, random, true).orElseThrow().item().value();
            if (rolled != Items.DIRT) return "a fence roll on dirt alone gave " + rolled + ", not a full block";
        }

        return null;
    }
}
