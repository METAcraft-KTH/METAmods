package se.datasektionen.mc.metacraft_season_4.item;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.floatprovider.UniformFloatProvider;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import se.datasektionen.mc.metacraft_lib.condition.conditions.NotInWall;
import se.datasektionen.mc.metacraft_lib.condition.conditions.SolidBlockBelow;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.datasektionen.mc.metacraft_season_4.block.Season4Blocks;
import se.datasektionen.mc.metacraft_season_4.item.boss_wands.DoubleTeamWand;
import se.datasektionen.mc.metacraft_season_4.item.boss_wands.EvokerFangsWand;
import se.datasektionen.mc.metacraft_season_4.item.boss_wands.ReinforcementsWand;
import se.datasektionen.mc.metacraft_season_4.item.components.Season4Components;
import se.datasektionen.mc.metacraft_season_4.util.DoubleTeamHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class Season4Items {

	private static final Item CAMPUS_LODESTONE = register(
			"campus_lodestone", settings -> new PolymerBlockItem(
				Season4Blocks.CAMPUS_LODESTONE, settings,
				Items.STRUCTURE_VOID, true
			),
			new Item.Settings().component(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.translatable("block.metacraft.campus_lodestone.lore1").styled(style -> style.withColor(Formatting.WHITE).withItalic(false)),
				Text.translatable("block.metacraft.campus_lodestone.lore2").styled(style -> style.withColor(Formatting.RED).withItalic(false)),
				Text.empty(),
				Text.translatable("block.metacraft.campus_lodestone.lore3"),
				Text.translatable("block.metacraft.campus_lodestone.lore4")
			))).component(DataComponentTypes.RARITY, Rarity.EPIC).maxCount(1).useBlockPrefixedTranslationKey()
	);

	public static final Item EVOKER_FANGS_WAND = register(
			"evoker_fangs_wand",
			EvokerFangsWand::new,
			new Item.Settings().fireproof().maxCount(1).rarity(Rarity.EPIC).component(
					Season4Components.MAX_RANGE, 64.0
			)
	);

	public static final Item SPAWN_REINFORCEMENTS_WAND = register(
			"spawn_reinforcements_wand",
			ReinforcementsWand::new,
			new Item.Settings().fireproof().maxCount(1).rarity(Rarity.EPIC).component(
					Season4Components.TRY_COUNT, ReinforcementsWand.DEFAULT_TRY_COUNT
			).component(
					Season4Components.SPAWNS, Pool.<EntityHelper.SpawnEntry>builder().add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.ZOMBIE),
									Optional.of(AllOfLootCondition.create(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									SpawnReason.REINFORCEMENT, 20, 10
							)
					).add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.SKELETON),
									Optional.of(AllOfLootCondition.create(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									SpawnReason.REINFORCEMENT, 20, 10
							)
					).add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.CREEPER),
									Optional.of(AllOfLootCondition.create(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									SpawnReason.REINFORCEMENT, 20, 10
							)
					).add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.SPIDER),
									Optional.of(AllOfLootCondition.create(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									SpawnReason.REINFORCEMENT,20, 10
							)
					).build()
			)
	);

	public static final Item DOUBLE_TEAM_WAND = register(
			"double_team_wand",
			DoubleTeamWand::new,
			new Item.Settings().fireproof().maxCount(1).rarity(Rarity.EPIC).component(
					Season4Components.DOUBLE_TEAM_SETTINGS, new DoubleTeamHandler.Settings(
							ConstantIntProvider.create(5), 10,
							UniformFloatProvider.create(5, 10), 0.25,
							EntityHelper.SpawnEntry.createNBTFromMap(
									Map.of("Hostile", true)
							),
							Optional.of(true)
					)
			)
	);

	public static void init() {
		Season4Components.init();
	}

	private static Item register(
			String id, Function<Item.Settings, Item> item, Item.Settings settings
	) {
		var key = RegistryKey.of(RegistryKeys.ITEM, Season4.getID(id));
		if (item instanceof BlockItem b) {
			Item.BLOCK_ITEMS.put(b.getBlock(), b);
		}
		return Registry.register(Registries.ITEM, key, item.apply(settings.registryKey(key)));
	}

}
