package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import metacraft.ovvar.Ovvar;
import net.minecraft.util.ExtraCodecs;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.PMap;
import org.pcollections.TreePMap;

import java.util.*;

public record SpotPlacements(PMap<Spot, Patches.Patch> patchMap) {

	private static boolean overlaps(Spot spot, Map<Spot, Patches.Patch> patches) {
		for (var overlap : spot.overlapping()) {
			if (patches.containsKey(overlap)) {
				return true;
			}
		}
		return false;
	}

	private static boolean overrides(Spot spot, Map<Spot, Patches.Patch> patches) {
		if (patches.containsKey(spot)) return true;
		return overlaps(spot, patches);
	}

	/**
	 * On the wire and on disk a design is its placement keys, {@code cell.patch}. It is decoded key
	 * by key so that one key this build cannot read does not cost a player the rest of their design:
	 * cells and patches come and go (BACK_LOW_LEFT and BACK_LOW_RIGHT went when BACK_BIG arrived, and
	 * their rows with them), and an item or a stored wardrobe row written before that must still open
	 * — minus whatever no longer exists, which is logged rather than passed over in silence.
	 */
	public static final Codec<SpotPlacements> CODEC = ExtraCodecs.nonEmptyList(Codec.STRING.listOf()).comapFlatMap(
			SpotPlacements::fromKeys,
			placements -> placements.asPlacementList().stream().map(Placement::key).toList()
	);

	/** @see #CODEC */
	public static DataResult<SpotPlacements> fromKeys(List<String> keys) {
		List<Placement> placements = new ArrayList<>();
		List<String> dropped = new ArrayList<>();
		for (String key : keys) {
			DataResult<Placement> parsed = Placement.CODEC.parse(JavaOps.INSTANCE, key);
			parsed.ifSuccess(placements::add);
			if (parsed.result().isEmpty()) dropped.add(key);
		}
		if (!dropped.isEmpty()) {
			Ovvar.LOGGER.warn("[ovvar] a saved design names {} placement(s) this build has no cell or patch for; dropping {}", dropped.size(), dropped);
		}
		// Nothing left that can be drawn is nothing: the component goes away rather than becoming an
		// empty design, which is a state the rest of the mod does not have (see remove()).
		if (placements.isEmpty()) return DataResult.error(() -> "no placement in " + keys + " names a cell and a patch this build has");
		return fromList(placements);
	}

	public static DataResult<SpotPlacements> fromList(List<Placement> placements) {
		PMap<Spot, Patches.Patch> patchMap = TreePMap.empty();
		Set<Placement> overlapping = new HashSet<>();
		for (var placement : placements) {
			if (overrides(placement.spot(), patchMap)) {
				overlapping.add(placement);
			} else {
				patchMap = patchMap.plus(placement.spot(), placement.patch());
			}
		}
		if (overlapping.isEmpty()) {
			return DataResult.success(new SpotPlacements(patchMap));
		} else {
			return DataResult.error(() -> "skipping overlapping patches: " + overlapping, new SpotPlacements(patchMap));
		}
	}

	public SpotPlacements {
		if (patchMap.keySet().stream().anyMatch(spot -> overlaps(spot, patchMap))) {
			throw new IllegalArgumentException("Overlapping spots detected!");
		}
	}

	public Optional<SpotPlacements> forPiece(Piece piece) {
		var newMap = PCollectionsHelper.collectToMap(
				patchMap.entrySet().stream().filter(entry -> entry.getKey().piece == piece),
				Map.Entry::getKey, Map.Entry::getValue,
				TreePMap.<Spot, Patches.Patch>empty()
		);
		if (newMap.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(new SpotPlacements(newMap));
		}
	}

	public SpotPlacements apply(Placement placement) {
		var newMap = patchMap;
		for (var overlap : placement.spot().overlapping()) {
			newMap = newMap.minus(overlap);
		}
		return new SpotPlacements(newMap.plus(placement.spot(), placement.patch()));
	}

	public static SpotPlacements apply(Optional<SpotPlacements> placements, Placement placement) {
		return placements.map(p -> p.apply(placement)).orElseGet(
				() -> new SpotPlacements(TreePMap.singleton(placement.spot(), placement.patch()))
		);
	}

	public boolean canApply(Placement placement) {
		if (patchMap.containsKey(placement.spot())) {
			return false;
		}
		for (var overlap : placement.spot().overlapping()) {
			if (patchMap.containsKey(overlap)) {
				return false;
			}
		}
		return true;
	}

	public static boolean canApply(Optional<SpotPlacements> placements, Placement placement) {
		return placements.map(p -> p.canApply(placement)).orElse(true);
	}

	public Optional<Patches.Patch> get(Spot spot) {
		return Optional.ofNullable(patchMap.get(spot));
	}

	public Optional<Placement> getPlacement(Spot spot) {
		return get(spot).map(patch -> new Placement(spot, patch));
	}

	public Optional<SpotPlacements> remove(Spot spot) {
		if (!patchMap.containsKey(spot)) return Optional.of(this);
		var newMap = patchMap.minus(spot);
		if (newMap.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(new SpotPlacements(newMap));
		}
	}

	public List<Placement> asPlacementList() {
		return patchMap.entrySet().stream().map(entry -> new Placement(entry.getKey(), entry.getValue())).toList();
	}

	public static List<Placement> asPlacementList(Optional<SpotPlacements> spotPlacements) {
		return spotPlacements.map(SpotPlacements::asPlacementList).orElse(List.of());
	}

}
