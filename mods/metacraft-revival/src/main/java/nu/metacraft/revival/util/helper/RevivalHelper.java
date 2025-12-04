package nu.metacraft.revival.util.helper;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.revival.RevivalConfig;
import nu.metacraft.revival.RevivalDamageTypes;
import nu.metacraft.revival.RevivalDialogs;
import nu.metacraft.revival.extension.ServerPlayerExtension;

import java.util.Optional;

public class RevivalHelper {

	public static boolean hasRevival(ServerPlayer player) {
		Holder<LootItemCondition> condition = RevivalConfig.getConfig(player.level().getServer()).reviveCondition();
		LootParams lootParams = new LootParams.Builder(player.level())
				.withParameter(LootContextParams.ORIGIN, player.position())
				.withOptionalParameter(LootContextParams.THIS_ENTITY, player)
				.create(LootContextParamSets.COMMAND);
		LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
		lootContext.pushVisitedElement(LootContext.createVisitedEntry(condition.value()));
		return condition.value().test(lootContext);
	}

	public static void updateRevivalMenu(ServerPlayer player) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious() && ((ServerPlayerExtension) player).metacraft$isRevivalMenuOpen()) {
			player.openDialog(RevivalDialogs.createRevivalDialog(player));
		}
	}

	public static void openRevivalMenu(ServerPlayer player) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			player.closeContainer();
			player.openDialog(RevivalDialogs.createRevivalDialog(player));
			((ServerPlayerExtension) player).metacraft$setRevivalMenuOpen(true);
		}
	}

	public static void playerAcceptedFate(ServerPlayer player) {
		((ServerPlayerExtension) player).metacraft$setRevivalMenuOpen(false);
		player.hurtServer(
				player.level(),
				new DamageSource(player.registryAccess().getOrThrow(RevivalDamageTypes.ACCEPTED_FATE)),
				Float.MAX_VALUE
		);
	}

}
