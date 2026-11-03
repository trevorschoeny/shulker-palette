package com.trevorschoeny.shulkerpalette.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.menu.PaletteScreen;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * The palette key: one key (V by default, a Keybindery chord if the player sets one) that does
 * everything in Shulker Palette. With a shulker in hand, in the world:
 * <ul>
 *   <li><b>tap</b> (released quickly, no scroll): opens the palette menu;</li>
 *   <li><b>hold</b>: shows the box in the HUD ({@code PalettePopup}), the instant it goes down;</li>
 *   <li><b>hold and scroll</b>: moves the material; with Shift also held, the shape (creative).</li>
 * </ul>
 * In an inventory screen, pressing it over a slot holding a shulker box opens the palette menu
 * for that box ({@code HoverOpen}).
 *
 * <p>While the palette menu is open, the key keeps working the same way: holding it shows the
 * HUD view again, over the menu, and a tap (no scroll) closes the menu instead of opening it.
 * Both the opening and the closing happen on release, not on press, so a hold never closes a
 * menu that just opened, and the HUD view is free to show the whole time either way.
 */
public final class KeysClient {
    private KeysClient() {}

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "keys"));

    /**
     * The palette key, V by default. Its id is new with V: the old Option key's id is still saved
     * in players' options with Left Alt, and a saved bind beats a new default.
     */
    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.shulkerpalette.palette", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);

    /**
     * How short a press has to be, with no scroll, to act on release (open the menu, or close
     * it): a tap. Longer than this and letting go does nothing by itself, since the HUD view
     * already showed everything a hold does; this only gates the open/close action, never the
     * HUD view, which shows the instant the key goes down ({@link #heldForPopup}).
     */
    private static final long TAP_MILLIS = 300;

    // The current press: when it went down (0 while up), and whether a scroll used it.
    private static long downSince;
    private static boolean scrolled;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(MODIFIER);
        ClientTickEvents.END_CLIENT_TICK.register(KeysClient::tap);
    }

    /** A scroll moved an axis while the palette key was held, so its release isn't a tap. */
    public static void markScrolled() {
        scrolled = true;
    }

    /**
     * Something else already acted on this press of the palette key (opened the menu by a hover
     * press, for instance): its eventual release must not act again. Marks the press as tracked
     * and already spent, the same way a scroll does.
     */
    public static void swallowRelease() {
        if (downSince == 0) downSince = System.currentTimeMillis();
        scrolled = true;
    }

    /**
     * On release of the palette key, act if it was a short press (a tap): no scroll, let go
     * within {@link #TAP_MILLIS}, in a context the key tracks in. No screen open and a shulker
     * in hand: opens the menu. The palette menu itself open: closes it. Held longer than that,
     * with no scroll, letting go does nothing; the HUD view already showed the box while held.
     *
     * <p>Read from {@code isHeld}, raw key state, every tick, not vanilla's click count: vanilla
     * only counts clicks for a registered mapping while no screen is open, so a press over the
     * palette menu would never be seen that way. Raw state works the same either way, and a real
     * press always spans at least one tick, so this never misses one.
     */
    private static void tap(Minecraft mc) {
        boolean down = trackable(mc) && KeybinderyAPI.getInstance().isHeld(MODIFIER);
        if (down && downSince == 0) downSince = System.currentTimeMillis();
        if (!down && downSince != 0) {
            boolean tapped = !scrolled && System.currentTimeMillis() - downSince < TAP_MILLIS;
            if (tapped) {
                if (mc.gui.screen() instanceof PaletteScreen menu) {
                    menu.onClose();
                } else if (mc.gui.screen() == null && mc.player != null && Palette.isShulker(mc.player.getMainHandItem())) {
                    mc.gui.setScreen(new PaletteScreen());
                }
            }
            downSince = 0;
            scrolled = false; // reset on release, so a scroll anywhere in the press counts
        }
    }

    /**
     * Is this a screen the palette key still works in: the world (no screen), or the palette
     * menu itself, as long as a library dialog on it isn't taking typed text (a V typed into a
     * palette's name is a letter, not the key).
     */
    private static boolean trackable(Minecraft mc) {
        Screen screen = mc.gui.screen();
        return screen == null || (screen instanceof PaletteScreen && !LibraryClient.dialogUp());
    }

    /** Is the palette key down (its whole chord)? Not while a screen is open: no gestures happen there. */
    public static boolean modifierDown() {
        return Minecraft.getInstance().gui.screen() == null && KeybinderyAPI.getInstance().isHeld(MODIFIER);
    }

    /**
     * Is the palette key down right now, in a context it tracks in (the world, or the palette
     * menu itself)? Drives the HUD view's visibility: it shows the instant this is true, with no
     * delay, since showing it costs nothing even for what turns out to be a quick tap.
     */
    public static boolean heldForPopup() {
        return trackable(Minecraft.getInstance()) && downSince != 0 && KeybinderyAPI.getInstance().isHeld(MODIFIER);
    }
    /** The palette key with Shift: the shape axis (creative). Shift is fixed, not a key of its own. */
    public static boolean shapeDown() {
        return modifierDown() && (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
    }
}
