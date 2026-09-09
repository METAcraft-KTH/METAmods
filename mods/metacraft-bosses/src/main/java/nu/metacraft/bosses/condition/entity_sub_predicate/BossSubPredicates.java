package nu.metacraft.bosses.condition.entity_sub_predicate;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.predicates.entity.EntitySubPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import nu.metacraft.bosses.METAcraftBosses;

public class BossSubPredicates {

	public static final Codec<BossPredicateType> BOSS_PREDICATE = register("boss", BossPredicateType.CODEC);

	public static void init() {

	}

	private static <T extends EntitySubPredicate> Codec<T> register(String id, Codec<T> codec) {
		return Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, METAcraftBosses.getID(id), codec);
	}

}
