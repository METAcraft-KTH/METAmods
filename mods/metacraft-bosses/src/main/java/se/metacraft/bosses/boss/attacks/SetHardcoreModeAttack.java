package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.metacraft_lib.util.helper.HardcoreHelper;

public class SetHardcoreModeAttack extends InstantAttack {

	public static final MapCodec<SetHardcoreModeAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.BOOL.fieldOf("hardcore").forGetter(a -> a.hardcore)
			).apply(instance, SetHardcoreModeAttack::new)
	);

	private final boolean hardcore;

	public SetHardcoreModeAttack(boolean hardcore) {
		this.hardcore = hardcore;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		HardcoreHelper.setHardcoreMode(ctx.getWorld().getServer(), hardcore);
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.SET_HARDCORE;
	}
}
