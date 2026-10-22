package com.trevorschoeny.shulkerpalette.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.Keys;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import com.trevorschoeny.shulkerpalette.popup.PalettePopup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The palette keys, as ordinary vanilla key binds (Controls, and Keybindery's chords when it
 * is installed), also shown on the mod's settings screen.
 *
 * <p>The modifier is held, not pressed: the client reports it to the server each time it
 * changes ({@link Keys}), at the start of the tick, so a click later in the same tick is
 * already judged with it.
 */
public final class KeysClient {
    private KeysClient() {}

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "keys"));

    /** Held with scroll or right-click for the palette gestures. Left Option (Alt) by default. */
    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.shulkerpalette.modifier", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY);
    /**
     * Held with scroll to move the palette's shape (creative). Meant to default to the chord
     * Left Alt + Left Shift, which needs Keybindery's chord defaults; until those land it ships
     * unbound, and an unbound shape key means Palette modifier + Left Shift.
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

    /** What the server was last told. */
    private static boolean reported;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(MODIFIER);
        KeyMappingHelper.registerKeyMapping(SHAPE);
        KeyMappingHelper.registerKeyMapping(PREVIOUS_SHAPE);
        KeyMappingHelper.registerKeyMapping(NEXT_SHAPE);
        KeyMappingHelper.registerKeyMapping(POWER);
        KeyMappingHelper.registerKeyMapping(REPAINT);

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player == null || !ClientPlayNetworking.canSend(Keys.Modifier.TYPE)) return;
            boolean held = modifierDown();
            if (held != reported) {
                reported = held;
                Keys.setClientModifier(held);
                ClientPlayNetworking.send(new Keys.Modifier(held));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (PREVIOUS_SHAPE.consumeClick()) stepShape(mc, -1);
            while (NEXT_SHAPE.consumeClick()) stepShape(mc, 1);
            while (POWER.consumeClick()) toggle(mc, PanelActions.POWER);
            while (REPAINT.consumeClick()) toggle(mc, PanelActions.REPAINT);
        });
        // A new connection starts with the server thinking the modifier is up.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            reported = false;
            Keys.setClientModifier(false);
        });
    }

    /** Asks the server to press a panel button for the shulker in hand; it checks the rules. */
    private static void toggle(Minecraft mc, int action) {
        if (mc.player == null || !Palette.isShulker(mc.player.getMainHandItem())
                || !ClientPlayNetworking.canSend(Keys.Toggle.TYPE)) return;
        ClientPlayNetworking.send(new Keys.Toggle(action));
    }

    /** The shape keys: one step through the box's shapes, as a shape scroll would. */
    private static void stepShape(Minecraft mc, int steps) {
        if (mc.player == null || !mc.player.isCreative() || Palette.activeOn(mc.player.getMainHandItem()) == null
                || !ClientPlayNetworking.canSend(Scroll.Payload.TYPE)) return;
        ClientPlayNetworking.send(new Scroll.Payload(steps, true));
        PalettePopup.steppedShape(steps);
    }

    /** Is the material modifier down (its whole chord, with Keybindery)? Not while a screen is open: no gestures happen there. */
    public static boolean modifierDown() {
        return Minecraft.getInstance().gui.screen() == null && held(MODIFIER);
    }

    /** Is the shape modifier down? Checked before the material one, since its chord includes it by default. */
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
