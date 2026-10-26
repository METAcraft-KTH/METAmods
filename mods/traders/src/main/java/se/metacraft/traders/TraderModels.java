package se.metacraft.traders;

import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.BbModelLoader;

import java.util.EnumMap;
import java.util.Map;

/**
 * The traders' Blockbench models ({@code /model/traders/<kind>_trader.bbmodel}), loaded once: BIL doesn't cache models,
 * and they must exist before Polymer builds the resource pack.
 */
public final class TraderModels {

	private static final Map<TraderKind, Model> MODELS = new EnumMap<>(TraderKind.class);

	static {
		for (TraderKind kind : TraderKind.values()) {
			MODELS.put(kind, BbModelLoader.load(Traders.getID(kind.entityName())));
		}
	}

	private TraderModels() {}

	public static Model get(TraderKind kind) {
		return MODELS.get(kind);
	}

	/** Forces the models to load during mod init. */
	public static void init() {
	}
}
