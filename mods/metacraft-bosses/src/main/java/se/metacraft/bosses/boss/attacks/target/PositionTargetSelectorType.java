package se.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;

public record PositionTargetSelectorType(MapCodec<? extends PositionTargetSelector> codec) {
}
