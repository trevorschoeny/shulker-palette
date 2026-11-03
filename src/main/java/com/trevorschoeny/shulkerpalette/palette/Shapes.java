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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.BlockFamilies;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import org.jspecify.annotations.Nullable;

/**
 * Which shapes each material comes in, from vanilla's own table of block families (stone:
 * stairs, slab, wall, button, pressure plate; oak: stairs, slab, fence, gate, door, trapdoor...).
 * Cushions aren't in that table (26.3 crafts them from wool slabs), so each color's wool gets its
 * cushion here too. Every other solid full block or variant block is its own material with just
 * that one variant (dirt: block), so general mode can match it too.
 *
 * <p>Two ways to read it, matching a palette's two variant modes:
 * <ul>
 *   <li><b>Specific</b> ({@link #own}): only the variants a material really has.</li>
 *   <li><b>General</b> ({@link #closest}): every variant; a gap (stone has no wall of its own,
 *       concrete nothing past stairs and slab) is filled from {@value #GAP_FILL_PATH}, the
 *       closest-looking block that has the shape, picked once by
 *       {@code tools/gen-shape-gap-fill.py} from the vanilla textures (color first, then
 *       texture among the near colors, so speckled stone gets an andesite wall, not lined
 *       stone bricks) and shipped baked in.</li>
 * </ul>
 * {@link Shape#NONE} is every item as itself, in both.
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

    /** Material (a family's base block, or a block on its own) to its own shapes. */
    private static @Nullable Map<Item, Map<Shape, Item>> byMaterial;
    /** Material to the closest-looking stand-in for each shape it lacks. */
    private static @Nullable Map<Item, Map<Shape, Item>> nearest;
    /** Any item of a family, its base included, to the family's base; a block on its own to itself. */
    private static @Nullable Map<Item, Item> materialOf;

    /** The baked gap-fill table: {@code tools/gen-shape-gap-fill.py}'s output. */
    private static final String GAP_FILL_PATH = "/assets/shulkerpalette/data/shape_gap_fill.json";

    private static synchronized void build() {
        if (byMaterial != null) return;
        Map<Item, Item> material = new HashMap<>();
        Map<Item, Map<Shape, Item>> shapes = direct(material);
        nearest = gapFill(shapes.keySet());
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
        // Every other block that's a full cube or one of the variant shapes, on its own: its own
        // material, having just that one shape (dirt: block; moss carpet: carpet). In the table so
        // general mode can find it a closest match for every other variant, like any family.
        for (Item item : BuiltInRegistries.ITEM) {
            if (material.containsKey(item) || !(item instanceof BlockItem blockItem)) continue;
            Shape shape = standaloneShape(blockItem.getBlock());
            if (shape == null) continue;
            shapes.computeIfAbsent(item, i -> new EnumMap<>(Shape.class)).put(shape, item);
            material.put(item, item);
        }
        return shapes;
    }

    /** The variant a block outside every family is, by its kind: a solid full cube is a block; null if neither. */
    private static @Nullable Shape standaloneShape(Block block) {
        if (block instanceof StairBlock) return Shape.STAIRS;
        if (block instanceof SlabBlock) return Shape.SLAB;
        if (block instanceof WallBlock) return Shape.WALL;
        if (block instanceof FenceBlock) return Shape.FENCE;
        if (block instanceof FenceGateBlock) return Shape.FENCE_GATE;
        if (block instanceof CarpetBlock) return Shape.CARPET;
        if (block instanceof BasePressurePlateBlock) return Shape.PRESSURE_PLATE;
        if (block instanceof ButtonBlock) return Shape.BUTTON;
        if (block instanceof TrapDoorBlock) return Shape.TRAPDOOR;
        if (block instanceof DoorBlock) return Shape.DOOR;
        // A full collision box, not a full outline: tall grass and vines outline a whole block
        // but are walked through, so they aren't building blocks.
        return block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? Shape.BLOCK : null;
    }

    private static @Nullable Item item(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    /**
     * The baked table's stand-ins, by material, for the materials vanilla's table knows. Missing
     * the resource, or an id it names that no longer resolves (a block renamed since the table
     * was generated), is left as a gap rather than a crash; {@code gen-shape-gap-fill.py} is
     * meant to be rerun when the block set changes.
     */
    private static Map<Item, Map<Shape, Item>> gapFill(Set<Item> materials) {
        Map<Item, Map<Shape, Item>> fill = new HashMap<>();
        try (var in = Shapes.class.getResourceAsStream(GAP_FILL_PATH)) {
            if (in == null) return fill;
            JsonObject root = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (var materialEntry : root.entrySet()) {
                Item material = item(materialEntry.getKey());
                if (material == null || !materials.contains(material)) continue;
                Map<Shape, Item> of = fill.computeIfAbsent(material, m -> new EnumMap<>(Shape.class));
                for (var shapeEntry : materialEntry.getValue().getAsJsonObject().entrySet()) {
                    Shape shape = Shape.byName(shapeEntry.getKey());
                    Item substitute = item(shapeEntry.getValue().getAsString());
                    if (shape != null && substitute != null) of.put(shape, substitute);
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("couldn't read " + GAP_FILL_PATH, e);
        }
        return fill;
    }

    /**
     * This item's material in this variant, its own, or null when the material doesn't come in
     * it. None is the item itself. An item outside the table (a torch, a lantern) has only its
     * full block: itself.
     */
    public static @Nullable Item own(Item item, Shape shape) {
        if (shape == Shape.NONE) return item;
        build();
        Item material = materialOf.get(item);
        if (material == null) return shape == Shape.BLOCK ? item : null; // a torch, a flower: itself
        return byMaterial.get(material).get(shape);
    }

    /**
     * This item's material in this variant, or the closest-looking block that has it when the
     * material doesn't: general mode. Null only for an item outside the table (a torch, a
     * flower) and a variant it lacks.
     */
    public static @Nullable Item closest(Item item, Shape shape) {
        Item own = own(item, shape);
        if (own != null) return own;
        Map<Shape, Item> fill = nearest.get(materialOf.get(item));
        return fill == null ? null : fill.get(shape);
    }

    /** {@link #closest} in general mode, {@link #own} in specific. */
    public static @Nullable Item variant(Item item, Shape shape, boolean general) {
        return general ? closest(item, shape) : own(item, shape);
    }

    /**
     * The variants the row offers for these blocks, in row order, None first: every variant in
     * general mode; in specific, the ones at least one of them has of its own.
     */
    public static List<Shape> offered(List<ItemStack> inPlay, boolean general) {
        if (general) return List.of(Shape.values());
        Set<Shape> found = EnumSet.of(Shape.NONE);
        for (ItemStack stack : inPlay) {
            if (!Draw.eligible(stack)) continue;
            for (Shape shape : Shape.values()) if (own(stack.getItem(), shape) != null) found.add(shape);
        }
        return List.copyOf(found);
    }

    /**
     * The variant that applies: the palette's, if these blocks still offer it, else None (as
     * the pick moves on when its block runs out). The palette keeps its choice, so it applies
     * again once blocks that have it are back in play.
     */
    public static Shape effective(Palette palette, List<ItemStack> inPlay) {
        return offered(inPlay, palette.general()).contains(palette.shape()) ? palette.shape() : Shape.NONE;
    }
}
