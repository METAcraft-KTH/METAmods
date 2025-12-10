package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;

public record AttackType(MapCodec<? extends Attack> codec) {
}
