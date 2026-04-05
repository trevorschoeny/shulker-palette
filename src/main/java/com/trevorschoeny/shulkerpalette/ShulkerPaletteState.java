package com.trevorschoeny.shulkerpalette;

import net.minecraft.world.item.ItemStack;

/**
 * Static signal fields for Shulker Palette placement interception.
 *
 * Strategy B: we intercept Player.getItemInHand() to return a fake item during
 * the useItemOn() call, so vanilla's entire placement pipeline thinks the player
 * is holding the palette block rather than the shulker box. No hotbar mutation.
 *
 * ── Signal flow ───────────────────────────────────────────────────────────────
 *   1. Client HEAD:  roll a block from the shulker, set clientOverride + pending* fields.
 *   2. Client getItemInHand():  returns clientOverride (for client-side prediction).
 *   3. Server handleUseItemOn HEAD:  transfer pending* → server* fields.
 *   4. Server getItemInHand():  returns serverOverride (for authoritative placement).
 *   5. Server useItemOn RETURN:  clear serverOverride, decrement shulker contents.
 *   6. Client useItemOn RETURN:  clear clientOverride.
 *
 * pending* fields cross the client→server thread boundary (volatile).
 * client* and server* fields are single-thread only.
 */
public final class ShulkerPaletteState {

    private ShulkerPaletteState() {}

    // ── Client-side override (render thread only) ─────────────────────────────
    /** The rolled item to return from client Player.getItemInHand(). Null = no override. */
    public static ItemStack clientOverride = null;

    // ── Client → Server signal (cross-thread, volatile) ───────────────────────
    /** Item ID to construct the override on the server side. Null = nothing pending. */
    public static volatile String pendingOverrideItemId = null;
    /** Player inventory slot holding the shulker box. */
    public static volatile int pendingShulkerInvSlot = -1;
    /** Slot index (0–26) inside the shulker whose item was rolled. */
    public static volatile int pendingInternalSlot = -1;

    // ── Rendering (render thread only) ──────────────────────────────────────
    /** Openness level for GUI rendering. */
    public static final float RENDER_OPENNESS_GUI = 2.0f;
    /** Openness level for in-game rendering. */
    public static final float RENDER_OPENNESS_INGAME = 1.75f;
    /** Scale for the representative items in GUI (inventory/hotbar). */
    public static final float INNER_ITEM_SCALE_GUI = 0.33f;
    /** Scale for the representative items in-game (held item, etc.). */
    public static final float INNER_ITEM_SCALE_INGAME = 0.5f;

    // ── Server-side state (server main thread only) ───────────────────────────
    /** The rolled item to return from server Player.getItemInHand(). Null = no override. */
    public static ItemStack serverOverride = null;
    /** Player inventory slot holding the shulker box (for decrement). */
    public static int serverShulkerInvSlot = -1;
    /** Internal shulker slot (for decrement). */
    public static int serverInternalSlot = -1;
}
