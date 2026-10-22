package com.trevorschoeny.shulkerpalette.palette;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The shape a palette places its materials in (creative only): a full block, or one of the
 * shaped blocks vanilla makes from a material. The second axis of a palette, next to which
 * material: stone in the box with stairs chosen places stone stairs. The order here is the
 * order the shape cycle runs in.
 */
public enum Shape implements StringRepresentable {
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

    public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
    public static final StreamCodec<ByteBuf, Shape> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.clamp(i, 0, values().length - 1)], Shape::ordinal);
}
