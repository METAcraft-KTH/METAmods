package nu.metacraft.lib.condition;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.condition.conditions.*;
import nu.metacraft.lib.condition.entity_sub_predicates.HealthPredicate;
import nu.metacraft.lib.condition.entity_sub_predicates.IsMovingPredicate;
import nu.metacraft.lib.condition.entity_sub_predicates.MultiSubPredicate;

public class METAcraftConditions {

	public static final LootItemConditionType SPAWN_PREDICATE = register("spawn_predicate", ValidateSpawnPredicate.CODEC);
	public static final LootItemConditionType SPAWN_RESTRICTION = register("spawn_restriction", ValidateSpawnRestriction.CODEC);
	public static final LootItemConditionType SOLID_BLOCK_BELOW = register("solid_block_below", SolidBlockBelow.CODEC);
	public static final LootItemConditionType NOT_IN_WALL = register("not_in_wall", NotInWall.CODEC);
	public static final LootItemConditionType IS_NEAR_GROUND = register("is_near_ground", IsNearGround.CODEC);
	public static final LootItemConditionType MATCHES_SPAWN_REASON = register("spawn_reason", MatchesSpawnReason.CODEC);
	public static final LootItemConditionType HAS_SKY_ACCESS = register("has_sky_access", HasSkyAccess.CODEC);
	public static final LootItemConditionType BLOCK_BELOW_CAN_SPAWN = register("block_below_can_spawn", BlockBelowCanSpawn.CODEC);
	public static final LootItemConditionType LOCAL_WEATHER = register("local_weather", LocalWeather.CODEC);


	public static void init() {
		METAcraftContextParameters.init();
		METAcraftContexTypes.init();
		EntitySubPredicates.init();
	}

	private static LootItemConditionType register(String id, MapCodec<? extends LootItemCondition> codec) {
		return Registry.register(
				BuiltInRegistries.LOOT_CONDITION_TYPE, METAcraftLib.getID(id), new LootItemConditionType(codec)
		);
	}

	public static class EntitySubPredicates {

		public static void init() {
			register("and", MultiSubPredicate.AndSubPredicate.CODEC);
			register("or", MultiSubPredicate.OrSubPredicate.CODEC);
			register("health", HealthPredicate.CODEC);
			register("is_moving", IsMovingPredicate.CODEC);
		}

		private static void register(String id, MapCodec<? extends EntitySubPredicate> codec) {
			Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, METAcraftLib.getID(id), codec);
		}
	}

}
