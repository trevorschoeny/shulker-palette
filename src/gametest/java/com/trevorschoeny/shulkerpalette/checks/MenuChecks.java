package com.trevorschoeny.shulkerpalette.checks;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.shulkerpalette.config.ConfigScreen;
import com.trevorschoeny.shulkerpalette.config.KeysClient;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
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

        // Keybindery is loaded (it's required), so the settings screen builds its chord rows.
        if (!FabricLoader.getInstance().isModLoaded("keybindery")) throw new AssertionError("Keybindery isn't loaded");
        ctx.runOnClient(mc -> ConfigScreen.create(null));

        // A tap opens the menu.
        ctx.getInput().pressKey(InputConstants.KEY_V);
        ctx.waitTicks(3);
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof PaletteScreen)) throw new AssertionError("a tap of V didn't open the palette menu");
        ctx.takeScreenshot("shulkerpalette-menu");

        // V again closes it, and its release doesn't open it again; E closes it too.
        ctx.getInput().pressKey(InputConstants.KEY_V);
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) throw new AssertionError("V didn't close the menu, or its release reopened it");
        ctx.getInput().pressKey(InputConstants.KEY_V);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(3);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) throw new AssertionError("E didn't close the menu");

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

        // V + Shift held through a scroll moves the shape (stone and granite have stairs and
        // slabs), not the pick, and opens nothing.
        ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
        ctx.getInput().holdKey(InputConstants.KEY_V);
        ctx.waitTicks(2);
        ctx.getInput().scroll(-1);
        ctx.waitTicks(2);
        ctx.getInput().releaseKey(InputConstants.KEY_V);
        ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) throw new AssertionError("releasing V + Shift after a scroll opened a screen");
        Palette shaped = ctx.computeOnClient(mc -> mc.player.getMainHandItem().getOrDefault(Palette.COMPONENT, Palette.OFF));
        if (shaped.shape() == Shape.BLOCK || shaped.selection() != 1) throw new AssertionError("V + Shift scroll gave shape " + shaped.shape() + ", pick " + shaped.selection());
        scene.world().getServer().runOnServer(server -> {
            ItemStack hand = server.getPlayerList().getPlayers().getFirst().getMainHandItem();
            hand.set(Palette.COMPONENT, hand.get(Palette.COMPONENT).withShape(Shape.BLOCK));
        });
        ctx.waitTicks(5);

        // The shape key's default is the chord V + Shift (Keybindery), and it holds in the
        // other press order too: V first, then Shift.
        boolean chord = ctx.computeOnClient(mc -> KeybinderyAPI.getInstance().getChord(KeysClient.SHAPE)
                .equals(Chord.ofKeys(InputConstants.KEY_V, InputConstants.KEY_LSHIFT)));
        if (!chord) throw new AssertionError("the shape key's default isn't V + Shift");
        ctx.getInput().holdKey(InputConstants.KEY_V);
        ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(2);
        ctx.getInput().scroll(-1);
        ctx.waitTicks(2);
        ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
        ctx.getInput().releaseKey(InputConstants.KEY_V);
        ctx.waitTicks(5);
        Palette vFirst = ctx.computeOnClient(mc -> mc.player.getMainHandItem().getOrDefault(Palette.COMPONENT, Palette.OFF));
        if (vFirst.shape() == Shape.BLOCK || vFirst.selection() != 1) throw new AssertionError("V then Shift scroll gave shape " + vFirst.shape() + ", pick " + vFirst.selection());
        scene.world().getServer().runOnServer(server -> {
            ItemStack hand = server.getPlayerList().getPlayers().getFirst().getMainHandItem();
            hand.set(Palette.COMPONENT, hand.get(Palette.COMPONENT).withShape(Shape.BLOCK));
        });
        ctx.waitTicks(5);

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

        repaint(ctx, scene);
    }

    /**
     * Repaint on blocks with a use of their own: a full barrel refuses (nothing is lost) and
     * opens nothing; an empty barrel and a crafting table are repainted, not used.
     */
    private static void repaint(ClientGameTestContext ctx, SceneWorld.Opened scene) {
        ItemStack painter = new ItemStack(Items.SHULKER_BOX);
        painter.set(Palette.COMPONENT, new Palette(true, Mode.PICK, true, 3, 0, Shape.BLOCK, Optional.empty(), 0));
        painter.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE, 64))));
        scene.world().getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().setItem(0, painter.copy()));
        ctx.waitTicks(5);
        BlockPos aim = ctx.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit ? hit.getBlockPos() : null);
        if (aim == null) throw new AssertionError("not looking at a block to repaint");

        String full = clickOn(ctx, scene, aim, Blocks.BARREL.defaultBlockState(), true);
        if (!full.contains("barrel")) throw new AssertionError("a full barrel was repainted into " + full);
        String empty = clickOn(ctx, scene, aim, Blocks.BARREL.defaultBlockState(), false);
        if (!empty.contains("stone")) throw new AssertionError("an empty barrel became " + empty + ", not stone");
        String table = clickOn(ctx, scene, aim, Blocks.CRAFTING_TABLE.defaultBlockState(), false);
        if (!table.contains("stone")) throw new AssertionError("a crafting table became " + table + ", not stone");
    }

    /** Sets {@code state} at {@code pos} (with a diamond inside when {@code filled}), right-clicks it, and names what's there after. */
    private static String clickOn(ClientGameTestContext ctx, SceneWorld.Opened scene, BlockPos pos, BlockState state, boolean filled) {
        scene.world().getServer().runOnServer(server -> {
            // Cleared first: setting a barrel over a barrel keeps the old one and what's in it.
            server.overworld().removeBlock(pos, false);
            server.overworld().setBlock(pos, state, Block.UPDATE_ALL);
            if (filled && server.overworld().getBlockEntity(pos) instanceof Container container) container.setItem(0, new ItemStack(Items.DIAMOND));
        });
        ctx.waitTicks(5);
        ctx.getInput().pressKey(options -> options.keyUse);
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) throw new AssertionError("a repaint click on " + state + " opened a screen");
        return scene.world().getServer().computeOnServer(server -> server.overworld().getBlockState(pos).getBlock().toString());
    }
}
