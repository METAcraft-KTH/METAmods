package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;
import nu.metacraft.lib.util.METACodecs;

import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ValidateSpawnPredicate implements LootItemCondition {

	public static final MapCodec<ValidateSpawnPredicate> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					METACodecs.SPAWN_REASON_CODEC.optionalFieldOf("spawn_reason_override").forGetter(a -> a.spawnReasonOverride)
			).apply(instance, ValidateSpawnPredicate::new)
	);

	private final Optional<EntitySpawnReason> spawnReasonOverride;

	public ValidateSpawnPredicate(Optional<EntitySpawnReason> spawnReasonOverride) {
		this.spawnReasonOverride = spawnReasonOverride;
	}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.SPAWN_PREDICATE;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var pos = lootContext.getOptional(LootContextParams.ORIGIN);
		if (pos == null) return false;
		var spawnReason = lootContext.getOptional(METAcraftContextParameters.SPAWN_REASON);
		if (spawnReason == null && spawnReasonOverride.isEmpty()) {
			return false;
		}
		return METAcraftContextParameters.getEntityType(lootContext).map(
			entityType -> SpawnPlacements.checkSpawnRules(
					entityType, lootContext.getLevel(), spawnReasonOverride.orElse(spawnReason),
					BlockPos.containing(pos),
					lootContext.getRandom()
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
		return spawnReasonOverride.isPresent() ? Set.of(LootContextParams.ORIGIN) : Set.of(LootContextParams.ORIGIN, METAcraftContextParameters.SPAWN_REASON);
	}
}
