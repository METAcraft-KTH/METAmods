package se.datasektionen.mc.metacraft_lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.context.ContextParameter;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftConditions;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftContextParameters;

import java.util.Set;

public class ValidateSpawnRestriction implements LootCondition {

	private static final ValidateSpawnRestriction INSTANCE = new ValidateSpawnRestriction();

	public static final MapCodec<ValidateSpawnRestriction> CODEC = MapCodec.unit(INSTANCE);

	public static ValidateSpawnRestriction getInstance() {
		return INSTANCE;
	}

	private ValidateSpawnRestriction() {}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.SPAWN_RESTRICTION;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var pos = lootContext.get(LootContextParameters.ORIGIN);
		if (pos == null) return false;
		return METAcraftContextParameters.getEntityType(lootContext).map(
			entityType -> SpawnRestriction.isSpawnPosAllowed(
					entityType, lootContext.getWorld(),
					BlockPos.ofFloored(pos)
			)
		).orElse(false);
	}

	@Override
	public void validate(LootTableReporter reporter) {
		LootCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityType(reporter);
	}

	@Override
	public Set<ContextParameter<?>> getAllowedParameters() {
		return Set.of(LootContextParameters.ORIGIN);
	}
}
