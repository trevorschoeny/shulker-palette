package com.trevorschoeny.shulkerpalette.palette;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * How a palette shulker chooses its next block. Set per shulker from the control panel.
 * The order here is the order the panel's mode button cycles through.
 */
public enum Mode implements StringRepresentable {
    /** Every item in the box is one ticket: 48 stone and 16 andesite places stone 75% of the time. */
    RANDOM(0, "random"),
    /** Every block type has equal odds, however many of it there are or how it is split into stacks. */
    EQUAL(1, "equal"),
    /** One block type, chosen by scrolling, placed every click. */
    PICK(2, "pick"),
    /** One row of the box, chosen by scrolling; each click draws from that row, weighted by count. */
    GRADIENT(3, "gradient");

    public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
    private static final IntFunction<Mode> BY_ID = ByIdMap.continuous(m -> m.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, Mode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, m -> m.id);

    private final int id;
    private final String name;

    Mode(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /** The mode after this one, wrapping round: what one press of the panel's mode button does. */
    public Mode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
