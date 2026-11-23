package se.metacraft.skins.gametest;

import se.metacraft.skins.ItemLocation;
import se.metacraft.skins.Perk;
import se.metacraft.skins.ReloadReport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Perks through the Skins module: which Triggers and effects a definition may combine, and the
 * held and worn (lasting) Perks a player gets from what they hold and wear, as the server keeps
 * them up tick by tick ({@code updateLastingPerks}). Event Perks are EventPerkTests'. The tests call
 * the update themselves, with their own Skins; the server's own run is SkinsCommandTests'.
 */
public final class PerkTests {
	static final String HASTE_HELD = "[ { trigger: \"held\", effects: [ { type: \"potion\", effect: \"minecraft:haste\", level: 1 } ] } ]";
	static final String HEARTS_WORN = "[ { trigger: \"worn\", effects: [ { type: \"attribute\", attribute: \"minecraft:max_health\", amount: 4 } ] } ]";

	/** The spec's worked examples load; every combination the two tables forbid rejects the skin, saying why. */
	@GameTest
	public void aTriggerOrEffectTheTablesDontAllowRejectsTheSkin(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create()
				.perky("green_pick", "pickaxe", """
						[ { trigger: "held", effects: [ { type: "potion", effect: "minecraft:haste", level: 1 } ] },
						{ trigger: "block_break", chance: 0.10, effects: [
						{ type: "particle", target: "other", particle: { type: "minecraft:happy_villager" }, count: 12 },
						{ type: "sound", target: "other", sound: "minecraft:block.amethyst_block.chime" } ] } ]""")
				.perky("sugar_blade", "sword", """
						[ { trigger: "hit", chance: 0.25, cooldown: 5, effects: [
						{ type: "potion", target: "other", effect: "minecraft:slowness", level: 1, duration: 3 },
						{ type: "particle", target: "other", particle: { type: "minecraft:item", item: "minecraft:sugar" }, count: 8 } ] } ]""")
				.perky("circlet", "helmet", """
						[ { trigger: "worn", tooltip: "While worn: +2 max hearts and a golden shimmer", effects: [
						{ type: "attribute", attribute: "minecraft:max_health", amount: 4 },
						{ type: "particle", particle: { type: "minecraft:wax_on" }, count: 1, interval: 20 } ] } ]""")
				.perky("worn_sword", "sword", "[ { trigger: \"worn\", effects: [ { type: \"potion\", effect: \"minecraft:speed\" } ] } ]")
				.perky("hitting_hat", "helmet", "[ { trigger: \"hit\", effects: [ { type: \"heal\", amount: 2 } ] } ]")
				.perky("breaking_sword", "sword", "[ { trigger: \"block_break\", effects: [ { type: \"heal\", amount: 2 } ] } ]")
				.perky("burning_pick", "pickaxe", "[ { trigger: \"held\", effects: [ { type: \"fire\", duration: 2 } ] } ]")
				.perky("hit_attribute", "axe", "[ { trigger: \"hit\", effects: [ { type: \"attribute\", attribute: \"minecraft:armor\", amount: 1 } ] } ]")
				.perky("kill_slows_victim", "sword", "[ { trigger: \"kill\", effects: [ { type: \"potion\", target: \"other\", effect: \"minecraft:slowness\", duration: 2 } ] } ]")
				.perky("timed_lasting", "boots", "[ { trigger: \"worn\", effects: [ { type: \"potion\", effect: \"minecraft:speed\", duration: 5 } ] } ]")
				.perky("lucky_lasting", "boots", "[ { trigger: \"worn\", chance: 0.5, effects: [ { type: \"potion\", effect: \"minecraft:speed\" } ] } ]")
				.perky("level_zero", "boots", "[ { trigger: \"worn\", effects: [ { type: \"potion\", effect: \"minecraft:speed\", level: 0 } ] } ]")
				.perky("typo", "boots", "[ { trigger: \"worn\", effects: [ { type: \"potion\", effect: \"minecraft:speed\", amplifier: 1 } ] } ]")
				.perky("no_such_effect", "boots", "[ { trigger: \"worn\", effects: [ { type: \"potion\", effect: \"minecraft:flying\" } ] } ]")
				.perky("hit_sound_on_self", "sword", "[ { trigger: \"hit\", effects: [ { type: \"sound\", target: \"self\", sound: \"minecraft:block.bell.use\" } ] } ]")
				.perky("kill_particles_on_self", "sword", "[ { trigger: \"kill\", effects: [ { type: \"particle\", target: \"self\", particle: { type: \"minecraft:soul\" } } ] } ]")
				.perky("break_sound_on_self", "pickaxe", "[ { trigger: \"block_break\", effects: [ { type: \"sound\", target: \"self\", sound: \"minecraft:block.bell.use\" } ] } ]")
				.perky("hurt_particles_on_self", "helmet", "[ { trigger: \"hurt\", effects: [ { type: \"particle\", target: \"self\", particle: { type: \"minecraft:soul\" } } ] } ]");
		ReloadReport report = fx.reload();
		assertThat(report.loaded().containsAll(List.of("green_pick", "sugar_blade", "circlet", "hurt_particles_on_self")), "the spec's examples didn't all load: " + report);
		assertRejected(report, "worn_sword", "\"worn\"");
		assertRejected(report, "hitting_hat", "\"hit\"");
		assertRejected(report, "breaking_sword", "\"block_break\"");
		assertRejected(report, "burning_pick", "fire");
		assertRejected(report, "hit_attribute", "attribute");
		assertRejected(report, "kill_slows_victim", "target");
		assertRejected(report, "timed_lasting", "duration");
		assertRejected(report, "lucky_lasting", "chance");
		assertRejected(report, "level_zero", "level");
		assertRejected(report, "typo", "amplifier");
		assertRejected(report, "no_such_effect", "minecraft:flying");
		// Hit, kill and block break play particles and sounds at the target, the victim or the block, never at the holder.
		assertRejected(report, "hit_sound_on_self", "target");
		assertRejected(report, "kill_particles_on_self", "target");
		assertRejected(report, "break_sound_on_self", "target");
		helper.succeed();
	}

