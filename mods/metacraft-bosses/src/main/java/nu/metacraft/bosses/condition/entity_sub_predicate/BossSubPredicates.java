package nu.metacraft.bosses.condition.entity_sub_predicate;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.criterion.EntitySubPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import nu.metacraft.bosses.METAcraftBosses;

public class BossSubPredicates {

	public static final MapCodec<BossPredicateType> BOSS_PREDICATE = register("boss", BossPredicateType.CODEC);

	public static void init() {

	}

	private static <T extends EntitySubPredicate> MapCodec<T> register(String id, MapCodec<T> codec) {
		return Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, METAcraftBosses.getID(id), codec);
	}

}
