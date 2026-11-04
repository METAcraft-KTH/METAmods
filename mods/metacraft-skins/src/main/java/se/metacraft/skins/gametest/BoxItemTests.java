package se.metacraft.skins.gametest;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import se.metacraft.skins.BoxResult;
import se.metacraft.skins.item.SkinBox;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * The Skin Box item through the Skins module: what clients are shown of it, how it stacks, the
 * ominous vault's file in the pack, and giving boxes. The right-click and {@code /itemskins givebox}
 * that drive it are SkinsCommandTests'.
 */
public final class BoxItemTests {
	private static final String SPOOKY = "{ name: \"&lSpooky Box\", description: \"Only in October.\", ominous: true,"
			+ " skins: [ { skin: \"cleaver\", weight: 1 }, { skin: \"pumpkin_hat\", weight: 3 }, { skin: \"nowhere\", weight: 5 } ] }";

	/** A vault to clients (an ominous one for an ominous box), named in its colours, listing its skins and chances highest first. */
	@GameTest
	public void aBoxIsAVaultListingItsSkinsAndChances(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("pumpkin_hat", "helmet", "Pumpkin Hat", null).skin("cleaver", "sword", "&cCleaver", null)
				.box("spooky.json5", SPOOKY)
				.box("plain.json5", "{ name: \"Plain\", skins: [ { skin: \"cleaver\", weight: 1 } ] }")
				.box("gone.json5", "{ name: \"Gone\", skins: [ { skin: \"nowhere\", weight: 1 } ] }");
		fx.reload();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();

		ItemStack spooky = view(helper, fx, SkinBox.of("spooky", 1), viewer);
		assertThat(spooky.is(Items.VAULT), "a client gets " + spooky);
		assertThat(SkinBox.OMINOUS_MODEL.equals(spooky.get(DataComponents.ITEM_MODEL)), "an ominous box is drawn as " + spooky.get(DataComponents.ITEM_MODEL));
		assertThat(!spooky.hasFoil(), "a box glints");
		assertThat(spooky.getHoverName().getString().equals("Spooky Box") && spooky.getHoverName().getSiblings().getFirst().getStyle().isBold(),
				"the box is named " + spooky.getHoverName());
		List<Component> lore = lore(spooky);
		assertThat(text(lore).equals(List.of("Only in October.", "Pumpkin Hat 75%", "Cleaver 25%", "", "Right-click to open")), "the box's tooltip: " + text(lore));
		assertColour(lore.get(0), ChatFormatting.GRAY);
		assertColour(lore.get(4), ChatFormatting.GRAY);
		// The skin's name in its colours, then its chance in gray.
		assertThat(Objects.equals(lore.get(2).getSiblings().getFirst().getSiblings().getFirst().getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED))
				&& Objects.equals(lore.get(2).getSiblings().getLast().getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.GRAY)), "the cleaver's line: " + lore.get(2));

		ItemStack plain = view(helper, fx, SkinBox.of("plain", 1), viewer);
		assertThat(Identifier.parse("minecraft:vault").equals(plain.get(DataComponents.ITEM_MODEL)), "a plain box is drawn as " + plain.get(DataComponents.ITEM_MODEL));
		assertThat(text(lore(plain)).equals(List.of("Cleaver 100%", "", "Right-click to open")), "the plain box's tooltip: " + text(lore(plain)));

		ItemStack gone = view(helper, fx, SkinBox.of("gone", 1), viewer);
		assertThat(text(lore(gone)).equals(List.of("Every skin in this box is unavailable.", "", "Right-click to open")), "an empty box's tooltip: " + text(lore(gone)));
		helper.succeed();
	}

	/** A Dormant box, and one whose id the server has never heard of, read "Unknown Skin Box" as a plain vault; fixed, they come back. */
	@GameTest
	public void aDormantOrUnknownBoxReadsUnknown(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("cleaver", "sword").box("broken.json5", "{ name: \"Broken\", ominous: true }");
		fx.reload();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		for (String id : List.of("broken", "made_up")) {
			ItemStack client = view(helper, fx, SkinBox.of(id, 1), viewer);
			assertThat(client.getHoverName().getString().equals("Unknown Skin Box: " + id) && text(lore(client)).equals(List.of("It isn't on this server.")),
					id + " reads " + client.getHoverName().getString() + " " + text(lore(client)));
			assertThat(Identifier.parse("minecraft:vault").equals(client.get(DataComponents.ITEM_MODEL)), id + " is drawn as " + client.get(DataComponents.ITEM_MODEL));
			assertColour(lore(client).getFirst(), ChatFormatting.DARK_GRAY);
		}
		fx.box("broken.json5", "{ name: \"Broken\", ominous: true, skins: [ { skin: \"cleaver\", weight: 1 } ] }");
		fx.reload();
		assertThat(view(helper, fx, SkinBox.of("broken", 1), viewer).getHoverName().getString().equals("Broken"), "the fixed box is still unknown");
		helper.succeed();
	}

	/** Boxes stack to 64, only with the same box; nothing that takes a vault takes one; the server calls it "Skin Box". */
	@GameTest
	public void boxesStackByBoxAndAreNoVaultToTheServer(GameTestHelper helper) {
		ItemStack box = SkinBox.of("spooky", 1);
		assertThat(box.getMaxStackSize() == 64, "a box stacks to " + box.getMaxStackSize());
		assertThat(SkinBox.boxOf(box).equals(Optional.of("spooky")), "the box is " + SkinBox.boxOf(box));
		assertThat(ItemStack.isSameItemSameComponents(box, SkinBox.of("spooky", 1)), "two spooky boxes don't stack");
		assertThat(!ItemStack.isSameItemSameComponents(box, SkinBox.of("winter", 1)), "different boxes stack");
		assertThat(!box.is(Items.VAULT) && !new ItemCost(Items.VAULT, 1).test(box), "the server takes a box for a vault");
		assertThat(box.getHoverName().getString().equals("Skin Box"), "the server calls a box " + box.getHoverName().getString());
		helper.succeed();
	}

	/** The ominous vault's item definition is always in the pack, so making a box ominous never changes the pack. */
	@GameTest
	public void theOminousVaultIsAlwaysInThePack(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("cleaver", "sword").box("plain.json5", "{ name: \"Plain\", skins: [ { skin: \"cleaver\", weight: 1 } ] }");
		fx.reload();
		var files = SkinFixtures.unzip(fx.pack().zip());
		assertThat(SkinFixtures.text(files, "assets/skins/items/box/ominous_vault.json").replace(" ", "")
				.equals("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/vault_ominous\"}}"),
				"the ominous vault: " + SkinFixtures.text(files, "assets/skins/items/box/ominous_vault.json"));
		String before = fx.pack().sha1();
		fx.box("plain.json5", "{ name: \"Plain\", ominous: true, skins: [ { skin: \"cleaver\", weight: 1 } ] }");
		var report = fx.reload();
		assertThat(fx.pack().sha1().equals(before) && !report.changed(), "making the box ominous changed the pack");
		helper.succeed();
	}

	@GameTest
	public void givenBoxesThatDontFitDropAtThePlayersFeet(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("cleaver", "sword").box("spooky.json5", SPOOKY).box("broken.json5", "{ name: \"Broken\" }");
		fx.reload();
		ServerPlayer full = ExtractTests.full(helper);
		full.getInventory().setItem(0, SkinBox.of("spooky", 60));
		ServerPlayer empty = ApplyTests.carrying(helper);

		assertThat(fx.skins.giveBoxes(List.of(full, empty), "spooky", 10) == BoxResult.DONE, "the boxes weren't given");
		assertThat(full.getInventory().getItem(0).getCount() == 64, "the stack was topped up to " + full.getInventory().getItem(0).getCount());
		ItemStack given = empty.getInventory().getItem(0);
		assertThat(SkinBox.boxOf(given).equals(Optional.of("spooky")) && given.getCount() == 10, "the second player got " + given);
		List<? extends ItemEntity> dropped = helper.getLevel().getEntities(EntityTypes.ITEM, e -> SkinBox.boxOf(e.getItem()).isPresent());
		assertThat(dropped.size() == 1 && dropped.getFirst().getItem().getCount() == 6 && dropped.getFirst().distanceTo(full) < 2, "dropped " + dropped);
		dropped.forEach(ItemEntity::discard);

		assertThat(fx.skins.giveBoxes(List.of(empty), "broken", 1) == BoxResult.DORMANT_BOX, "a Dormant box was given");
		assertThat(fx.skins.giveBoxes(List.of(empty), "nowhere", 1) == BoxResult.UNKNOWN_BOX, "an unknown box was given");
		assertThat(empty.getInventory().getItem(1).isEmpty() && given.getCount() == 10, "a refused give gave something");
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
