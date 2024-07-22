package se.datasektionen.mc.faster_minecarts;

import com.google.common.collect.ImmutableList;
import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.Setting;
import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.Settings;
import io.github.fablabsmc.fablabs.api.fiber.v1.exception.ValueDeserializationException;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.FiberSerialization;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.JanksonValueSerializer;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree;
import net.fabricmc.loader.api.FabricLoader;
import se.datasektionen.mc.faster_minecarts.configs.BlockBoostConfig;
import se.datasektionen.mc.faster_minecarts.configs.DamageConfig;
import se.datasektionen.mc.faster_minecarts.configs.EntityFactorConfig;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Settings(onlyAnnotated = true)
public class FasterMinecartsConfig {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FasterMinecarts.NAMESPACE + ".json");
	private static final JanksonValueSerializer jankson = new JanksonValueSerializer(false);

	public static void init() {
		config = new FasterMinecartsConfig();
		configTree = ConfigTree.builder().applyFromPojo(config).build();
		var file = configPath.toFile();
		if (file.exists()) {
			try (var stream = new BufferedInputStream(new FileInputStream(file))) {
				FiberSerialization.deserialize(configTree, stream, jankson);
			} catch (IOException | ValueDeserializationException err) {
				err.printStackTrace();
			}
		} else {
			try {
				file.createNewFile();
			} catch (IOException err) {
				err.printStackTrace();
			}
			try (var stream = new BufferedOutputStream(new FileOutputStream(file))) {
				FiberSerialization.serialize(configTree, stream, jankson);
			} catch (IOException err) {
				err.printStackTrace();
			}
		}
		config.onReload();
	}

	public void onReload() {
		topSpeedFactorWithPassengerAccess = new EntityFactorConfig(topSpeedFactorWithPassenger);
		topSpeedFactorNoPassengerAccess = new EntityFactorConfig(topSpeedFactorNoPassenger);
		poweredRailAccelerationFactorAccess = new EntityFactorConfig(poweredRailAccelerationFactor);
		entityDamageBlacklistAccess = new DamageConfig(entityDamageBlacklist, entityDamageBlacklistIsWhitelist);
		blockBoostersAccess = new BlockBoostConfig(blockBoosters);
	}

	private static FasterMinecartsConfig config;
	private static ConfigTree configTree;

	public static FasterMinecartsConfig getConfig() {
		return config;
	}

	@Setting(comment = "If true, all minecarts are super fast. If false, it requires an upgrade.")
	public boolean globalFasterMinecarts = false;

	@Setting(comment = "The top speed of superspeed Minecarts in blocks per second. In vanilla it's 8.")
	@Setting.Constrain.Range(min=0)
	public double maxMinecartSpeed = 60;

	@Setting(comment = "The top speed of superspeed Minecarts underwater in blocks per second. Is 4 in vanilla, meaning half of the normal top speed.")
	@Setting.Constrain.Range(min=0)
	public double maxMinecartSpeedUnderwater = 45;

	@Setting(comment = "Minecarts travelling faster than this will hurt mobs they run into. Set to 0 to disable.")
	@Setting.Constrain.Range(min=0)
	public double dangerousMinecartSpeed = 30 / 3.6 / 20;

	@Setting(comment = "The the factor that the speed will be multiplied by before dealing damage.")
	@Setting.Constrain.Range(min=0)
	public double damageFactor = 2.16 * 20;

	@Setting(
			comment = "Here you can change the top speed of various minecarts when they contain passengers. " +
					"The default value is 1. This is multiplied to the top speed to calculate the real top speed. " +
					"Syntax: entity_id*factor, #tag*factor or just factor to apply to all Minecarts without a custom entry."
	)
	private List<String> topSpeedFactorWithPassenger = new ArrayList<>();
	private static EntityFactorConfig topSpeedFactorWithPassengerAccess;

	public static EntityFactorConfig getTopSpeedFactorWithPassenger() {
		return topSpeedFactorWithPassengerAccess;
	}

	@Setting(
			comment = "Minecarts without entity passengers can only reach 75% of the minecart top speed. " +
					"Here you can customise this value. Syntax: entity_id*factor, #tag*factor or just factor to apply to all Minecarts without a custom entry."
	)
	private List<String> topSpeedFactorNoPassenger = new ArrayList<>();

	private static EntityFactorConfig topSpeedFactorNoPassengerAccess;

	public static EntityFactorConfig getTopSpeedFactorNoPassenger() {
		return topSpeedFactorNoPassengerAccess;
	}

	@Setting(
			comment = "Allows you to set the powered rail acceleration factor per minecart. " +
					"Syntax: entity_id*factor, #tag*factor or just factor to apply to all Minecarts without a custom entry."
	)
	private List<String> poweredRailAccelerationFactor = new ArrayList<>(ImmutableList.of(
			"0.08"
	));
	private static EntityFactorConfig poweredRailAccelerationFactorAccess;

	public static EntityFactorConfig getPoweredRailAccelerationFactor() {
		return poweredRailAccelerationFactorAccess;
	}

	@Setting(
			comment = "List of mobs that will not be damaged by fast-moving minecarts. " +
					"As usual you can add # in front to check for tags. " +
					"Add a \">\" at the end to signify that it shouldn't damage passengers either."
	)
	public List<String> entityDamageBlacklist = new ArrayList<>(ImmutableList.of(
			"minecraft:minecart>",
			"minecraft:chest_minecart",
			"minecraft:command_block_minecart",
			"minecraft:furnace_minecart",
			"minecraft:hopper_minecart",
			"minecraft:tnt_minecart",
			"minecraft:spawner_minecart",
			"minecraft:item",
			"minecraft:experience_orb"
	));

	private static DamageConfig entityDamageBlacklistAccess;

	public static DamageConfig getEntityDamageBlacklist() {
		return entityDamageBlacklistAccess;
	}

	@Setting(comment = "If true, entityDamageBlacklist will be treated like a whitelist.")
	public boolean entityDamageBlacklistIsWhitelist = false;


	@Setting(
			comment = "Here you define blocks that will cause allow minecarts to go faster when placed on top of them." +
					"Syntax: <block>+<top_speed_increase>"
	)
	private List<String> blockBoosters = new ArrayList<>(ImmutableList.of(
			"ice+5",
			"packed_ice+10",
			"blue_ice+20"
	));

	private static BlockBoostConfig blockBoostersAccess;

	public static BlockBoostConfig getBlockBoosters() {
		return blockBoostersAccess;
	}
}
