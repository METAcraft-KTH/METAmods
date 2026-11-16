package se.metacraft.ovvar.content.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.Chapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Punkphöz's visits, saved with the world: how many he has made, and on which one each chapter's ovve was last on
 * offer, so the chapters that have waited longest are the likeliest next. Chapter ids it doesn't know (a chapter
 * since removed) are kept as they are.
 */
public final class PunkphozHistory extends SavedData {

	public static final Codec<PunkphozHistory> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("visits", 0).forGetter(PunkphozHistory::visits),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("last_offered", Map.of()).forGetter(h -> h.lastOffered)
	).apply(i, PunkphozHistory::new));

	private static final SavedDataType<PunkphozHistory> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "punkphoz"), PunkphozHistory::new, CODEC, null
	);

	private int visits;
	/** By chapter id, the visit (counted from 0) on which its ovve was last on offer. */
	private final Map<String, Integer> lastOffered = new HashMap<>();

	public PunkphozHistory() {}

	public PunkphozHistory(int visits, Map<String, Integer> lastOffered) {
		this.visits = visits;
		this.lastOffered.putAll(lastOffered);
	}

	/** The world's history, kept in the overworld's data storage. */
	public static PunkphozHistory get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public int visits() {
		return visits;
	}

	/** The visit on which this chapter's ovve was last on offer, or -1 if it never was. */
	public int lastOffered(Chapter chapter) {
		return lastOffered.getOrDefault(chapter.id, -1);
	}

	/** Counts a visit on which these chapters are on offer. Doesn't mark the history dirty: the caller does. */
	void recordVisit(Iterable<Chapter> offered) {
		for (Chapter chapter : offered) lastOffered.put(chapter.id, visits);
		visits++;
	}
}
