package com.trevorschoeny.shulkerpalette.palette;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;

/**
 * What the control panel's buttons do to a palette. The client sends a button id through
 * vanilla's menu-button packet (the one the enchanting table uses), and the server applies
 * it here, so every change is checked where the palette lives.
 *
 * <p>The palette's state reaches the panel the other way, packed into one menu data slot
 * ({@link #pack}), since a placed box's components never reach the client.
 */
public final class PanelActions {
    private PanelActions() {}

    public static final int POWER = 0;
    public static final int MODE = 1;
    public static final int REPAINT = 2;
    /** Save: answered with the box's palette for the clipboard ({@link Clipboard}), changes nothing. */
    public static final int SAVE = 3;

    /** The palette after button {@code id}, or the same palette if the press isn't allowed. */
    public static Palette apply(Palette palette, int id, Player player) {
        Palette changed = switch (id) {
            case POWER -> with(palette, !palette.on(), palette.mode(), palette.repaint());
            case MODE -> palette.on() ? with(palette, true, palette.mode().next(), palette.repaint()) : palette;
            // Repaint is creative only in v1; the panel hides it in survival, and the server agrees.
            case REPAINT -> palette.on() && player.isCreative() ? with(palette, true, palette.mode(), !palette.repaint()) : palette;
            default -> palette;
        };
        return changed;
    }

    /**
     * A changed palette has no next block yet (fingerprint 0), so the first time it is in a
     * player's inventory the server chooses one for the new settings ({@link Upkeep}).
     */
    private static Palette with(Palette p, boolean on, Mode mode, boolean repaint) {
        return new Palette(on, mode, repaint, p.rows(), p.selection(), Optional.empty(), 0);
    }

    // The packed form: bit 0 on, bits 1-2 mode, bit 3 repaint, bits 4-8 rows, bit 9 "this menu
    // has a palette", bit 10 "opened from the hand". Menu data travels as a short, so it stays
    // under 15 bits.
    private static final int PRESENT = 1 << 9;
    private static final int HELD = 1 << 10;

    public static int pack(Palette p, boolean held) {
        return PRESENT | (held ? HELD : 0) | (p.on() ? 1 : 0) | p.mode().ordinal() << 1 | (p.repaint() ? 1 : 0) << 3 | p.rows() << 4;
    }

    /** Was the menu with this packed palette opened over the shulker in hand? */
    public static boolean held(int packed) {
        return (packed & HELD) != 0;
    }

    /** The panel's view of a packed palette; empty when the menu has none (a container another mod made). */
    public static Optional<Palette> unpack(int packed) {
        if ((packed & PRESENT) == 0) return Optional.empty();
        return Optional.of(new Palette((packed & 1) != 0, Mode.values()[packed >> 1 & 3], (packed >> 3 & 1) != 0,
                packed >> 4 & 31, 0, Optional.empty(), 0));
    }
}
