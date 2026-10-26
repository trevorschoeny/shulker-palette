package com.trevorschoeny.shulkerpalette.checks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.trevorschoeny.shulkerpalette.panel.CheatSheetClient;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import testgallery.SceneWorld;

/** Checks for the cheat-sheet scene (test-gallery/scenes/shulker-palette/cheat-sheet.json). */
public class CheatSheetChecks implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulker-palette", "cheat-sheet")) {
            cheatSheet(ctx, scene);
        }
    }

    /**
     * Station "shulkerpalette:cheat_sheet": an empty box slot with a cheat sheet entry shows
     * that item as the ghost (the slot decoration in {@link CheatSheetClient}), and its tooltip
     * names it and how many go there.
     */
    private static void cheatSheet(ClientGameTestContext ctx, SceneWorld.Opened scene) {
        JsonObject station = scene.station("shulkerpalette:cheat_sheet");
        JsonArray o = station.getAsJsonArray("origin");
        BlockPos pos = new BlockPos(o.get(0).getAsInt() + 2, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
        scene.standAt(station, 30);
        ctx.waitTicks(5);
        scene.world().getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.openMenu((MenuProvider) server.overworld().getBlockEntity(pos));
        });
        ctx.waitForScreen(ShulkerBoxScreen.class);
        ctx.waitTicks(5);

        String problem = ctx.computeOnClient(mc -> {
            AbstractContainerMenu menu = mc.player.containerMenu;
            Slot slot = null;
            for (Slot s : menu.slots) {
                if (!(s.container instanceof Inventory) && s.getContainerSlot() == 0) { slot = s; break; }
            }
            if (slot == null) return "no box slot 0 on the opened menu";
            ItemStack ghost = CheatSheetClient.ghostFor(menu, slot);
            if (!ghost.is(Items.DIORITE) || ghost.getCount() != 9) return "slot 0's ghost is " + ghost;
            List<Component> tooltip = CheatSheetClient.tooltipFor(ghost);
            if (tooltip.size() != 2 || !tooltip.get(0).getString().equals(ghost.getHoverName().getString())) return "tooltip is " + tooltip;
            if (!tooltip.get(1).getString().contains("9")) return "tooltip's count line is " + tooltip.get(1).getString();
            return null;
        });
        if (problem != null) throw new AssertionError(problem);
    }
}
