package com.trevorschoeny.shulkerpalette.library;

import com.trevlar.menukit.api.dialog.ConfirmDialog;
import com.trevlar.menukit.api.dialog.EditDialog;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.ListView;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.TextField;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.TextRole;
import com.trevlar.menukit.api.element.Toggle;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevorschoeny.shulkerpalette.mixin.ScreenPosAccessor;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.PaletteTransfer;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The library at an open box: the panel's Save and Load.
 *
 * <ul>
 *   <li><b>Save</b>: the panel asks the server for the box's palette; when it arrives, a
 *       dialog asks for a name and a shelf, and the palette is filed. A name already on the
 *       shelf is only replaced after a second question.</li>
 *   <li><b>Load</b>: a list beside the panel, This world then All worlds. A click sends the
 *       palette to the server, which fills the box (creative) or sets its cheat sheet
 *       (survival). Files that don't parse are listed with their problem and can't be picked.</li>
 * </ul>
 * The answer to each shows as its button's tooltip for a moment (the action bar is hidden
 * behind the open screen).
 */
public final class LibraryClient {
    private LibraryClient() {}

    /** Which button an answer belongs to. */
    public enum Answer { SAVE, LOAD }

    private static final long SHOW_MILLIS = 2500;
    private static final int LIST_WIDTH = 130;
    // The control panel's width (14 px buttons in a padded panel), the room between panels,
    // and a panel's own border and padding on both sides.
    private static final int CONTROLS_WIDTH = 22;
    private static final int GAP = 4;
    private static final int PANEL_PADDING = 10;
    private static final int BOX_WIDTH = 176; // vanilla's shulker screen
    private static final int LIST_HEIGHT = 120;
    private static final int STRIP = 4;

    private static @Nullable Component answer;
    private static Answer answerFor = Answer.SAVE;
    private static long answeredAt;

    // Save: the palette the server sent, waiting for a name. Null while no dialog is open.
    private static @Nullable SavedPalette pending;
    private static String draftName = "";
    private static Library.Shelf draftShelf = Library.Shelf.WORLD;
    private static boolean confirmingReplace;

    // Load: the menu the list was opened for (so it closes with its screen), and what it shows.
    private static int loadMenu = -1;
    private static List<Item> loadItems = List.of();

    /** A Load list line: a shelf heading, or a palette. */
    private sealed interface Item permits Heading, Row {}
    private record Heading(Library.Shelf shelf) implements Item {}
    private record Row(Library.Entry entry) implements Item {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PaletteTransfer.Saved.TYPE, (payload, context) -> {
            if (!(context.client().gui.screen() instanceof ShulkerBoxScreen screen)) return;
            if (payload.palette().slots().isEmpty()) {
                answer(Answer.SAVE, Component.translatable("shulkerpalette.library.empty_box"));
                return;
            }
            pending = payload.palette();
            draftShelf = Library.folder(Library.Shelf.WORLD) != null ? Library.Shelf.WORLD : Library.Shelf.GLOBAL;
            // A renamed box suggests its own name; a plain one the next free "Palette N".
            boolean named = !screen.getTitle().equals(Component.translatable("container.shulkerBox"));
            draftName = named ? screen.getTitle().getString() : Library.freshName(draftShelf);
            confirmingReplace = false;
        });

        Panel saveDialog = EditDialog.builder()
                .id(ShulkerPalette.MOD_ID + ":save")
                .title(Component.translatable("shulkerpalette.library.save.title"))
                .content(Flow.builder().vertical().gap(4)
                        .add(TextField.builder().size(160, 20).maxLength(64)
                                .state(() -> draftName, v -> draftName = v)
                                .onSubmit(v -> trySave())
                                .build())
                        .add(Toggle.builder().size(160, 20)
                                .label(() -> Component.translatable(draftShelf == Library.Shelf.WORLD
                                        ? "shulkerpalette.library.save.world" : "shulkerpalette.library.save.global"))
                                .state(() -> draftShelf == Library.Shelf.WORLD,
                                        world -> draftShelf = world && Library.folder(Library.Shelf.WORLD) != null
                                                ? Library.Shelf.WORLD : Library.Shelf.GLOBAL)
                                .build())
                        .build())
                .saveLabel(Component.translatable("shulkerpalette.library.save"))
                .onSave(LibraryClient::trySave)
                .onCancel(() -> pending = null)
                .build()
                .visibleWhen(() -> pending != null && !confirmingReplace);

        Panel replaceDialog = ConfirmDialog.builder()
                .id(ShulkerPalette.MOD_ID + ":replace")
                .title(() -> Component.translatable("shulkerpalette.library.replace.title", draftName))
                .body(Component.translatable("shulkerpalette.library.replace.body"))
                .confirmLabel(Component.translatable("shulkerpalette.library.replace"))
                .onConfirm(LibraryClient::save)
                .onCancel(() -> confirmingReplace = false)
                .build()
                .visibleWhen(() -> pending != null && confirmingReplace);

