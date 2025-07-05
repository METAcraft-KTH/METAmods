package nu.metacraft.season_4.boss;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Util;
import net.minecraft.world.TeleportTarget;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackType;
import nu.metacraft.bosses.boss.attacks.InstantAttack;

import java.util.stream.Collectors;

public class PlayerShuffleAttack extends InstantAttack {

	private static final PlayerShuffleAttack INSTANCE = new PlayerShuffleAttack();

	public static final MapCodec<PlayerShuffleAttack> CODEC = MapCodec.unit(INSTANCE);

	public static PlayerShuffleAttack getInstance() {
		return INSTANCE;
	}

	private PlayerShuffleAttack() {}

	@Override
	public AttackType getType() {
		return Season4Attacks.PLAYER_SHUFFLE;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		var sources = ctx.boss().getPlayerTargets();
		if (sources.isEmpty()) return;
		var targets = sources.stream().map(PlayerPosition::fromEntity).collect(Collectors.toList());
		Util.shuffle(targets, ctx.random());
		for (int i = 0; i < sources.size(); i++) {
			var target = targets.get(i);
			sources.get(i).teleportTo(
					new TeleportTarget(
							ctx.getWorld(), target.position(), target.deltaMovement(),
							target.yaw(), target.pitch(), TeleportTarget.NO_OP
					)
			);
		}
	}

	@Override
	public Attack copy(RegistryWrapper.WrapperLookup lookup) {
		return this;
	}
}
