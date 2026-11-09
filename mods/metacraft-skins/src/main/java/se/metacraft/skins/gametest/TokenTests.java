package se.metacraft.skins.gametest;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import se.metacraft.skins.OwnedSkin;
import se.metacraft.skins.OwnershipChange;
import se.metacraft.skins.item.SkinToken;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Skin Tokens through the Skins module: what the token is, what each viewer reads on it, a Claim
 * (who owns what afterwards, and what's left of the token), and giving tokens. The menu and the
 * right-click that drive a Claim are ClaimMenuTests' and SkinsCommandTests'.
 */
public final class TokenTests {

	/** Shown to clients as paper with the shared token model, 64 to a stack; nothing that takes paper takes it. */
	@GameTest
	public void aTokenIsPaperToClientsButNeverToRecipesOrTrades(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null);
		fx.reload();
		ItemStack token = SkinToken.of("crown_test", 1);
		assertThat(token.getMaxStackSize() == 64, "a token stacks to " + token.getMaxStackSize());
		assertThat(SkinToken.skinOf(token).equals(java.util.Optional.of("crown_test")), "the token is for " + SkinToken.skinOf(token));
		ItemStack two = SkinToken.of("crown_test", 1);
		assertThat(ItemStack.isSameItemSameComponents(token, two), "two crown tokens don't stack");

		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		ItemStack client = PolymerItemUtils.getPolymerItemStack(token, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
		assertThat(client.is(Items.PAPER), "a client gets " + client);
		assertThat(Identifier.parse("skins:token/skin_token").equals(client.get(DataComponents.ITEM_MODEL)), "the token's model is " + client.get(DataComponents.ITEM_MODEL));
		assertThat(Integer.valueOf(64).equals(client.get(DataComponents.MAX_STACK_SIZE)), "a client stacks it to " + client.get(DataComponents.MAX_STACK_SIZE));
		var pack = SkinFixtures.unzip(fx.pack().zip());
		assertThat(pack.containsKey("assets/skins/items/token/skin_token.json"), "the pack has no token item definition");
		assertThat(SkinFixtures.text(pack, "assets/skins/items/token/skin_token.json").contains("\"skins:token/skin_token\""), "the token isn't drawn with its own model");
		assertThat(SkinFixtures.text(pack, "assets/skins/models/token/skin_token.json").contains("\"skins:item/skin_token\""), "the token model doesn't use its texture");
		assertThat(pack.containsKey("assets/skins/textures/item/skin_token.png"), "the pack has no token texture");

		// Paper and gunpowder make a rocket; a token and gunpowder make nothing. A paper-buying trade doesn't take it.
		var level = helper.getLevel();
		var recipes = level.getServer().getRecipeManager();
		assertThat(recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 1, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.GUNPOWDER))), level).isPresent(),
				"the firework recipe is gone, so this check checks nothing");
		assertThat(recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 1, List.of(token, new ItemStack(Items.GUNPOWDER))), level).isEmpty(),
				"a token crafts as paper");
		assertThat(!new ItemCost(Items.PAPER, 1).test(token), "a villager takes a token as paper");
		helper.succeed();
	}

	@GameTest
	public void eachViewerReadsTheTokenForThem(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("crown_test", "helmet", "Crown", "Fit for a king.", PerkTests.HEARTS_WORN)
				.skin("plain", "sword", "Plain Blade", null).skin("broken", "sword")
				.perky("green_pick", "pickaxe", "Green Pick", null, """
						[ { trigger: "held", effects: [ { type: "potion", effect: "minecraft:haste", level: 1 } ] },
						{ trigger: "block_break", chance: 0.1, effects: [ { type: "particle", particle: { type: "minecraft:happy_villager" } },
						{ type: "sound", target: "other", sound: "minecraft:block.amethyst_block.chime" } ] } ]""")
				.perky("sugar_blade", "sword", "Sugar Blade", null, """
						[ { trigger: "hit", chance: 0.25, cooldown: 5, effects: [ { type: "potion", target: "other", effect: "minecraft:slowness", duration: 3 } ] },
						{ trigger: "kill", tooltip: "On kill: a sugar rush", effects: [ { type: "heal", amount: 4 } ] } ]""");
		fx.file("broken", "skin.json5", "{ name: \"Broken\", kind: \"nope\" }");
		fx.reload();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		fx.skins.grant(owner.nameAndId(), "crown_test");

		ItemStack crown = view(helper, fx, SkinToken.of("crown_test", 3), other);
		Component tokenName = Objects.requireNonNull(crown.get(DataComponents.ITEM_NAME));
		assertThat(tokenName.getString().equals("Skin Token: Crown") && tokenName.getSiblings().size() == 2, "the token is named " + tokenName);
		Component prefix = tokenName.getSiblings().get(0);
		assertLine(prefix, "Skin Token: ", ChatFormatting.GOLD);
		assertThat(prefix.getStyle().isBold(), "\"Skin Token: \" isn't bold");
		assertThat(tokenName.getSiblings().get(1).equals(fx.skins.skin("crown_test").orElseThrow().styledName()),
				"the skin's part of the name isn't its own: " + tokenName);
		assertThat(!Boolean.TRUE.equals(crown.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "a token glints");
		List<Component> lore = lore(crown);
		assertThat(text(lore).equals(List.of("Fits any helmet", "While worn: +2 max hearts", "Fit for a king.", "", "Right-click to claim")),
				"the token's lore: " + text(lore));
		assertLine(lore.get(0), "Fits any helmet", ChatFormatting.GRAY);
		assertLine(lore.get(1), "While worn: +2 max hearts", ChatFormatting.BLUE);
		assertLine(lore.get(4), "Right-click to claim", ChatFormatting.GRAY);

		List<String> owned = text(lore(view(helper, fx, SkinToken.of("crown_test", 1), owner)));
		assertThat(owned.equals(List.of("Fits any helmet", "While worn: +2 max hearts", "Fit for a king.", "", "✔ You already own this skin — trade it away")),
				"the Owner reads " + owned);
		List<String> pick = text(lore(view(helper, fx, SkinToken.of("green_pick", 1), owner)));
		assertThat(pick.equals(List.of("Fits any pickaxe", "While held: Haste I", "On block break (10%): particles and a sound", "", "Right-click to claim")),
				"the pick's token reads " + pick);
		List<String> blade = text(lore(view(helper, fx, SkinToken.of("sugar_blade", 1), owner)));
		assertThat(blade.equals(List.of("Fits any sword", "On hit (25%, 5s cooldown): Slowness I on the target for 3s", "On kill: a sugar rush", "",
				"Right-click to claim")), "the blade's token reads " + blade);
		List<String> plain = text(lore(view(helper, fx, SkinToken.of("plain", 1), owner)));
		assertThat(plain.equals(List.of("Fits any sword", "", "Right-click to claim")), "a token with no description: " + plain);

		for (String id : List.of("broken", "nowhere")) {
			ItemStack unknown = view(helper, fx, SkinToken.of(id, 1), owner);
			String name = Objects.requireNonNull(unknown.get(DataComponents.ITEM_NAME)).getString();
			assertThat(name.equals("Unknown skin token: " + id), "a token for " + id + " reads " + name);
			assertThat(text(lore(unknown)).stream().noneMatch(l -> l.contains("claim")), "a token for " + id + " says it can be claimed");
		}
		helper.succeed();
	}

	/**
	 * A limited skin's token names its Materials as vanilla's item names do, in vanilla order
	 * whatever order the admin listed them in, with the Kind in the plural.
	 */
	@GameTest
	public void aLimitedSkinsTokenSaysWhichMaterialsItFits(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().limited("cap", "helmet", "[\"leather\"]").limited("shell_cap", "helmet", "[\"turtle\", \"leather\"]")
				.limited("picks", "pickaxe", "[\"iron\", \"wood\", \"stone\"]").limited("blades", "sword", "[\"netherite\", \"diamond\"]")
				.limited("greaves", "leggings", "[\"gold\"]").skin("plain", "sword");
		fx.reload();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		for (var expected : List.of(List.of("cap", "Fits leather helmets"), List.of("shell_cap", "Fits leather and turtle helmets"),
				List.of("picks", "Fits wooden, stone and iron pickaxes"), List.of("blades", "Fits diamond and netherite swords"),
				List.of("greaves", "Fits golden leggings"), List.of("plain", "Fits any sword"))) {
			List<String> lore = text(lore(view(helper, fx, SkinToken.of(expected.get(0), 1), viewer)));
			assertThat(!lore.isEmpty() && lore.getFirst().equals(expected.get(1)), expected.get(0) + "'s token reads " + lore);
		}
		helper.succeed();
	}

	/** A Dyeable Hat's token says, in gray under what it fits, that dyeing the helmet recolours it. */
	@GameTest
	public void aDyeableHatsTokenSaysDyeingRecoloursIt(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().dyeableHat("cowboy_hat_test", "[\"leather\"]");
		fx.reload();
		List<Component> lore = lore(view(helper, fx, SkinToken.of("cowboy_hat_test", 1), helper.makeMockServerPlayerInLevel()));
		assertThat(text(lore).equals(List.of("Fits leather helmets", "Dye the helmet to recolour it", "", "Right-click to claim")), "the token reads " + text(lore));
		assertLine(lore.get(1), "Dye the helmet to recolour it", ChatFormatting.GRAY);
		helper.succeed();
	}

	@GameTest
	public void claimingMakesThePlayerTheOwnerAndUsesUpOneToken(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null);
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, SkinToken.of("crown_test", 3));
		ItemStack token = player.getInventory().getItem(0);

		assertThat(fx.skins.claim(player, token) == OwnershipChange.DONE, "the claim wasn't made");
		assertThat(fx.skins.ownedSkins(player.getUUID()).owns("crown_test"), "the player doesn't own the crown_test");
		assertThat(fx.restart().ownedSkins(player.getUUID()).skins().getFirst().state() == OwnedSkin.State.UNAPPLIED, "the claim wasn't saved");
		assertThat(player.getInventory().getItem(0).getCount() == 2, "the claim left " + player.getInventory().getItem(0));

		// The rest of the stack is for trading: claiming again is refused and keeps them.
		assertThat(fx.skins.claim(player, token) == OwnershipChange.ALREADY_OWNED, "an owned skin was claimed again");
		assertThat(player.getInventory().getItem(0).getCount() == 2, "an already-owned claim used a token");
		helper.succeed();
	}

	@GameTest
	public void aTokenForAnUnknownOrDormantSkinCantBeClaimed(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("broken", "sword");
		fx.file("broken", "skin.json5", "{ name: \"Broken\", kind: \"nope\" }");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, SkinToken.of("broken", 1), SkinToken.of("nowhere", 1));
		assertThat(fx.skins.claim(player, player.getInventory().getItem(0)) == OwnershipChange.DORMANT_SKIN, "a Dormant skin's token was claimed");
		assertThat(fx.skins.claim(player, player.getInventory().getItem(1)) == OwnershipChange.UNKNOWN_SKIN, "an unknown skin's token was claimed");
		assertThat(player.getInventory().getItem(0).getCount() == 1 && player.getInventory().getItem(1).getCount() == 1, "a refused claim used a token");
		assertThat(fx.skins.ownedSkins(player.getUUID()).skins().isEmpty(), "a refused claim granted something");

		// Only a token the player carries, and only a token.
		fx.skin("crown_test", "helmet");
		fx.reload();
		assertThat(fx.skins.claim(player, SkinToken.of("crown_test", 1)) == OwnershipChange.NO_TOKEN, "a token the player doesn't carry was claimed");
		player.getInventory().setItem(2, new ItemStack(Items.PAPER));
		assertThat(fx.skins.claim(player, player.getInventory().getItem(2)) == OwnershipChange.NO_TOKEN, "paper was claimed");
		helper.succeed();
	}

	@GameTest
	public void aClaimThatCantBeSavedKeepsTheToken(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, SkinToken.of("crown_test", 1));
		try {
			Files.createDirectories(fx.root.resolve("skins-data"));
			Files.writeString(fx.root.resolve("skins-data/players"), "not a folder");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		assertThat(fx.skins.claim(player, player.getInventory().getItem(0)) == OwnershipChange.SAVE_FAILED, "a claim that couldn't be saved didn't say so");
		assertThat(player.getInventory().getItem(0).getCount() == 1, "a claim that couldn't be saved used the token");
		assertThat(!fx.skins.ownedSkins(player.getUUID()).owns("crown_test"), "a claim that couldn't be saved counted");
		helper.succeed();
	}

	@GameTest
	public void aDamagedFileRefusesAClaimAndKeepsTheToken(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, SkinToken.of("crown_test", 1));
		String damaged = "{ \"uuid\": \"" + player.getUUID() + "\", \"skins\": ";
		fx.playerFile(player.getUUID(), damaged);
		assertThat(fx.skins.claim(player, player.getInventory().getItem(0)) == OwnershipChange.DAMAGED_FILE, "a damaged file took a claim");
		assertThat(player.getInventory().getItem(0).getCount() == 1, "a refused claim used the token");
		assertThat(SkinFixtures.read(fx.playerFile(player.getUUID())).equals(damaged), "the damaged file was overwritten");
		helper.succeed();
	}

	/** Tokens go into the inventory; what doesn't fit drops at the player's feet. */
	@GameTest
	public void givenTokensThatDontFitDropAtThePlayersFeet(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet").skin("broken", "sword");
		fx.file("broken", "skin.json5", "{ name: \"Broken\", kind: \"nope\" }");
		fx.reload();
		ServerPlayer full = helper.makeMockServerPlayerInLevel();
		full.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));   // where the test is, so its chunk is loaded
		full.setGameMode(GameType.SURVIVAL);   // a creative inventory swallows what doesn't fit, as with vanilla's /give
		full.getInventory().clearContent();
		for (int i = 1; i < 36; i++) full.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
		full.getInventory().setItem(40, new ItemStack(Items.DIRT, 64));
		full.getInventory().setItem(0, SkinToken.of("crown_test", 60));
		ServerPlayer empty = ApplyTests.carrying(helper);

		assertThat(fx.skins.giveTokens(List.of(full, empty), "crown_test", 10) == OwnershipChange.DONE, "the tokens weren't given");
		assertThat(full.getInventory().getItem(0).getCount() == 64, "the stack was topped up to " + full.getInventory().getItem(0).getCount());
		ItemStack given = empty.getInventory().getItem(0);
		assertThat(SkinToken.skinOf(given).equals(java.util.Optional.of("crown_test")) && given.getCount() == 10, "the second player got " + given);
		List<? extends ItemEntity> dropped = helper.getLevel().getEntities(EntityTypes.ITEM,
				e -> SkinToken.skinOf(e.getItem()).isPresent());
		assertThat(dropped.size() == 1 && dropped.getFirst().getItem().getCount() == 6, "dropped " + dropped + " for a player at " + full.position());
		assertThat(dropped.getFirst().distanceTo(full) < 2, "dropped at " + dropped.getFirst().position() + ", not the feet of a player at " + full.position());
		dropped.forEach(ItemEntity::discard);

		assertThat(fx.skins.giveTokens(List.of(empty), "broken", 1) == OwnershipChange.DORMANT_SKIN, "a Dormant skin's tokens were given");
		assertThat(fx.skins.giveTokens(List.of(empty), "nowhere", 1) == OwnershipChange.UNKNOWN_SKIN, "an unknown skin's tokens were given");
		assertThat(empty.getInventory().getItem(1).isEmpty() && empty.getInventory().getItem(0).getCount() == 10, "a refused give gave something");
		helper.succeed();
	}

	/** What Polymer sends this viewer of the token, read by the test's Skins. */
	static ItemStack view(GameTestHelper helper, SkinFixtures fx, ItemStack token, ServerPlayer viewer) {
		ItemStack client = PolymerItemUtils.getPolymerItemStack(token, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
		return fx.skins.tokenView(token, client, viewer.getUUID());
	}

	private static List<Component> lore(ItemStack client) {
		return client.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	private static List<String> text(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	private static void assertLine(Component line, String text, ChatFormatting colour) {
		assertThat(line != null && line.getString().equals(text), "expected \"" + text + "\", got " + (line == null ? "nothing" : "\"" + line.getString() + "\""));
		assertThat(Objects.equals(line.getStyle().getColor(), TextColor.fromLegacyFormat(colour)), "\"" + text + "\" is coloured " + line.getStyle().getColor());
		assertThat(!line.getStyle().isItalic(), "\"" + text + "\" is italic");
	}
}
