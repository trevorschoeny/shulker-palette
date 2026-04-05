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

        // Read pending fields atomically (all three set together by client HEAD).
        String itemId     = ShulkerPaletteState.pendingOverrideItemId;
        int shulkerSlot   = ShulkerPaletteState.pendingShulkerInvSlot;
        int internalSlot  = ShulkerPaletteState.pendingInternalSlot;

        // Nothing pending = not a shulker palette placement.
        if (itemId == null || itemId.isEmpty()) return;

        // Consume pending fields immediately.
        ShulkerPaletteState.pendingOverrideItemId = null;
        ShulkerPaletteState.pendingShulkerInvSlot = -1;
        ShulkerPaletteState.pendingInternalSlot   = -1;

        // Build the server override stack and publish.
        ShulkerPaletteState.serverOverride      = ShulkerPaletteRoll.stackForId(itemId);
        ShulkerPaletteState.serverShulkerInvSlot = shulkerSlot;
        ShulkerPaletteState.serverInternalSlot   = internalSlot;
    }
}
