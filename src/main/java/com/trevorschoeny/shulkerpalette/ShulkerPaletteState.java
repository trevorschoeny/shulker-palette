package com.trevorschoeny.shulkerpalette;

import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Static signal fields for Shulker Palette placement interception.
 *
 * Strategy B: we intercept Player.getItemInHand() to return a fake item during
 * the useItemOn() call, so vanilla's entire placement pipeline thinks the player
 * is holding the palette block rather than the shulker box. No hotbar mutation.
 *
 * ── Signal flow ───────────────────────────────────────────────────────────────
 *   1. Client HEAD:  roll a block from the shulker, enqueue a PendingRoll + set clientOverride.
 *   2. Client getItemInHand():  returns clientOverride (for client-side prediction).
 *   3. Server handleUseItemOn HEAD:  dequeue one PendingRoll → set server* fields.
 *   4. Server getItemInHand():  returns serverOverride (for authoritative placement).
 *   5. Server useItemOn RETURN:  clear serverOverride, decrement shulker contents.
 *   6. Client useItemOn RETURN:  clear clientOverride.
 *
 * The pending queue crosses the client→server thread boundary (ConcurrentLinkedQueue).
 * Each client roll enqueues its own record, so rapid clicks never overwrite each other.
 * client* and server* fields are single-thread only.
 */
public final class ShulkerPaletteState {

    private ShulkerPaletteState() {}

    // ── Pending roll record (one per client placement attempt) ────────────────
    /** Bundles the three fields needed to reconstruct a roll on the server side. */
    public record PendingRoll(String overrideItemId, int shulkerInvSlot, int internalSlot) {}

    // ── Client-side override (render thread only) ─────────────────────────────
    /** The rolled item to return from client Player.getItemInHand(). Null = no override. */
    public static ItemStack clientOverride = null;

    // ── Client → Server signal (thread-safe FIFO queue) ──────────────────────
    /** Queue of pending rolls — one entry per client useItemOn, consumed in order by server. */
    public static final ConcurrentLinkedQueue<PendingRoll> pendingRolls = new ConcurrentLinkedQueue<>();

    // ── Rendering (render thread only) ──────────────────────────────────────
    /** Openness level for GUI rendering. */
    public static final float RENDER_OPENNESS_GUI = 5.0f;
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
