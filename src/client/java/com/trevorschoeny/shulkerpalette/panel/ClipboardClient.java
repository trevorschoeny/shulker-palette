package com.trevorschoeny.shulkerpalette.panel;

import com.trevorschoeny.shulkerpalette.palette.Clipboard;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The client's half of Save and Load: the clipboard itself, and what the player is told.
 * The answer shows as the pressed button's tooltip for a moment (the action bar is hidden
 * behind the open screen).
 */
public final class ClipboardClient {
    private ClipboardClient() {}

    /** Which button an answer belongs to. */
    enum Button { SAVE, LOAD }

    private static final long SHOW_MILLIS = 2500;
    private static @Nullable Component answer;
    private static Button answerFor = Button.SAVE;
    private static long answeredAt;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Clipboard.Saved.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            if (mc.level == null) return;
            mc.keyboardHandler.setClipboard(payload.palette().write(mc.level.registryAccess()));
            answer(Button.SAVE, Component.translatable("shulkerpalette.clipboard.saved"));
        });
    }

    /** Load: send the palette on the clipboard to the open box, or say what is wrong with it. */
    public static void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.gui.screen() instanceof ShulkerBoxScreen screen)) return;
        SavedPalette.parse(mc.keyboardHandler.getClipboard(), mc.level.registryAccess()).ifSuccess(palette -> {
            ClientPlayNetworking.send(new Clipboard.Load(screen.getMenu().containerId, palette));
            answer(Button.LOAD, Component.translatable("shulkerpalette.clipboard.loaded"));
        }).ifError(error -> answer(Button.LOAD, Component.literal(error.message())));
    }

    /** The answer to show as this button's tooltip, while it is fresh; else null. */
    static @Nullable Component answerFor(Button button) {
        boolean fresh = answer != null && answerFor == button && System.currentTimeMillis() - answeredAt < SHOW_MILLIS;
        return fresh ? answer : null;
    }

    private static void answer(Button button, Component message) {
        answer = message;
        answerFor = button;
        answeredAt = System.currentTimeMillis();
    }
}