        // A popover just left of the control panel, level with the box's top. Sharing the
        // panel's region would stack the two and push the panel out of place.
        Panel loadList = Panel.builder(ShulkerPalette.MOD_ID + ":load")
                .popover()
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(LibraryClient::loadListOrigin)))
                .elements(List.of(Flow.builder().vertical().gap(4)
                        .add(TextLabel.builder().role(TextRole.HEADING).text(Component.translatable("shulkerpalette.library.load.title")).build())
                        .add(ScrollContainer.builder().size(LIST_WIDTH, LIST_HEIGHT).content(List.of(loadListView())).build())
                        .add(Button.builder().label(Component.translatable("shulkerpalette.library.manage")).size(LIST_WIDTH, 16)
                                .onClick(() -> Minecraft.getInstance().gui.setScreen(new LibraryScreen(null)))
                                .build())
                        .build()))
                .build()
                .visibleWhen(() -> openMenuId() >= 0 && openMenuId() == loadMenu);

        new ScreenPanelAdapter(loadList).on(ShulkerBoxScreen.class);
        new ScreenPanelAdapter(saveDialog).on(ShulkerBoxScreen.class);
        new ScreenPanelAdapter(replaceDialog).on(ShulkerBoxScreen.class);
    }

    private static PanelElement loadListView() {
        return ListView.<Item>builder()
                .items(() -> loadItems)
                .key(item -> switch (item) {
                    case Heading h -> "heading/" + h.shelf();
                    case Row r -> r.entry().id();
                })
                .row(LibraryClient::loadRow)
                // A click on a palette's row picks it; the list keeps no selection.
                .selection(() -> null, LibraryClient::pick)
                .gap(1)
                .empty(TextLabel.builder().role(TextRole.MUTED).text(Component.translatable("shulkerpalette.library.load.none")).build())
                .build();
    }

    private static PanelElement loadRow(Item item) {
        return switch (item) {
            case Heading h -> TextLabel.builder().role(TextRole.MUTED).text(Component.translatable(shelfKey(h.shelf()))).build();
            case Row r -> LibraryRows.start(r.entry(), STRIP).build();
        };
    }

    /** The panel's Load button: opens the list for this box, or closes it. */
    public static void toggleLoad() {
        int menu = openMenuId();
        if (menu < 0) return;
        if (loadMenu == menu) {
            loadMenu = -1;
            return;
        }
        List<Item> items = new ArrayList<>();
        for (Library.Shelf shelf : Library.Shelf.values()) {
            List<Library.Entry> entries = Library.list(shelf);
            if (entries.isEmpty()) continue;
            items.add(new Heading(shelf));
            for (Library.Entry entry : entries) items.add(new Row(entry));
        }
        loadItems = items;
        loadMenu = menu;
    }

    /** A click in the Load list: send that palette to the open box. */
    private static void pick(@Nullable Object key) {
        if (key == null || !(Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen)) return;
        for (Item item : loadItems) {
            if (!(item instanceof Row(Library.Entry entry)) || !entry.id().equals(key)) continue;
            entry.palette().ifSuccess(palette -> {
                ClientPlayNetworking.send(new PaletteTransfer.Load(screen.getMenu().containerId, palette));
                answer(Answer.LOAD, Component.translatable("shulkerpalette.library.loaded", entry.name()));
                loadMenu = -1;
            });
        }
    }

    /** Save pressed in the dialog: ask before replacing a palette of the same name. */
    private static void trySave() {
        if (draftName.isBlank()) draftName = Library.freshName(draftShelf);
        if (Library.exists(draftShelf, draftName)) confirmingReplace = true;
        else save();
    }

    private static void save() {
        SavedPalette palette = pending;
        pending = null;
        confirmingReplace = false;
        if (palette == null) return;
        boolean saved = Library.save(draftShelf, draftName.strip(), palette);
        answer(Answer.SAVE, saved
                ? Component.translatable("shulkerpalette.library.saved", Component.translatable(shelfKey(draftShelf)))
                : Component.translatable("shulkerpalette.library.save_failed"));
    }

    static String shelfKey(Library.Shelf shelf) {
        return shelf == Library.Shelf.WORLD ? "shulkerpalette.library.shelf.world" : "shulkerpalette.library.shelf.global";
    }

    /**
     * Left of the control panel when there's room; else right of the box (item-list mods may
     * be there, but a narrow screen has none); else at the screen's left edge over the box.
     */
    private static ScreenOrigin loadListOrigin() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ScreenPosAccessor screen)) return new ScreenOrigin(0, 0);
        int width = LIST_WIDTH + PANEL_PADDING;
        int left = screen.shulkerpalette$leftPos() - CONTROLS_WIDTH - GAP - width;
        int right = screen.shulkerpalette$leftPos() + BOX_WIDTH + GAP;
        int x = left >= GAP ? left : right + width <= mc.getWindow().getGuiScaledWidth() - GAP ? right : GAP;
        return new ScreenOrigin(x, screen.shulkerpalette$topPos());
    }

    private static int openMenuId() {
        return Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen ? screen.getMenu().containerId : -1;
    }

    /** The answer to show as this button's tooltip, while it is fresh; else null. */
    public static @Nullable Component answerFor(Answer button) {
        boolean fresh = answer != null && answerFor == button && System.currentTimeMillis() - answeredAt < SHOW_MILLIS;
        return fresh ? answer : null;
    }

    private static void answer(Answer button, Component message) {
        answer = message;
        answerFor = button;
        answeredAt = System.currentTimeMillis();
    }
}
