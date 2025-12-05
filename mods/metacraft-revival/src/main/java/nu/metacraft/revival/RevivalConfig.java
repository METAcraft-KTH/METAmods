package nu.metacraft.revival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.container.ServerAware;

import java.util.List;
import java.util.Optional;

public record RevivalConfig(
		Optional<Integer> maxWaitTime,
		int reviveDuration,
		ReviveEffects reviveEffects,
		ObjectStorage<Holder<LootItemCondition>> reviveCondition
) {

	public static final Codec<RevivalConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_wait_time").forGetter(RevivalConfig::maxWaitTime),
					ExtraCodecs.NON_NEGATIVE_INT.fieldOf("revive_duration").forGetter(RevivalConfig::reviveDuration),
					ReviveEffects.CODEC.fieldOf("revive_effects").forGetter(RevivalConfig::reviveEffects),
					ObjectStorage.createCodec(LootItemCondition.CODEC).fieldOf("revive_condition").forGetter(RevivalConfig::reviveCondition)
			).apply(instance, RevivalConfig::new)
	);

	public record ReviveEffects(
			FloatProvider health,
			IntRange air,
			IntRange hunger,
			MinMaxBounds.Doubles saturation,
			List<MobEffectInstance> effects
	) {
		public static final Codec<ReviveEffects> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						FloatProvider.CODEC.fieldOf("health").forGetter(ReviveEffects::health),
						IntRange.CODEC.fieldOf("air").forGetter(ReviveEffects::air),
						IntRange.CODEC.fieldOf("hunger").forGetter(ReviveEffects::hunger),
						MinMaxBounds.Doubles.CODEC.fieldOf("saturation").forGetter(ReviveEffects::saturation),
						MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(ReviveEffects::effects)
				).apply(instance, ReviveEffects::new)
		);
	}

	private static Holder<LootItemCondition> getDefaultReviveCondition() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return Holder.direct(AllOfCondition.allOf().build());
		} else {
			return Holder.direct(AnyOfCondition.anyOf().build());
		}
	}

	private static final ServerAware<ConfigContainer<RevivalConfig>, WorldData> CONFIG = ConfigContainer.Builder.create(
			CODEC, () -> new RevivalConfig(
					Optional.of(2400), 200, new ReviveEffects(
							ConstantFloat.of(1.0f), IntRange.lowerBound(10),
							IntRange.lowerBound(1), MinMaxBounds.Doubles.exactly(0.0),
							List.of(
									new MobEffectInstance(MobEffects.HUNGER, 30*20)
							)
					),
					ObjectStorage.fromValue(LootItemCondition.CODEC, getDefaultReviveCondition())
			)
	).reloadAfterServer().buildRegistryAware(
			FabricLoader.getInstance().getConfigDir().resolve("metacraft-revival.json"),
			(config, server) -> new WorldData(
					config.reviveCondition.parse(server.reloadableRegistries().lookup()).resultOrPartial(
							METAcraftRevival.LOGGER::error
					).orElse(getDefaultReviveCondition())
			)
	);

	public record WorldData(Holder<LootItemCondition> reviveCondition) {

	}

	public static RevivalConfig getConfig() {
		return CONFIG.getContainer().get();
	}

	public static WorldData getConfig(MinecraftServer server) {
		return CONFIG.get(server);
	}

}
