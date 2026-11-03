package com.trevorschoeny.shulkerpalette.checks;

import com.trevorschoeny.shulkerpalette.palette.Shape;
import com.trevorschoeny.shulkerpalette.palette.Shapes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * Dev tool: writes every material's direct (pre-gap-fill) shapes to /tmp/shape-gaps.txt, for
 * {@code tools/gen-shape-gap-fill.py} to read when the table needs regenerating (a new
 * Minecraft version adds blocks). Not a self-check: it isn't in {@code fabric.mod.json}'s
 * gametest list, so normal test runs never touch it. To regenerate: add
 * {@code "com.trevorschoeny.shulkerpalette.checks.GapDump"} to that list's {@code client}
 * array, run {@code ./gradlew :shulker-palette:runClientGameTest}, then take it back out and
 * run {@code tools/gen-shape-gap-fill.py}. Reads {@link Shapes#direct}, the table before
 * gap-fill, so a stale baked table in the jar doesn't mask what's actually still missing.
 */
public final class GapDump implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {
    @Override
    public void runTest(net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> dump());
    }

    static void dump() {
        Map<Item, Item> material = new HashMap<>();
        Map<Item, Map<Shape, Item>> shapes = Shapes.direct(material);
        StringBuilder out = new StringBuilder();
        for (Item base : shapes.keySet()) {
            out.append(BuiltInRegistries.ITEM.getKey(base).getPath());
            Map<Shape, Item> of = shapes.get(base);
            for (Shape s : Shape.values()) {
                Item v = of.get(s);
                out.append('|').append(s.getSerializedName()).append('=').append(v == null ? "-" : BuiltInRegistries.ITEM.getKey(v).getPath());
            }
            out.append('\n');
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/shape-gaps.txt"), out);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
