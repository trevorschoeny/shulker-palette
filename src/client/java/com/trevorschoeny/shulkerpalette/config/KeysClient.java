package com.trevorschoeny.shulkerpalette.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.menu.PaletteScreen;
import com.trevorschoeny.shulkerpalette.palette.HeldActions;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import com.trevorschoeny.shulkerpalette.popup.PalettePopup;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The palette keys, as ordinary vanilla key binds (Controls, and Keybindery's chords when it
 * is installed), also shown on the mod's settings screen.
 *
 * <p>The palette key (V) does two things: held while scrolling it moves the material, and a tap
 * (pressed and released with no scroll in between) opens the palette menu for the shulker in
 * hand. The shape key (V + Shift) held while scrolling moves the shape.
 */
public final class KeysClient {
    private KeysClient() {}

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "keys"));

    /** Hold with scroll to move the material; tap to open the palette menu. V by default. */
    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.shulkerpalette.modifier", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    /**
     * Held with scroll to move the palette's shape (creative). Meant to default to the chord
     * V + Left Shift, which needs Keybindery's chord defaults (asked of Lead, 2026-10-23); until
     * those land it ships unbound, and an unbound shape key means the palette key + Left Shift.
     */
    public static final KeyMapping SHAPE = new KeyMapping(
            "key.shulkerpalette.shape", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
    /** Step the shape without scrolling, for players who'd rather press keys. Unbound by default. */
    public static final KeyMapping PREVIOUS_SHAPE = new KeyMapping(
            "key.shulkerpalette.shape_previous", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping NEXT_SHAPE = new KeyMapping(
            "key.shulkerpalette.shape_next", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
    /** Turns the shulker in hand into a palette, or back into plain storage. Unbound by default. */
    public static final KeyMapping POWER = new KeyMapping(
            "key.shulkerpalette.power", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
    /** Flips repaint on the palette in hand. Unbound by default. */
    public static final KeyMapping REPAINT = new KeyMapping(
            "key.shulkerpalette.repaint", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

    // The palette key's tap: down since this press, and whether a scroll used it.
    private static boolean wasDown;
    private static boolean scrolled;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(MODIFIER);
        KeyMappingHelper.registerKeyMapping(SHAPE);
        KeyMappingHelper.registerKeyMapping(PREVIOUS_SHAPE);
        KeyMappingHelper.registerKeyMapping(NEXT_SHAPE);
        KeyMappingHelper.registerKeyMapping(POWER);
        KeyMappingHelper.registerKeyMapping(REPAINT);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tap(mc);
            while (PREVIOUS_SHAPE.consumeClick()) stepShape(mc, -1);
            while (NEXT_SHAPE.consumeClick()) stepShape(mc, 1);
            while (POWER.consumeClick()) act(mc, PanelActions.POWER);
            while (REPAINT.consumeClick()) act(mc, PanelActions.REPAINT);
        });
    }

    /** A scroll moved an axis while the palette key was held, so its release isn't a tap. */
    public static void markScrolled() {
        scrolled = true;
    }

    /**
     * On release of the palette key, open the menu if nothing scrolled since it went down. A tap
     * shorter than a tick is down and up again before this runs, so a press counted by vanilla
     * (its click count) counts as down too.
     */
    private static void tap(Minecraft mc) {
        boolean pressed = false;
        while (MODIFIER.consumeClick()) pressed = true;
        boolean down = mc.gui.screen() == null && held(MODIFIER);
        if ((down || pressed) && !wasDown) wasDown = true;
        if (!down && wasDown) {
            if (!scrolled && mc.gui.screen() == null && mc.player != null && Palette.isShulker(mc.player.getMainHandItem())) {
                mc.gui.setScreen(new PaletteScreen());
            }
            wasDown = false;
            scrolled = false; // reset on release, so a scroll anywhere in the hold counts
        }
    }

    /** Asks the server to press a palette button for the shulker in hand; it checks the rules. */
    public static void act(Minecraft mc, int action) {
        if (mc.player == null || !Palette.isShulker(mc.player.getMainHandItem())
                || !ClientPlayNetworking.canSend(HeldActions.Act.TYPE)) return;
        ClientPlayNetworking.send(new HeldActions.Act(action));
    }

    /** The shape keys: one step through the box's shapes, as a shape scroll would. */
    private static void stepShape(Minecraft mc, int steps) {
        if (mc.player == null || !mc.player.isCreative() || Palette.activeOn(mc.player.getMainHandItem()) == null
                || !ClientPlayNetworking.canSend(Scroll.Payload.TYPE)) return;
        ClientPlayNetworking.send(new Scroll.Payload(steps, true));
        PalettePopup.steppedShape(steps);
    }

    /** Is the palette key down (its whole chord, with Keybindery)? Not while a screen is open: no gestures happen there. */
    public static boolean modifierDown() {
        return Minecraft.getInstance().gui.screen() == null && held(MODIFIER);
    }

    /** Is the shape key down? Checked before the palette key, since its chord includes it by default. */
    public static boolean shapeDown() {
        if (Minecraft.getInstance().gui.screen() != null) return false;
        boolean bound = FabricLoader.getInstance().isModLoaded("keybindery")
                ? !KeybinderyAPI.getInstance().getChord(SHAPE).isUnbound() : !SHAPE.isUnbound();
        if (bound) return held(SHAPE);
        return held(MODIFIER) && InputConstants.isKeyDown(InputConstants.KEY_LSHIFT);
    }

    /** Every key of the mapping's chord held (Keybindery), else its one key. */
    private static boolean held(KeyMapping mapping) {
        return FabricLoader.getInstance().isModLoaded("keybindery") ? KeybinderyAPI.getInstance().isHeld(mapping) : mapping.isDown();
    }
}
