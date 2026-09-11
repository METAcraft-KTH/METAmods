package nu.metacraft.revival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.FloatProviders;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.storage.loot.IntLimit;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.container.ServerAware;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public record RevivalConfig(
		Optional<Integer> maxWaitTime,
		int reviveDuration,
		boolean itemPickup,
		boolean xpPickup,
		ReviveEffects reviveEffects
) {

	public static final MapCodec<RevivalConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_wait_time").forGetter(RevivalConfig::maxWaitTime),
					ExtraCodecs.NON_NEGATIVE_INT.fieldOf("revive_duration").forGetter(RevivalConfig::reviveDuration),
					Codec.BOOL.fieldOf("item_pickup").forGetter(RevivalConfig::itemPickup),
					Codec.BOOL.fieldOf("xp_pickup").forGetter(RevivalConfig::xpPickup),
					ReviveEffects.CODEC.fieldOf("revive_effects").forGetter(RevivalConfig::reviveEffects)
			).apply(instance, RevivalConfig::new)
	);

	public record ReviveEffects(
			FloatProvider health,
			IntLimit air,
			IntLimit hunger,
			MinMaxBounds.Doubles saturation,
			List<MobEffectInstance> effects
	) {
		public static final Codec<ReviveEffects> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						FloatProviders.CODEC.fieldOf("health").forGetter(ReviveEffects::health),
						IntLimit.CODEC.fieldOf("air").forGetter(ReviveEffects::air),
						IntLimit.CODEC.fieldOf("hunger").forGetter(ReviveEffects::hunger),
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

	private static final ServerAware<ConfigContainer<ServerAware.ConfigPair<RevivalConfig, WorldData>>, WorldData> CONFIG = ConfigContainer.Builder.create(
			CODEC, () -> new RevivalConfig(
					Optional.of(2400), 200, false, false, new ReviveEffects(
							ConstantFloat.of(1.0f), IntLimit.lowerBound(10),
							IntLimit.lowerBound(1), MinMaxBounds.Doubles.exactly(0.0),
							List.of(
									new MobEffectInstance(MobEffects.HUNGER, 30*20)
							)
					)
			)
	).reloadAfterServer().makeRegistryAware(
			WorldData.CODEC
	).refreshOnReload().setInitializer(() -> new WorldData(getDefaultReviveCondition())).build(
			FabricLoader.getInstance().getConfigDir().resolve("metacraft-revival.json")
	);

	public record WorldData(Holder<@NotNull LootItemCondition> reviveCondition) {
		public static final MapCodec<WorldData> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						LootItemCondition.CODEC.lenientOptionalFieldOf("revive_condition", getDefaultReviveCondition()).forGetter(WorldData::reviveCondition)
				).apply(instance, WorldData::new)
		);
	}

	public static RevivalConfig getConfig() {
		return CONFIG.getContainer().get().staticValues();
	}

	public static WorldData getConfig(MinecraftServer server) {
		return CONFIG.get(server);
	}

}
