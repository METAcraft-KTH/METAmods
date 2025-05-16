package se.metacraft.bosses.item;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Rarity;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.floatprovider.UniformFloatProvider;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import se.datasektionen.mc.metacraft_lib.condition.conditions.NotInWall;
import se.datasektionen.mc.metacraft_lib.condition.conditions.SolidBlockBelow;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.metacraft.bosses.METAcraftBosses;
import se.metacraft.bosses.item.boss_wands.DoubleTeamWand;
import se.metacraft.bosses.item.boss_wands.EvokerFangsWand;
import se.metacraft.bosses.item.boss_wands.ReinforcementsWand;
import se.metacraft.bosses.item.components.BossComponents;
import se.metacraft.bosses.util.DoubleTeamHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class BossItems {

	public static final Item EVOKER_FANGS_WAND = register(
			"evoker_fangs_wand",
			EvokerFangsWand::new,
			new Item.Settings().fireproof().maxCount(1).rarity(Rarity.EPIC).component(
					BossComponents.MAX_RANGE, 64.0
			)
	);

	public static final Item SPAWN_REINFORCEMENTS_WAND = register(
			"spawn_reinforcements_wand",
			ReinforcementsWand::new,
			new Item.Settings().fireproof().maxCount(1).rarity(Rarity.EPIC).component(
					BossComponents.TRY_COUNT, ReinforcementsWand.DEFAULT_TRY_COUNT
			).component(
					BossComponents.SPAWNS, DataPool.<EntityHelper.SpawnEntry>builder().add(
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
					BossComponents.DOUBLE_TEAM_SETTINGS, new DoubleTeamHandler.Settings(
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
		BossComponents.init();
	}

	private static Item register(
			String id, Function<Item.Settings, Item> item, Item.Settings settings
	) {
		var key = RegistryKey.of(RegistryKeys.ITEM, METAcraftBosses.getID(id));
		if (item instanceof BlockItem b) {
			Item.BLOCK_ITEMS.put(b.getBlock(), b);
		}
		return Registry.register(Registries.ITEM, key, item.apply(settings.registryKey(key)));
	}

}
