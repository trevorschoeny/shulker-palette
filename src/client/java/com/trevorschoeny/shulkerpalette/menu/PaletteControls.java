package com.trevorschoeny.shulkerpalette.menu;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.config.KeysClient;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.HeldActions;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import com.trevorschoeny.shulkerpalette.palette.Target;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The palette's controls (power, mode, repaint, save, load, clear cheat sheet), for whichever
 * shulker a screen is about: the one in hand (the palette menu) or one open through Open
 * Anywhere (the panel beside its section). One list, drawn two ways: as the menu's labeled
 * rows ({@link #rows}) or as a column of small icon buttons ({@link #icons}).
 *
 * <p>Every control reads the box every frame and asks the server to change it
 * ({@link HeldActions}); the server checks the box is still there and applies the rules.
 */
public final class PaletteControls {
    private PaletteControls() {}

    /**
     * The shulker the controls are for: how to read it now, and the menu slot that names it to
     * the server ({@link Target#HELD} for the hand).
     */
    public record For(Supplier<ItemStack> stack, IntSupplier menuSlot) {
        public static final For HAND = new For(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        }, () -> Target.HELD);

        /** Its palette as the server last sent it; a plain shulker reads as off. */
        public Palette palette() {
            return stack().get().getOrDefault(Palette.COMPONENT, Palette.OFF);
        }

        /** Asks the server to press {@code action} for this shulker. */
        public void act(int action) {
            if (!Palette.isShulker(stack().get()) || !ClientPlayNetworking.canSend(HeldActions.Act.TYPE)) return;
            if (action == PanelActions.SAVE) LibraryClient.expectSave(this);
            ClientPlayNetworking.send(new HeldActions.Act(action, menuSlot().getAsInt()));
        }
    }

    /**
     * One control.
     *
     * @param needsOn   only does something while the palette is on (the menu greys it, the icon column hides it)
     * @param shown     whether it applies at all here (repaint: creative; clear: a cheat sheet)
     * @param lit       drawn lit in the icon column (repaint on)
     */
    record Control(Supplier<Identifier> icon, Supplier<Component> label, Supplier<Component> tooltip,
                   Runnable onClick, boolean needsOn, BooleanSupplier shown, BooleanSupplier lit) {}

    private static final int ICON_SIZE = 14; // the icons are 10 px, drawn 1:1 inside a 2 px inset
    private static final int TINT_ON = 0x6040C040;

    private static List<Control> controls(For box) {
        BooleanSupplier always = () -> true;
        BooleanSupplier never = () -> false;
        return List.of(
                new Control(() -> sprite(box.palette().on() ? "power_on" : "power_off"),
                        () -> Component.translatable(box.palette().on() ? "shulkerpalette.menu.power.on" : "shulkerpalette.menu.power.off"),
                        () -> Component.translatable(box.palette().on() ? "shulkerpalette.panel.power.on" : "shulkerpalette.panel.power.off"),
                        () -> box.act(PanelActions.POWER), false, always, never),
                new Control(() -> sprite("mode_" + box.palette().mode().getSerializedName()),
                        () -> Component.translatable("shulkerpalette.menu.mode",
                                Component.translatable("shulkerpalette.menu.mode." + box.palette().mode().getSerializedName())),
                        () -> Component.translatable("shulkerpalette.panel.mode." + box.palette().mode().getSerializedName(),
                                KeysClient.MODIFIER.getTranslatedKeyMessage()),
                        () -> box.act(PanelActions.MODE), true, always, never),
                new Control(() -> sprite("repaint"),
                        () -> Component.translatable(box.palette().repaint() ? "shulkerpalette.menu.repaint.on" : "shulkerpalette.menu.repaint.off"),
                        () -> Component.translatable(box.palette().repaint() ? "shulkerpalette.panel.repaint.on" : "shulkerpalette.panel.repaint.off"),
                        () -> box.act(PanelActions.REPAINT), true, PaletteControls::creative, () -> box.palette().repaint()),
                new Control(() -> sprite("save"),
                        () -> Component.translatable("shulkerpalette.menu.save"),
                        () -> answerOr(LibraryClient.Answer.SAVE, "shulkerpalette.panel.save"),
                        () -> box.act(PanelActions.SAVE), false, always, never),
                new Control(() -> sprite("load"),
                        () -> Component.translatable("shulkerpalette.menu.load"),
                        () -> answerOr(LibraryClient.Answer.LOAD, "shulkerpalette.panel.load"),
                        () -> LibraryClient.toggleLoad(box), false, always, never),
                new Control(() -> sprite("clear"),
                        () -> Component.translatable("shulkerpalette.menu.clear"),
                        () -> Component.translatable("shulkerpalette.panel.clear"),
                        () -> box.act(PanelActions.CLEAR), false, () -> box.stack().get().has(CheatSheet.COMPONENT), never));
    }

    /** The menu's labeled rows: an off palette's mode and repaint are greyed, not hidden. */
    static List<PanelElement> rows(For box) {
        return controls(box).stream().<PanelElement>map(c -> MenuRow.of(c.icon(), c.label(), c.onClick(),
                b -> b.tooltip(c.tooltip())
                        .disabledWhen(() -> c.needsOn() && !box.palette().on())
                        .visibleWhen(c.shown()))).toList();
    }

    /** A column of small icon buttons: what doesn't apply is hidden, so the rest close up in a vertical Flow. */
    public static List<PanelElement> icons(For box) {
        return controls(box).stream().<PanelElement>map(c -> Button.builder()
                .icon(c.icon())
                .size(ICON_SIZE, ICON_SIZE)
                .tint(() -> c.lit().getAsBoolean() ? TINT_ON : 0)
                .onClick(c.onClick())
                .tooltip(c.tooltip())
                .visibleWhen(() -> c.shown().getAsBoolean() && (!c.needsOn() || box.palette().on()))
                .build()).toList();
    }

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative();
    }

    /** A Save or Load control's tooltip: the answer to the last press while it is fresh, else what it does. */
    private static Component answerOr(LibraryClient.Answer button, String key) {
        Component answer = LibraryClient.answerFor(button);
        return answer != null ? answer : Component.translatable(key);
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, name);
    }
}
