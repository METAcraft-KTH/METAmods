package se.datasektionen.mc.portalopening.raid;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.intprovider.ConstantIntProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record Wave(Multimap<String, MobEntry> manualSpawns, Optional<AutoSpawnEntry> autoSpawns, String command) {

	public Wave(MobEntry mobs) {
		this(
				HashMultimap.create(),
				Optional.of(new AutoSpawnEntry(mobs, ConstantIntProvider.create(500), 5)),
				"tellraw @a {\"text\":\"Starting Next Wave\"}"
		);
		this.manualSpawns.put("groupName", mobs);
	}

	public Wave(Map<String, List<MobEntry>> mobs, Optional<AutoSpawnEntry> autoSpawns, String command) {
		this(HashMultimap.create(), autoSpawns, command);
		mobs.forEach(this.manualSpawns::putAll);
	}

	public static final Codec<Wave> wave = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, MobEntry.CODEC.listOf()).fieldOf("manualSpawns").forGetter(wave -> {
				return wave.manualSpawns.asMap().entrySet().stream().map(
						entry -> Pair.of(entry.getKey(), new ArrayList<>(entry.getValue()))
				).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
			}),
			AutoSpawnEntry.CODEC.optionalFieldOf("autoSpawns").forGetter(Wave::autoSpawns),
			Codec.STRING.fieldOf("command").forGetter(Wave::command)
	).apply(instance, Wave::new));

}
