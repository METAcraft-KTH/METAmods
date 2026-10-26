import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.metacraft.config.util.helper.JanksonHelper;
import se.metacraft.traders.TraderKind;
import se.metacraft.traders.TradersConfig;
import se.metacraft.traders.TradersConfig.Stack;
import se.metacraft.traders.TradersConfig.Trade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code config/traders.json5}: what the file may say, and which of its trades become offers. */
public class TradersConfigTest {

	/** The trades name items. */
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static DataResult<TradersConfig> parse(String json) {
		return TradersConfig.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	private static Trade trade(String price, String result) {
		return Trade.of(Stack.of(price, 32), Stack.of(result, 1));
	}

	@Test
	public void defaultsRoundTripThroughTheFile(@TempDir Path dir) {
		var file = dir.resolve("traders.json5");
		JanksonHelper.save(file, TradersConfig.CODEC.codec(), TradersConfig.createDefault());
		assertEquals(Optional.of(TradersConfig.createDefault()), JanksonHelper.load(file, TradersConfig.CODEC.codec()));
	}

	@Test
	public void defaultsListTheTradersInOrder() {
		var json = TradersConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, TradersConfig.createDefault()).getOrThrow();
		assertEquals(List.of("data", "it", "media"), List.copyOf(json.getAsJsonObject().getAsJsonObject("trades").keySet()));
	}

	/** The README's example is the default file: json5, with unquoted keys, trailing commas and counts left out. */
	@Test
	public void readmeExampleIsTheDefaults(@TempDir Path dir) throws IOException {
		var file = dir.resolve("traders.json5");
		Files.writeString(file, """
				{
					trades: {
						data: [{ price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:data_ovve" } }],
						it: [
							{ price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:it_ovve" } },
							{ price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:it_kisel_ovve" } },
						],
						media: [{ price: { item: "minecraft:iron_ingot", count: 32 }, result: { item: "ovvar:media_frack" } }],
					},
				}
				""");
		assertEquals(Optional.of(TradersConfig.createDefault()), JanksonHelper.load(file, TradersConfig.CODEC.codec()));
	}

	@Test
	public void readsEveryTradeField() {
		var config = parse("""
				{"trades": {"it": [{
					"price": {"item": "minecraft:iron_ingot", "count": 20},
					"second_price": {"item": "minecraft:book"},
					"result": {"item": "minecraft:diamond", "count": 2},
					"max_uses": 3,
					"id": "diamonds"
				}]}}
				""").getOrThrow();
		var expected = new Trade(Stack.of("minecraft:iron_ingot", 20), Optional.of(Stack.of("minecraft:book", 1)), Stack.of("minecraft:diamond", 2), 3, Optional.of("diamonds"));
		assertEquals(Map.of(TraderKind.IT, List.of(expected)), config.trades());
	}

	@Test
	public void leftOutFieldsTakeTheirDefaults() {
		var trade = parse("{\"trades\": {\"data\": [{\"price\": {\"item\": \"minecraft:iron_ingot\"}, \"result\": {\"item\": \"minecraft:emerald\"}}]}}")
				.getOrThrow().trades().get(TraderKind.DATA).getFirst();
		assertEquals(1, trade.price().count());
		assertEquals(Optional.empty(), trade.secondPrice());
		assertEquals(TradersConfig.UNLIMITED, trade.maxUses());
		assertEquals(Optional.empty(), trade.id());
	}

	/** Uses are counted under the result's item unless the trade names its own id, so a price change keeps the count. */
	@Test
	public void keyIsTheResultItemOrTheId() {
		assertEquals("minecraft:emerald", trade("minecraft:iron_ingot", "minecraft:emerald").key());
		assertEquals("minecraft:emerald", trade("minecraft:gold_ingot", "minecraft:emerald").key());
		var named = new Trade(Stack.of("minecraft:iron_ingot", 32), Optional.empty(), Stack.of("minecraft:emerald", 1), 1, Optional.of("summer"));
		assertEquals("summer", named.key());
		var emptyId = "{\"trades\": {\"data\": [{\"price\": {\"item\": \"minecraft:iron_ingot\"}, \"result\": {\"item\": \"minecraft:emerald\"}, \"id\": \"\"}]}}";
		assertTrue(parse(emptyId).isError(), "an empty id loaded");
	}

	/** A key naming no trader is an error, but the loader (resultOrPartial) still keeps every other trader's trades. */
	@Test
	public void unknownTraderIsAnErrorButTheRestLoads() {
		var result = parse("""
				{"trades": {
					"Data": [{"price": {"item": "minecraft:iron_ingot"}, "result": {"item": "minecraft:emerald"}}],
					"it": [{"price": {"item": "minecraft:iron_ingot"}, "result": {"item": "minecraft:diamond"}}]
				}}
				""");
		assertTrue(result.isError(), "a \"Data\" key loaded");
		var partial = result.resultOrPartial().orElseThrow();
		assertEquals(List.of(TraderKind.IT), List.copyOf(partial.trades().keySet()));
	}

	@Test
	public void rejectsOutOfRangeValues() {
		var trade = "{\"price\": {\"item\": \"minecraft:iron_ingot\", \"count\": %d}, \"result\": {\"item\": \"minecraft:emerald\"}, \"max_uses\": %d}";
		assertTrue(parse("{\"trades\": {\"data\": [" + trade.formatted(1, 1) + "]}}").isSuccess());
		assertTrue(parse("{\"trades\": {\"data\": [" + trade.formatted(0, 1) + "]}}").isError(), "a count of 0 loaded");
		assertTrue(parse("{\"trades\": {\"data\": [" + trade.formatted(100, 1) + "]}}").isError(), "a count of 100 loaded");
		assertTrue(parse("{\"trades\": {\"data\": [" + trade.formatted(1, 0) + "]}}").isError(), "a max_uses of 0 loaded");
	}

	@Test
	public void missingItemsAreTheUnregisteredOnes() {
		assertEquals(List.of(), trade("minecraft:iron_ingot", "minecraft:emerald").missingItems());
		var trade = new Trade(Stack.of("minecraft:iron_ingot", 32), Optional.of(Stack.of("nope:missing", 1)), Stack.of("nope:gone", 1), 1, Optional.empty());
		assertEquals(List.of(Identifier.parse("nope:missing"), Identifier.parse("nope:gone")), trade.missingItems());
	}

	// An item stack only gets its components once a server has loaded, so turning the trades into offers is left to
	// the client game test; here the trades that would become offers are picked.
	@Test
	public void tradesWithMissingItemsAreLeftOut() {
		var sold = trade("minecraft:iron_ingot", "minecraft:emerald");
		var config = new TradersConfig(Map.of(
				TraderKind.DATA, List.of(trade("minecraft:iron_ingot", "nope:missing"), sold),
				TraderKind.IT, List.of(trade("nope:missing", "minecraft:diamond"))
		));
		assertEquals(Map.of(TraderKind.DATA, List.of(sold), TraderKind.IT, List.of()), config.validTrades());
		// the config itself keeps them, so they come back with the mod
		assertEquals(2, config.trades().get(TraderKind.DATA).size());
	}

	/** The defaults sell ovvar's items, which this test run doesn't have: every trade is left out, none fails. */
	@Test
	public void defaultsWithoutOvvarHaveNoOffers() {
		var trades = TradersConfig.createDefault().validTrades();
		for (var kind : TraderKind.values()) {
			assertEquals(List.of(), trades.get(kind), kind.id());
		}
	}
}
