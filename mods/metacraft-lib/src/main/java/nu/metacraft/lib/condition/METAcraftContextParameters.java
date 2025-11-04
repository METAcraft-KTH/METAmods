package nu.metacraft.lib.condition;

import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.mixin.AccessorLootTableReporter;
import nu.metacraft.lib.util.EntityRef;

import java.util.Optional;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

public class METAcraftContextParameters {

	public static final ContextKey<EntityType<?>> ENTITY_TYPE = register("entity_type");
	public static final ContextKey<AABB> BOUNDING_BOX = register("bounding_box");
	public static final ContextKey<EntitySpawnReason> SPAWN_REASON = register("spawn_reason");

	public static void validateEntityType(ValidationContext reporter) {
		var allowed = ((AccessorLootTableReporter) reporter).getContextKeySet().allowed();
		if (!allowed.contains(LootContextParams.THIS_ENTITY) && !allowed.contains(ENTITY_TYPE)) {
			reporter.reportProblem(
					() -> "Parameters " + LootContextParams.THIS_ENTITY + " and " + ENTITY_TYPE +
					" are not provided in this context (note that only one of them is necessary)"
			);
		}
	}

	public static void validateEntityOrBlockEntity(
			ValidationContext reporter, ContextKey<?> additional
	) {
		var allowed = ((AccessorLootTableReporter) reporter).getContextKeySet().allowed();
		if (
				!allowed.contains(LootContextParams.THIS_ENTITY) &&
				!allowed.contains(LootContextParams.BLOCK_ENTITY) &&
				!allowed.contains(additional)
		) {
			reporter.reportProblem(
					() -> "Parameters " + LootContextParams.THIS_ENTITY + ", "  +
					LootContextParams.BLOCK_ENTITY + " and " + additional +
					" are not provided in this context (note that only one of them is necessary)"
			);
		}
	}

	public static Optional<EntityType<?>> getEntityType(LootContext context) {
		if (context.hasParameter(ENTITY_TYPE)) {
			return Optional.ofNullable(context.getOptionalParameter(ENTITY_TYPE));
		}
		if (context.hasParameter(LootContextParams.THIS_ENTITY)) {
			return Optional.ofNullable(context.getOptionalParameter(LootContextParams.THIS_ENTITY).getType());
		}
		return Optional.empty();
	}

	public static Optional<AABB> getBoundingBox(LootContext context) {
		if (context.hasParameter(BOUNDING_BOX)) {
			return Optional.ofNullable(context.getOptionalParameter(BOUNDING_BOX));
		}
		return EntityRef.fromContext(context).map(EntityRef::getBoundingBox);
	}

	public static void init() {

	}

	private static <T> ContextKey<T> register(String id) {
		return new ContextKey<>(METAcraftLib.getID(id));
	}

}
