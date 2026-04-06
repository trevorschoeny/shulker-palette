package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteCompositeRenderer;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteRoll;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteState;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.BlockItem;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts item model resolution to swap the shulker renderer for palettes.
 *
 * For palette shulkers, replaces the ShulkerBoxSpecialRenderer with a
 * ShulkerPaletteCompositeRenderer that renders BOTH the open shulker base
 * AND the representative block inside it in a single submit() call.
 *
 * Because both models share the same 3D pass (same Z-buffer), the block
 * naturally clips against the shulker walls — visible above the rim,
 * hidden behind the walls.
 *
 * Works in ALL rendering contexts: GUI atlas, held item, item frames.
 */
@Mixin(ItemModelResolver.class)
public class ShulkerPaletteGuiMixin {

    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    private void trevorMod$openLidForPalette(ItemStackRenderState renderState,
                                               ItemStack itemStack,
                                               ItemDisplayContext displayContext,
                                               Level level, ItemOwner owner, int seed,
                                               CallbackInfo ci) {
        if (!ShulkerPalette.isEnabled()) return;
        if (!ShulkerPalette.isShulkerPalette(itemStack)) return;

        // Find the top 3 most common blocks inside the shulker.
        java.util.List<ItemStack> topItems = ShulkerPaletteRoll.topNItems(itemStack, 3);

        // Give palette shulkers a distinct model identity so the GUI atlas cache
        // renders them separately from non-palette shulkers (which render closed).
        // Include the actual item IDs so shulkers with different contents get
        // different cache entries — otherwise they all show the same items.
        renderState.appendModelIdentityElement("shulker_palette_open");
        for (ItemStack rep : topItems) {
            renderState.appendModelIdentityElement(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(rep.getItem()).toString());
        }

        // Walk the layers and find the ShulkerBoxSpecialRenderer.
        ItemStackRenderStateAccessor stateAccessor = (ItemStackRenderStateAccessor) (Object) renderState;
        ItemStackRenderState.LayerRenderState[] layers = stateAccessor.trevorMod$getLayers();
        int count = stateAccessor.trevorMod$getActiveLayerCount();

        for (int i = 0; i < count; i++) {
            LayerRenderStateAccessor layerAccessor = (LayerRenderStateAccessor) (Object) layers[i];
            Object renderer = layerAccessor.trevorMod$getSpecialRenderer();
            if (renderer instanceof ShulkerBoxSpecialRenderer shulkerRenderer) {
                // Build the open-lid shulker renderer.
                ShulkerBoxSpecialRendererAccessor sra = (ShulkerBoxSpecialRendererAccessor) shulkerRenderer;
                float openness = (displayContext == ItemDisplayContext.GUI)
                        ? ShulkerPaletteState.RENDER_OPENNESS_GUI
                        : ShulkerPaletteState.RENDER_OPENNESS_INGAME;
                ShulkerBoxSpecialRenderer openRenderer = new ShulkerBoxSpecialRenderer(
                        sra.trevorMod$getShulkerBoxRenderer(),
                        openness,
                        sra.trevorMod$getOrientation(),
                        sra.trevorMod$getMaterial()
                );

                if (!topItems.isEmpty()) {
                    // Resolve each representative block's model into a render state.
                    // These recursive updateForTopItem calls are safe — the representative
                    // items aren't shulker palettes, so our mixin exits early.
                    ItemModelResolver resolver = (ItemModelResolver) (Object) this;
                    java.util.List<ItemStackRenderState> innerStates = new java.util.ArrayList<>();
                    java.util.List<Boolean> isBlockList = new java.util.ArrayList<>();
                    for (ItemStack rep : topItems) {
                        ItemStackRenderState innerState = new ItemStackRenderState();
                        resolver.updateForTopItem(innerState, rep, displayContext,
                                level, owner, seed);

                        // For GUI context: strip the inner item's display transform
                        // to prevent double GUI rotation (parent + item's own = distorted).
                        if (displayContext == ItemDisplayContext.GUI) {
                            ItemStackRenderStateAccessor innerAccessor =
                                    (ItemStackRenderStateAccessor) (Object) innerState;
                            ItemStackRenderState.LayerRenderState[] innerLayers = innerAccessor.trevorMod$getLayers();
                            int innerCount = innerAccessor.trevorMod$getActiveLayerCount();
                            for (int j = 0; j < innerCount; j++) {
                                ((LayerRenderStateAccessor) (Object) innerLayers[j])
                                        .trevorMod$setTransform(ItemTransform.NO_TRANSFORM);
                            }
                        }

                        isBlockList.add(rep.getItem() instanceof BlockItem);
                        innerStates.add(innerState);
                    }

                    // Swap in the composite renderer — renders all models in one pass.
                    ShulkerPaletteCompositeRenderer composite = new ShulkerPaletteCompositeRenderer(
                            openRenderer, innerStates, isBlockList);
                    layers[i].setupSpecialModel(composite, null);
                } else {
                    // No blocks inside — just the open shulker.
                    layers[i].setupSpecialModel(openRenderer, null);
                }

                // Also mark as animated so atlas re-renders when contents change.
                renderState.setAnimated();
                break;
            }
        }
    }
}
