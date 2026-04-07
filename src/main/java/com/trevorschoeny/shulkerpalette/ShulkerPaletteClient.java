package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.menukit.config.MKFamily;
import com.trevorschoeny.menukit.MenuKit;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public class ShulkerPaletteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MKFamily family = MenuKit.family("trevmods")
                .displayName("Trev's Mods")
                .description("Quality-of-life mods for creative builders.")
                .modId(ShulkerPaletteMod.MOD_ID);

        KeyMapping.Category category = family.getKeybindCategory();

        ShulkerPalette.initClient(category);

        // Register config
        family.configCategory(ShulkerPaletteMod.MOD_ID, "Shulker Palette", () -> {
            ShulkerPaletteConfig cfg = ShulkerPaletteConfig.get();
            return ConfigCategory.createBuilder()
                    .name(Component.literal("Shulker Palette"))
                    .tooltip(Component.literal("Place blocks directly from shulker boxes"))
                    .option(Option.<Boolean>createBuilder()
                            .name(Component.literal("Enable Shulker Palette"))
                            .description(OptionDescription.of(Component.literal(
                                    "When enabled, right-clicking with a shulker palette places " +
                                    "a random block from inside instead of placing the shulker box.")))
                            .binding(true, () -> cfg.enabled, val -> cfg.enabled = val)
                            .controller(BooleanControllerBuilder::create)
                            .build())
                    .build();
        }, ShulkerPaletteConfig::save);
    }
}
