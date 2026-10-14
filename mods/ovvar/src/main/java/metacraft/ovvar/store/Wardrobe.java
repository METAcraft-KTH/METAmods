package metacraft.ovvar.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Everything the store keeps for one player, as one row: their design per chapter (the sewn
 * patches, in sewing order) and their stash (the patches they own but have not sewn, counted by
 * patch id). Visuals and patches only — nothing about the items that carry them.
 *
 * A patch lives in exactly one place, in the stash or on a design, and a sew or unpick moves it
 * between the two within this one record, so one compare-and-set write covers both sides.
 * {@code version} is the store's version of the row; 0 is "not in the store yet".
 */
public record Wardrobe(Map<Chapter, SpotPlacements> designs, Map<String, Integer> stash, long version) {
	public static final Wardrobe NONE = new Wardrobe(Map.of(), Map.of(), 0);

	private static final Codec<Map<String, List<Placement>>> DESIGNS_CODEC = Codec.unboundedMap(Codec.STRING, Placement.CODEC.listOf());

	public static final Codec<Wardrobe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			DESIGNS_CODEC.optionalFieldOf("designs", Map.of()).forGetter(w -> {
				Map<String, List<Placement>> out = new TreeMap<>();
				w.designs.forEach((chapter, placements) -> out.put(chapter.id, placements.asPlacementList()));
				return out;
			}),
			Codec.unboundedMap(Codec.STRING, Codec.intRange(1, Integer.MAX_VALUE)).optionalFieldOf("stash", Map.of()).forGetter(w -> w.stash),
			Codec.LONG.optionalFieldOf("version", 0L).forGetter(Wardrobe::version)
	).apply(instance, (designs, stash, version) -> {
		Map<Chapter, SpotPlacements> out = new TreeMap<>();
		designs.forEach((id, list) -> {
			Chapter chapter = Chapter.byId(id);
			if (chapter != null && !list.isEmpty()) out.put(chapter, SpotPlacements.fromList(list).getOrThrow());
		});
		return new Wardrobe(out, stash, version);
	}));

	public Wardrobe {
		designs = Collections.unmodifiableMap(new TreeMap<>(designs));
		stash = Collections.unmodifiableMap(new TreeMap<>(stash));
	}

	// ---- designs

	public Optional<SpotPlacements> design(Chapter chapter) {
		return Optional.ofNullable(designs.get(chapter));
	}

	/** Same sewn patches on this chapter, whatever else differs. */
	public boolean sameDesign(Chapter chapter, Optional<SpotPlacements> other) {
		return Objects.equals(design(chapter), other);
	}

	public Optional<Placement> at(Chapter chapter, Spot spot) {
		return design(chapter).flatMap(p -> p.getPlacement(spot));
	}

	/** The chapter's design replaced outright, patches conjured or dropped (gamemaster commands). */
	public Wardrobe withDesign(Chapter chapter, @Nullable SpotPlacements placements) {
		Map<Chapter, SpotPlacements> out = new TreeMap<>(designs);
		if (placements == null) out.remove(chapter);
		else out.put(chapter, placements);
		return new Wardrobe(out, stash, version);
	}

	// ---- the stash

	public int count(Patches.Patch patch) {
		return stash.getOrDefault(patch.id(), 0);
	}

	public int stashSize() {
		return stash.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** The stash's patches, catalogue order, each once however many are held. */
	public List<Patches.Patch> stashed() {
		List<Patches.Patch> out = new ArrayList<>();
		for (Patches.Patch patch : Patches.all()) if (count(patch) > 0) out.add(patch);
		return out;
	}

	/** {@code n} more of a patch in the stash (n may be negative; never below zero). */
	public Wardrobe add(Patches.Patch patch, int n) {
		Map<String, Integer> out = new TreeMap<>(stash);
		int now = count(patch) + n;
		if (now <= 0) out.remove(patch.id());
		else out.put(patch.id(), now);
		return new Wardrobe(designs, out, version);
	}

	// ---- moving patches between the two

	/** Is there one of the patch in the stash, and is the spot free for it? */
	public boolean canSew(Chapter chapter, Placement placement) {
		return count(placement.patch()) > 0 && SpotPlacements.canApply(design(chapter), placement);
	}

	/** One patch out of the stash and onto the design; empty when {@link #canSew} says no. */
	public Optional<Wardrobe> sew(Chapter chapter, Placement placement) {
		if (!canSew(chapter, placement)) return Optional.empty();
		return Optional.of(add(placement.patch(), -1).withDesign(chapter, SpotPlacements.apply(design(chapter), placement)));
	}

	/** The patch on the spot off the design and back into the stash; empty when there is none. */
	public Optional<Wardrobe> unpick(Chapter chapter, Spot spot) {
		Optional<Placement> there = at(chapter, spot);
		if (there.isEmpty()) return Optional.empty();
		SpotPlacements rest = design(chapter).flatMap(p -> p.remove(spot)).orElse(null);
		return Optional.of(withDesign(chapter, rest).add(there.get().patch(), 1));
	}

	// ---- housekeeping

	public boolean isEmpty() {
		return designs.isEmpty() && stash.isEmpty();
	}

	public Wardrobe withVersion(long version) {
		return new Wardrobe(designs, stash, version);
	}

	/** Same designs and stash, whatever the version. */
	public boolean sameContents(Wardrobe other) {
		return designs.equals(other.designs) && stash.equals(other.stash);
	}
}
