import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import nu.metacraft.qol.QolConfig;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import nu.metacraft.qol.void_anchor.rift.RiftStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QolConfigTests {

	@BeforeAll
	public static void init() {
		QolTests.init();
	}

	@Test
	public void defaultsRoundTrip() {
		var json = QolConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, QolConfig.DEFAULT).getOrThrow();
		assertEquals(QolConfig.DEFAULT, QolConfig.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
		var voidAnchor = json.getAsJsonObject().getAsJsonObject("void_anchor");
		assertTrue(voidAnchor.get("enabled").getAsBoolean());
		assertEquals("minecraft:end_crystal", voidAnchor.get("fuel_item").getAsString());
		assertEquals("shatter", voidAnchor.get("rift_style").getAsString());
	}

	@Test
	public void missingSectionsTakeTheirDefaults() {
		// A file written before a feature existed has no section for it; that must not break loading.
		assertEquals(QolConfig.DEFAULT, QolConfig.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
	}

	// Item stacks only get their components once a server has loaded, so the stack-level check
	// (isFuel) is exercised by the game tests; here the configured id resolves to an item.
	@Test
	public void fuelIsTheConfiguredItem() {
		assertEquals(Optional.of(Items.END_CRYSTAL), VoidAnchorConfig.DEFAULT.fuel());
	}

	@Test
	public void unknownFuelItem() {
		var config = new VoidAnchorConfig(true, Identifier.fromNamespaceAndPath("nope", "missing"), 0, 6, 0.3, 30, 3, RiftStyle.CRACK);
		assertEquals(Optional.empty(), config.fuel());
		// A second look neither throws nor warns again.
		assertEquals(Optional.empty(), config.fuel());
	}

	@Test
	public void rejectsOutOfRangeValues() {
		var json = VoidAnchorConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, VoidAnchorConfig.DEFAULT).getOrThrow().getAsJsonObject();
		json.addProperty("rift_ticks", 0);
		assertTrue(VoidAnchorConfig.CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
	}

	@Test
	public void riftStyleReadsByName() {
		var json = VoidAnchorConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, VoidAnchorConfig.DEFAULT).getOrThrow().getAsJsonObject();
		json.addProperty("rift_style", "shatter");
		assertEquals(RiftStyle.SHATTER, VoidAnchorConfig.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow().riftStyle());
		json.addProperty("rift_style", "sparkles");
		assertTrue(VoidAnchorConfig.CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
	}

}
