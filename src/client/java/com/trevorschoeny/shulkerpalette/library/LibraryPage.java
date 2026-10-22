package com.trevorschoeny.shulkerpalette.library;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.Icon;
import com.trevlar.menukit.api.element.ListView;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.Tabs;
import com.trevlar.menukit.api.element.TextField;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.TextRole;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.Blaze3D;
import org.jspecify.annotations.Nullable;

/**
 * The palette library page: two tabs, This world and All worlds, each a live list of its
 * saved palettes. A selected row offers Rename, Move to the other tab and Copy to clipboard;
 * the list's own remove button deletes, after asking. Above the list, Paste from clipboard
 * files a palette from text and Open folder shows the files.
 *
 * <p>No loading here: a palette goes into a box only from the box's own Load list.
 *
 * <p>The page is one set of MenuKit elements laid out from the room it is given, so it can be
 * hosted anywhere: today in {@link LibraryScreen}, and as a Mod Hub custom page once Mod Hub
 * supports them (a {@code page("library", label, LibraryPage::build)} registration).
 */
public final class LibraryPage {
    private LibraryPage() {}

    private static final int STRIP = 8;

    // The page's state. Static, like every MenuKit page's: one library page is open at a time.
    private static String tab = "world";
    private static final Map<Library.Shelf, List<Library.Entry>> entries = new EnumMap<>(Library.Shelf.class);
    private static @Nullable String selected;
    private static @Nullable String renaming;
    private static String draft = "";
    private static Component status = Component.empty();

    /** Builds the page, reading both shelves fresh. Run each time the page is shown. */
    public static List<PanelElement> build() {
        refresh();
        status = Component.empty();
        selected = null;
        renaming = null;
        Tabs tabs = Tabs.builder()
                .mode(Tabs.Mode.WRAP)
                .state(() -> tab, id -> { tab = id; selected = null; renaming = null; })
                .tab("world", Component.translatable("shulkerpalette.library.shelf.world"), shelfBody(Library.Shelf.WORLD))
                .tab("global", Component.translatable("shulkerpalette.library.shelf.global"), shelfBody(Library.Shelf.GLOBAL))
                .build();
        return List.of(tabs);
    }

    private static void refresh() {
        for (Library.Shelf shelf : Library.Shelf.values()) entries.put(shelf, Library.list(shelf));
    }

    /** One tab: its actions, a status line, and the list filling the rest. */
    private static List<PanelElement> shelfBody(Library.Shelf shelf) {
        boolean hasFolder = Library.folder(shelf) != null;
        if (!hasFolder) {
            return List.of(TextLabel.builder().role(TextRole.MUTED)
                    .text(Component.translatable("shulkerpalette.library.no_world")).build());
        }
        Flow actions = Flow.builder().gap(4)
                .add(Button.builder().label(Component.translatable("shulkerpalette.library.paste")).size(0, 20)
                        .tooltip(Component.translatable("shulkerpalette.library.paste.tooltip"))
                        .onClick(() -> paste(shelf)).build())
                .add(Button.builder().label(Component.translatable("shulkerpalette.library.folder")).size(0, 20)
                        .onClick(() -> openFolder(shelf)).build())
                .add(Flow.spacer())
                .add(TextLabel.builder().role(TextRole.MUTED).text(() -> status).build())
                .build();
        ListView<Library.Entry> list = ListView.<Library.Entry>builder()
                .items(() -> entries.getOrDefault(shelf, List.of()))
                .key(Library.Entry::id)
                .row(LibraryPage::row)
                .selection(() -> selected, id -> { selected = id; if (id == null || !id.equals(renaming)) renaming = null; })
                .remove(Component.translatable("shulkerpalette.library.delete"), id -> {
                    find(id).ifPresent(Library::delete);
                    refresh();
                })
                .confirmRemove(e -> Component.translatable("shulkerpalette.library.delete.confirm", e.name()))
                .gap(2)
                .empty(TextLabel.builder().role(TextRole.MUTED).text(Component.translatable("shulkerpalette.library.none")).build())
                .build();
        ScrollContainer box = ScrollContainer.builder().fitWidth().fillHeight().content(List.of(list)).build();
        return List.of(Flow.builder().vertical().gap(6).add(actions).add(box).build());
    }

