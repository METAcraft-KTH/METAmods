package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.context.ContextParameter;
import net.minecraft.util.math.BlockPos;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;
import nu.metacraft.lib.util.ExtraCodecs;

import java.util.Optional;
import java.util.Set;

public class ValidateSpawnPredicate implements LootCondition {

	public static final MapCodec<ValidateSpawnPredicate> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ExtraCodecs.SPAWN_REASON_CODEC.optionalFieldOf("spawn_reason_override").forGetter(a -> a.spawnReasonOverride)
			).apply(instance, ValidateSpawnPredicate::new)
	);

	private final Optional<SpawnReason> spawnReasonOverride;

	public ValidateSpawnPredicate(Optional<SpawnReason> spawnReasonOverride) {
		this.spawnReasonOverride = spawnReasonOverride;
	}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.SPAWN_PREDICATE;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var pos = lootContext.get(LootContextParameters.ORIGIN);
		if (pos == null) return false;
		var spawnReason = lootContext.get(METAcraftContextParameters.SPAWN_REASON);
		if (spawnReason == null && spawnReasonOverride.isEmpty()) {
			return false;
		}
		return METAcraftContextParameters.getEntityType(lootContext).map(
			entityType -> SpawnRestriction.canSpawn(
					entityType, lootContext.getWorld(), spawnReasonOverride.orElse(spawnReason),
					BlockPos.ofFloored(pos),
					lootContext.getRandom()
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
		return spawnReasonOverride.isPresent() ? Set.of(LootContextParameters.ORIGIN) : Set.of(LootContextParameters.ORIGIN, METAcraftContextParameters.SPAWN_REASON);
	}
}
