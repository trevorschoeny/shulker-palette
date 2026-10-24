package com.trevorschoeny.shulkerpalette.config;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.shulkerpalette.library.LibraryScreen;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen, built with YACL. Mod Menu opens it from its list, and so does Mod Hub,
 * which reads the same {@code modmenu} entrypoint, so one screen serves all three.
 *
 * <p>Keys are Keybindery chord rows right here (Keybindery is required), so they're set where
 * the rest of the mod is.
 */
public final class ConfigScreen {
    private ConfigScreen() {}

    public static Screen create(Screen parent) {
        OptionGroup.Builder keys = OptionGroup.createBuilder()
                .name(Component.translatable("shulkerpalette.config.keys"));
        chord(keys, KeysClient.MODIFIER, "key.shulkerpalette.palette", "shulkerpalette.config.modifier.description");
        chord(keys, KeysClient.SHAPE, "key.shulkerpalette.shape_scroll", "shulkerpalette.config.shape.description");
        chord(keys, KeysClient.PREVIOUS_SHAPE, "key.shulkerpalette.shape_previous", "shulkerpalette.config.shape_step.description");
        chord(keys, KeysClient.NEXT_SHAPE, "key.shulkerpalette.shape_next", "shulkerpalette.config.shape_step.description");
        chord(keys, KeysClient.POWER, "key.shulkerpalette.power", "shulkerpalette.config.power.description");
        chord(keys, KeysClient.REPAINT, "key.shulkerpalette.repaint", "shulkerpalette.config.repaint.description");

        // The library is a MenuKit page (lists of palettes, later the item picker), which YACL
        // can't host, so it opens as a screen of its own.
        OptionGroup library = OptionGroup.createBuilder()
                .name(Component.translatable("shulkerpalette.library.title"))
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("shulkerpalette.config.library"))
                        .description(OptionDescription.of(Component.translatable("shulkerpalette.config.library.description")))
                        .action((screen, option) -> Minecraft.getInstance().gui.setScreen(new LibraryScreen(screen)))
                        .build())
                .build();

        OptionGroup look = OptionGroup.createBuilder()
                .name(Component.translatable("shulkerpalette.config.look"))
                .option(toggle("rainbow", Config::rainbow, Config::setRainbow))
                .option(toggle("popup", Config::popup, Config::setPopup))
                .option(toggle("open_box", Config::openBoxInHand, Config::setOpenBoxInHand))
                .option(toggle("placement_ghost", false, Config::placementGhost, Config::setPlacementGhost))
                .option(toggle("repaint_ghost", Config::repaintGhost, Config::setRepaintGhost))
                .build();

        // Only with Open Anywhere installed: the palette panel on a box opened there. Off by
        // default, so the palette is handled in hand.
        OptionGroup openAnywhere = FabricLoader.getInstance().isModLoaded("openanywhere")
                ? OptionGroup.createBuilder()
                        .name(Component.translatable("shulkerpalette.config.open_anywhere"))
                        .option(toggle("open_anywhere_panel", false, Config::openAnywherePanel, Config::setOpenAnywherePanel))
                        .build()
                : null;

        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.translatable("shulkerpalette.config.title"))
                .group(keys.build())
                .group(library)
                .group(look);
        if (openAnywhere != null) category.group(openAnywhere);
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("shulkerpalette.config.title"))
                .category(category.build())
                .build()
                .generateScreen(parent);
    }

    /** A switch that defaults on, as most look switches do. */
    private static Option<Boolean> toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return toggle(key, true, getter, setter);
    }

    private static Option<Boolean> toggle(String key, boolean byDefault, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("shulkerpalette.config." + key))
                .description(OptionDescription.of(Component.translatable("shulkerpalette.config." + key + ".description")))
                .binding(byDefault, getter, setter)
                .controller(BooleanControllerBuilder::create)
                .build();
    }

    /** One key as a Keybindery chord row: name, current chord, conflicts and reset. */
    private static void chord(OptionGroup.Builder group, KeyMapping mapping, String name, String description) {
        group.options(KeybinderyAPI.getInstance().createYACLChordOption(mapping,
                Component.translatable(name), OptionDescription.of(Component.translatable(description))));
    }
}
