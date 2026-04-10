package com.trevorschoeny.shulkerpalette;

import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static signal fields for Shulker Palette placement interception.
 *
 * Strategy B: we intercept Player.getItemInHand() to return a fake item during
 * the useItemOn() call, so vanilla's entire placement pipeline thinks the player
 * is holding the palette block rather than the shulker box. No hotbar mutation.
 *
 * ── Signal flow ───────────────────────────────────────────────────────────────
 *   1. Client HEAD:  roll a block from the shulker, store the PendingRoll + set clientOverride.
 *   2. Client getItemInHand():  returns clientOverride (for client-side prediction).
 *   3. Server handleUseItemOn HEAD:  get-and-clear the PendingRoll → set server* fields.
 *   4. Server getItemInHand():  returns serverOverride (for authoritative placement).
 *   5. Server useItemOn RETURN:  clear serverOverride, decrement shulker contents.
 *   6. Client useItemOn RETURN:  clear clientOverride.
 *
 * ── Why AtomicReference instead of a FIFO queue ────────────────────────────────
 * Earlier versions used ConcurrentLinkedQueue, assuming each client useItemOn
 * would always reach the server. But other mods (harvest-with-ease, doubledoors,
 * rightclickharvest, etc.) can cancel useItemOn via Fabric's UseBlockCallback or
 * similar events. When that happens, the client's HEAD inject still enqueues a
 * roll but the packet never arrives at the server — leaving a stale entry. On
 * the next placement, the server would dequeue that stale entry and apply it
 * to an unrelated placement, causing "every block becomes a deepslate wall" or
 * similar bugs.
 *
 * Switching to a single-slot AtomicReference makes the system self-healing:
 * each new client roll OVERWRITES any unconsumed previous roll. Stale state can
 * never accumulate. The worst case is one wrong placement, immediately corrected
 * by the next click.
 */
public final class ShulkerPaletteState {

    private ShulkerPaletteState() {}

    // ── Pending roll record (one per client placement attempt) ────────────────
    /** Bundles the three fields needed to reconstruct a roll on the server side. */
    public record PendingRoll(String overrideItemId, int shulkerInvSlot, int internalSlot) {}

    // ── Client-side override (render thread only) ─────────────────────────────
    /** The rolled item to return from client Player.getItemInHand(). Null = no override. */
    public static ItemStack clientOverride = null;

    // ── Client → Server signal (single-slot atomic reference) ────────────────
    /**
     * The most recent pending roll. Client HEAD sets it; server HEAD get-and-clears it.
     * Intentionally single-slot: if the previous roll wasn't consumed (e.g., because
     * another mod cancelled the useItemOn packet), it gets overwritten here rather
     * than accumulating as stale state.
     */
    public static final AtomicReference<PendingRoll> pendingRoll = new AtomicReference<>();

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
