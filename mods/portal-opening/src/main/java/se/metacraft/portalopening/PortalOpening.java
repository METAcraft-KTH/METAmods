package se.metacraft.portalopening;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import nu.metacraft.lib.config.container.ConfigContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.portalopening.raid.MobEntry;
import se.metacraft.portalopening.raid.Wave;

import java.nio.file.Path;
import java.util.Optional;

public class PortalOpening implements ModInitializer {
	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("portal-opening.json");

	private static final ConfigContainer<Config> config = ConfigContainer.Builder.create(
			Config.CODEC, () -> {
				var config = new Config();
				CompoundTag piglin = new CompoundTag();
				piglin.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIGLIN).toString());

				config.getWaves().add(new Wave(new MobEntry(
						WeightedList.of(MobEntry.EntityEntry.fromData(piglin)), ConstantInt.of(1), 0.5, Optional.of(ConstantInt.of(1))
				)));

				CompoundTag ghast = new CompoundTag();
				ghast.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.GHAST).toString());

				CompoundTag brute = new CompoundTag();
				brute.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIGLIN_BRUTE).toString());

				CompoundTag hoglin = new CompoundTag();
				hoglin.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.HOGLIN).toString());

				config.getWaves().add(new Wave(new MobEntry(new WeightedList.Builder<MobEntry.EntityEntry>().add(
						MobEntry.EntityEntry.fromData(ghast), 2
				).add(
						MobEntry.EntityEntry.fromData(brute), 1
				).build(), UniformInt.of(5, 10), 0.75, Optional.of(UniformInt.of(1, 2)))));
				return config;
			}
	).build(configPath);

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
		config.reload();
	}
}
