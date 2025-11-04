package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

public class ValidateSpawnRestriction implements LootItemCondition {

	private static final ValidateSpawnRestriction INSTANCE = new ValidateSpawnRestriction();

	public static final MapCodec<ValidateSpawnRestriction> CODEC = MapCodec.unit(INSTANCE);

	public static ValidateSpawnRestriction getInstance() {
		return INSTANCE;
	}

	private ValidateSpawnRestriction() {}

	@Override
	public LootItemConditionType getType() {
		return METAcraftConditions.SPAWN_RESTRICTION;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var pos = lootContext.getOptionalParameter(LootContextParams.ORIGIN);
		if (pos == null) return false;
		return METAcraftContextParameters.getEntityType(lootContext).map(
			entityType -> SpawnPlacements.isSpawnPositionOk(
					entityType, lootContext.getLevel(),
					BlockPos.containing(pos)
			)
		).orElse(false);
	}

	@Override
	public void validate(ValidationContext reporter) {
		LootItemCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityType(reporter);
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.ORIGIN);
	}
}
