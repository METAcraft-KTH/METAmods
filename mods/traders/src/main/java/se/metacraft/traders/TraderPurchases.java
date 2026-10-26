package se.metacraft.traders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How many times each player has made each trade, for the trades' {@code max_uses}: saved with the world, so a limit
 * holds across screens and restarts. Every trade is counted, so a limit added later counts what was bought before it.
 */
public final class TraderPurchases extends SavedData {

	public static final Codec<TraderPurchases> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Codec.INT)).fieldOf("players")
					.forGetter(purchases -> purchases.players)
	).apply(i, TraderPurchases::new));

	private static final SavedDataType<TraderPurchases> TYPE = new SavedDataType<>(
			Traders.getID("purchases"), TraderPurchases::new, CODEC, null
	);

	/** Per player, the number of times by {@link #key(TraderKind, String)}. */
	private final Map<UUID, Map<String, Integer>> players = new HashMap<>();

	public TraderPurchases() {}

	private TraderPurchases(Map<UUID, Map<String, Integer>> players) {
		players.forEach((player, counts) -> this.players.put(player, new HashMap<>(counts)));
	}

	public static TraderPurchases get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** {@code data/ovvar:data_ovve}: a trader's trade, by its {@link TradersConfig.Trade#key()}. */
	public static String key(TraderKind kind, String tradeKey) {
		return kind.id() + "/" + tradeKey;
	}

	public int count(UUID player, TraderKind kind, String tradeKey) {
		return players.getOrDefault(player, Map.of()).getOrDefault(key(kind, tradeKey), 0);
	}

	public void record(UUID player, TraderKind kind, String tradeKey) {
		players.computeIfAbsent(player, p -> new HashMap<>()).merge(key(kind, tradeKey), 1, Integer::sum);
		setDirty();
	}
}
