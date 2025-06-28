package se.datasektionen.mc.metacraft_lib;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.test.TestEnvironmentDefinition;
import net.minecraft.world.GameRules;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class METAcraftDataGen implements DataGeneratorEntrypoint {

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(TestProvider::new);
	}

	public static class TestProvider extends FabricDynamicRegistryProvider {

		public TestProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
			entries.add(
					RegistryKey.of(RegistryKeys.TEST_ENVIRONMENT, METAcraftLib.getID("default")),
					new TestEnvironmentDefinition.GameRules(
							List.of(new TestEnvironmentDefinition.GameRules.RuleValue<>(GameRules.DO_MOB_SPAWNING, false)),
							List.of()
					)
			);
		}

		@Override
		public String getName() {
			return "TestEnvironments";
		}
	}

}