    /** One palette: strip and name (or the rename field), its mode and rows, and while selected its actions. */
    private static PanelElement row(Library.Entry first) {
        String id = first.id();
        TextField field = TextField.builder().size(120, 18).maxLength(64)
                .state(() -> draft, v -> draft = v)
                .onSubmit(v -> {
                    find(id).ifPresent(e -> say(Library.rename(e, v.strip()) ? "renamed" : "name_taken"));
                    renaming = null;
                    refresh();
                })
                .onCancel(() -> renaming = null)
                .visibleWhen(() -> id.equals(renaming))
                .build();
        Flow.Builder row = Flow.builder().boxed().gap(4);
        // The strip and name give way to the rename field while renaming.
        row.add(LibraryRows.start(first, STRIP).visibleWhen(() -> !id.equals(renaming)).build()).add(field).add(Flow.spacer());
        first.palette().result().ifPresent(p -> row
                .add(Icon.builder().sprite(Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, "mode_" + p.mode().getSerializedName())).size(10, 10).build())
                .add(TextLabel.builder().role(TextRole.MUTED).text(Component.translatable("shulkerpalette.library.rows", p.rows())).build()));
        boolean ok = first.palette().isSuccess();
        row.add(action("rename", () -> id.equals(selected) && ok && !id.equals(renaming), () -> {
            find(id).ifPresent(e -> draft = e.name());
            renaming = id;
            field.focus();
        }));
        row.add(action(first.shelf() == Library.Shelf.WORLD ? "to_global" : "to_world", () -> id.equals(selected) && ok && Library.folder(first.shelf().other()) != null, () -> {
            find(id).ifPresent(e -> say(Library.move(e) ? "moved" : "name_taken_there"));
            refresh();
        }));
        row.add(action("copy", () -> id.equals(selected) && ok, () -> find(id).ifPresent(e -> {
            Minecraft mc = Minecraft.getInstance();
            mc.keyboardHandler.setClipboard(e.palette().getOrThrow().write(e.name(), Library.registries()));
            say("copied");
        })));
        return row.build();
    }

    private static Button action(String key, BooleanSupplier visible, Runnable onClick) {
        return Button.builder().label(Component.translatable("shulkerpalette.library." + key)).size(0, 18)
                .visibleWhen(visible).onClick(onClick).build();
    }

    /** Paste from clipboard: file the palette on the clipboard under its own name, or a fresh one. */
    private static void paste(Library.Shelf shelf) {
        Minecraft mc = Minecraft.getInstance();
        SavedPalette.parseNamed(mc.keyboardHandler.getClipboard(), Library.registries()).ifSuccess(named -> {
            String name = named.name().isBlank() ? Library.freshName(shelf) : named.name();
            // Never overwrite on paste: a taken name gets a number.
            String unique = name;
            for (int n = 2; Library.exists(shelf, unique); n++) unique = name + " " + n;
            say(Library.save(shelf, unique, named.palette()) ? "pasted" : "save_failed");
            refresh();
        }).ifError(error -> status = Component.literal(error.message()));
    }

    private static void openFolder(Library.Shelf shelf) {
        Path folder = Library.folder(shelf);
        if (folder == null) return;
        try {
            Files.createDirectories(folder);
            Blaze3D.openPath(folder);
        } catch (IOException e) {
            ShulkerPalette.LOGGER.error("[library] couldn't open {}", folder, e);
        }
    }

    private static Optional<Library.Entry> find(String id) {
        for (List<Library.Entry> shelf : entries.values()) {
            for (Library.Entry e : shelf) if (e.id().equals(id)) return Optional.of(e);
        }
        return Optional.empty();
    }

    private static void say(String key) {
        status = Component.translatable("shulkerpalette.library.status." + key);
    }
}