	/** Held means the main hand: the same pickaxe in the offhand, or a plain one in the main hand, gives nothing. */
	@GameTest
	public void heldPerksWorkInTheMainHandOnly(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("green_pick", "pickaxe", HASTE_HELD);
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE));
		fx.skins.grant(player.nameAndId(), "green_pick");
		fx.skins.apply(player, "green_pick", ItemLocation.hotbar(1));

		fx.skins.updateLastingPerks(player);
		assertThat(!player.hasEffect(MobEffects.HASTE), "a plain pickaxe in the main hand gave Haste");
		player.getInventory().setSelectedSlot(1);
		fx.skins.updateLastingPerks(player);
		MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
		assertThat(haste != null && haste.getAmplifier() == 0, "holding the skinned pickaxe gave " + haste);
		assertThat(haste.showIcon() && !haste.isVisible(), "the Perk's Haste shows swirls or no icon: " + haste);

		player.setItemSlot(EquipmentSlot.OFFHAND, player.getInventory().removeItemNoUpdate(1));
		fx.skins.updateLastingPerks(player);
		assertThat(!player.hasEffect(MobEffects.HASTE), "the pickaxe in the offhand still gives Haste");
		helper.succeed();
	}

	/** Worn means an armour slot: the crown on the head adds hearts; carried in the hand, it doesn't. Taking it off ends it at once. */
	@GameTest
	public void wornPerksWorkInAnArmourSlotAndEndWhenTakenOff(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("circlet", "helmet", """
				[ { trigger: "worn", effects: [ { type: "attribute", attribute: "minecraft:max_health", amount: 4 },
				{ type: "potion", effect: "minecraft:night_vision" } ] } ]""");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.GOLDEN_HELMET));
		fx.skins.grant(player.nameAndId(), "circlet");
		fx.skins.apply(player, "circlet", ItemLocation.hotbar(0));

		fx.skins.updateLastingPerks(player);
		assertThat(player.getMaxHealth() == 20 && !player.hasEffect(MobEffects.NIGHT_VISION), "a crown in the hand counts as worn: " + player.getMaxHealth());
		player.setItemSlot(EquipmentSlot.HEAD, player.getInventory().removeItemNoUpdate(0));
		fx.skins.updateLastingPerks(player);
		assertThat(player.getMaxHealth() == 24, "the worn crown gives " + player.getMaxHealth() + " max health");
		assertThat(player.hasEffect(MobEffects.NIGHT_VISION), "the worn crown gives no Night Vision");
		fx.skins.updateLastingPerks(player);
		assertThat(player.getMaxHealth() == 24, "a second tick added the hearts again: " + player.getMaxHealth());

		player.getInventory().setItem(3, player.getItemBySlot(EquipmentSlot.HEAD));
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		fx.skins.updateLastingPerks(player);
		assertThat(player.getMaxHealth() == 20 && !player.hasEffect(MobEffects.NIGHT_VISION), "taking the crown off left " + player.getMaxHealth()
				+ " max health and " + player.getEffect(MobEffects.NIGHT_VISION));
		helper.succeed();
	}

	/**
	 * Held and worn Perks are the Owner's only: lent to another player, the pick in their hand and
	 * the crown on their head give them nothing, and back with the Owner both work again.
	 */
	@GameTest
	public void heldAndWornPerksWorkOnlyForTheOwner(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("green_pick", "pickaxe", HASTE_HELD).perky("circlet", "helmet", HEARTS_WORN);
		fx.reload();
		ServerPlayer alice = ApplyTests.carrying(helper, new ItemStack(Items.IRON_PICKAXE));
		alice.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		ServerPlayer bob = ApplyTests.carrying(helper);
		fx.skins.grant(alice.nameAndId(), "green_pick");
		fx.skins.grant(alice.nameAndId(), "circlet");
		fx.skins.apply(alice, "green_pick", ItemLocation.hotbar(0));
		fx.skins.apply(alice, "circlet", ItemLocation.worn(EquipmentSlot.HEAD));

		hand(alice, bob);
		fx.skins.updateLastingPerks(alice);
		fx.skins.updateLastingPerks(bob);
		assertThat(!bob.hasEffect(MobEffects.HASTE) && bob.getMaxHealth() == 20, "the lent pick and crown give their holder " + bob.getEffect(MobEffects.HASTE)
				+ " and " + bob.getMaxHealth() + " max health");
		assertThat(fx.skins.perks(bob.getMainHandItem(), bob).isEmpty(), "the lent pick has Perks for its holder: " + fx.skins.perks(bob.getMainHandItem(), bob));
		assertThat(fx.skins.perks(bob.getMainHandItem(), alice).size() == 1, "the lent pick lost its Perks for its Owner");

		hand(bob, alice);
		fx.skins.updateLastingPerks(bob);
		fx.skins.updateLastingPerks(alice);
		assertThat(alice.hasEffect(MobEffects.HASTE) && alice.getMaxHealth() == 24, "back with the Owner, the pick and crown give " + alice.getEffect(MobEffects.HASTE)
				+ " and " + alice.getMaxHealth() + " max health");
		helper.succeed();
	}

	/** Hands {@code from}'s first hotbar item and helmet to {@code to}, into the same places. */
	private static void hand(ServerPlayer from, ServerPlayer to) {
		to.getInventory().setItem(0, from.getInventory().removeItemNoUpdate(0));
		to.setItemSlot(EquipmentSlot.HEAD, from.getItemBySlot(EquipmentSlot.HEAD));
		from.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
	}

	/** The strongest potion wins, and the Perk never weakens or removes an effect from elsewhere, during or after. */
	@GameTest
	public void aLastingPotionNeverWeakensAnotherEffect(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("green_pick", "pickaxe", """
				[ { trigger: "held", effects: [ { type: "potion", effect: "minecraft:haste", level: 2 } ] } ]""");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.IRON_PICKAXE));
		fx.skins.grant(player.nameAndId(), "green_pick");
		fx.skins.apply(player, "green_pick", ItemLocation.hotbar(0));
		ItemStack pick = player.getInventory().getItem(0);

		// A drunk Haste III outlasts and outranks the Perk's Haste II, before and after.
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, 1200, 2));
		fx.skins.updateLastingPerks(player);
		assertEffect(player, MobEffects.HASTE, 2, 1200, "under a stronger potion");
		putAway(fx, player);
		assertEffect(player, MobEffects.HASTE, 2, 1200, "after putting the pick away");

		// A weaker one, long or short, waits under the Perk's and comes back with its own time left.
		for (int duration : List.of(1200, 100)) {
			player.removeEffect(MobEffects.HASTE);
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, duration, 0));
			player.getInventory().setItem(0, pick);
			fx.skins.updateLastingPerks(player);
			assertThat(player.getEffect(MobEffects.HASTE).getAmplifier() == 1, "the stronger Perk lost to Haste I: " + player.getEffect(MobEffects.HASTE));
			putAway(fx, player);
			assertEffect(player, MobEffects.HASTE, 0, duration, "after the Perk's Haste II ended");
		}
		helper.succeed();
	}

	private static void putAway(SkinFixtures fx, ServerPlayer player) {
		player.getInventory().setItem(9, player.getInventory().removeItemNoUpdate(0));
		fx.skins.updateLastingPerks(player);
	}

	/** Attribute Perks of several items add up. */
	@GameTest
	public void attributePerksAddUpAcrossItems(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("circlet", "helmet", HEARTS_WORN)
				.perky("boots", "boots", "[ { trigger: \"worn\", effects: [ { type: \"attribute\", attribute: \"minecraft:max_health\", amount: 2 } ] } ]");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		fx.skins.grant(player.nameAndId(), "circlet");
		fx.skins.grant(player.nameAndId(), "boots");
		fx.skins.apply(player, "circlet", ItemLocation.worn(EquipmentSlot.HEAD));
		fx.skins.apply(player, "boots", ItemLocation.worn(EquipmentSlot.FEET));

		fx.skins.updateLastingPerks(player);
		assertThat(player.getMaxHealth() == 26, "the crown and boots give " + player.getMaxHealth() + " max health, not 26");
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		fx.skins.updateLastingPerks(player);
		assertThat(player.getMaxHealth() == 22, "the boots alone give " + player.getMaxHealth() + " max health, not 22");
		helper.succeed();
	}

	/** The settings switch turns every Perk off (Looks stay) and back on, through a reload. */
	@GameTest
	public void theSettingsSwitchTurnsAllPerksOff(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("green_pick", "pickaxe", HASTE_HELD);
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.IRON_PICKAXE));
		fx.skins.grant(player.nameAndId(), "green_pick");
		fx.skins.apply(player, "green_pick", ItemLocation.hotbar(0));
		ItemStack pick = player.getInventory().getItem(0);
		fx.skins.updateLastingPerks(player);
		assertThat(player.hasEffect(MobEffects.HASTE) && fx.skins.perks(pick, player).size() == 1, "the pick's Perk doesn't work to begin with");

		fx.settings("{ \"perks-enabled\": false }").reload();
		fx.skins.updateLastingPerks(player);
		assertThat(fx.skins.perks(pick, player).isEmpty(), "switched off, the pick still has " + fx.skins.perks(pick, player));
		assertThat(!player.hasEffect(MobEffects.HASTE), "switched off, the pick still gives Haste");
		assertThat(fx.skins.liveSkin(pick).isPresent(), "switching Perks off took the Look too");

		fx.settings("{ \"perks-enabled\": true }").reload();
		fx.skins.updateLastingPerks(player);
		assertThat(player.hasEffect(MobEffects.HASTE), "switched back on, the pick gives no Haste");
		helper.succeed();
	}

	/** Perks are read from the skin's definition as it stands: an edit and a reload change the item in hand. */
	@GameTest
	public void anEditedPerkTakesEffectAfterAReload(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("circlet", "helmet", """
				[ { trigger: "worn", effects: [ { type: "potion", effect: "minecraft:speed", level: 1 },
				{ type: "attribute", attribute: "minecraft:max_health", amount: 4 } ] } ]""");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		fx.skins.grant(player.nameAndId(), "circlet");
		fx.skins.apply(player, "circlet", ItemLocation.worn(EquipmentSlot.HEAD));
		fx.skins.updateLastingPerks(player);
		assertEffect(player, MobEffects.SPEED, 0, -1, "before the edit");

		fx.perky("circlet", "helmet", """
				[ { trigger: "worn", effects: [ { type: "potion", effect: "minecraft:speed", level: 2 },
				{ type: "attribute", attribute: "minecraft:max_health", amount: 8 } ] } ]""");
		fx.skins.updateLastingPerks(player);
		assertThat(player.getMaxHealth() == 24, "the edit counted before the reload");
		assertThat(!fx.reload().changed(), "a Perk-only edit changed the pack");
		fx.skins.updateLastingPerks(player);
		assertEffect(player, MobEffects.SPEED, 1, -1, "after the edit and reload");
		assertThat(player.getMaxHealth() == 28, "after the edit and reload the crown gives " + player.getMaxHealth() + " max health");
		Perk.Potion speed = (Perk.Potion) fx.skins.perks(player.getItemBySlot(EquipmentSlot.HEAD), player).getFirst().effects().getFirst();
		assertThat(speed.level() == 2, "perks(stack, holder) reads " + speed);
		helper.succeed();
	}

	/** A particle aura plays every {@code interval} ticks, around the wearer, for whoever is near. */
	@GameTest(maxTicks = 200)
	public void anAuraKeepsItsInterval(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("circlet", "helmet", """
				[ { trigger: "worn", effects: [ { type: "particle", particle: { type: "minecraft:wax_on" }, count: 3, interval: 10 } ] } ]""");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)));
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			fx.skins.grant(player.nameAndId(), "circlet");
			fx.skins.apply(player, "circlet", ItemLocation.worn(EquipmentSlot.HEAD));
		} catch (RuntimeException | Error e) {
			helper.getLevel().getServer().getPlayerList().remove(player);
			throw e;
		}
		player.sent(ClientboundLevelParticlesPacket.class);
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (ticks[0]++ < 40) fx.skins.updateLastingPerks(player);
		});
		helper.runAfterDelay(45, () -> {
			helper.getLevel().getServer().getPlayerList().remove(player);
			List<ClientboundLevelParticlesPacket> auras = player.sent(ClientboundLevelParticlesPacket.class).stream()
					.filter(p -> p.particle().getType() == ParticleTypes.WAX_ON).toList();
			assertThat(auras.size() == 4, "40 ticks of a 10-tick aura sent " + auras.size() + " bursts");
			assertThat(auras.stream().allMatch(p -> p.count() == 3), "a burst isn't 3 particles: " + auras.stream().map(ClientboundLevelParticlesPacket::count).toList());
			helper.succeed();
		});
	}

	/**
	 * A head aura with {@code while_moving} plays only while the wearer moves. The players near see it
	 * around the top of the head; the wearer sees each particle behind their eyes, by where they face,
	 * never moving forward, so it's never in their view in first person.
	 */
	@GameTest(maxTicks = 200)
	public void aHeadAuraWhileMovingStaysOutOfTheWearersView(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("lid", "helmet", """
				[ { trigger: "worn", effects: [ { type: "particle", particle: { type: "minecraft:portal" }, at: "head", while_moving: true,
					count: 3, interval: 2, speed: 0.4 } ] } ]""");
		fx.reload();
		SkinFixtures.Listening wearer = SkinFixtures.join(helper);
		SkinFixtures.Listening watcher = SkinFixtures.join(helper);
		var start = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5));
		try {
			wearer.getInventory().clearContent();
			wearer.setPos(start);
			watcher.setPos(start.add(3, 0, 0));
			wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			fx.skins.grant(wearer.nameAndId(), "lid");
			fx.skins.apply(wearer, "lid", ItemLocation.worn(EquipmentSlot.HEAD));
		} catch (RuntimeException | Error e) {
			helper.getLevel().getServer().getPlayerList().remove(wearer);
			helper.getLevel().getServer().getPlayerList().remove(watcher);
			throw e;
		}
		wearer.sent(ClientboundLevelParticlesPacket.class);
		watcher.sent(ClientboundLevelParticlesPacket.class);
		int[] ticks = {0};
		List<ClientboundLevelParticlesPacket> whileStill = new java.util.ArrayList<>();
		List<String> own = new java.util.ArrayList<>(), inView = new java.util.ArrayList<>();
		helper.onEachTick(() -> {
			int t = ticks[0]++;
			if (t >= 40) return;
			// Still for 20 ticks, then walking 0.2 blocks a tick for 20, facing the way they walk (south, +z).
			wearer.setYRot(0);
			if (t >= 20) wearer.setPos(start.add(0, 0, 0.2 * (t - 19)));
			fx.skins.updateLastingPerks(wearer);
			if (t == 19) whileStill.addAll(watcher.sent(ClientboundLevelParticlesPacket.class));
			// The wearer's own: one particle a packet, at the back of the head, heading back or sideways only.
			for (var p : wearer.sent(ClientboundLevelParticlesPacket.class)) {
				if (p.particle().getType() != ParticleTypes.PORTAL) continue;
				own.add(p.count() + "@" + p.z());
				if (p.count() != 0 || p.z() > wearer.getEyePosition().z - 0.45 + 1e-6 || p.zDist() > 1e-6 || p.y() <= wearer.getEyeY()) {
					inView.add("count " + p.count() + ", z " + (p.z() - wearer.getEyePosition().z) + " from the eyes, moving " + p.zDist());
				}
			}
		});
		helper.runAfterDelay(45, () -> {
			helper.getLevel().getServer().getPlayerList().remove(wearer);
			helper.getLevel().getServer().getPlayerList().remove(watcher);
			List<ClientboundLevelParticlesPacket> seen = watcher.sent(ClientboundLevelParticlesPacket.class).stream()
					.filter(p -> p.particle().getType() == ParticleTypes.PORTAL).toList();
			assertThat(whileStill.stream().noneMatch(p -> p.particle().getType() == ParticleTypes.PORTAL), "the aura played while the wearer stood still");
			assertThat(seen.size() == 10 && seen.stream().allMatch(p -> p.count() == 3 && p.xMaxSpeed() == 0.4f), "20 ticks of walking sent the watcher "
					+ seen.size() + " bursts: " + seen.stream().map(p -> p.count() + "x" + p.xMaxSpeed()).toList());
			assertThat(seen.stream().allMatch(p -> p.y() > wearer.getEyeY()), "a burst was below the wearer's eyes: " + seen.stream().map(p -> p.y()).toList());
			assertThat(own.size() == 30, "20 ticks of walking sent the wearer " + own.size() + " particles, not 10 bursts of 3: " + own);
			assertThat(inView.isEmpty(), "the wearer's own particles could get in their view: " + inView);
			helper.succeed();
		});
	}

	/** {@code at} and {@code while_moving} are for lasting Perks only, and {@code at} is body or head. */
	@GameTest
	public void aHeadAuraIsForLastingPerksOnly(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create()
				.perky("on_hit", "sword", "[ { trigger: \"hit\", effects: [ { type: \"particle\", particle: { type: \"minecraft:portal\" }, at: \"head\" } ] } ]")
				.perky("feet", "helmet", "[ { trigger: \"worn\", effects: [ { type: \"particle\", particle: { type: \"minecraft:portal\" }, at: \"feet\" } ] } ]")
				.perky("fine", "helmet", "[ { trigger: \"worn\", effects: [ { type: \"particle\", particle: { type: \"minecraft:portal\" }, at: \"head\","
						+ " while_moving: true } ] } ]");
		ReloadReport report = fx.reload();
		assertRejected(report, "on_hit", "at and while_moving are for held and worn Perks only");
		assertRejected(report, "feet", "must be body or head, not \"feet\"");
		assertThat(report.loaded().contains("fine"), "a head aura while moving was rejected: " + report.rejections());
		String line = se.metacraft.skins.PerkLines.line(fx.skins.skin("fine").orElseThrow().perks().getFirst()).getString();
		assertThat(line.equals("While worn: a particle aura while moving"), "the Perk's line: " + line);
		helper.succeed();
	}

	static void assertEffect(net.minecraft.world.entity.LivingEntity player, Holder<MobEffect> effect, int amplifier, int duration, String when) {
		MobEffectInstance instance = player.getEffect(effect);
		assertThat(instance != null && instance.getAmplifier() == amplifier && (duration < 0 || instance.getDuration() == duration),
				when + ": expected level " + (amplifier + 1) + (duration < 0 ? "" : " for " + duration + " ticks") + ", got " + instance);
	}

	private static void assertRejected(ReloadReport report, String id, String reasonFragment) {
		var rejection = report.rejections().stream().filter(r -> r.skinId().equals(id)).findFirst()
				.orElseThrow(() -> SkinFixtures.fail(id + " was not rejected: " + report.loaded()));
		assertThat(rejection.file().getFileName().toString().equals("skin.json5"), id + " blamed " + rejection.file());
		assertThat(rejection.reason().contains(reasonFragment), id + "'s reason \"" + rejection.reason() + "\" does not mention " + reasonFragment);
	}
}
