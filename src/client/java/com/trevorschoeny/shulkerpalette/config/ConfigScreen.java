package com.trevorschoeny.shulkerpalette.config;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen, built with YACL. Mod Menu opens it from its list, and so does Mod Hub,
 * which reads the same {@code modmenu} entrypoint, so one screen serves all three.
 *
 * <p>Keys: with Keybindery installed they're chord rows right here; without it, a button opens
 * vanilla's key binds, so the keys are always one click from the mod's settings.
 */
public final class ConfigScreen {
    private ConfigScreen() {}

    public static Screen create(Screen parent) {
        OptionGroup.Builder keys = OptionGroup.createBuilder()
                .name(Component.translatable("shulkerpalette.config.keys"));
        if (FabricLoader.getInstance().isModLoaded("keybindery")) {
            keys.options(KeybinderyAPI.getInstance().createYACLChordOption(KeysClient.MODIFIER,
                    Component.translatable("key.shulkerpalette.modifier"),
                    OptionDescription.of(Component.translatable("shulkerpalette.config.modifier.description"))));
            keys.options(KeybinderyAPI.getInstance().createYACLChordOption(KeysClient.POWER,
                    Component.translatable("key.shulkerpalette.power"),
                    OptionDescription.of(Component.translatable("shulkerpalette.config.power.description"))));
            keys.options(KeybinderyAPI.getInstance().createYACLChordOption(KeysClient.REPAINT,
                    Component.translatable("key.shulkerpalette.repaint"),
                    OptionDescription.of(Component.translatable("shulkerpalette.config.repaint.description"))));
        } else {
            keys.option(ButtonOption.createBuilder()
                    .name(Component.translatable("shulkerpalette.config.keybinds"))
                    .description(OptionDescription.of(Component.translatable("shulkerpalette.config.keybinds.description")))
                    .action((screen, option) -> Minecraft.getInstance().gui.setScreen(new KeyBindsScreen(screen, Minecraft.getInstance().options)))
                    .build());
        }

        OptionGroup look = OptionGroup.createBuilder()
                .name(Component.translatable("shulkerpalette.config.look"))
                .option(toggle("rainbow", Config::rainbow, Config::setRainbow))
                .option(toggle("popup", Config::popup, Config::setPopup))
                .option(toggle("open_box", Config::openBoxInHand, Config::setOpenBoxInHand))
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("shulkerpalette.config.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("shulkerpalette.config.title"))
                        .group(keys.build())
                        .group(look)
                        .build())
                .build()
                .generateScreen(parent);
    }

    /** A look switch; every one defaults on. */
    private static Option<Boolean> toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("shulkerpalette.config." + key))
                .description(OptionDescription.of(Component.translatable("shulkerpalette.config." + key + ".description")))
                .binding(true, getter, setter)
                .controller(BooleanControllerBuilder::create)
                .build();
    }
}
