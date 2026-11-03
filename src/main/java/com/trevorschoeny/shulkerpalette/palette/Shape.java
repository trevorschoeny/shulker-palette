package com.trevorschoeny.shulkerpalette.palette;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * The variant a palette places its materials in (creative only): none (the box's items as they
 * are), a full block, or one of the shaped blocks vanilla makes from a material. The second axis
 * of a palette, next to which material: stone in the box with stairs chosen places stone stairs.
 * The order here is the order the variant row runs in.
 */
public enum Shape implements StringRepresentable {
    /** No variant: each item places as it is in the box (stairs stay stairs). The default. */
    NONE("none"),
    BLOCK("block"),
    STAIRS("stairs"),
    SLAB("slab"),
    WALL("wall"),
    FENCE("fence"),
    FENCE_GATE("fence_gate"),
    CARPET("carpet"),
    CUSHION("cushion"),
    PRESSURE_PLATE("pressure_plate"),
    BUTTON("button"),
    TRAPDOOR("trapdoor"),
    DOOR("door");

    private final String name;

    Shape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** This shape by its serialized name ("fence_gate"), or null. */
    public static @Nullable Shape byName(String name) {
        for (Shape shape : values()) if (shape.getSerializedName().equals(name)) return shape;
        return null;
    }

    public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
    public static final StreamCodec<ByteBuf, Shape> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.clamp(i, 0, values().length - 1)], Shape::ordinal);
}
