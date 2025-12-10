package nu.metacraft.bosses.item;

import nu.metacraft.lib.condition.conditions.NotInWall;
import nu.metacraft.lib.condition.conditions.SolidBlockBelow;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.item.boss_wands.DoubleTeamWand;
import nu.metacraft.bosses.item.boss_wands.EvokerFangsWand;
import nu.metacraft.bosses.item.boss_wands.ReinforcementsWand;
import nu.metacraft.bosses.item.components.BossComponents;
import nu.metacraft.bosses.util.DoubleTeamHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;

public class BossItems {

	public static final Item EVOKER_FANGS_WAND = register(
			"evoker_fangs_wand",
			EvokerFangsWand::new,
			new Item.Properties().fireResistant().stacksTo(1).rarity(Rarity.EPIC).component(
					BossComponents.MAX_RANGE, 64.0
			)
	);

	public static final Item SPAWN_REINFORCEMENTS_WAND = register(
			"spawn_reinforcements_wand",
			ReinforcementsWand::new,
			new Item.Properties().fireResistant().stacksTo(1).rarity(Rarity.EPIC).component(
					BossComponents.TRY_COUNT, ReinforcementsWand.DEFAULT_TRY_COUNT
			).component(
					BossComponents.SPAWNS, WeightedList.<EntityHelper.SpawnEntry>builder().add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.ZOMBIE),
									Optional.of(AllOfCondition.allOf(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									EntitySpawnReason.REINFORCEMENT, 20, 10
							)
					).add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.SKELETON),
									Optional.of(AllOfCondition.allOf(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									EntitySpawnReason.REINFORCEMENT, 20, 10
							)
					).add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.CREEPER),
									Optional.of(AllOfCondition.allOf(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									EntitySpawnReason.REINFORCEMENT, 20, 10
							)
					).add(
							EntityHelper.SpawnEntry.createEntry(
									EntityHelper.SpawnEntry.createEntityNBTFrom(EntityType.SPIDER),
									Optional.of(AllOfCondition.allOf(List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance()))),
									EntitySpawnReason.REINFORCEMENT,20, 10
							)
					).build()
			)
	);

	public static final Item DOUBLE_TEAM_WAND = register(
			"double_team_wand",
			DoubleTeamWand::new,
			new Item.Properties().fireResistant().stacksTo(1).rarity(Rarity.EPIC).component(
					BossComponents.DOUBLE_TEAM_SETTINGS, new DoubleTeamHandler.Settings(
							ConstantInt.of(5), 10,
							UniformFloat.of(5, 10), 0.25,
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
			String id, Function<Item.Properties, Item> item, Item.Properties settings
	) {
		var key = ResourceKey.create(Registries.ITEM, METAcraftBosses.getID(id));
		if (item instanceof BlockItem b) {
			Item.BY_BLOCK.put(b.getBlock(), b);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item.apply(settings.setId(key)));
	}

}
