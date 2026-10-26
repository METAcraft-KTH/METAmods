package se.metacraft.traders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code config/traders.json5}: what each trader sells. The trades on offer are worked out once per load (so a
 * {@code /reload} applies to the next screen opened), leaving out the ones naming an item that isn't registered.
 */
public record TradersConfig(Map<TraderKind, List<Trade>> trades) {

	/** A trade that never runs out. */
	public static final int UNLIMITED = Integer.MAX_VALUE;

	/** Some number of an item, by registry id (any registered item, a mod's too). */
	public record Stack(Identifier item, int count) {
		public static final Codec<Stack> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(Stack::item),
				ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Stack::count)
		).apply(i, Stack::new));

		public static Stack of(String item, int count) {
			return new Stack(Identifier.parse(item), count);
		}

		private ItemCost cost() {
			return new ItemCost(BuiltInRegistries.ITEM.getValue(item), count);
		}

		private ItemStack stack() {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(item), count);
		}
	}

	/** One offer: a price (one or two stacks) for a result, which each player can make {@code maxUses} times. */
	public record Trade(Stack price, Optional<Stack> secondPrice, Stack result, int maxUses, Optional<String> id) {
		public static final Codec<Trade> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(Stack.CODEC.fieldOf("price"), "What the player pays.").forGetter(Trade::price),
				CommentCodec.comment(Stack.CODEC.optionalFieldOf("second_price"), "An optional second payment.").forGetter(Trade::secondPrice),
				CommentCodec.comment(Stack.CODEC.fieldOf("result"), "What the player gets.").forGetter(Trade::result),
				CommentCodec.comment(ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_uses", UNLIMITED), "Times each player can make the trade, ever (the default never runs out).").forGetter(Trade::maxUses),
				CommentCodec.comment(ExtraCodecs.NON_EMPTY_STRING.optionalFieldOf("id"), "What the players' uses are counted under. Defaults to the result's item, so a trader's trades selling the same item share one count; a new id starts the count again.").forGetter(Trade::id)
		).apply(i, Trade::new));

		public static Trade of(Stack price, Stack result) {
			return new Trade(price, Optional.empty(), result, UNLIMITED, Optional.empty());
		}

		/** What the players' uses of this trade are counted under, for its trader (see {@link TraderPurchases}). */
		public String key() {
			return id.orElseGet(() -> result.item().toString());
		}

		/** The items this trade names that aren't registered (a mod that's not installed), in the order it names them. */
		public List<Identifier> missingItems() {
			return Stream.of(Optional.of(price), secondPrice, Optional.of(result)).flatMap(Optional::stream)
					.map(Stack::item).filter(item -> !BuiltInRegistries.ITEM.containsKey(item)).distinct().toList();
		}

		/** The offer, already made {@code uses} times. Only for a trade with no {@link #missingItems()}. */
		public MerchantOffer toOffer(int uses) {
			return new MerchantOffer(price.cost(), secondPrice.map(Stack::cost), result.stack(), Math.min(uses, maxUses), maxUses, 0, 0f);
		}
	}

	public static final MapCodec<TradersConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			CommentCodec.comment(
					Codec.unboundedMap(TraderKind.CODEC, Trade.CODEC.listOf()).fieldOf("trades"),
					"What each trader sells, by trader: data, it and media. Items are registry ids, so any item from any installed mod works.",
					"A trade naming an item that isn't registered is left out and logged, and comes back once the item is."
			).forGetter(TradersConfig::trades)
	).apply(i, TradersConfig::new));

	private static final ConfigContainer<TradersConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, TradersConfig::createDefault
	).reloadAfterServer().build(Traders.MODID);

	/** The last loaded config's trades on offer: worked out again whenever the container holds a new config. */
	@Nullable
	private static Resolved resolved;

	private record Resolved(TradersConfig config, Map<TraderKind, List<Trade>> trades) {}

	public static TradersConfig get() {
		return CONTAINER.get();
	}

	/** In the order the traders stand in, so the file written on the first start reads data, it, media. */
	public static TradersConfig createDefault() {
		var iron = Stack.of("minecraft:iron_ingot", 32);
		var trades = new EnumMap<TraderKind, List<Trade>>(TraderKind.class);
		trades.put(TraderKind.DATA, List.of(Trade.of(iron, Stack.of("ovvar:data_ovve", 1))));
		trades.put(TraderKind.IT, List.of(
				Trade.of(iron, Stack.of("ovvar:it_ovve", 1)),
				Trade.of(iron, Stack.of("ovvar:it_kisel_ovve", 1))
		));
		trades.put(TraderKind.MEDIA, List.of(Trade.of(iron, Stack.of("ovvar:media_frack", 1))));
		return new TradersConfig(trades);
	}

	/**
	 * Each trader's trades from the loaded config that can be offered, worked out (and their missing items logged) once
	 * per load. Every mod's items must be registered by then, so not during mod init: the server start is the first call.
	 */
	public static Map<TraderKind, List<Trade>> tradesOnOffer() {
		var config = get();
		var last = resolved;
		if (last == null || last.config() != config) {
			last = new Resolved(config, config.validTrades());
			resolved = last;
		}
		return last.trades();
	}

	public static List<Trade> tradesOnOffer(TraderKind kind) {
		return tradesOnOffer().getOrDefault(kind, List.of());
	}

	/**
	 * Each trader's trades, leaving out (and logging) every one naming an item that isn't registered, as when its mod
	 * isn't installed. The file keeps those trades, so they come back with the mod.
	 */
	public Map<TraderKind, List<Trade>> validTrades() {
		var result = new EnumMap<TraderKind, List<Trade>>(TraderKind.class);
		trades.forEach((kind, list) -> result.put(kind, list.stream().filter(trade -> {
			var missing = trade.missingItems();
			if (!missing.isEmpty()) {
				Traders.LOGGER.warn("Leaving out a trade of the {} trader: {} isn't registered", kind.id(),
						missing.stream().map(Identifier::toString).collect(Collectors.joining(", ")));
			}
			return missing.isEmpty();
		}).toList()));
		return result;
	}
}
