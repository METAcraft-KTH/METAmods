package se.metacraft.skins.gametest;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.MobHatCooldowns;
import se.metacraft.skins.MobHats;
import se.metacraft.skins.Skins;
import se.metacraft.skins.SkinsSettings;
import se.metacraft.skins.item.MobHat;
import se.metacraft.skins.item.SkinBox;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Mob Hats ({@link MobHats}): the chance, the {@code mob-hat-chances} setting behind it (one per box), the
 * cooldown a hat-wearing mob's spawn starts around it (and its despawn ends), which hats a mob can wear, what a hat-wearing mob drops, and what clients are sent of the hat.
 */
public final class MobHatTests {
	/** Hats in the box, and a sword that isn't one. */
	private static SkinFixtures classicBox() {
		return SkinFixtures.create().skin("pumpkin_hat", "helmet", "Pumpkin Hat", null).skin("top_hat", "helmet", "Top Hat", null).skin("cleaver", "sword")
				.box(MobHats.CLASSIC + ".json5", "{ name: \"Hats\", skins: [ { skin: \"pumpkin_hat\", weight: 10 }, { skin: \"top_hat\", weight: 1 },"
						+ " { skin: \"cleaver\", weight: 5 }, { skin: \"nowhere\", weight: 1 } ] }");
	}

	/** The base percent, 99% less on cooldown. */
	@GameTest
	public void theChanceIsCutOnCooldown(GameTestHelper helper) {
		assertThat(MobHats.chance(5, false) == 0.05, "5% off cooldown: " + MobHats.chance(5, false));
		assertThat(Math.abs(MobHats.chance(5, true) - 0.0005) < 1e-12, "5% on cooldown: " + MobHats.chance(5, true));
		assertThat(Math.abs(MobHats.chance(100, true) - 0.01) < 1e-12 && MobHats.chance(100, false) == 1, "100%: " + MobHats.chance(100, true));
		assertThat(MobHats.chance(0, false) == 0 && MobHats.chance(0, true) == 0, "0%");
		helper.succeed();
	}

