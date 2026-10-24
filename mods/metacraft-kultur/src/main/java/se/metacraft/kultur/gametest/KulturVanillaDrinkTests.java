package se.metacraft.kultur.gametest;

import eu.pb4.polymer.core.api.other.PolymerMobEffect;
import se.metacraft.kultur.drinks.Alcohol;
import se.metacraft.kultur.drinks.DrinkEffects;
import se.metacraft.kultur.drinks.DrinkKind;
import se.metacraft.kultur.drinks.DrinkPotions;
import se.metacraft.kultur.drinks.DrunkTicker;
import se.metacraft.kultur.drinks.Drunkenness;
import se.metacraft.kultur.drinks.KulturDamageTypes;
import se.metacraft.kultur.drinks.KulturDrinks;
import se.metacraft.kultur.drinks.ToClient;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The chapter drinks without Brewery ({@link KulturDrinks}). Every test here passes at once when
 * Brewery is loaded, as {@code KulturDrinkTests} does without it. Run the other mode with
 * {@code -PkulturNoBrewery}.
 *
 * <p>A mock player is not ticked as a real one is (no client drives it), so the tests tick its
 * drunkenness themselves ({@link #ticks}), counting its effects down as the server would.
 */
public final class KulturVanillaDrinkTests {
	@GameTest
	public void drinksAreActiveWithoutBrewery(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		helper.assertTrue(KulturDrinks.active(), "the drinks are not active without Brewery");
		helper.assertTrue(KulturDrinks.registered(), "the drinks were not registered");
		helper.succeed();
	}

	// --- The meter: kept with the player, cleared by death ---

	/** A restart mid-drunk: the meter comes back from the player's save, and Inebriated with it within a second. */
	@GameTest
	public void meterSurvivesSaveAndLoad(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 55);
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		player.saveWithoutId(out);
		CompoundTag saved = out.buildResult();
		saved.remove("UUID");   // loaded into another mock player, which keeps its own
		saved.remove("active_effects");   // so Inebriated has to come back from the meter, not from the save

		ServerPlayer loaded = drinker(helper);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
		helper.assertValueEqual(Drunkenness.get(loaded), 55.0, "alcohol after save and load");
		ticks(helper, loaded, DrunkTicker.REFRESH_EVERY);
		MobEffectInstance inebriated = loaded.getEffect(KulturDrinks.INEBRIATED);
		helper.assertTrue(inebriated != null, "no Inebriated a second after loading");
		helper.assertValueEqual(inebriated.getAmplifier(), 3, "Inebriated's level after loading (IV)");
		sober(player, loaded);
		helper.succeed();
	}

	@GameTest
	public void deathClearsTheMeter(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 55);
		ticks(helper, player, 80);
		helper.assertTrue(player.hasEffect(KulturDrinks.INEBRIATED), "not Inebriated before dying");
		ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		ticks(helper, respawned, 20);
		helper.assertValueEqual(Drunkenness.get(respawned), 0.0, "alcohol after death");
		helper.assertTrue(respawned.getActiveEffects().isEmpty(), "effects after death: " + respawned.getActiveEffects());
		sober(respawned);
		helper.succeed();
	}

	/** A live respawn (leaving the End) is not a death: Brewery copies the meter then, and so does this. */
	@GameTest
	public void liveRespawnKeepsTheMeter(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 55);
		ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, true, Entity.RemovalReason.CHANGED_DIMENSION);
		helper.assertValueEqual(Drunkenness.get(respawned), 55.0, "alcohol after a live respawn");
		sober(respawned);
		helper.succeed();
	}

	// --- The steps ---

	@GameTest
	public void meterDrains(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 60);
		ticks(helper, player, 1200);
		helper.assertTrue(Math.abs(Drunkenness.get(player) - 45.6) < 1e-6, "alcohol after a minute from 60: " + Drunkenness.get(player));
		Drunkenness.set(player, 0.005);
		ticks(helper, player, 1);
		helper.assertValueEqual(Drunkenness.get(player), 0.0, "alcohol drained past zero");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void stepEffectsAtTheirLevels(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		player.setHealth(player.getMaxHealth());

		at(player, 35);
		ticks(helper, player, 80);
		shows(helper, player, MobEffects.WEAKNESS, "at 35");
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "slowness at 35");

		at(player, 45);
		ticks(helper, player, 80);
		shows(helper, player, MobEffects.WEAKNESS, "at 45");
		shows(helper, player, MobEffects.SLOWNESS, "at 45");
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "nausea at 45");

		at(player, 65);
		ticks(helper, player, 320);
		shows(helper, player, MobEffects.NAUSEA, "at 65");
		helper.assertFalse(player.hasEffect(MobEffects.DARKNESS), "darkness at 65");

		at(player, 85);
		player.setHealth(player.getMaxHealth());
		ticks(helper, player, 320);
		shows(helper, player, MobEffects.DARKNESS, "at 85");
		shows(helper, player, MobEffects.WEAKNESS, "at 85");
		shows(helper, player, MobEffects.SLOWNESS, "at 85");
		shows(helper, player, MobEffects.NAUSEA, "at 85");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void inebriatedShowsTheStep(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		double[] levels = {10, 35, 45, 55, 65, 75, 85};
		for (int i = 0; i < levels.length; i++) {
			at(player, levels[i]);
			ticks(helper, player, 20);
			MobEffectInstance inebriated = player.getEffect(KulturDrinks.INEBRIATED);
			helper.assertTrue(inebriated != null, "no Inebriated at " + levels[i]);
			helper.assertValueEqual(inebriated.getAmplifier(), i, "Inebriated's amplifier at " + levels[i]);
			helper.assertTrue(inebriated.showIcon(), "Inebriated hides its icon");
			int sober = Alcohol.ticksUntilSober(Drunkenness.get(player));
			helper.assertTrue(Math.abs(inebriated.getDuration() - sober) <= 20,
					"Inebriated's timer at " + levels[i] + ": " + inebriated.getDuration() + ", sober in " + sober);
		}
		// Sobering from 85 to 75 steps Inebriated down at once, timer and all.
		at(player, 75);
		ticks(helper, player, 20);
		helper.assertValueEqual(player.getEffect(KulturDrinks.INEBRIATED).getAmplifier(), 5, "Inebriated's amplifier after stepping down to 75");
		at(player, 0);
		ticks(helper, player, 1);
		helper.assertFalse(player.hasEffect(KulturDrinks.INEBRIATED), "Inebriated outlived the alcohol");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void inebriatedLooksLikeLuck(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		MobEffect effect = KulturDrinks.INEBRIATED.value();
		helper.assertTrue(effect instanceof PolymerMobEffect, "Inebriated is not a Polymer effect");
		MobEffect shown = ((PolymerMobEffect) effect).getPolymerReplacement(effect, PacketContext.get());
		helper.assertTrue(shown == MobEffects.LUCK.value(), "a vanilla client is shown " + shown + ", not Luck");
		helper.succeed();
	}

	/** The stagger is a push the player's own client is sent; the server does not move a player. */
	@GameTest
	public void staggerMovesThePlayer(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		at(player, 75);
		List<Vec3> pushes = pushesTo(player, () -> ticks(helper, player, 40));
		helper.assertValueEqual(pushes.size(), 10, "pushes in 40 ticks (one every 4)");
		for (Vec3 push : pushes) {
			helper.assertTrue(push.horizontalDistance() > 0, "a push with no sideways part: " + push);
			helper.assertTrue(push.length() <= 0.2 + 1e-9, "a push past Brewery's cap: " + push);
		}
		at(player, 45);
		helper.assertTrue(pushesTo(player, () -> ticks(helper, player, 40)).isEmpty(), "pushed at 45, below the stagger's step");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void staggerSkipsRidingAndFlying(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer rider = drinker(helper);
		Boat boat = EntityTypes.OAK_BOAT.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		boat.snapTo(rider.position());
		helper.getLevel().addFreshEntity(boat);
		helper.assertTrue(rider.startRiding(boat), "the drinker could not get in the boat");
		at(rider, 75);
		helper.assertTrue(pushesTo(rider, () -> ticks(helper, rider, 40)).isEmpty(), "a player in a boat was pushed");
		rider.stopRiding();
		boat.discard();

		ServerPlayer flier = drinker(helper);
		flier.setGameMode(GameType.CREATIVE);
		flier.getAbilities().flying = true;
		at(flier, 75);
		helper.assertTrue(pushesTo(flier, () -> ticks(helper, flier, 40)).isEmpty(), "a flying player was pushed");

		ServerPlayer sleeper = drinker(helper);
		sleeper.setSleepingPos(sleeper.blockPosition());
		helper.assertTrue(sleeper.isSleeping(), "the drinker is not asleep");
		at(sleeper, 75);
		helper.assertTrue(pushesTo(sleeper, () -> ticks(helper, sleeper, 40)).isEmpty(), "a sleeping player was pushed");
		sleeper.clearSleepingPos();
		sober(rider, flier, sleeper);
		helper.succeed();
	}

	@GameTest
	public void poisoningNeverKills(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		at(player, 75);
		player.setHealth(3);
		ticks(helper, player, 400);
		helper.assertTrue(player.isAlive(), "alcohol poisoning killed the drinker");
		helper.assertTrue(player.getHealth() >= 1, "alcohol poisoning took the drinker below half a heart: " + player.getHealth());
		helper.assertTrue(player.getHealth() < 3, "no alcohol poisoning at 75");
		DamageSource last = player.getLastDamageSource();
		helper.assertTrue(last != null && last.is(KulturDamageTypes.ALCOHOL_POISONING), "last damage was " + last);
		sober(player);
		helper.succeed();
	}

	/** An effect refreshed on its rate must outlast the rate, or its icon blinks out between refreshes. */
	@GameTest
	public void stepEffectsDoNotFlicker(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		at(player, 99);   // still at step VII after the 960 ticks below
		player.setHealth(player.getMaxHealth());
		ticks(helper, player, 320);   // every step's effect has come once
		List<String> gaps = new ArrayList<>();
		for (int i = 0; i < 640; i++) {
			ticks(helper, player, 1);
			for (Holder<MobEffect> effect : List.of(MobEffects.WEAKNESS, MobEffects.SLOWNESS, MobEffects.NAUSEA, MobEffects.DARKNESS, KulturDrinks.INEBRIATED)) {
				if (!player.hasEffect(effect) && gaps.size() < 10) gaps.add(effect.getRegisteredName() + " at tick " + player.tickCount);
			}
		}
		helper.assertTrue(gaps.isEmpty(), "effects blinked out: " + gaps);
		sober(player);
		helper.succeed();
	}

	// --- The drinks: recipes, drinking, the blackout, sobering ---

	/** Each brewing recipe makes its drink and nothing else does; water and a potato is no other recipe. */
	@GameTest
	public void eachBrewingRecipeMakesItsDrink(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
		ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
		ItemStack alcohol = DrinkPotions.bottle(DrinkKind.ALCOHOL);
		List<String> wrong = new ArrayList<>();
		brews(helper, wrong, water, Items.POTATO, DrinkKind.ALCOHOL);
		brews(helper, wrong, alcohol, Items.SWEET_BERRIES, DrinkKind.SPIKEN);
		brews(helper, wrong, alcohol, Items.SUSPICIOUS_STEW, DrinkKind.SLAGGAN);
		brews(helper, wrong, awkward, Items.POTATO, null);
		brews(helper, wrong, water, Items.SWEET_BERRIES, null);
		brews(helper, wrong, awkward, Items.SUSPICIOUS_STEW, null);
		brews(helper, wrong, alcohol, Items.POTATO, null);
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** PolymITer's Nyckeln took any potion, so a Potion of Healing made one; it takes a water bottle only. */
	@GameTest
	public void nyckelnCraftsOnlyFromWater(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ItemStack fromWater = craft(helper, new ItemStack(Items.APPLE), PotionContents.createItemStack(Items.POTION, Potions.WATER));
		helper.assertTrue(fromWater.is(KulturDrinks.NYCKELN), "apple and a water bottle craft " + fromWater + ", not Nyckeln");
		ItemStack fromHealing = craft(helper, new ItemStack(Items.APPLE), PotionContents.createItemStack(Items.POTION, Potions.HEALING));
		helper.assertFalse(fromHealing.is(KulturDrinks.NYCKELN), "apple and a Potion of Healing craft Nyckeln");
		helper.succeed();
	}

	@GameTest
	public void drinkingAddsAlcohol(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		drink(helper, player, DrinkPotions.bottle(DrinkKind.SPIKEN));
		helper.assertValueEqual(Drunkenness.get(player), 30.0, "alcohol after a Spiken");
		helper.assertValueEqual(player.getEffect(KulturDrinks.INEBRIATED).getAmplifier(), 1, "Inebriated's amplifier at 30 (II)");
		drink(helper, player, DrinkPotions.bottle(DrinkKind.ALCOHOL));
		helper.assertValueEqual(Drunkenness.get(player), 50.0, "alcohol after a Spiken and an Alcoholic Beverage");
		helper.assertValueEqual(player.getEffect(KulturDrinks.INEBRIATED).getAmplifier(), 3, "Inebriated's amplifier at 50 (IV)");
		drink(helper, player, DrinkPotions.bottle(DrinkKind.SLAGGAN));
		helper.assertValueEqual(Drunkenness.get(player), 85.0, "alcohol after a Släggan on top");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void spikenGivesSpeedOrSlowness(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer first = drinker(helper);
		drink(helper, first, DrinkPotions.bottle(DrinkKind.SPIKEN));
		MobEffectInstance pace = speedOrSlowness(helper, first, "after one Spiken");
		helper.assertValueEqual(pace.getDuration(), 4000, "Spiken's effect's duration");
		helper.assertValueEqual(pace.getAmplifier(), 0, "Spiken's effect's amplifier at 30");

		ServerPlayer drunk = drinker(helper);
		Drunkenness.set(drunk, 45);
		drink(helper, drunk, DrinkPotions.bottle(DrinkKind.SPIKEN));
		helper.assertValueEqual(speedOrSlowness(helper, drunk, "at 75").getAmplifier(), 1, "Spiken's effect's amplifier at 75");

		ServerPlayer past = drinker(helper);
		Drunkenness.set(past, 80);
		drink(helper, past, DrinkPotions.bottle(DrinkKind.SPIKEN));
		helper.assertFalse(past.hasEffect(MobEffects.SPEED) || past.hasEffect(MobEffects.SLOWNESS), "the drink's own effect came with the blackout");
		sober(first, drunk, past);
		helper.succeed();
	}

	@GameTest
	public void slagganStrengthOrWeaknessWhenDrunk(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		for (int i = 0; i < 8; i++) {   // three times in four slow: try it a few times
			ServerPlayer drunk = drinker(helper);
			Drunkenness.set(drunk, 30);
			drink(helper, drunk, DrinkPotions.bottle(DrinkKind.SLAGGAN));
			boolean hammer = drunk.hasEffect(MobEffects.SLOWNESS) && drunk.hasEffect(MobEffects.STRENGTH) && !drunk.hasEffect(MobEffects.SPEED) && !drunk.hasEffect(MobEffects.WEAKNESS);
			boolean other = drunk.hasEffect(MobEffects.SPEED) && drunk.hasEffect(MobEffects.WEAKNESS) && !drunk.hasEffect(MobEffects.SLOWNESS) && !drunk.hasEffect(MobEffects.STRENGTH);
			helper.assertTrue(hammer || other, "Släggan at 65 gave " + drunk.getActiveEffects());

			ServerPlayer sober = drinker(helper);
			drink(helper, sober, DrinkPotions.bottle(DrinkKind.SLAGGAN));
			helper.assertFalse(sober.hasEffect(MobEffects.STRENGTH) || sober.hasEffect(MobEffects.WEAKNESS), "Släggan at 35 gave " + sober.getActiveEffects());
			speedOrSlowness(helper, sober, "after one Släggan");
			sober(drunk, sober);
		}
		helper.succeed();
	}

	@GameTest
	public void nyckelnIsSober(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		drink(helper, player, new ItemStack(KulturDrinks.NYCKELN));
		helper.assertValueEqual(Drunkenness.get(player), 0.0, "alcohol after Nyckeln");
		helper.assertTrue(player.getActiveEffects().isEmpty(), "effects after Nyckeln: " + player.getActiveEffects());
		Drunkenness.set(player, Alcohol.BLACKOUT_AT);
		drink(helper, player, new ItemStack(KulturDrinks.NYCKELN));
		helper.assertValueEqual(Drunkenness.get(player), Alcohol.BLACKOUT_AT, "alcohol after Nyckeln at the limit");
		helper.assertFalse(player.hasEffect(MobEffects.BLINDNESS), "Nyckeln blacked the drinker out");
		sober(player);
		helper.succeed();
	}

	/** Only drinking counts: a splash or lingering bottle of a drink makes nobody drunk. */
	@GameTest(maxTicks = 100)
	public void splashAndLingeringAffectNobody(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		Vec3 above = helper.absoluteVec(new Vec3(2.5, 3.5, 2.5));
		ThrownSplashPotion splash = new ThrownSplashPotion(helper.getLevel(), above.x, above.y, above.z,
				PotionContents.createItemStack(Items.SPLASH_POTION, DrinkPotions.potion(DrinkKind.SPIKEN)));
		ThrownLingeringPotion lingering = new ThrownLingeringPotion(helper.getLevel(), above.x, above.y + 0.5, above.z,
				PotionContents.createItemStack(Items.LINGERING_POTION, DrinkPotions.potion(DrinkKind.SLAGGAN)));
		for (var thrown : List.of(splash, lingering)) {
			thrown.setDeltaMovement(0, -0.5, 0);
			helper.getLevel().addFreshEntity(thrown);
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(splash.isRemoved() && lingering.isRemoved(), "the bottles have not landed");
			helper.assertValueEqual(Drunkenness.get(player), 0.0, "alcohol after being splashed");
			helper.assertTrue(player.getActiveEffects().isEmpty(), "effects after being splashed: " + player.getActiveEffects());
			helper.assertEntityNotPresent(EntityTypes.AREA_EFFECT_CLOUD);
		});
	}

	@GameTest
	public void blackoutAt100(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 70);
		player.setHealth(player.getMaxHealth());
		Vec3 from = player.position();
		var lines = sentTo(player, ClientboundSetActionBarTextPacket.class, () -> drink(helper, player, DrinkPotions.bottle(DrinkKind.SPIKEN)));
		blackedOut(helper, player, from, lines);
		sober(player);
		helper.succeed();
	}

	/** A blackout in the Nether wakes you in the Nether, under its roof, not on top of it. */
	@GameTest(dimension = "minecraft:the_nether")
	public void blackoutInTheNetherStaysInTheNether(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 90);
		player.setHealth(player.getMaxHealth());
		Vec3 from = player.position();
		var lines = sentTo(player, ClientboundSetActionBarTextPacket.class, () -> drink(helper, player, DrinkPotions.bottle(DrinkKind.ALCOHOL)));
		blackedOut(helper, player, from, lines);
		helper.assertTrue(player.level().dimension() == Level.NETHER, "woke up in " + player.level().dimension().identifier());
		helper.assertTrue(player.getY() < 127, "woke up on the Nether's roof, at y " + player.getY());
		sober(player);
		helper.succeed();
	}

	/**
	 * Out at sea or over the void, far from any loaded chunk: the blackout finds nowhere to go, loads
	 * nothing looking (a chunk load per try would freeze the server), and leaves the player where they
	 * are with their effects, water breathing included. The rest of the blackout still happens.
	 */
	@GameTest
	public void blackoutWithNowhereToGoStaysPut(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerLevel level = helper.getLevel();
		ServerPlayer player = drinker(helper);
		Vec3 home = player.position();
		// Moved in this tick, so the server has not yet loaded anything around the new spot.
		player.snapTo(1_000_000.5, 100, 1_000_000.5);
		helper.assertTrue(level.getChunkSource().getChunkNow(player.getBlockX() >> 4, player.getBlockZ() >> 4) == null,
				"the far-off chunk is already loaded");
		player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 6000, 0));
		player.setHealth(player.getMaxHealth());
		Drunkenness.set(player, 90);
		int loaded = level.getChunkSource().getLoadedChunksCount();
		try {
			var lines = sentTo(player, ClientboundSetActionBarTextPacket.class, () -> drink(helper, player, DrinkPotions.bottle(DrinkKind.ALCOHOL)));
			helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), loaded, "loaded chunks after the blackout");
			helper.assertTrue(level.getChunkSource().getChunkNow(player.getBlockX() >> 4, player.getBlockZ() >> 4) == null,
					"the blackout loaded the chunk it stood in");
			helper.assertTrue(player.getX() == 1_000_000.5 && player.getZ() == 1_000_000.5, "the player moved to " + player.position());
			helper.assertTrue(player.hasEffect(MobEffects.WATER_BREATHING), "the blackout took water breathing from a player it left at sea");
			helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "no blindness after the blackout");
			helper.assertValueEqual(Drunkenness.get(player), Alcohol.AFTER_BLACKOUT, "alcohol after the blackout");
			DamageSource last = player.getLastDamageSource();
			helper.assertTrue(last != null && last.is(KulturDamageTypes.ALCOHOL_POISONING), "the blackout's damage was " + last);
			helper.assertValueEqual(lines.size(), 1, "action bar lines after the blackout");
		} finally {
			player.snapTo(home);
			sober(player);
		}
		helper.succeed();
	}

	/** Bread takes 3 off, milk 10; neither takes the meter below zero, or does anything when sober. */
	@GameTest
	public void breadAndMilkSober(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 20);
		drink(helper, player, new ItemStack(Items.BREAD));
		helper.assertValueEqual(Drunkenness.get(player), 17.0, "alcohol after bread at 20");
		drink(helper, player, new ItemStack(Items.MILK_BUCKET));
		helper.assertValueEqual(Drunkenness.get(player), 7.0, "alcohol after milk at 17");
		drink(helper, player, new ItemStack(Items.MILK_BUCKET));
		helper.assertValueEqual(Drunkenness.get(player), 0.0, "alcohol after milk at 7");
		drink(helper, player, new ItemStack(Items.BREAD));
		helper.assertValueEqual(Drunkenness.get(player), 0.0, "alcohol after bread when sober");
		sober(player);
		helper.succeed();
	}

	// --- Helpers ---

	private static void brews(GameTestHelper helper, List<String> wrong, ItemStack input, Item reagent, DrinkKind want) {
		ServerLevel level = helper.getLevel();
		BrewingInput brewing = new BrewingInput(input.copy(), new ItemStack(reagent));
		List<ItemStack> outputs = new ArrayList<>();
		for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
			if (holder.value() instanceof BrewingRecipe recipe && recipe.matches(brewing, level)) outputs.add(recipe.assemble(brewing));
		}
		String what = DrinkPotions.kindOf(input) + "/" + input.getItem() + " + " + reagent;
		if (want == null) {
			for (ItemStack out : outputs) {
				if (DrinkPotions.kindOf(out) != null) wrong.add(what + " brews " + DrinkPotions.kindOf(out));
			}
		} else if (outputs.size() != 1 || DrinkPotions.kindOf(outputs.getFirst()) != want || !outputs.getFirst().is(Items.POTION)) {
			wrong.add(what + " brews " + outputs + ", wanted one " + want);
		}
	}

	private static ItemStack craft(GameTestHelper helper, ItemStack... grid) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(grid.length, 1, List.of(grid));
		return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
	}

	/** Drinks (or eats) a stack to the end, the way a player finishing it does. */
	static void drink(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.finishUsingItem(helper.getLevel(), player);
	}

	private static MobEffectInstance speedOrSlowness(GameTestHelper helper, ServerPlayer player, String when) {
		MobEffectInstance speed = player.getEffect(MobEffects.SPEED), slowness = player.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue((speed == null) != (slowness == null), "not exactly one of speed and slowness " + when + ": " + player.getActiveEffects());
		return speed != null ? speed : slowness;
	}

	private static void blackedOut(GameTestHelper helper, ServerPlayer player, Vec3 from, List<ClientboundSetActionBarTextPacket> lines) {
		helper.assertValueEqual(Drunkenness.get(player), Alcohol.AFTER_BLACKOUT, "alcohol after the blackout");
		helper.assertTrue(Math.abs(player.getX() - from.x) <= 251 && Math.abs(player.getZ() - from.z) <= 251,
				"woke up too far away: from " + from + " to " + player.position());
		MobEffectInstance blindness = player.getEffect(MobEffects.BLINDNESS);
		helper.assertTrue(blindness != null, "no blindness after the blackout");
		helper.assertValueEqual(blindness.getDuration(), 200, "blindness's duration (10 s)");
		helper.assertTrue(blindness.showIcon(), "blindness hides its icon");
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (!effect.is(MobEffects.BLINDNESS) && !effect.is(KulturDrinks.INEBRIATED)) helper.fail("left over after the blackout: " + effect);
		}
		DamageSource last = player.getLastDamageSource();
		helper.assertTrue(last != null && last.is(KulturDamageTypes.ALCOHOL_POISONING), "the blackout's damage was " + last);
		helper.assertValueEqual(lines.size(), 1, "action bar lines after the blackout");
		Component line = lines.getFirst().text();
		helper.assertValueEqual(line.getString(), DrinkEffects.HEADACHE, "the blackout's line");
		helper.assertTrue(line.getStyle().isBold(), "the blackout's line is not bold");
		helper.assertTrue(line.getStyle().getColor() != null && line.getStyle().getColor().getValue() == DrinkEffects.HEADACHE_COLOUR,
				"the blackout's line's colour: " + line.getStyle().getColor());
	}

	/** True when this mode is on; otherwise the test has nothing to do and succeeds. */
	private static boolean vanilla(GameTestHelper helper) {
		if (KulturDrinks.active()) return true;
		helper.succeed();   // Brewery brews the drinks; KulturDrinkTests covers that
		return false;
	}

	/** A sober survival player who can take damage. */
	static ServerPlayer drinker(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// A player takes no damage until their client says it has loaded; a mock has no client to say it.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setGameMode(GameType.SURVIVAL);
		player.removeAllEffects();
		Drunkenness.set(player, 0);
		player.tickCount = 0;
		return player;
	}

	/** Sets the meter and starts the player's tick count over, so every rate comes round in a known number of ticks. */
	static void at(ServerPlayer player, double alcohol) {
		Drunkenness.set(player, alcohol);
		player.tickCount = 0;
	}

	/**
	 * {@code n} server ticks of a player's drunkenness: the tick count goes up, effects count down
	 * and the hurt cooldown wears off as on a real player, then {@link DrunkTicker} runs.
	 */
	static void ticks(GameTestHelper helper, ServerPlayer player, int n) {
		for (int i = 0; i < n; i++) {
			player.tickCount++;
			if (player.getInvulnerableTime() > 0) player.setInvulnerableTime(player.getInvulnerableTime() - 1);
			for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
				if (!effect.tickServer(helper.getLevel(), player, () -> {})) player.removeEffect(effect.getEffect());
			}
			DrunkTicker.tick(player);
		}
	}

	/** The stagger's pushes sent to this player's client while {@code during} runs. */
	static List<Vec3> pushesTo(ServerPlayer player, Runnable during) {
		List<Vec3> pushes = new ArrayList<>();
		try (var watch = ToClient.watch((to, packet) -> {
			if (to == player && packet instanceof ClientboundExplodePacket explode) explode.playerKnockback().ifPresent(pushes::add);
		})) {
			during.run();
		}
		return pushes;
	}

	/** Packets of a type sent to this player's client while {@code during} runs. */
	static <P extends Packet<?>> List<P> sentTo(ServerPlayer player, Class<P> type, Runnable during) {
		List<P> sent = new ArrayList<>();
		try (var watch = ToClient.watch((to, packet) -> {
			if (to == player && type.isInstance(packet)) sent.add(type.cast(packet));
		})) {
			during.run();
		}
		return sent;
	}

	static void shows(GameTestHelper helper, ServerPlayer player, Holder<MobEffect> effect, String when) {
		MobEffectInstance instance = player.getEffect(effect);
		helper.assertTrue(instance != null, "no " + effect.getRegisteredName() + " " + when);
		helper.assertTrue(instance.showIcon(), effect.getRegisteredName() + " hides its icon " + when);
		helper.assertValueEqual(instance.getAmplifier(), 0, effect.getRegisteredName() + "'s amplifier " + when);
	}

	/** Leaves the mock players sober, so the server's own tick has nothing to do to them after the test. */
	static void sober(ServerPlayer... players) {
		for (ServerPlayer player : players) {
			Drunkenness.set(player, 0);
			player.removeAllEffects();
		}
	}
}
