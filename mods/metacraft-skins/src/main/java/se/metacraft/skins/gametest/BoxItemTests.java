package se.metacraft.skins.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import se.metacraft.skins.BoxResult;
import se.metacraft.skins.item.SkinBox;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.trading.ItemCost;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * The Skin Box item through the Skins module: what clients are shown of it, how it stacks, the
 * mark it carries and the model the pack draws it with, and giving boxes. The right-click and {@code /itemskins givebox}
 * that drive it are SkinsCommandTests'.
 */
public final class BoxItemTests {
	private static final String SPOOKY = "{ name: \"&lSpooky Box\", description: \"Only in October.\", ominous: true,"
			+ " skins: [ { skin: \"cleaver\", weight: 1 }, { skin: \"pumpkin_hat\", weight: 3 }, { skin: \"nowhere\", weight: 5 } ] }";

	/**
	 * A vault to clients, marked as a box (an old file's {@code ominous} changes nothing), named in its colours with "(Right-Click)":
	 * its description, a blank line, "Random Loot (1 item)", then "* 1x" and each skin, highest chance first.
	 */
	@GameTest
	public void aBoxIsAMarkedVaultListingItsSkinsAndChances(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("pumpkin_hat", "helmet", "Pumpkin Hat", null).skin("cleaver", "sword", "&cCleaver", null)
				.box("spooky.json5", SPOOKY)
				.box("plain.json5", "{ name: \"Plain\", skins: [ { skin: \"cleaver\", weight: 1 } ] }")
				.box("gone.json5", "{ name: \"Gone\", skins: [ { skin: \"nowhere\", weight: 1 } ] }");
		fx.reload();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();

		ItemStack spooky = view(helper, fx, SkinBox.of("spooky"), viewer);
		assertThat(spooky.is(Items.VAULT), "a client gets " + spooky);
		assertDrawnAsBox(spooky, "a box once ominous");
		assertThat(!spooky.hasFoil(), "a box glints");
		Component name = spooky.getHoverName();
		assertThat(name.getString().equals("Spooky Box (Right-Click)") && name.getSiblings().getFirst().getSiblings().getFirst().getStyle().isBold(),
				"the box is named " + name);
		assertColour(name.getSiblings().getLast(), ChatFormatting.GRAY);
		List<Component> lore = lore(spooky);
		assertThat(text(lore).equals(List.of("Only in October.", "", "Random Loot (1 item)", "* 1x Pumpkin Hat", "* 1x Cleaver")), "the box's tooltip: " + text(lore));
		assertColour(lore.get(0), ChatFormatting.GRAY);
		// "Random Loot" white and bold, " (1 item)" gray and not bold.
		Component loot = lore.get(2);
		assertColour(loot.getSiblings().getFirst(), ChatFormatting.WHITE);
		assertColour(loot.getSiblings().getLast(), ChatFormatting.GRAY);
		assertThat(loot.getSiblings().getFirst().getStyle().isBold() && !loot.getSiblings().getLast().getStyle().isBold(), "the loot line: " + loot);
		// "* 1x " white and not bold, then the skin's name in its colours.
		Component cleaver = lore.get(4);
		assertColour(cleaver.getSiblings().getFirst(), ChatFormatting.WHITE);
		assertThat(!cleaver.getSiblings().getFirst().getStyle().isBold()
				&& Objects.equals(cleaver.getSiblings().getLast().getSiblings().getFirst().getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED)),
				"the cleaver's line: " + cleaver);

		ItemStack plain = view(helper, fx, SkinBox.of("plain"), viewer);
		assertDrawnAsBox(plain, "a plain box");
		assertThat(text(lore(plain)).equals(List.of("", "Random Loot (1 item)", "* 1x Cleaver")), "the plain box's tooltip: " + text(lore(plain)));