	/** A cooldown lasts 1 to 6 hours, both ends possible in principle, and differs from spawn to spawn. */
	@GameTest
	public void aCooldownLastsOneToSixHours(GameTestHelper helper) {
		RandomSource random = RandomSource.create(42);
		Set<Duration> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			Duration length = MobHats.cooldownLength(random);
			assertThat(length.compareTo(Duration.ofHours(1)) >= 0 && length.compareTo(Duration.ofHours(6)) <= 0, "a cooldown of " + length);
			seen.add(length);
		}
		assertThat(seen.size() > 900, "only " + seen.size() + " lengths in 1000");
		helper.succeed();
	}

	/**
	 * A cooldown covers the 3x3 chunks centred on the one the spawn was in, in that dimension only,
	 * until it ends. Cooldowns don't stack: a later start leaves chunks already on cooldown as they are
	 * and only takes the free ones. Ending one owner's cooldown leaves every other owner's chunks alone.
	 */
	@GameTest
	public void aCooldownCoversTheChunksAround(GameTestHelper helper) {
		ResourceKey<Level> overworld = Level.OVERWORLD;
		MobHatCooldowns cooldowns = new MobHatCooldowns();
		UUID first = UUID.randomUUID(), second = UUID.randomUUID();
		Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
		// Block (20, -5) is in chunk (1, -1): chunks 0 to 2 by -2 to 0, blocks x 0 to 47, z -32 to 15.
		cooldowns.start(overworld, 20, -5, first, Duration.ofHours(2), t0);
		for (int[] in : new int[][] { { 20, -5 }, { 0, -32 }, { 47, 15 }, { 0, 15 }, { 47, -32 } }) {
			assertThat(cooldowns.onCooldown(overworld, in[0], in[1], t0), "not on cooldown at " + in[0] + ", " + in[1]);
		}
		for (int[] out : new int[][] { { -1, -5 }, { 48, -5 }, { 20, 16 }, { 20, -33 } }) {
			assertThat(!cooldowns.onCooldown(overworld, out[0], out[1], t0), "on cooldown at " + out[0] + ", " + out[1]);
		}
		assertThat(!cooldowns.onCooldown(Level.NETHER, 20, -5, t0), "on cooldown in the Nether");
		assertThat(cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofMinutes(119))), "ended before 2 hours");
		assertThat(!cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofHours(2))), "still on after 2 hours");

		// A longer one from the chunk next door doesn't stretch the chunks already on cooldown, but takes the free ones.
		cooldowns.start(overworld, 40, -5, second, Duration.ofHours(5), t0);
		assertThat(!cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofHours(2))), "a neighbour's cooldown stacked on it");
		assertThat(cooldowns.onCooldown(overworld, 60, -5, t0.plus(Duration.ofHours(4))), "a neighbour's cooldown didn't take a free chunk");

		// The first owner's cooldown ends: its chunks come off, the second's free chunk stays on.
		cooldowns.end(first);
		assertThat(!cooldowns.onCooldown(overworld, 40, -5, t0), "a shared chunk stayed on after its owner's cooldown ended");
		assertThat(!cooldowns.onCooldown(overworld, 0, -5, t0), "the first's chunk stayed on");
		assertThat(cooldowns.onCooldown(overworld, 60, -5, t0), "ending the first's cooldown ended the second's");
		cooldowns.end(UUID.randomUUID());
		assertThat(cooldowns.onCooldown(overworld, 60, -5, t0), "ending a cooldown nobody has ended one");

		// Once a cooldown runs out, its chunks are free for a new owner.
		cooldowns.start(overworld, 20, -5, first, Duration.ofHours(1), t0);
		cooldowns.start(overworld, 20, -5, second, Duration.ofHours(3), t0.plus(Duration.ofHours(1)));
		cooldowns.end(first);
		assertThat(cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofHours(2))), "an old owner ended a new owner's cooldown");
		helper.succeed();
	}

	/**
	 * A mob spawning with a hat starts a 1 to 6 hour cooldown where it spawned; one spawning bare
	 * doesn't. Its death leaves the cooldown on; its despawn ends it. A mob that spawned with a hat in a
	 * chunk already on cooldown doesn't end that cooldown when it despawns.
	 */
	@GameTest
	public void aHatWearingSpawnStartsACooldownADespawnEndsIt(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
		var dimension = helper.getLevel().dimension();
		MobHatCooldowns cooldowns = fx.skins.mobHatCooldowns();
		fx.skins.setMobHatChance(MobHats.CLASSIC, 0);
		Zombie bare = bareZombie(helper);
		MobHats.spawned(fx.skins, bare, t0);
		assertThat(!cooldowns.onCooldown(dimension, bare.getBlockX(), bare.getBlockZ(), t0), "a bare zombie started a cooldown");

		fx.skins.setMobHatChance(MobHats.CLASSIC, 100);
		Zombie hatted = bareZombie(helper);
		MobHats.spawned(fx.skins, hatted, t0);
		int x = hatted.getBlockX(), z = hatted.getBlockZ();
		assertThat(MobHats.wearsHat(hatted), "no hat at 100%");
		assertThat(cooldowns.onCooldown(dimension, x, z, t0.plus(Duration.ofMinutes(59))), "no cooldown within the hour");
		assertThat(cooldowns.onCooldown(dimension, x + 16, z - 16, t0), "no cooldown in the corner chunk");
		assertThat(!cooldowns.onCooldown(dimension, x, z, t0.plus(Duration.ofHours(6))), "still on cooldown after 6 hours");

		// Its death leaves the cooldown on.
		MobHats.died(fx.skins, hatted, helper.getLevel());
		assertThat(cooldowns.onCooldown(dimension, x, z, t0), "its death ended the cooldown");
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, hatted.getBoundingBox().inflate(3)).forEach(ItemEntity::discard);

		// One wearing a hat that started no cooldown (as one that spawned with a hat inside it, against
		// the 1% odds there, would have): its despawn ends nothing.
		Zombie lucky = bareZombie(helper);
		MobHats.putOnHat(fx.skins, lucky);
		MobHats.despawned(fx.skins, lucky);
		assertThat(cooldowns.onCooldown(dimension, x, z, t0), "a despawn ended a cooldown it didn't start");

		// A fresh start: one spawning with a hat and despawning leaves no cooldown.
		cooldowns.clear();
		Zombie wanderer = bareZombie(helper);
		MobHats.spawned(fx.skins, wanderer, t0);
		assertThat(cooldowns.onCooldown(dimension, x, z, t0), "no cooldown after a hat-wearing spawn");
		MobHats.despawned(fx.skins, wanderer);
		assertThat(!cooldowns.onCooldown(dimension, x, z, t0), "its despawn didn't end its cooldown");
		assertThat(!cooldowns.onCooldown(dimension, x + 16, z - 16, t0), "its despawn left the corner chunk on");
		helper.succeed();
	}

	/** A zombie at the test's corner, wearing nothing on its head. */
	private static Zombie bareZombie(GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		return zombie;
	}

	/**
	 * {@code mob-hat-chances} gives each of {@link MobHats#BOXES} 5% by default. Setting one box's takes
	 * at once, leaves the other box's and the other settings as they are, and is written to the file, so a
	 * reload reads it back; a box left out of the file gets 5%; a value outside 0 to 100 in the file is
	 * logged and the defaults apply.
	 */
	@GameTest
	public void eachBoxsChanceIsASettingThatSaves(GameTestHelper helper) throws Exception {
		SkinFixtures fx = classicBox().settings("{ \"perks-enabled\": false }");
		fx.reload();
		assertThat(SkinsSettings.MOB_HAT_CHANCE == 5 && MobHats.BOXES.stream().allMatch(box -> fx.skins.settings().mobHatChance(box) == 5),
				"the defaults: " + fx.skins.settings());
		double before = fx.skins.setMobHatChance(MobHats.WINTER, 1.5);
		assertThat(before == 5 && fx.skins.settings().mobHatChance(MobHats.WINTER) == 1.5 && fx.skins.settings().mobHatChance(MobHats.CLASSIC) == 5
				&& !fx.skins.settings().perksEnabled(), "after setting Winter to 1.5%: " + fx.skins.settings());
		String file = Files.readString(fx.skins.settingsFile());
		assertThat(file.contains("mob-hat-chances") && file.contains("1.5"), "the file: " + file);
		fx.reload();
		assertThat(fx.skins.settings().mobHatChance(MobHats.WINTER) == 1.5 && fx.skins.settings().mobHatChance(MobHats.CLASSIC) == 5
				&& !fx.skins.settings().perksEnabled(), "after a reload: " + fx.skins.settings());
		fx.settings("{ \"mob-hat-chances\": { \"classic\": 2 } }").reload();
		assertThat(fx.skins.settings().mobHatChance(MobHats.CLASSIC) == 2 && fx.skins.settings().mobHatChance(MobHats.WINTER) == 5,
				"with Winter left out of the file: " + fx.skins.settings());
		fx.settings("{ \"mob-hat-chances\": { \"classic\": 150 } }").reload();
		assertThat(fx.skins.settings().mobHatChance(MobHats.CLASSIC) == 5, "after 150% in the file: " + fx.skins.settings());
		helper.succeed();
	}

	/** A mob outside the cold takes its chance from the Classic box's setting, not the Winter box's. */
	@GameTest
	public void aMobOutsideTheColdUsesTheClassicChance(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		Instant now = Instant.now();
		fx.skins.setMobHatChance(MobHats.CLASSIC, 0);
		fx.skins.setMobHatChance(MobHats.WINTER, 100);
		Zombie zombie = bareZombie(helper);
		assertThat(!zombie.level().getBiome(zombie.blockPosition()).is(MobHats.COLD), "the test runs in a cold biome");
		MobHats.spawned(fx.skins, zombie, now);
		assertThat(!MobHats.wearsHat(zombie), "a hat at Classic 0%, Winter 100%");
		fx.skins.setMobHatChance(MobHats.CLASSIC, 100);
		fx.skins.setMobHatChance(MobHats.WINTER, 0);
		MobHats.spawned(fx.skins, zombie, now);
		assertThat(MobHats.wearsHat(zombie), "no hat at Classic 100%, Winter 0%");
		helper.succeed();
	}

	/** A spawning mob puts on a hat by the setting as it is now: always at 100%, never at 0% (off cooldown). */
	@GameTest
	public void aSpawningMobUsesTheChanceSetNow(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		Instant now = Instant.now();
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		fx.skins.setMobHatChance(MobHats.CLASSIC, 0);
		MobHats.spawned(fx.skins, zombie, now);
		assertThat(!MobHats.wearsHat(zombie), "a hat at 0%");
		fx.skins.setMobHatChance(MobHats.CLASSIC, 100);
		MobHats.spawned(fx.skins, zombie, now);
		assertThat(MobHats.wearsHat(zombie), "no hat at 100%");
		helper.succeed();
	}

	/** Only the box's usable hats: not its sword, nor a skin it lists that isn't on the server; weights don't matter. None without the box. */
	@GameTest
	public void aMobWearsAHatFromTheBoxAndNeverDropsIt(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		assertThat(MobHats.hats(fx.skins).equals(List.of("pumpkin_hat", "top_hat")), "the hats: " + MobHats.hats(fx.skins));
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		assertThat(MobHats.putOnHat(fx.skins, zombie), "no hat was put on");
		Optional<String> hat = MobHat.skinOf(zombie.getItemBySlot(EquipmentSlot.HEAD));
		assertThat(hat.filter(Set.of("pumpkin_hat", "top_hat")::contains).isPresent() && MobHats.wearsHat(zombie), "the zombie wears " + hat);
		assertThat(zombie.getDropChances().byEquipment(EquipmentSlot.HEAD) == 0, "the hat could drop");
		// Picking up a helmet doesn't take the hat off: as with any armour it can't wear, vanilla puts it in the mob's hand.
		zombie.setCanPickUpLoot(true);
		zombie.equipItemIfPossible(helper.getLevel(), new ItemStack(Items.DIAMOND_HELMET));
		assertThat(MobHat.skinOf(zombie.getItemBySlot(EquipmentSlot.HEAD)).equals(hat),
				"after a diamond helmet the zombie wears " + zombie.getItemBySlot(EquipmentSlot.HEAD));
		Zombie bare = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 2, 1));
		bare.equipItemIfPossible(helper.getLevel(), new ItemStack(Items.DIAMOND_HELMET));
		assertThat(bare.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "a zombie with no hat didn't put the helmet on");

		SkinFixtures empty = SkinFixtures.create().skin("pumpkin_hat", "helmet");
		empty.reload();
		Mob creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new BlockPos(3, 2, 1));
		assertThat(!MobHats.putOnHat(empty.skins, creeper) && !MobHats.wearsHat(creeper), "a hat with no box to take it from");
		helper.succeed();
	}

	/** A hat-wearing mob drops one box and not the hat, a baby too; a mob with no hat drops no box. */
	@GameTest
	public void aHatWearingMobDropsTheBox(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		Skins previous = MetacraftSkins.use(fx.skins);
		try {
			Zombie hatted = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			MobHats.putOnHat(fx.skins, hatted);
			assertDrops(helper, hatted, List.of(MobHats.CLASSIC), "a zombie with a hat");

			Zombie baby = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			baby.setBaby(true);
			MobHats.putOnHat(fx.skins, baby);
			assertDrops(helper, baby, List.of(MobHats.CLASSIC), "a baby zombie with a hat");

			Zombie bare = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			assertDrops(helper, bare, List.of(), "a zombie with no hat");
		} finally {
			MetacraftSkins.use(previous);
		}
		helper.succeed();
	}

	/** In a cold biome a zombie, skeleton, stray or creeper takes a Winter hat; any other mob, or anywhere not cold, a Classic one. */
	@GameTest
	public void coldBiomeMobsTakeAWinterHat(GameTestHelper helper) {
		for (var type : List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.CREEPER)) {
			assertThat(MobHats.boxFor(type, true).equals(MobHats.WINTER), type + " in the cold: " + MobHats.boxFor(type, true));
			assertThat(MobHats.boxFor(type, false).equals(MobHats.CLASSIC), type + " not in the cold: " + MobHats.boxFor(type, false));
		}
		for (var type : List.of(EntityTypes.HUSK, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.BOGGED, EntityTypes.PARCHED)) {
			assertThat(MobHats.boxFor(type, true).equals(MobHats.CLASSIC), type + " in the cold: " + MobHats.boxFor(type, true));
		}
		helper.succeed();
	}

	/** A mob in a Winter hat wears one of the Winter box's hats and drops a Winter box; a hat that remembers no box, a Classic one. */
	@GameTest
	public void aWinterHatDropsTheWinterBox(GameTestHelper helper) {
		SkinFixtures fx = classicBox().skin("santa_hat", "helmet", "Santa Hat", null)
				.box(MobHats.WINTER + ".json5", "{ name: \"Winter\", skins: [ { skin: \"santa_hat\", weight: 10 }, { skin: \"cleaver\", weight: 5 } ] }");
		fx.reload();
		Skins previous = MetacraftSkins.use(fx.skins);
		try {
			Zombie winter = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			assertThat(MobHats.putOnHat(fx.skins, winter, MobHats.WINTER), "no Winter hat was put on");
			assertThat(MobHat.skinOf(winter.getItemBySlot(EquipmentSlot.HEAD)).equals(Optional.of("santa_hat")), "the zombie wears " + winter.getItemBySlot(EquipmentSlot.HEAD));
			assertDrops(helper, winter, List.of(MobHats.WINTER), "a zombie in a Winter hat");

			Zombie old = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			old.setItemSlot(EquipmentSlot.HEAD, MobHat.of("top_hat"));
			assertDrops(helper, old, List.of(MobHats.CLASSIC), "a zombie in a hat that remembers no box");
		} finally {
			MetacraftSkins.use(previous);
		}
		helper.succeed();
	}

	/** Clients see the skin's Look worn on the head, with no armour drawn; a hat whose skin is gone is a plain leather helmet. */
	@GameTest
	public void clientsSeeTheLookOnTheHead(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Skins previous = MetacraftSkins.use(fx.skins);
		try {
			ItemStack worn = view(helper, MobHat.of("top_hat"), viewer);
			Equippable equippable = worn.get(DataComponents.EQUIPPABLE);
			assertThat(Identifier.fromNamespaceAndPath("skins", "top_hat").equals(worn.get(DataComponents.ITEM_MODEL)), "the hat's model: " + worn.get(DataComponents.ITEM_MODEL));
			assertThat(equippable != null && equippable.slot() == EquipmentSlot.HEAD && equippable.assetId().isEmpty(), "worn as " + equippable);

			ItemStack gone = view(helper, MobHat.of("nowhere"), viewer);
			assertThat(gone.is(Items.LEATHER_HELMET) && Identifier.withDefaultNamespace("leather_helmet").equals(gone.get(DataComponents.ITEM_MODEL)),
					"a hat of no skin: " + gone + ", " + gone.get(DataComponents.ITEM_MODEL));
		} finally {
			MetacraftSkins.use(previous);
		}
		helper.succeed();
	}

	/** Kills the mob and checks the Skin Boxes it dropped, by box id, and that it dropped no Mob Hat. */
	private static void assertDrops(GameTestHelper helper, Mob mob, List<String> boxes, String what) {
		var area = mob.getBoundingBox().inflate(3);
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
		mob.kill(helper.getLevel());
		List<ItemStack> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getItem).toList();
		List<String> droppedBoxes = dropped.stream().flatMap(stack -> SkinBox.boxOf(stack).stream()).toList();
		assertThat(droppedBoxes.equals(boxes), what + " dropped the boxes " + droppedBoxes);
		assertThat(dropped.stream().noneMatch(stack -> stack.is(MobHat.ITEM)), what + " dropped its hat");
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
	}

	private static ItemStack view(GameTestHelper helper, ItemStack hat, ServerPlayer viewer) {
		return PolymerItemUtils.getPolymerItemStack(hat, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
	}
}
