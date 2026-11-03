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
     * Station "shulkerpalette:shapes": the variants a material really has (cushions included);
     * general mode's closest match for a gap (stone's wall is stone bricks, not granite); what
     * each mode offers, None first; None placing the box's items as they are; specific placing
     * only blocks that have the variant, at their own ratios; general giving every block its own
     * variant or closest match; survival ignoring variants; old saved palettes reading as None;
     * and repaint carrying a stair's facing and half onto the new stair.
     */
    private static String shapes() {
        if (Shapes.own(Items.STONE, Shape.STAIRS) != Items.STONE_STAIRS) return "stone has no stairs";
        if (Shapes.own(Items.STONE, Shape.BUTTON) != Items.STONE_BUTTON) return "stone has no button";
        if (Shapes.own(Items.OAK_PLANKS, Shape.DOOR) != Items.OAK_DOOR) return "oak has no door";
        if (Shapes.own(Items.OAK_STAIRS, Shape.SLAB) != Items.OAK_SLAB) return "oak stairs don't count as oak";
        if (Shapes.own(Items.OAK_STAIRS, Shape.NONE) != Items.OAK_STAIRS) return "None changed oak stairs";
        if (Shapes.own(Items.OAK_STAIRS, Shape.BLOCK) != Items.OAK_PLANKS) return "oak stairs' block isn't planks";
        if (Shapes.own(Items.WOOL.white(), Shape.CARPET) != Items.CARPET.white()) return "white wool has no carpet";
        if (Shapes.own(Items.WOOL.white(), Shape.CUSHION) != Items.CUSHION.white()) return "white wool has no cushion";
        if (Shapes.own(Items.DIRT, Shape.STAIRS) != null) return "dirt has stairs";
        if (Shapes.own(Items.DIRT, Shape.BLOCK) != Items.DIRT) return "dirt isn't its own block";

        // Gaps: specific has none of its own; general's closest match, pinned so a bad
        // regeneration is caught (granite would be the wrong pick for stone's wall).
        if (Shapes.own(Items.STONE, Shape.WALL) != null) return "stone has a wall of its own";
        Item stoneWall = Shapes.closest(Items.STONE, Shape.WALL);
        if (stoneWall != Items.STONE_BRICK_WALL) return "stone's closest wall is " + stoneWall + ", not stone bricks";
        if (Shapes.closest(Items.DIRT, Shape.WALL) != null) return "dirt has a closest wall";

        // What each mode offers for stone and dirt: specific, None and stone's own; general, all.
        List<ItemStack> stoneAndDirt = List.of(new ItemStack(Items.STONE, 32), new ItemStack(Items.DIRT, 32));
        List<Shape> specific = Shapes.offered(stoneAndDirt, false);
        if (specific.get(0) != Shape.NONE || !specific.contains(Shape.STAIRS) || specific.contains(Shape.WALL)) return "specific offers " + specific;
        if (!Shapes.offered(stoneAndDirt, true).equals(List.of(Shape.values()))) return "general doesn't offer every variant";

        ItemContainerContents contents = ItemContainerContents.fromItems(stoneAndDirt);
        RandomSource random = RandomSource.create(1);

        // None: the box as it is, both items.
        Palette none = new Palette(true, Mode.RANDOM, false, 3, 0, Shape.NONE, Optional.empty(), 0);
        boolean sawStone = false, sawDirt = false;
        for (int i = 0; i < 60; i++) {
            Item rolled = Draw.roll(none, contents, random, true).orElseThrow().item().value();
            sawStone |= rolled == Items.STONE;
            sawDirt |= rolled == Items.DIRT;
            if (rolled != Items.STONE && rolled != Items.DIRT) return "None placed " + rolled;
        }
        if (!sawStone || !sawDirt) return "None didn't place both items";

        // Specific stairs: dirt has none, so it drops out and every roll is stone stairs.
        Palette stairs = none.withShape(Shape.STAIRS);
        for (int i = 0; i < 50; i++) {
            Item rolled = Draw.roll(stairs, contents, random, true).orElseThrow().item().value();
            if (rolled != Items.STONE_STAIRS) return "a specific stairs roll gave " + rolled;
        }
        // Survival: no variants, the items as they are.
        boolean dirt = false;
        for (int i = 0; i < 50; i++) dirt |= Draw.roll(stairs, contents, random, false).orElseThrow().item().value() == Items.DIRT;
        if (!dirt) return "survival rolls never gave dirt, so the variant applied";

        // Specific wall: nothing in the box has one, so it falls back to None.
        Item specificWall = Draw.roll(none.withShape(Shape.WALL), contents, random, true).orElseThrow().item().value();
        if (specificWall != Items.STONE && specificWall != Items.DIRT) return "a specific wall nothing has gave " + specificWall;

        // General wall: stone takes its closest match, dirt (no match at all) drops out.
        Palette generalWall = none.withShape(Shape.WALL).withGeneral(true);
        for (int i = 0; i < 30; i++) {
            Item rolled = Draw.roll(generalWall, contents, random, true).orElseThrow().item().value();
            if (rolled != Items.STONE_BRICK_WALL) return "a general wall roll gave " + rolled;
        }

        // A palette saved before None existed: "block" was the default, so it reads as None;
        // any other old shape carries over.
        Palette old = Palette.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"on\":true,\"shape\":\"block\"}")).getOrThrow();
        if (old.shape() != Shape.NONE) return "an old block palette reads as " + old.shape();
        Palette oldStairs = Palette.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"on\":true,\"shape\":\"stairs\"}")).getOrThrow();
        if (oldStairs.shape() != Shape.STAIRS) return "an old stairs palette reads as " + oldStairs.shape();

        BlockState oldStair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
        BlockState carried = Repaint.carry(oldStair, Blocks.STONE_STAIRS.defaultBlockState());
        if (carried.getValue(StairBlock.FACING) != Direction.EAST || carried.getValue(StairBlock.HALF) != Half.TOP) return "repaint lost the stair's facing: " + carried;
        return null;
    }
}
