package se.datasektionen.mc.metacraft_lib.condition.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.SpawnReason;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.util.dynamic.Codecs;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftConditions;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftContextParameters;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.*;

public class MatchesSpawnReason implements LootCondition {

	public static final MapCodec<MatchesSpawnReason> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.withAlternative(
							Codecs.nonEmptyList(ExtraCodecs.SPAWN_REASON_CODEC.listOf()),
							ExtraCodecs.SPAWN_REASON_CODEC, List::of
					).fieldOf("spawn_reason").forGetter(a -> a.spawnReasons.stream().toList())
			).apply(instance, MatchesSpawnReason::new)
	);

	private final Set<SpawnReason> spawnReasons;

	public MatchesSpawnReason(List<SpawnReason> spawnReasons) {
		this.spawnReasons = EnumSet.copyOf(spawnReasons);
	}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.MATCHES_SPAWN_REASON;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var spawnReason = lootContext.get(METAcraftContextParameters.SPAWN_REASON);
		if (spawnReason == null) {
			return false;
		}
		return spawnReasons.contains(spawnReason);
	}

	@Override
	public Set<LootContextParameter<?>> getRequiredParameters() {
		return Set.of(METAcraftContextParameters.SPAWN_REASON);
	}
}
