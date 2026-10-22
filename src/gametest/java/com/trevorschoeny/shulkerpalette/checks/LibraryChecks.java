package com.trevorschoeny.shulkerpalette.checks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.trevorschoeny.shulkerpalette.library.Library;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.library.LibraryScreen;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.PaletteTransfer;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import testgallery.SceneWorld;

/** Checks for the library scene (test-gallery/scenes/shulker-palette/library.json). */
public class LibraryChecks implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulker-palette", "library")) {
            library(ctx, scene);
        }
    }

    /**
     * Station "shulkerpalette:library": a palette saved to This world reads back with its name
     * and slots; rename, move to All worlds and delete work; a hand-broken file is listed with
     * its line and problem; and loading a palette into the placed box (creative) fills it.
     */
    private static void library(ClientGameTestContext ctx, SceneWorld.Opened scene) {
        JsonObject station = scene.station("shulkerpalette:library");
        JsonArray o = station.getAsJsonArray("origin");
        BlockPos pos = new BlockPos(o.get(0).getAsInt() + 1, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
        scene.standAt(station, 30);
        ctx.waitTicks(5);

        SavedPalette wall = new SavedPalette(Mode.GRADIENT, 3, List.of(
                new SavedPalette.Entry(0, ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.GRANITE, 20))),
                new SavedPalette.Entry(9, ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIORITE, 7)))));

        String problem = ctx.computeOnClient(mc -> {
            Library.Shelf world = Library.Shelf.WORLD, global = Library.Shelf.GLOBAL;
            if (Library.folder(world) == null) return "This world has no folder in singleplayer";
            if (!Library.save(world, "Check \"wall\"", wall)) return "save failed";
            Library.Entry saved = find(world, "Check 'wall'");
            if (saved == null) return "saved palette not listed by name: " + Library.list(world);
            SavedPalette back = saved.palette().getOrThrow();
            if (back.mode() != Mode.GRADIENT || back.slots().size() != 2 || back.slots().get(1).slot() != 9) return "read back wrong: " + back;

            if (!Library.rename(saved, "Check renamed")) return "rename failed";
            Library.Entry renamed = find(world, "Check renamed");
            if (renamed == null || find(world, "Check 'wall'") != null) return "rename left the old name or lost the new one";
            if (!Library.move(renamed)) return "move failed";
            Library.Entry moved = find(global, "Check renamed");
            if (moved == null || find(world, "Check renamed") != null) return "move didn't move it to All worlds";
            if (!Library.delete(moved) || find(global, "Check renamed") != null) return "delete failed";

            try {
                Files.createDirectories(Library.folder(world));
                Files.writeString(Library.folder(world).resolve("check-broken.txt"),
                        "shulker_palette name=\"Broken\"\nrow 1: stnoe 3\n");
            } catch (java.io.IOException e) {
                return "couldn't write the broken file: " + e;
            }
            Library.Entry broken = Library.list(world).stream().filter(e -> e.key().equals("check-broken")).findFirst().orElse(null);
            if (broken == null || !broken.palette().isError()) return "broken file not listed as an error";
            String message = broken.palette().error().orElseThrow().message();
            Library.delete(broken);
            if (!message.startsWith("Line 2")) return "broken file's error doesn't name its line: " + message;
            return null;
        });
        if (problem != null) throw new AssertionError(problem);

        // Load: open the placed box and send the palette, as the Load list does.
        scene.world().getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.openMenu((MenuProvider) server.overworld().getBlockEntity(pos));
        });
        ctx.waitForScreen(ShulkerBoxScreen.class);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new PaletteTransfer.Load(mc.player.containerMenu.containerId, wall)));
        ctx.waitTicks(10);
        String loaded = scene.world().getServer().computeOnServer(server -> {
            Container box = (Container) server.overworld().getBlockEntity(pos);
            if (!box.getItem(0).is(Items.GRANITE) || box.getItem(0).getCount() != 20) return "slot 0 holds " + box.getItem(0);
            if (!box.getItem(9).is(Items.DIORITE)) return "slot 9 holds " + box.getItem(9);
            return null;
        });
        ctx.takeScreenshot("shulkerpalette-library-loaded");
        if (loaded != null) throw new AssertionError(loaded);

        // Pictures for a look, not assertions: the Load list with a palette on each shelf,
        // then the Save dialog the panel's Save button opens.
        ctx.runOnClient(mc -> {
            Library.save(Library.Shelf.WORLD, "Stone wall", wall);
            Library.save(Library.Shelf.GLOBAL, "Concrete fade", wall);
            LibraryClient.toggleLoad();
        });
        ctx.waitTicks(5);
        ctx.takeScreenshot("shulkerpalette-library-load-list");
        ctx.runOnClient(mc -> {
            LibraryClient.toggleLoad();
            mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, PanelActions.SAVE);
        });
        ctx.waitTicks(10);
        ctx.takeScreenshot("shulkerpalette-library-save-dialog");
        ctx.runOnClient(mc -> mc.gui.setScreen(new LibraryScreen(null)));
        ctx.waitTicks(5);
        ctx.takeScreenshot("shulkerpalette-library-page");
        ctx.runOnClient(mc -> {
            Library.list(Library.Shelf.WORLD).forEach(Library::delete);
            Library.list(Library.Shelf.GLOBAL).stream().filter(e -> e.name().equals("Concrete fade")).forEach(Library::delete);
        });
    }

    private static Library.Entry find(Library.Shelf shelf, String name) {
        return Library.list(shelf).stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }
}
