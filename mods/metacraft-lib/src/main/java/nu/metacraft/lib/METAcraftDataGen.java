package nu.metacraft.lib;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class METAcraftDataGen implements DataGeneratorEntrypoint {

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(TestProvider::new);
	}

	public static class TestProvider extends FabricDynamicRegistryProvider {

		public TestProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void configure(HolderLookup.Provider registries, Entries entries) {
			entries.add(
					ResourceKey.create(Registries.TEST_ENVIRONMENT, METAcraftLib.getID("default")),
					new TestEnvironmentDefinition.SetGameRules(
							new GameRuleMap.Builder().set(
									GameRules.SPAWN_MOBS, false
							).build()
					)
			);
		}

		@Override
		public String getName() {
			return "TestEnvironments";
		}
	}

}
