package com.trevorschoeny.shulkerpalette.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.menu.PaletteScreen;
import com.trevorschoeny.shulkerpalette.palette.HeldActions;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import com.trevorschoeny.shulkerpalette.palette.Target;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
 *   <li><b>hold and scroll</b>: moves the material; with Shift also held, the variant (creative);</li>
 *   <li><b>hold, and double-tap Shift</b> (no scroll): switches the variant row between general and
 *       specific (creative).</li>
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
    // When Shift was last pressed during this press (0: none waiting for a second press).
    private static long lastShiftPress;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(MODIFIER);
        ClientTickEvents.END_CLIENT_TICK.register(KeysClient::tap);
    }

    /** A scroll moved an axis while the palette key was held, so its release isn't a tap. */
    public static void markScrolled() {
        scrolled = true;
    }

    /** A variant scroll: the palette key's release isn't a tap, and no Shift press before it pairs with one after. */
    public static void markShapeScrolled() {
        scrolled = true;
        lastShiftPress = 0;
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
        shiftTap(mc, down && mc.gui.screen() == null);
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
     * A double tap of Shift while the palette key is held in the world (two presses within
     * {@link #TAP_MILLIS}, no variant scroll between): switches general and specific. The server
     * checks creative and an on palette. Any Shift during the press spends it, so letting go of
     * the palette key afterwards opens nothing.
     *
     * <p>Counted from presses as they happen (the sneak key's click count), not from polling
     * whether Shift is down each tick: a quick double tap fits between two ticks, and polling
     * would see one long press. Shift is the sneak key's default, so a player who rebinds sneak
     * double-taps that instead.
     */
    private static void shiftTap(Minecraft mc, boolean keyDown) {
        int presses = 0;
        while (mc.options.keyShift.consumeClick()) presses++; // drained every tick, so old presses never count
        if (!keyDown) {
            lastShiftPress = 0; // a double tap lives inside one press of the palette key
            return;
        }
        // Shift held from before the palette key went down spends the press too (V + Shift for a variant).
        if (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) scrolled = true;
        long now = System.currentTimeMillis();
        for (int i = 0; i < presses; i++) {
            scrolled = true;
            if (lastShiftPress != 0 && now - lastShiftPress < TAP_MILLIS) {
                lastShiftPress = 0;
                if (mc.player != null && mc.player.isCreative() && Palette.activeOn(mc.player.getMainHandItem()) != null
                        && ClientPlayNetworking.canSend(HeldActions.Act.TYPE)) {
                    ClientPlayNetworking.send(new HeldActions.Act(PanelActions.VARIANT_MODE, Target.HELD));
                }
            } else {
                lastShiftPress = now;
            }
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
