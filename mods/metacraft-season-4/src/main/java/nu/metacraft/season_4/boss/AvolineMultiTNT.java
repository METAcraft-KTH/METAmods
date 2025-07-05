package nu.metacraft.season_4.boss;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.RegistryWrapper;
import nu.metacraft.season_4.entity.entities.bosses.AvolineBossEntity;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackType;
import nu.metacraft.bosses.boss.attacks.InstantAttack;

public class AvolineMultiTNT extends InstantAttack {

	private static final AvolineMultiTNT INSTANCE = new AvolineMultiTNT();

	public static final MapCodec<AvolineMultiTNT> CODEC = MapCodec.unit(INSTANCE);

	public static AvolineMultiTNT getInstance() {
		return INSTANCE;
	}

	private AvolineMultiTNT() {}

	@Override
	public AttackType getType() {
		return Season4Attacks.AVOLINE_MULTI_TNT;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		ctx.boss().getPlayerTargets().forEach(player -> {
			ctx.getWorld().spawnEntity(AvolineBossEntity.createTNTFlyingTowards(ctx.boss(), player));
		});
	}

	@Override
	public Attack copy(RegistryWrapper.WrapperLookup lookup) {
		return this;
	}
}
