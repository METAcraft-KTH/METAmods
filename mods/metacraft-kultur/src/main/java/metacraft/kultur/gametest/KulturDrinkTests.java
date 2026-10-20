package metacraft.kultur.gametest;

import eu.pb4.brewery.BreweryInit;
import eu.pb4.brewery.drink.AlcoholManager;
import eu.pb4.brewery.drink.DrinkType;
import eu.pb4.brewery.drink.DrinkUtils;
import metacraft.kultur.Kultur;
import metacraft.kultur.compat.brewery.BreweryEffectsFiles;
import metacraft.kultur.drinks.KulturDamageTypes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The chapter drinks, as Brewery reads them from {@code data/kultur/brewery_drinks}: every one
 * loaded, every recipe brewing its own drink and no other (Brewery's included), and PolymITer's
 * effects — the stagger, and the blackout at {@value #BLACKOUT_AT} alcohol. Brewery is only a dev
 * runtime here; on a server without it the drinks are inert data.
 */
public final class KulturDrinkTests {
	/** The alcohol level the blackout comes at; the {@code apply_check} in every alcoholic drink. */
	private static final double BLACKOUT_AT = 100;

	private static final List<String> DRINKS = List.of("alcohol", "spiken", "slaggan", "nyckeln");

	/** Each drink and the cauldron that brews it. (Built when asked: a stack cannot be made while mods initialise.) */
	private static Map<String, List<ItemStack>> recipes() {
		return Map.of(
			"alcohol", List.of(new ItemStack(Items.POTATO, 4), new ItemStack(Items.SUGAR, 2)),
			"spiken", List.of(new ItemStack(Items.POTATO, 4), new ItemStack(Items.SWEET_BERRIES, 4)),
			"slaggan", List.of(new ItemStack(Items.POTATO, 4), new ItemStack(Items.SUSPICIOUS_STEW)),
			"nyckeln", List.of(new ItemStack(Items.APPLE, 2), new ItemStack(Items.SUGAR)));
	}

	private static final List<String> DISTILLED = List.of("spiken", "slaggan");

	@GameTest
	public void everyDrinkIsLoaded(GameTestHelper helper) {
		if (breweryHere(helper)) WithBrewery.everyDrinkIsLoaded(helper);
	}

	/**
	 * A cauldron offers every drink whose ingredients are all in it, however much else is, and the
	 * best-cooked one wins (the brewing stand does the same among the distilled ones). So each
	 * recipe must match its own drink and no other of its kind — Brewery's vodka is six potatoes,
	 * which is why ours take four.
	 */
	@GameTest
	public void eachRecipeBrewsOnlyItsDrink(GameTestHelper helper) {
		if (breweryHere(helper)) WithBrewery.eachRecipeBrewsOnlyItsDrink(helper);
	}

	/**
	 * Brewery's own drunkenness — the stagger, the nausea, alcohol poisoning, bread and milk sobering
	 * you up — is its {@code brewery_effects.json}, which Brewery 0.17 looks for by listing the resource
	 * root, and 26.3 refuses an empty path; {@code BreweryEffectsMixin} finds it per namespace instead.
	 */
	@GameTest
	public void breweryDrunkennessLoads(GameTestHelper helper) {
		if (breweryHere(helper)) WithBrewery.breweryDrunkennessLoads(helper);
	}

	@GameTest
	public void spikenStaggers(GameTestHelper helper) {
		if (breweryHere(helper)) WithBrewery.spikenStaggers(helper);
	}

	@GameTest
	public void slagganHitsLikeAHammer(GameTestHelper helper) {
		if (breweryHere(helper)) WithBrewery.slagganHitsLikeAHammer(helper);
	}

	@GameTest
	public void theDrinkPastTheLimitBlacksOut(GameTestHelper helper) {
		if (breweryHere(helper)) WithBrewery.theDrinkPastTheLimitBlacksOut(helper);
	}

	@GameTest
	public void nyckelnIsSober(GameTestHelper helper) {
		if (breweryHere(helper)) WithBrewery.nyckelnIsSober(helper);
	}

	/**
	 * With Brewery on the server, Kultur's own brewing (metacraft.kultur.drinks) stays out of the way:
	 * no potions, no Nyckeln item, no Inebriated, no recipes, no damage type of its own.
	 */
	@GameTest
	public void noDrinksOfOurOwnBesideBrewery(GameTestHelper helper) {
		if (!breweryHere(helper)) return;
		List<String> wrong = new ArrayList<>();
		for (String drink : List.of("alcohol", "spiken", "slaggan")) {
			if (BuiltInRegistries.POTION.containsKey(id(drink))) wrong.add("potion " + drink + " registered");
		}
		if (BuiltInRegistries.ITEM.containsKey(id("nyckeln"))) wrong.add("item nyckeln registered");
		if (BuiltInRegistries.MOB_EFFECT.containsKey(id("inebriated"))) wrong.add("effect inebriated registered");
		for (String drink : DRINKS) {
			if (helper.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id(drink))).isPresent()) wrong.add("recipe " + drink + " loaded");
		}
		if (helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(KulturDamageTypes.ALCOHOL_POISONING).isPresent()) {
			wrong.add("damage type alcohol_poisoning loaded");
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	private static boolean breweryHere(GameTestHelper helper) {
		if (FabricLoader.getInstance().isModLoaded("brewery")) return true;
		helper.succeed();   // nothing to test: without Brewery the drinks are data nobody reads
		return false;
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, path);
	}

	/**
	 * Everything that names a Brewery class, in a class of its own: it is loaded only once
	 * {@link #breweryHere} has said Brewery is there, so the tests above load without it.
	 */
	private static final class WithBrewery {
		static void everyDrinkIsLoaded(GameTestHelper helper) {
			List<String> wrong = new ArrayList<>();
			for (String id : DRINKS) {
				DrinkType type = BreweryInit.DRINK_TYPES.get(id(id));
				if (type == null) { wrong.add(id + ": not loaded"); continue; }
				if (type.requireDistillation() != DISTILLED.contains(id)) wrong.add(id + ": distillation " + type.requireDistillation());
				if (!type.barrelInfo().isEmpty()) wrong.add(id + ": wants a barrel");
			}
			if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
			helper.succeed();
		}

		static void eachRecipeBrewsOnlyItsDrink(GameTestHelper helper) {
			List<String> wrong = new ArrayList<>();
			for (var recipe : recipes().entrySet()) {
				boolean distilled = DISTILLED.contains(recipe.getKey());
				List<Identifier> matches = DrinkUtils.findTypes(recipe.getValue(), null, Blocks.FIRE, new ItemStack(Items.GLASS_BOTTLE)).stream()
						.filter(type -> type.requireDistillation() == distilled)
						.map(BreweryInit.DRINK_TYPE_ID::get)
						.toList();
				if (!matches.equals(List.of(id(recipe.getKey())))) wrong.add(recipe.getKey() + " brews " + matches);
			}
			if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
			helper.succeed();
		}

		static void breweryDrunkennessLoads(GameTestHelper helper) {
			// A dev environment also builds the defaults in code, so look at the file lookup itself.
			var files = BreweryEffectsFiles.find(helper.getLevel().getServer().getResourceManager());
			helper.assertTrue(files.containsKey(Identifier.fromNamespaceAndPath("brewery", "brewery_effects.json")),
					"Brewery's drunkenness file not found: " + files.keySet());
			helper.assertFalse(BreweryInit.ALCOHOL_EFFECTS.isEmpty(), "Brewery loaded no drunkenness effects");
			helper.succeed();
		}

		static void spikenStaggers(GameTestHelper helper) {
			ServerPlayer player = drinker(helper);
			drink(helper, player, "spiken");
			helper.assertTrue(player.hasEffect(MobEffects.SPEED) || player.hasEffect(MobEffects.SLOWNESS), "Spiken sent the drinker neither fast nor slow");
			helper.assertFalse(player.hasEffect(MobEffects.BLINDNESS), "one Spiken blacked the drinker out");
			helper.succeed();
		}

		static void slagganHitsLikeAHammer(GameTestHelper helper) {
			ServerPlayer player = drinker(helper);
			AlcoholManager.of(player).alcoholLevel = 40;   // drunk enough for the strength or the weakness
			drink(helper, player, "slaggan");
			// Släggan's pairs are a delayed effect, run from the drinker's tick, which a mock player has
			// none of; tick the drinking for it.
			AlcoholManager.of(player).tick();
			boolean hammer = player.hasEffect(MobEffects.SLOWNESS) && player.hasEffect(MobEffects.STRENGTH);
			boolean other = player.hasEffect(MobEffects.SPEED) && player.hasEffect(MobEffects.WEAKNESS);
			helper.assertTrue(hammer || other, "Släggan gave neither slow-and-strong nor fast-and-weak");
			helper.succeed();
		}

		static void theDrinkPastTheLimitBlacksOut(GameTestHelper helper) {
			ServerPlayer player = drinker(helper);
			AlcoholManager.of(player).alcoholLevel = BLACKOUT_AT;
			float health = player.getHealth();
			drink(helper, player, "spiken");
			helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "no blindness after the blackout");
			helper.assertFalse(player.hasEffect(MobEffects.SPEED) || player.hasEffect(MobEffects.SLOWNESS), "the stagger outlived the blackout");
			helper.assertTrue(player.getHealth() < health, "the blackout did no damage");
			helper.assertValueEqual(AlcoholManager.of(player).alcoholLevel, 30.0, "alcohol level after the blackout");
			helper.succeed();
		}

		static void nyckelnIsSober(GameTestHelper helper) {
			ServerPlayer player = drinker(helper);
			AlcoholManager.of(player).alcoholLevel = BLACKOUT_AT;
			drink(helper, player, "nyckeln");
			helper.assertFalse(player.hasEffect(MobEffects.BLINDNESS), "Nyckeln blacked the drinker out");
			helper.assertValueEqual(AlcoholManager.of(player).alcoholLevel, BLACKOUT_AT, "alcohol level after Nyckeln");
			helper.succeed();
		}

		private static ServerPlayer drinker(GameTestHelper helper) {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			// A player takes no damage until their client says it has loaded; a mock has no client to say it.
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);
			player.removeAllEffects();
			AlcoholManager.of(player).alcoholLevel = 0;
			return player;
		}

		/** A finished bottle of the best quality, drunk to the end. */
		private static void drink(GameTestHelper helper, ServerPlayer player, String id) {
			ItemStack bottle = DrinkUtils.createDrink(id(id), 0, 10, DISTILLED.contains(id) ? 1 : 0, Blocks.FIRE);
			player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
			bottle.finishUsingItem(helper.getLevel(), player);
		}
	}
}
