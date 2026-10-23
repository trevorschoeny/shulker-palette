package com.trevorschoeny.shulkerpalette.checks;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.openanywhere.api.ShownItem;
import com.trevorschoeny.shulkerpalette.compat.OpenAnywhereClient;
import com.trevorschoeny.shulkerpalette.mixin.ScreenPosAccessor;
import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.HeldActions;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import testgallery.SceneWorld;

/** Checks for the open-anywhere scene (test-gallery/scenes/shulker-palette/open-anywhere.json). */
public class OpenAnywhereChecks implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulker-palette", "open-anywhere")) {
            openAnywhere(ctx, scene);
        }
    }

    /**
     * Station "shulkerpalette:open_anywhere": a palette box opened from the inventory through Open
     * Anywhere is the one its controls change (named by its menu slot); its cheat sheet's ghost
     * shows in the right slot; a survival load sets ghosts on it; a creative load fills its slots.
     */
    private static void openAnywhere(ClientGameTestContext ctx, SceneWorld.Opened scene) {
        scene.standAt(scene.station("shulkerpalette:open_anywhere"), 30);
        int menuSlot = open(ctx);
        ctx.takeScreenshot("shulkerpalette-open-anywhere");

        // The ghost: the cheat sheet names slot 5, diorite.
        String ghost = ctx.computeOnClient(mc -> {
            var menu = ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu();
            for (Slot slot : menu.slots) {
                if (OpenAnywhereClient.ghost(menu, slot).is(Items.DIORITE)) return null;
            }
            return "no diorite ghost in the open box";
        });
        if (ghost != null) throw new AssertionError(ghost);

        // Power, sent for the open box by its menu slot: it goes off; the hand is empty.
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new HeldActions.Act(PanelActions.POWER, menuSlot)));
        ctx.waitTicks(5);
        boolean on = scene.world().getServer().computeOnServer(server -> box(server.getPlayerList().getPlayers().getFirst()).getOrDefault(Palette.COMPONENT, Palette.OFF).on());
        if (on) throw new AssertionError("power for the open box didn't turn it off");
        boolean stillOpen = scene.world().getServer().computeOnServer(server -> OpenItems.openAt(server.getPlayerList().getPlayers().getFirst(), menuSlot) != null);
        if (!stillOpen) throw new AssertionError("changing the open box's palette closed it");

        // Survival load: a cheat sheet on the box, its items untouched.
        SavedPalette wall = new SavedPalette(Mode.RANDOM, 3, List.of(
                new SavedPalette.Entry(4, ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.ANDESITE, 7)))));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new HeldActions.LoadHeld(wall, menuSlot)));
        ctx.waitTicks(5);
        String survival = scene.world().getServer().computeOnServer(server -> {
            ItemStack box = box(server.getPlayerList().getPlayers().getFirst());
            List<SavedPalette.Entry> sheet = box.get(CheatSheet.COMPONENT);
            if (sheet == null || sheet.size() != 1 || sheet.getFirst().slot() != 4) return "survival load set the cheat sheet to " + sheet;
            return null;
        });
        if (survival != null) throw new AssertionError(survival);

        // Creative load: the box's slots on the menu fill, and the box's contents follow.
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(3);
        scene.world().getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE));
        ctx.waitTicks(5);
        int creativeSlot = open(ctx);
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new HeldActions.LoadHeld(wall, creativeSlot)));
        ctx.waitTicks(10);
        String creative = scene.world().getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (OpenItems.openAt(player, creativeSlot) == null) return "the box closed on a creative load";
            for (OpenItems.OpenItem item : OpenItems.of(player)) {
                if (item.menuSlot() != creativeSlot) continue;
                ItemStack box = item.stack();
                var items = box.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.EMPTY).itemCopies().toList();
                if (items.size() < 5 || !items.get(4).is(Items.ANDESITE)) return "creative load: box slot 4 holds " + (items.size() > 4 ? items.get(4) : "nothing");
                if (!items.get(0).isEmpty()) return "creative load left the stone in slot 0";
                return null;
            }
            return "no open box after a creative load";
        });
        if (creative != null) throw new AssertionError(creative);
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(3);
    }

    /** Opens the inventory and double-clicks hotbar slot 0's box; returns the open box's menu slot. */
    private static int open(ClientGameTestContext ctx) {
        boolean creative = ctx.computeOnClient(mc -> mc.player.isCreative());
        if (creative) {
            // Creative's inventory is the creative screen's Inventory tab; its slots are wrappers
            // with no usable index, so the box is found by what it holds.
            ctx.getInput().pressKey(o -> o.keyInventory);
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> ((FabricCreativeModeInventoryScreen) mc.gui.screen()).setSelectedTab(CreativeModeTabs.allTabs().stream()
                    .filter(t -> t.getType() == CreativeModeTab.Type.INVENTORY).findFirst().orElseThrow()));
            ctx.waitTicks(3);
            hover(ctx, s -> s.isActive() && s.getItem().is(Items.SHULKER_BOX));
        } else {
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(3);
            hover(ctx, s -> s.container instanceof Inventory && s.getContainerSlot() == 0 && s.isActive());
        }
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            for (int i = 0; i < 2; i++) for (int action : new int[] {1, 0})
                mouse.invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
        });
        ctx.waitTicks(8);
        return ctx.computeOnClient(mc -> {
            OpenItems.OpenItem shown = ShownItem.get();
            if (shown == null || !Palette.isShulker(shown.stack())) throw new AssertionError("no shulker shown after the double-click; screen " + mc.gui.screen());
            return shown.menuSlot();
        });
    }

    private static void hover(ClientGameTestContext ctx, Predicate<Slot> p) {
        double[] at = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) throw new AssertionError("no container screen");
            Slot found = null;
            for (Slot s : screen.getMenu().slots) if (p.test(s)) { found = s; break; }
            if (found == null) throw new AssertionError("no slot with the box on " + screen);
            var edge = (ScreenPosAccessor) screen;
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[] {(edge.shulkerpalette$leftPos() + found.x + 8) * f, (edge.shulkerpalette$topPos() + found.y + 8) * f};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
    }

    /** The box in hotbar slot 0, on the server. */
    private static ItemStack box(net.minecraft.server.level.ServerPlayer player) {
        return player.getInventory().getItem(0);
    }
}
