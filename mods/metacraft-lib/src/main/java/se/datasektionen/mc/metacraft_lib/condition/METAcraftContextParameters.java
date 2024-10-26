package se.datasektionen.mc.metacraft_lib.condition;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.context.ContextParameter;
import net.minecraft.util.math.Box;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorLootTableReporter;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;

import java.util.Optional;

public class METAcraftContextParameters {

	public static final ContextParameter<EntityType<?>> ENTITY_TYPE = register("entity_type");
	public static final ContextParameter<Box> BOUNDING_BOX = register("bounding_box");
	public static final ContextParameter<SpawnReason> SPAWN_REASON = register("spawn_reason");

	public static void validateEntityType(LootTableReporter reporter) {
		var allowed = ((AccessorLootTableReporter) reporter).getContextType().getAllowed();
		if (!allowed.contains(LootContextParameters.THIS_ENTITY) && !allowed.contains(ENTITY_TYPE)) {
			reporter.report(
					"Parameters " + LootContextParameters.THIS_ENTITY + " and " + ENTITY_TYPE +
					" are not provided in this context (note that only one of them is necessary)"
			);
		}
	}

	public static void validateEntityOrBlockEntity(
			LootTableReporter reporter, ContextParameter<?> additional
	) {
		var allowed = ((AccessorLootTableReporter) reporter).getContextType().getAllowed();
		if (
				!allowed.contains(LootContextParameters.THIS_ENTITY) &&
				!allowed.contains(LootContextParameters.BLOCK_ENTITY) &&
				!allowed.contains(additional)
		) {
			reporter.report(
					"Parameters " + LootContextParameters.THIS_ENTITY + ", "  +
					LootContextParameters.BLOCK_ENTITY + " and " + additional +
					" are not provided in this context (note that only one of them is necessary)"
			);
		}
	}

	public static Optional<EntityType<?>> getEntityType(LootContext context) {
		if (context.hasParameter(ENTITY_TYPE)) {
			return Optional.ofNullable(context.get(ENTITY_TYPE));
		}
		if (context.hasParameter(LootContextParameters.THIS_ENTITY)) {
			return Optional.ofNullable(context.get(LootContextParameters.THIS_ENTITY).getType());
		}
		return Optional.empty();
	}

	public static Optional<Box> getBoundingBox(LootContext context) {
		if (context.hasParameter(BOUNDING_BOX)) {
			return Optional.ofNullable(context.get(BOUNDING_BOX));
		}
		return EntityRef.fromContext(context).map(EntityRef::getBoundingBox);
	}

	public static void init() {

	}

	private static <T> ContextParameter<T> register(String id) {
		return new ContextParameter<>(METAcraftLib.getID(id));
	}

}