		ItemStack gone = view(helper, fx, SkinBox.of("gone"), viewer);
		assertThat(text(lore(gone)).equals(List.of("", "Random Loot (1 item)", "Every skin in this box is unavailable.")), "an empty box's tooltip: " + text(lore(gone)));
		helper.succeed();
	}

	/** A Dormant box, and one whose id the server has never heard of, read "Unknown Skin Box", still marked as a box; fixed, they come back. */
	@GameTest
	public void aDormantOrUnknownBoxReadsUnknown(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("cleaver", "sword").box("broken.json5", "{ name: \"Broken\", ominous: true }");
		fx.reload();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		for (String id : List.of("broken", "made_up")) {
			ItemStack client = view(helper, fx, SkinBox.of(id), viewer);
			assertThat(client.getHoverName().getString().equals("Unknown Skin Box: " + id) && text(lore(client)).equals(List.of("It isn't on this server.")),
					id + " reads " + client.getHoverName().getString() + " " + text(lore(client)));
			assertDrawnAsBox(client, id);
			assertColour(lore(client).getFirst(), ChatFormatting.DARK_GRAY);
		}
		fx.box("broken.json5", "{ name: \"Broken\", ominous: true, skins: [ { skin: \"cleaver\", weight: 1 } ] }");
		fx.reload();
		assertThat(view(helper, fx, SkinBox.of("broken"), viewer).getHoverName().getString().equals("Broken (Right-Click)"), "the fixed box is still unknown");
		helper.succeed();
	}

	/** Boxes don't stack, on the server or as clients see them; nothing that takes a vault takes one; the server calls it "Skin Box". */
	@GameTest
	public void boxesDontStackAndAreNoVaultToTheServer(GameTestHelper helper) {
		ItemStack box = SkinBox.of("spooky");
		assertThat(box.getMaxStackSize() == 1, "a box stacks to " + box.getMaxStackSize());
		assertThat(SkinBox.boxOf(box).equals(Optional.of("spooky")), "the box is " + SkinBox.boxOf(box));
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		ItemStack client = PolymerItemUtils.getPolymerItemStack(box, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
		assertThat(Integer.valueOf(1).equals(client.get(DataComponents.MAX_STACK_SIZE)), "a client stacks it to " + client.get(DataComponents.MAX_STACK_SIZE));
		// Recipes and villagers match what they take by item: one asking for a vault never takes a box.
		assertThat(!box.is(Items.VAULT) && !Ingredient.of(Items.VAULT).test(box), "a recipe takes a box for a vault");
		assertThat(!new ItemCost(Items.VAULT, 1).test(box), "a villager takes a box for a vault");
		assertThat(box.getHoverName().getString().equals("Skin Box"), "the server calls a box " + box.getHoverName().getString());
		helper.succeed();
	}

	/**
	 * The pack always has the box's model, with its texture, and vanilla's vault item definition
	 * overridden to draw a vault marked as a box with it and any other vault as the vault; so a box
	 * file never changes the pack.
	 */
	@GameTest
	public void theBoxsModelIsAlwaysInThePack(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("cleaver", "sword").box("plain.json5", "{ name: \"Plain\", skins: [ { skin: \"cleaver\", weight: 1 } ] }");
		fx.reload();
		var files = SkinFixtures.unzip(fx.pack().zip());
		JsonObject vault = JsonParser.parseString(SkinFixtures.text(files, "assets/minecraft/items/vault.json")).getAsJsonObject().getAsJsonObject("model");
		JsonObject box = vault.getAsJsonArray("cases").get(0).getAsJsonObject();
		assertThat(vault.get("property").getAsString().equals("minecraft:custom_model_data") && box.get("when").getAsString().equals(SkinBox.LOOK)
				&& box.getAsJsonObject("model").get("model").getAsString().equals("skins:box/skin_box")
				&& vault.getAsJsonObject("fallback").get("model").getAsString().equals("minecraft:block/vault"), "the vault's item definition: " + vault);
		JsonObject model = JsonParser.parseString(SkinFixtures.text(files, "assets/skins/models/box/skin_box.json")).getAsJsonObject();
		assertThat(model.getAsJsonObject("textures").get("0").getAsString().equals("skins:item/skin_box") && model.getAsJsonArray("elements").size() > 0,
				"the box's model: " + model.get("textures"));
		assertThat(files.containsKey("assets/skins/textures/item/skin_box.png"), "the pack has no box texture");
		String before = fx.pack().sha1();
		fx.box("other.json5", "{ name: \"Other\", ominous: true, skins: [ { skin: \"cleaver\", weight: 1 } ] }");
		var report = fx.reload();
		assertThat(fx.pack().sha1().equals(before) && !report.changed(), "adding a box changed the pack");
		helper.succeed();
	}

	/** A vault as clients have it, marked as a Skin Box. */
	private static void assertDrawnAsBox(ItemStack client, String what) {
		assertThat(client.is(Items.VAULT) && SkinBox.MODEL.equals(client.get(DataComponents.ITEM_MODEL))
				&& client.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY).strings().equals(List.of(SkinBox.LOOK)),
				what + " is drawn as " + client.get(DataComponents.ITEM_MODEL) + ", " + client.get(DataComponents.CUSTOM_MODEL_DATA));
	}

	@GameTest
	public void givenBoxesThatDontFitDropAtThePlayersFeet(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("cleaver", "sword").box("spooky.json5", SPOOKY).box("broken.json5", "{ name: \"Broken\" }");
		fx.reload();
		ServerPlayer full = ExtractTests.full(helper);
		ServerPlayer empty = ApplyTests.carrying(helper);

		// Each box is an item of its own: ten boxes fill ten slots, or drop as ten items.
		assertThat(fx.skins.giveBoxes(List.of(full, empty), "spooky", 10) == BoxResult.DONE, "the boxes weren't given");
		for (int slot = 0; slot < 10; slot++) {
			ItemStack given = empty.getInventory().getItem(slot);
			assertThat(SkinBox.boxOf(given).equals(Optional.of("spooky")) && given.getCount() == 1, "the second player got " + given + " in slot " + slot);
		}
		assertThat(empty.getInventory().getItem(10).isEmpty(), "the second player got more than ten boxes");
		List<? extends ItemEntity> dropped = helper.getLevel().getEntities(EntityTypes.ITEM, e -> SkinBox.boxOf(e.getItem()).isPresent() && e.distanceTo(full) < 2);
		assertThat(dropped.size() == 10 && dropped.stream().allMatch(e -> e.getItem().getCount() == 1), "dropped " + dropped);
		dropped.forEach(ItemEntity::discard);
		ItemStack given = empty.getInventory().getItem(0);

		assertThat(fx.skins.giveBoxes(List.of(empty), "broken", 1) == BoxResult.DORMANT_BOX, "a Dormant box was given");
		assertThat(fx.skins.giveBoxes(List.of(empty), "nowhere", 1) == BoxResult.UNKNOWN_BOX, "an unknown box was given");
		assertThat(empty.getInventory().getItem(10).isEmpty() && given.getCount() == 1, "a refused give gave something");
		helper.succeed();
	}

	/** What Polymer sends this viewer of the box, read by the test's Skins. */
	static ItemStack view(GameTestHelper helper, SkinFixtures fx, ItemStack box, ServerPlayer viewer) {
		ItemStack client = PolymerItemUtils.getPolymerItemStack(box, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
		return fx.skins.boxView(box, client);
	}

	private static List<Component> lore(ItemStack client) {
		return client.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	private static List<String> text(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	private static void assertColour(Component line, ChatFormatting colour) {
		assertThat(Objects.equals(line.getStyle().getColor(), TextColor.fromLegacyFormat(colour)), "\"" + line.getString() + "\" is coloured " + line.getStyle().getColor());
		assertThat(!line.getStyle().isItalic(), "\"" + line.getString() + "\" is italic");
	}
}
