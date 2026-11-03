package com.trevorschoeny.shulkerpalette.checks;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevorschoeny.shulkerpalette.palette.Fill;
import com.trevorschoeny.shulkerpalette.palette.HeldActions;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import com.trevorschoeny.shulkerpalette.palette.Target;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Hiddenite's backend: a survival Load sets a SlotKit reservation per slot instead of Shulker
 * Palette's own ghost, and Fill tops those reservations up from the inventory and whatever else
 * is open. No scene; these are plain machine checks of server-side state, like SlotKit's own.
 */
public class ReservationChecks implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            BlockPos chest = server.computeOnServer(s -> player(s).blockPosition().offset(3, 0, 3));
            ctx.waitTicks(2);

            loadSetsAReservation(ctx, server);
            fillTopsUpFromInventoryAndAnOpenChest(ctx, server, chest);
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    // A held shulker loaded with a saved palette gets a reservation per slot, capped at the
    // saved count, instead of real items or Shulker Palette's own ghost.
    private static void loadSetsAReservation(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> player(s).getInventory().setItem(0, new ItemStack(Items.SHULKER_BOX)));
        ctx.waitTicks(2);
        SavedPalette saved = new SavedPalette(Mode.RANDOM, 3, List.of(
                new SavedPalette.Entry(4, ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIORITE, 9)))));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new HeldActions.LoadHeld(saved, Target.HELD)));
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ItemStack box = player(s).getInventory().getItem(0);
            Reservation r = Reservations.get(box, 4);
            check(r.item().is(Items.DIORITE) && r.limit(r.item()) == 9, "load: slot 4's reservation is " + r);
            check(box.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                            net.minecraft.world.item.component.ItemContainerContents.EMPTY)
                    .itemCopies().allMatch(ItemStack::isEmpty), "load: survival put real items in the box");
        });
    }

    // Fill, called the way a self-check (later: a button) would: tops up a held box's reserved
    // slot from the inventory first, then an open chest, stopping at the cap.
    private static void fillTopsUpFromInventoryAndAnOpenChest(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            container(level, chest).setItem(0, new ItemStack(Items.DIORITE, 5));

            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.DIORITE, 2));
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            Reservations.set(box, 0, Reservation.of(new ItemStack(Items.DIORITE)).withCap(9));
            p.getInventory().setItem(1, box);
            p.openMenu((MenuProvider) level.getBlockEntity(chest));
        });
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Target target = Target.resolve(p, Target.inventorySlot(1));
            check(target != null, "fill: the box in inventory slot 1 didn't resolve as a target");
            Fill.fill(p, target);
            ItemStack box = p.getInventory().getItem(1);
            var items = box.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.EMPTY).itemCopies().toList();
            check(!items.isEmpty() && items.get(0).is(Items.DIORITE) && items.get(0).getCount() == 7,
                    "fill: box slot 0 holds " + (items.isEmpty() ? "nothing" : items.get(0)) + ", not 7 diorite");
            check(p.getInventory().countItem(Items.DIORITE) == 0, "fill: left " + p.getInventory().countItem(Items.DIORITE) + " diorite in the inventory");
            check(container(level(s), chest).getItem(0).isEmpty(), "fill: left " + container(level(s), chest).getItem(0) + " in the chest");
            p.closeContainer();
        });
        ctx.waitTicks(3);
    }

    private static Container container(ServerLevel level, BlockPos pos) {
        return (Container) level.getBlockEntity(pos);
    }

    private static ServerLevel level(MinecraftServer s) {
        return (ServerLevel) player(s).level();
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static final List<String> FAILURES = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static void check(boolean ok, String failure) {
        if (!ok) FAILURES.add(failure);
    }
}
