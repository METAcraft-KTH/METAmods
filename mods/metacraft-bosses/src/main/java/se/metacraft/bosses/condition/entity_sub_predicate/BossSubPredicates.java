package se.metacraft.bosses.condition.entity_sub_predicate;

import com.mojang.serialization.MapCodec;
import net.minecraft.predicate.entity.EntitySubPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.metacraft.bosses.METAcraftBosses;

public class BossSubPredicates {

	public static final MapCodec<BossPredicateType> BOSS_PREDICATE = register("boss", BossPredicateType.CODEC);

	public static void init() {

	}

	private static <T extends EntitySubPredicate> MapCodec<T> register(String id, MapCodec<T> codec) {
		return Registry.register(Registries.ENTITY_SUB_PREDICATE_TYPE, METAcraftBosses.getID(id), codec);
	}

}
