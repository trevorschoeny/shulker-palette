package com.trevorschoeny.shulkerpalette.palette;

import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.BlockFamilies;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Which shapes each material comes in, from vanilla's own table of block families (stone:
 * stairs, slab, wall, button, pressure plate; oak: stairs, slab, fence, gate, door, trapdoor...).
 * Cushions aren't in that table (26.3 crafts them from wool slabs), so each color's wool gets its
 * cushion here too.
 *
 * <p>Vanilla's table is full of gaps: stone has no wall of its own (cobblestone does), concrete
 * has no shapes past stairs and slab, and so on. After the direct variants, every material's
 * missing shapes are filled from {@value #GAP_FILL_PATH}: the closest-looking block that does
 * have the shape, picked once by {@code tools/gen-shape-gap-fill.py} from the vanilla textures
 * (average color and a roughness score, so a flat texture isn't matched to a busy one just
 * because their colors line up) and shipped baked in, not computed at runtime. So after this
 * runs, every material offers every shape; {@link #available} and {@link #variant} don't need
 * to know the difference between a material's own variant and a borrowed one.
 *
 * <p>A shaped item in a box counts as its material: oak stairs in the box can place an oak slab.
 * Built once, on first use, after every block is registered. Both sides read it: the server to
 * choose the next block, the client to draw the shape popup.
 */
public final class Shapes {
    private Shapes() {}

    private static final Map<BlockFamily.Variant, Shape> VARIANTS = Map.ofEntries(
            Map.entry(BlockFamily.Variant.STAIRS, Shape.STAIRS),
            Map.entry(BlockFamily.Variant.SLAB, Shape.SLAB),
            Map.entry(BlockFamily.Variant.WALL, Shape.WALL),
            Map.entry(BlockFamily.Variant.FENCE, Shape.FENCE),
            Map.entry(BlockFamily.Variant.CUSTOM_FENCE, Shape.FENCE),
            Map.entry(BlockFamily.Variant.FENCE_GATE, Shape.FENCE_GATE),
            Map.entry(BlockFamily.Variant.CUSTOM_FENCE_GATE, Shape.FENCE_GATE),
            Map.entry(BlockFamily.Variant.CARPET, Shape.CARPET),
            Map.entry(BlockFamily.Variant.PRESSURE_PLATE, Shape.PRESSURE_PLATE),
            Map.entry(BlockFamily.Variant.BUTTON, Shape.BUTTON),
            Map.entry(BlockFamily.Variant.TRAPDOOR, Shape.TRAPDOOR),
            Map.entry(BlockFamily.Variant.DOOR, Shape.DOOR));

    /** Material (a family's base block) to its shapes, BLOCK included. */
    private static @Nullable Map<Item, Map<Shape, Item>> byMaterial;
    /** Any item of a family, its base included, to the family's base. */
    private static @Nullable Map<Item, Item> materialOf;

    /** The baked gap-fill table: {@code tools/gen-shape-gap-fill.py}'s output. */
    private static final String GAP_FILL_PATH = "/assets/shulkerpalette/data/shape_gap_fill.json";

    private static synchronized void build() {
        if (byMaterial != null) return;
        Map<Item, Item> material = new HashMap<>();
        Map<Item, Map<Shape, Item>> shapes = direct(material);
        gapFill(shapes);
        materialOf = material;
        byMaterial = shapes;
    }

    /**
     * The direct, vanilla table only, with no gap-fill: a fresh build every call, not the cached
     * one {@link #build} keeps. For {@code tools/gen-shape-gap-fill.py} (through its gametest
     * dump) to read what still needs filling; {@link #variant} and {@link #available} never use
     * this, so they never see a gap the table hasn't filled yet.
     */
    public static Map<Item, Map<Shape, Item>> direct(Map<Item, Item> material) {
        Map<Item, Map<Shape, Item>> shapes = new HashMap<>();
        BlockFamilies.getAllFamilies().forEach(family -> {
            Item base = family.getBaseBlock().asItem();
            if (base == Items.AIR) return;
            Map<Shape, Item> of = shapes.computeIfAbsent(base, b -> new EnumMap<>(Shape.class));
            of.put(Shape.BLOCK, base);
            material.putIfAbsent(base, base);
            family.getVariants().forEach((variant, block) -> {
                Shape shape = VARIANTS.get(variant);
                Item item = block.asItem();
                if (shape == null || item == Items.AIR) return;
                of.putIfAbsent(shape, item);
                material.putIfAbsent(item, base);
            });
        });
        // Cushions: <color>_wool to <color>_cushion, where both exist.
        for (DyeColor color : DyeColor.values()) {
            Item wool = item(color.getSerializedName() + "_wool");
            Item cushion = item(color.getSerializedName() + "_cushion");
            if (wool == null || cushion == null) continue;
            shapes.computeIfAbsent(wool, w -> new EnumMap<>(Shape.class)).put(Shape.BLOCK, wool);
            shapes.get(wool).put(Shape.CUSHION, cushion);
            material.putIfAbsent(wool, wool);
            material.putIfAbsent(cushion, wool);
        }
        return shapes;
    }

    private static @Nullable Item item(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    /**
     * Fills every material's missing shapes from the baked table, by material item id. Missing
     * the resource, or an id it names that no longer resolves (a block renamed since the table
     * was generated), is left as a gap rather than a crash; {@code gen-shape-gap-fill.py} is
     * meant to be rerun when the block set changes.
     */
    private static void gapFill(Map<Item, Map<Shape, Item>> shapes) {
        try (var in = Shapes.class.getResourceAsStream(GAP_FILL_PATH)) {
            if (in == null) return;
            JsonObject root = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (var materialEntry : root.entrySet()) {
                Item material = item(materialEntry.getKey());
                Map<Shape, Item> of = shapes.get(material);
                if (material == null || of == null) continue;
                for (var shapeEntry : materialEntry.getValue().getAsJsonObject().entrySet()) {
                    Shape shape = Shape.byName(shapeEntry.getKey());
                    Item substitute = item(shapeEntry.getValue().getAsString());
                    if (shape != null && substitute != null) of.putIfAbsent(shape, substitute);
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("couldn't read " + GAP_FILL_PATH, e);
        }
    }

    /**
     * This item's material in this shape, or null when the material doesn't come in it. An item
     * outside every family (dirt, a lantern) has only its full-block shape: itself.
     */
    public static @Nullable Item variant(Item item, Shape shape) {
        build();
        Item material = materialOf.get(item);
        if (material == null) return shape == Shape.BLOCK ? item : null;
        return byMaterial.get(material).get(shape);
    }

    /** The shapes at least one block in these slots comes in, in cycle order; empty when none is eligible. */
    public static List<Shape> available(List<ItemStack> slots) {
        Set<Shape> found = EnumSet.noneOf(Shape.class);
        for (ItemStack stack : slots) {
            if (!Draw.eligible(stack)) continue;
            for (Shape shape : Shape.values()) if (variant(stack.getItem(), shape) != null) found.add(shape);
        }
        return List.copyOf(found);
    }

    /** The palette's shape if these slots still offer it, else a full block (as the pick moves on when its block runs out). */
    public static Shape effective(Shape chosen, List<ItemStack> slots) {
        return available(slots).contains(chosen) ? chosen : Shape.BLOCK;
    }
}
