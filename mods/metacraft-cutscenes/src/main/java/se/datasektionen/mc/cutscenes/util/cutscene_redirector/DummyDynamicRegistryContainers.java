package se.datasektionen.mc.cutscenes.util.cutscene_redirector;

import com.mojang.serialization.Lifecycle;
import net.minecraft.registry.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.world.dimension.DimensionOptions;
import se.datasektionen.mc.cutscenes.mixin.AccessorDataPackContents;

import java.util.List;

public class DummyDynamicRegistryContainers {

	private static final FeatureSet FEATURE_SET = FeatureSet.empty();

	public static CombinedDynamicRegistries<ServerDynamicRegistryType> createDynamicRegistries() {
		var dimRegistry = new SimpleRegistry<>(
				RegistryKeys.DIMENSION, Lifecycle.stable()
		);
		Registry.register(
				dimRegistry, DimensionOptions.OVERWORLD, new DimensionOptions(
						null, null
				)
		);
		return ServerDynamicRegistryType.createCombinedDynamicRegistries().with(
				ServerDynamicRegistryType.WORLDGEN,
				new DynamicRegistryManager.ImmutableImpl(List.of(
						new SimpleRegistry<>(
								RegistryKeys.BIOME, Lifecycle.stable()
						),
						new SimpleRegistry<>(
								RegistryKeys.DAMAGE_TYPE, Lifecycle.stable()
						),
						new SimpleRegistry<>(
								RegistryKeys.ENCHANTMENT, Lifecycle.stable()
						)
				)).toImmutable()
		).with(
				ServerDynamicRegistryType.DIMENSIONS,
				new DynamicRegistryManager.ImmutableImpl(List.of(
						dimRegistry
				)).toImmutable()
		);
	}

	public static DataPackContents createDataPackContents(CombinedDynamicRegistries<ServerDynamicRegistryType> registries) {
		return AccessorDataPackContents.init(
				registries,
				registries.getCombinedRegistryManager(),
				getFeatureSet(),
				CommandManager.RegistrationEnvironment.ALL,
				List.of(), 4
		);
	}

	public static FeatureSet getFeatureSet() {
		return FEATURE_SET;
	}

}
