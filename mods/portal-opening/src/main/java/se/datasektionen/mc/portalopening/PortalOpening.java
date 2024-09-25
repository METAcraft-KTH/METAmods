package se.datasektionen.mc.portalopening;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.config.container.ConfigContainer;
import se.datasektionen.mc.portalopening.raid.MobEntry;
import se.datasektionen.mc.portalopening.raid.Wave;

import java.nio.file.Path;
import java.util.Optional;

public class PortalOpening implements ModInitializer {
	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("portal-opening.json");

	private static ConfigContainer<Config> config = ConfigContainer.Builder.create(
			Config.CODEC, configPath, () -> {
				var config = new Config();
				NbtCompound piglin = new NbtCompound();
				piglin.putString("id", Registries.ENTITY_TYPE.getId(EntityType.PIGLIN).toString());

				config.getWaves().add(new Wave(new MobEntry(
						DataPool.of(piglin), ConstantIntProvider.create(1), 0.5, Optional.of(ConstantIntProvider.create(1))
				)));

				NbtCompound ghast = new NbtCompound();
				ghast.putString("id", Registries.ENTITY_TYPE.getId(EntityType.GHAST).toString());

				NbtCompound brute = new NbtCompound();
				brute.putString("id", Registries.ENTITY_TYPE.getId(EntityType.PIGLIN_BRUTE).toString());

				NbtCompound hoglin = new NbtCompound();
				hoglin.putString("id", Registries.ENTITY_TYPE.getId(EntityType.HOGLIN).toString());

				config.getWaves().add(new Wave(new MobEntry(new DataPool.Builder<NbtCompound>().add(
						ghast, 2
				).add(
						brute, 1
				).build(), UniformIntProvider.create(5, 10), 0.75, Optional.of(UniformIntProvider.create(1, 2)))));
				return config;
			}
	).build();

	public static final Logger LOGGER = LogManager.getLogger("portal-opening");


	@Override
	public void onInitialize() {
		Commands.init();
		getConfig();
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			PortalOpeningDimensionData.getInstance(world).tick();
		});
	}


	/**
	 * Returns the config.
	 * DO NOT CACHE THIS IN VARIABLES FOR LONGER PERIODS OF TIME!
	 * @return The config.
	 */
	public static Config getConfig() {
		return config.get();
	}

	public static void reloadConfig() {
		config = null;
	}
}
