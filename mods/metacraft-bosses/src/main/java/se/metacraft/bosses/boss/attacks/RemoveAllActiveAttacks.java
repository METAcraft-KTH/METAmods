package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;

public class RemoveAllActiveAttacks extends InstantAttack {

	private static final RemoveAllActiveAttacks INSTANCE = new RemoveAllActiveAttacks();

	public static final MapCodec<RemoveAllActiveAttacks> CODEC = MapCodec.unit(INSTANCE);

	public static RemoveAllActiveAttacks getInstance() {
		return INSTANCE;
	}

	private RemoveAllActiveAttacks() {}

	@Override
	public void trigger(BossContext<?> ctx) {
		ctx.boss().removeAllAttacks();
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.REMOVE_ALL_ATTACKS;
	}
}
