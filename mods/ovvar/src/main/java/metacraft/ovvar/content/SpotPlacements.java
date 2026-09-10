package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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

	public static final Codec<SpotPlacements> CODEC = ExtraCodecs.nonEmptyList(Placement.CODEC.listOf()).comapFlatMap(
			placements -> {
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
			},
			SpotPlacements::asPlacementList
	);

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
			newMap = patchMap.minus(overlap);
		}
		return new SpotPlacements(newMap.plus(placement.spot(), placement.patch()));
	}

	public static SpotPlacements apply(Optional<SpotPlacements> placements, Placement placement) {
		return placements.map(p -> p.apply(placement)).orElseGet(
				() -> new SpotPlacements(TreePMap.singleton(placement.spot(), placement.patch()))
		);
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
