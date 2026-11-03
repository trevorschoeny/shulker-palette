package com.trevorschoeny.shulkerpalette.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.menu.PaletteScreen;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The palette key: one key (V by default, a Keybindery chord if the player sets one) that does
 * everything in Shulker Palette. With a shulker in hand, in the world:
 * <ul>
 *   <li><b>tap</b> (released quickly, no scroll): opens the palette menu;</li>
 *   <li><b>hold</b>: shows the box in the HUD ({@code PalettePopup}), before any scroll;</li>
 *   <li><b>hold and scroll</b>: moves the material; with Shift also held, the shape (creative).</li>
 * </ul>
 * In an inventory screen, pressing it over a slot holding a shulker box opens the palette menu
 * for that box ({@code HoverOpen}).
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
     * How long the key is held before it counts as a hold: the HUD view shows, and letting go
     * no longer opens the menu. ponytail: a fixed guess; make it a setting if taps feel cut off.
     */
    public static final long HOLD_MILLIS = 300;

    // The current press: when it went down (0 while up), and whether a scroll used it.
    private static long downSince;
    private static boolean scrolled;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(MODIFIER);
        ClientTickEvents.END_CLIENT_TICK.register(KeysClient::tap);
    }

    /**
     * The palette key closed the menu while held: the release that follows ends that press,
     * so it isn't a tap and doesn't open the menu again.
     */
    public static void swallowTap() {
        if (downSince == 0) downSince = System.currentTimeMillis();
        scrolled = true;
    }

    /** A scroll moved an axis while the palette key was held, so its release isn't a tap. */
    public static void markScrolled() {
        scrolled = true;
    }

    /**
     * On release of the palette key, open the menu if it was a tap: no scroll, and let go before
     * it became a hold. A tap shorter than a tick is down and up again before this runs, so a
     * press counted by vanilla (its click count) counts as down too.
     */
    private static void tap(Minecraft mc) {
        boolean pressed = false;
        while (MODIFIER.consumeClick()) pressed = true;
        boolean down = mc.gui.screen() == null && KeybinderyAPI.getInstance().isHeld(MODIFIER);
        long now = System.currentTimeMillis();
        if ((down || pressed) && downSince == 0) downSince = now;
        if (!down && downSince != 0) {
            boolean tapped = !scrolled && now - downSince < HOLD_MILLIS;
            if (tapped && mc.gui.screen() == null && mc.player != null && Palette.isShulker(mc.player.getMainHandItem())) {
                mc.gui.setScreen(new PaletteScreen());
            }
            downSince = 0;
            scrolled = false; // reset on release, so a scroll anywhere in the press counts
        }
    }

    /** Is the palette key down (its whole chord)? Not while a screen is open: no gestures happen there. */
    public static boolean modifierDown() {
        return Minecraft.getInstance().gui.screen() == null && KeybinderyAPI.getInstance().isHeld(MODIFIER);
    }

    /** Has the palette key been down long enough to count as a hold (or been used to scroll)? */
    public static boolean holding() {
        return modifierDown() && downSince != 0 && (scrolled || System.currentTimeMillis() - downSince >= HOLD_MILLIS);
    }

    /** The palette key with Shift: the shape axis (creative). Shift is fixed, not a key of its own. */
    public static boolean shapeDown() {
        return modifierDown() && (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
    }
}
