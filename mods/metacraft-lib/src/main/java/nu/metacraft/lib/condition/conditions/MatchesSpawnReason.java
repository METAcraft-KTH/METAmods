package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;
import nu.metacraft.lib.util.METACodecs;

import java.util.*;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class MatchesSpawnReason implements LootItemCondition {

	public static final MapCodec<MatchesSpawnReason> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.withAlternative(
							net.minecraft.util.ExtraCodecs.nonEmptyList(METACodecs.SPAWN_REASON_CODEC.listOf()),
							METACodecs.SPAWN_REASON_CODEC, List::of
					).fieldOf("spawn_reason").forGetter(a -> a.spawnReasons.stream().toList())
			).apply(instance, MatchesSpawnReason::new)
	);

	private final Set<EntitySpawnReason> spawnReasons;

	public MatchesSpawnReason(List<EntitySpawnReason> spawnReasons) {
		this.spawnReasons = EnumSet.copyOf(spawnReasons);
	}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.MATCHES_SPAWN_REASON;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var spawnReason = lootContext.getOptionalParameter(METAcraftContextParameters.SPAWN_REASON);
		if (spawnReason == null) {
			return false;
		}
		return spawnReasons.contains(spawnReason);
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(METAcraftContextParameters.SPAWN_REASON);
	}
}
