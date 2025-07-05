package nu.metacraft.lib.condition;

import com.mojang.serialization.MapCodec;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.predicate.entity.EntitySubPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.condition.conditions.*;
import nu.metacraft.lib.condition.entity_sub_predicates.HealthPredicate;
import nu.metacraft.lib.condition.entity_sub_predicates.IsMovingPredicate;
import nu.metacraft.lib.condition.entity_sub_predicates.MultiSubPredicate;

public class METAcraftConditions {

	public static final LootConditionType SPAWN_PREDICATE = register("spawn_predicate", ValidateSpawnPredicate.CODEC);
	public static final LootConditionType SPAWN_RESTRICTION = register("spawn_restriction", ValidateSpawnRestriction.CODEC);
	public static final LootConditionType SOLID_BLOCK_BELOW = register("solid_block_below", SolidBlockBelow.CODEC);
	public static final LootConditionType NOT_IN_WALL = register("not_in_wall", NotInWall.CODEC);
	public static final LootConditionType IS_NEAR_GROUND = register("is_near_ground", IsNearGround.CODEC);
	public static final LootConditionType MATCHES_SPAWN_REASON = register("spawn_reason", MatchesSpawnReason.CODEC);
	public static final LootConditionType HAS_SKY_ACCESS = register("has_sky_access", HasSkyAccess.CODEC);
	public static final LootConditionType BLOCK_BELOW_CAN_SPAWN = register("block_below_can_spawn", BlockBelowCanSpawn.CODEC);
	public static final LootConditionType LOCAL_WEATHER = register("local_weather", LocalWeather.CODEC);


	public static void init() {
		METAcraftContextParameters.init();
		METAcraftContexTypes.init();
		EntitySubPredicates.init();
	}

	private static LootConditionType register(String id, MapCodec<? extends LootCondition> codec) {
		return Registry.register(
				Registries.LOOT_CONDITION_TYPE, METAcraftLib.getID(id), new LootConditionType(codec)
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
			Registry.register(Registries.ENTITY_SUB_PREDICATE_TYPE, METAcraftLib.getID(id), codec);
		}
	}

}
