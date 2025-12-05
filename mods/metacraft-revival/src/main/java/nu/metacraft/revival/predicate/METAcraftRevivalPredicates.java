package nu.metacraft.revival.predicate;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import nu.metacraft.revival.METAcraftRevival;

public class METAcraftRevivalPredicates {

	public static void init() {
		register("revival", RevivalPredicate.CODEC);
	}

	private static void register(String id, MapCodec<? extends EntitySubPredicate> codec) {
		Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, METAcraftRevival.getID(id), codec);
	}

}
