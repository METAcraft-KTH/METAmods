package nu.metacraft.repair_fix;

import com.google.common.collect.ImmutableList;
import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.Setting;
import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.Settings;
import io.github.fablabsmc.fablabs.api.fiber.v1.exception.ValueDeserializationException;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.FiberSerialization;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.JanksonValueSerializer;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.repair_fix.parse.reader.EnchantmentConfigReader;
import nu.metacraft.repair_fix.parse.reader.RepairConfigReader;
import nu.metacraft.repair_fix.util.FutureValue;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Settings(onlyAnnotated = true)
public final class RepairFixConfig {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(RepairFix.modid + ".json");
	private static final JanksonValueSerializer jankson = new JanksonValueSerializer(false);

	public static void init() {
		config = new RepairFixConfig();
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
		config.onLoad();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			getConfig().repairConfigReader.complete(new RepairConfigReader(
					server.getRegistryManager(), server.getOverworld().getEnabledFeatures()
			));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			getConfig().repairConfigReader.clear();
		});
	}

	public void onLoad() {
		enchantmentConfigReader.loadConfig();
		repairConfigReader.apply(RepairConfigReader::loadConfig);
	}

	private static RepairFixConfig config;
	private static ConfigTree configTree;

	public static RepairFixConfig getConfig() {
		return config;
	}

	@Setting(
			comment = "Allows you to change the maximum repair cost. " +
					"To remove the maximum repair cost, set it to -1. " +
					"This setting will still show \"Too Expensive\" on the client unless the client installs a mod, " +
					"but the result will still show up in the output slot on Java Edition." +
					"This setting does not work on Bedrock Edition for now."
	)
	@Setting.Constrain.Range(min=-1)
	private int maxRepairConst = 40;

	@Setting(comment = "If set to true, the xp cost will never ever exceed the maximum repair cost stated above.")
	public boolean capAtMaxLevel = true;

	public int getMaxRepairCost() {
		if (maxRepairConst < 0) {
			return Integer.MAX_VALUE;
		} else {
			return maxRepairConst;
		}
	}

	public static final String COMBINE_SYMBOL = "&";
	public static final String SEPARATE_SYMBOL = "|";
	public static final String ADDITIONAL_COST_SYMBOL = "$";

	@Setting(
			comment = "List of items and what should or should not repair them. " +
					"Uses the following syntax: \"<itemToRepair><symbol><repairMaterial>\" where <symbol> is \"" + COMBINE_SYMBOL + "\" " +
					"to allow repair and \"" + SEPARATE_SYMBOL + "\" to prevent repair." +
					"You can also use " + ADDITIONAL_COST_SYMBOL + " to set how many items of the material it takes to repair the item fully."
	)
	public List<? extends String> repairItemCostBalancing = new ArrayList<>(
			ImmutableList.of(

					"netherite_helmet&netherite_ingot$1",
					"netherite_chestplate&netherite_ingot$1",
					"netherite_leggings&netherite_ingot$1",
					"netherite_boots&netherite_ingot$1",
					"netherite_sword&netherite_ingot$1",
					"netherite_axe&netherite_ingot$1",
					"netherite_pickaxe&netherite_ingot$1",
					"netherite_shovel&netherite_ingot$1",
					"netherite_hoe&netherite_ingot$1",

					"netherite_helmet&netherite_scrap$4",
					"netherite_chestplate&netherite_scrap$4",
					"netherite_leggings&netherite_scrap$4",
					"netherite_boots&netherite_scrap$4",
					"netherite_sword&netherite_scrap$4",
					"netherite_axe&netherite_scrap$4",
					"netherite_pickaxe&netherite_scrap$4",
					"netherite_shovel&netherite_scrap$4",
					"netherite_hoe&netherite_scrap$4"
			)
	);
	public final FutureValue<RepairConfigReader> repairConfigReader = new FutureValue<>();


	@Setting(
			comment = "List of enchantment pairs that should be allowed or not allowed. " +
					"Uses the following syntax: \"<enchantment><symbol><enchantment>\" where <symbol> is \"" + COMBINE_SYMBOL + "\" " +
					"to force compatibility and \"" + SEPARATE_SYMBOL + "\" to force incompatibility." +
					"You can also use " + ADDITIONAL_COST_SYMBOL + " to set a custom additional cost." +
					"Examples:  \"mending" + COMBINE_SYMBOL + "infinity" + ADDITIONAL_COST_SYMBOL + "10\" makes mending and infinity no longer incompatible, but costs 10 extra levels. " +
					"\"minecraft:mending" + COMBINE_SYMBOL + "minecraft:infinity\" is also valid." +
					" \"sharpness" + SEPARATE_SYMBOL + "looting\" makes looting and sharpness incompatible." +
					"This setting also does not work on Bedrock Edition."
	)
	public List<String> enchantmentCompatOverrides = new ArrayList<>(
			ImmutableList.of(
					"mending&infinity$10"
			)
	);

	public final EnchantmentConfigReader enchantmentConfigReader = new EnchantmentConfigReader();


	public enum RemoveBaseCostMode {
		DEFAULT,
		ENCHANTING_ONLY,
		NONE
	}

	@Setting(
			comment = "This controls the method used when increasing the cost of items while enchanting. It has 3 modes. " +
					"DEFAULT is like vanilla, everything will double the cost, including repairing items. " +
					"ENCHANTING_ONLY is the recommended mode. In this mode, it will only double the cost if at least one enchantment was added. " +
					"NONE removes incremental enchantment costs completely. This makes enchanting with an anvil very cheap."
	)
	public RemoveBaseCostMode baseCostIncreaseMode = RemoveBaseCostMode.ENCHANTING_ONLY;


}
