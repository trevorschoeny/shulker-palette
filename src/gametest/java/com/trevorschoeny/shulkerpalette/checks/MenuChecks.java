package com.trevorschoeny.shulkerpalette.checks;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.shulkerpalette.menu.PaletteScreen;
import com.trevorschoeny.shulkerpalette.palette.HeldActions;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import com.trevorschoeny.shulkerpalette.palette.Shape;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import testgallery.SceneWorld;

/** Checks for the menu scene (test-gallery/scenes/shulker-palette/menu.json). */
public class MenuChecks implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulker-palette", "menu")) {
            menu(ctx, scene);
        }
    }

    /**
     * Station "shulkerpalette:menu": a tap of the palette key with a shulker in hand opens the
     * palette menu, and holding it through a scroll moves the pick and opens nothing; the menu's
     * power and load act on the box in hand; a palette that's on places blocks, never itself, and
     * one turned off places as a shulker box.
     */
    private static void menu(ClientGameTestContext ctx, SceneWorld.Opened scene) {
        JsonObject station = scene.station("shulkerpalette:menu");
        scene.standAt(station, 30);
        ItemStack palette = new ItemStack(Items.SHULKER_BOX);
        palette.set(Palette.COMPONENT, new Palette(true, Mode.PICK, false, 3, 0, Shape.BLOCK, Optional.empty(), 0));
        palette.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE, 64), new ItemStack(Items.GRANITE, 32))));
        scene.world().getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, palette.copy());
        });
        ctx.waitTicks(10);

        // A tap opens the menu.
        ctx.getInput().pressKey(InputConstants.KEY_V);
        ctx.waitTicks(3);
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof PaletteScreen)) throw new AssertionError("a tap of V didn't open the palette menu");
        ctx.takeScreenshot("shulkerpalette-menu");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(3);

        // Held through a scroll: the pick moves, and the release opens nothing.
        ctx.getInput().holdKey(InputConstants.KEY_V);
        ctx.waitTicks(2);
        ctx.getInput().scroll(-1);
        ctx.waitTicks(2);
        ctx.getInput().releaseKey(InputConstants.KEY_V);
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) throw new AssertionError("releasing V after a scroll opened a screen");
        int selection = ctx.computeOnClient(mc -> mc.player.getMainHandItem().getOrDefault(Palette.COMPONENT, Palette.OFF).selection());
        if (selection != 1) throw new AssertionError("the scroll didn't move the pick: selection " + selection);

        // On: a right-click on the ground places a block from the box, not the box.
        BlockPos target = ctx.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                ? hit.getBlockPos().relative(hit.getDirection()) : null);
        if (target == null) throw new AssertionError("not looking at a block to place on");
        ctx.getInput().pressKey(options -> options.keyUse);
        ctx.waitTicks(5);
        String placedOn = scene.world().getServer().computeOnServer(server -> server.overworld().getBlockState(target).getBlock().toString());
        if (!placedOn.contains("granite")) throw new AssertionError("an on palette placed " + placedOn + ", not the picked granite");

        // Power off from the menu's action: the next right-click places the box itself.
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new HeldActions.Act(PanelActions.POWER)));
        ctx.waitTicks(5);
        scene.world().getServer().runOnServer(server -> server.overworld().removeBlock(target, false));
        ctx.waitTicks(3);
        ctx.getInput().pressKey(options -> options.keyUse);
        ctx.waitTicks(5);
        boolean box = scene.world().getServer().computeOnServer(server -> server.overworld().getBlockState(target).getBlock() instanceof ShulkerBoxBlock);
        if (!box) throw new AssertionError("an off palette didn't place as a shulker box");

        // Load into the box in hand (creative fills it).
        scene.world().getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().setItem(0, palette.copy()));
        ctx.waitTicks(5);
        SavedPalette wall = new SavedPalette(Mode.RANDOM, 3, List.of(
                new SavedPalette.Entry(4, ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIORITE, 9)))));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new HeldActions.LoadHeld(wall)));
        ctx.waitTicks(5);
        String loaded = scene.world().getServer().computeOnServer(server -> {
            List<ItemStack> items = server.getPlayerList().getPlayers().getFirst().getMainHandItem()
                    .getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList();
            if (items.size() < 5 || !items.get(4).is(Items.DIORITE)) return "slot 4 holds " + (items.size() > 4 ? items.get(4) : "nothing");
            if (!items.get(0).isEmpty()) return "slot 0 still holds " + items.get(0);
            return null;
        });
        if (loaded != null) throw new AssertionError(loaded);
    }
}
