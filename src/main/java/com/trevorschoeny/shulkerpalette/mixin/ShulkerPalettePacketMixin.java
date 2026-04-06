package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.ShulkerPaletteRoll;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteState;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transfers Shulker Palette pending fields from the client thread to
 * server-side fields, mirroring the pattern in ServerGamePacketListenerMixin.
 *
 * Runs at HEAD of handleUseItemOn() on the server main thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ShulkerPalettePacketMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleUseItemOn", at = @At("HEAD"))
    private void trevorMod$transferShulkerPaletteSignal(ServerboundUseItemOnPacket packet,
                                                         CallbackInfo ci) {
        // Only process on the server main thread.
        MinecraftServer server = this.player.level().getServer();
        if (server == null || !server.isSameThread()) return;

        // Dequeue the next pending roll. Each client useItemOn enqueued exactly one,
        // and packets arrive in order, so this always pairs with the correct placement.
        ShulkerPaletteState.PendingRoll roll = ShulkerPaletteState.pendingRolls.poll();
        if (roll == null) return; // Not a shulker palette placement.

        // Build the server override stack and publish.
        ShulkerPaletteState.serverOverride      = ShulkerPaletteRoll.stackForId(roll.overrideItemId());
        ShulkerPaletteState.serverShulkerInvSlot = roll.shulkerInvSlot();
        ShulkerPaletteState.serverInternalSlot   = roll.internalSlot();
    }
}
