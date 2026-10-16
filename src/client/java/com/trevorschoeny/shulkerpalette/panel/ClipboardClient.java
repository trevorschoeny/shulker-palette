package com.trevorschoeny.shulkerpalette.panel;

import com.trevorschoeny.shulkerpalette.palette.Clipboard;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;

/** The client's half of Save and Load: the clipboard itself, and what the player is told. */
public final class ClipboardClient {
    private ClipboardClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Clipboard.Saved.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            if (mc.level == null) return;
            mc.keyboardHandler.setClipboard(payload.palette().encode(mc.level.registryAccess()));
            tell(Component.translatable("shulkerpalette.clipboard.saved"));
        });
    }

    /** Load: send the palette on the clipboard to the open box, or say there isn't one. */
    public static void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.gui.screen() instanceof ShulkerBoxScreen screen)) return;
        SavedPalette.decode(mc.keyboardHandler.getClipboard(), mc.level.registryAccess()).ifPresentOrElse(
                palette -> ClientPlayNetworking.send(new Clipboard.Load(screen.getMenu().containerId, palette)),
                () -> tell(Component.translatable("shulkerpalette.clipboard.none")));
    }

    private static void tell(Component message) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(message, false);
    }
}
