package com.trevorschoeny.shulkerpalette.checks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import testgallery.SceneWorld;

/** Checks for the rows scene (test-gallery/scenes/shulker-palette/rows.json). */
public class RowChecks implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulker-palette", "rows")) {
            rowsLive(ctx, scene);
        }
    }

    /**
     * Station "shulkerpalette:rows_live": a placed six-row palette box opens with every extra
     * row's slot on the menu (27 box, 36 player, 117 extra), rows 4 to 6 live and row 7 shut,
     * and the items saved in rows 5 and 6 reach the client.
     */
    private static void rowsLive(ClientGameTestContext ctx, SceneWorld.Opened scene) {
        JsonObject station = scene.station("shulkerpalette:rows_live");
        JsonArray o = station.getAsJsonArray("origin");
        BlockPos pos = new BlockPos(o.get(0).getAsInt() + 1, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
        scene.standAt(station, 30);
        ctx.waitTicks(5);
        scene.world().getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.openMenu((MenuProvider) server.overworld().getBlockEntity(pos));
        });
        ctx.waitForScreen(ShulkerBoxScreen.class);
        ctx.waitTicks(10);
        ctx.takeScreenshot("shulkerpalette-rows-top");

        String problem = ctx.computeOnClient(mc -> {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu.slots.size() != 180) return "menu has " + menu.slots.size() + " slots, expected 180";
            int row4 = 63, row7 = 63 + 27;
            if (!menu.getSlot(row4).isActive()) return "row 4 is shut";
            if (!menu.getSlot(row4 + 17).isActive()) return "row 5 is shut";
            if (menu.getSlot(row7).isActive()) return "row 7 is live on a six-row box";
            if (!menu.getSlot(63 + 9).getItem().is(net.minecraft.world.item.Items.GRANITE)) return "granite missing from row 5";
            if (!menu.getSlot(63 + 18).getItem().is(net.minecraft.world.item.Items.DIORITE)) return "diorite missing from row 6";
            return null;
        });

        // Scroll the grid down a notch at a time, a look after each.
        ctx.getInput().setCursorPos(ctx.computeOnClient(mc -> mc.getWindow().getScreenWidth() / 2.0),
                ctx.computeOnClient(mc -> mc.getWindow().getScreenHeight() / 2.0 - 60 * mc.getWindow().getGuiScale()));
        for (int notch = 1; notch <= 4; notch++) {
            ctx.getInput().scroll(-1);
            ctx.waitTicks(10);
            ctx.takeScreenshot("shulkerpalette-rows-notch" + notch);
        }
        if (problem != null) throw new AssertionError(problem);
    }
}
