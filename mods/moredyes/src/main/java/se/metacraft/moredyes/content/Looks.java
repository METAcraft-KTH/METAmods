package se.metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import se.metacraft.moredyes.MoreDyes;
import se.metacraft.moredyes.MoreDyesConfig;
import se.metacraft.moredyes.content.block.ClientStates;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a family is shown to vanilla clients, decided once at startup from the colour count and
 * Polymer's donor budget, and applied to every colour of that family:
 *
 * <ul>
 *   <li>{@link Look#DONOR}: one visible donor state per server state per colour, carrying our model
 *	   as chunk geometry. Best looking and cheapest for the client, but each colour spends states
 *	   from a finite vanilla pool.</li>
 *   <li>{@link Look#DISPLAY}: one shared invisible donor per server state (hitbox and outline) plus
 *	   an item display per placed block carrying the model. Costs nothing per colour.</li>
 * </ul>
 *
 * Polymer itself has no such fallback: when a pool is empty it hands back null, which
 * {@link ClientStates} turns into a startup failure. This planner is what makes adding a colour a
 * data change: the decision is deterministic, logged, and never differs between colours. A server
 * can set it per family in {@code config/moredyes.json5} ({@link MoreDyesConfig}); for testing,
 * {@code -Dmoredyes.look.<family>=donor|display} overrides that.
 */
public final class Looks {
	public enum Look { DONOR, DISPLAY }

	private static final Map<Family, Look> PLAN = new EnumMap<>(Family.class);

	private Looks() {}

	public static Look of(Family family) {
		return PLAN.getOrDefault(family, Look.DONOR);
	}

	public static void plan(int colours) {
		decide(Family.STAINED_GLASS_PANE, barsPools(), 1, colours);
	}

	/**
	 * @param pools	  every pool the family draws from; the tightest one decides
	 * @param perColour  visible states one colour takes from each pool in DONOR mode
	 */
	private static void decide(Family family, List<BlockModelType> pools, int perColour, int colours) {
		int tightest = Integer.MAX_VALUE;
		BlockModelType tightestPool = null;
		for (BlockModelType pool : pools) {
			// Polymer's getBlocksLeft already leaves out the state requestBlock keeps back for the
			// invisible donor (until an empty block was requested), so it is the usable count as is.
			int usable = PolymerBlockResourceUtils.getBlocksLeft(pool);
			if (usable < tightest) {
				tightest = usable;
				tightestPool = pool;
			}
		}
		int fit = perColour == 0 ? Integer.MAX_VALUE : tightest / perColour;
		Look look = fit >= colours ? Look.DONOR : Look.DISPLAY;
		var configured = MoreDyesConfig.get().look(family);
		if (configured != MoreDyesConfig.LookChoice.AUTO) {
			look = configured == MoreDyesConfig.LookChoice.DONOR ? Look.DONOR : Look.DISPLAY;
			MoreDyes.LOGGER.info("[{}] {} look set to {} by config/moredyes.json5", MoreDyes.MOD_ID, family.id, look);
		}
		String override = System.getProperty("moredyes.look." + family.id);
		if (override != null) {
			look = Look.valueOf(override.toUpperCase(Locale.ROOT));
			MoreDyes.LOGGER.warn("[{}] {} look forced to {} by -Dmoredyes.look.{}", MoreDyes.MOD_ID, family.id, look, family.id);
		}
		PLAN.put(family, look);
		MoreDyes.LOGGER.info("[{}] {} look: {} ({} colour(s); tightest pool {} has room for {} in donor mode)",
				MoreDyes.MOD_ID, family.id, look, colours, tightestPool, fit);
	}

	/** All 32 copper-bars pools: every horizontal connection set, waterlogged or not. */
	private static List<BlockModelType> barsPools() {
		List<BlockModelType> pools = new ArrayList<>();
		Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		for (int mask = 0; mask < 16; mask++) {
			List<Direction> sides = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				if ((mask & (1 << i)) != 0) sides.add(horizontal[i]);
			}
			pools.add(BlockModelType.getBars(false, sides));
			pools.add(BlockModelType.getBars(true, sides));
		}
		return pools;
	}
}
