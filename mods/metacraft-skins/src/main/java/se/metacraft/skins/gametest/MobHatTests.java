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

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Mob Hats ({@link MobHats}): the chance, the {@code mob-hat-chance} setting behind it, the
 * cooldown a hat-wearing mob's death starts around it, which hats a mob can wear, what a hat-wearing mob drops, and what clients are sent of the hat.
 */
public final class MobHatTests {
	/** Hats in the box, and a sword that isn't one. */
	private static SkinFixtures classicBox() {
		return SkinFixtures.create().skin("pumpkin_hat", "helmet", "Pumpkin Hat", null).skin("top_hat", "helmet", "Top Hat", null).skin("cleaver", "sword")
				.box(MobHats.HATS_FROM + ".json5", "{ name: \"Hats\", skins: [ { skin: \"pumpkin_hat\", weight: 10 }, { skin: \"top_hat\", weight: 1 },"
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

	/** A cooldown lasts 1 to 6 hours, both ends possible in principle, and differs from death to death. */
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
	 * A cooldown covers the 3x3 chunks centred on the one the death was in, in that dimension only,
	 * until it ends; a later death nearby moves a chunk's end later, never earlier.
	 */
	@GameTest
	public void aCooldownCoversTheChunksAround(GameTestHelper helper) {
		ResourceKey<Level> overworld = Level.OVERWORLD;
		MobHatCooldowns cooldowns = new MobHatCooldowns();
		Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
		// Block (20, -5) is in chunk (1, -1): chunks 0 to 2 by -2 to 0, blocks x 0 to 47, z -32 to 15.
		cooldowns.start(overworld, 20, -5, Duration.ofHours(2), t0);
		for (int[] in : new int[][] { { 20, -5 }, { 0, -32 }, { 47, 15 }, { 0, 15 }, { 47, -32 } }) {
			assertThat(cooldowns.onCooldown(overworld, in[0], in[1], t0), "not on cooldown at " + in[0] + ", " + in[1]);
		}
		for (int[] out : new int[][] { { -1, -5 }, { 48, -5 }, { 20, 16 }, { 20, -33 } }) {
			assertThat(!cooldowns.onCooldown(overworld, out[0], out[1], t0), "on cooldown at " + out[0] + ", " + out[1]);
		}
		assertThat(!cooldowns.onCooldown(Level.NETHER, 20, -5, t0), "on cooldown in the Nether");
		assertThat(cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofMinutes(119))), "ended before 2 hours");
		assertThat(!cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofHours(2))), "still on after 2 hours");

		// A shorter one later doesn't cut a longer one short; a longer one from a neighbour chunk does extend it.
		cooldowns.start(overworld, 20, -5, Duration.ofHours(1), t0.plus(Duration.ofMinutes(10)));
		assertThat(cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofMinutes(119))), "a shorter cooldown cut it short");
		cooldowns.start(overworld, 40, -5, Duration.ofHours(5), t0);
		assertThat(cooldowns.onCooldown(overworld, 20, -5, t0.plus(Duration.ofHours(4))), "a neighbour's cooldown didn't reach it");
		assertThat(!cooldowns.onCooldown(overworld, 0, -5, t0.plus(Duration.ofHours(4))), "a neighbour's cooldown reached two chunks over");
		helper.succeed();
	}

	/** A hat-wearing mob's death starts a 1 to 6 hour cooldown where it died; a bare mob's doesn't. */
	@GameTest
	public void aHatWearingMobsDeathStartsACooldown(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
		var dimension = helper.getLevel().dimension();
		Zombie bare = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		MobHats.died(fx.skins, bare, helper.getLevel(), t0);
		assertThat(!fx.skins.mobHatCooldowns().onCooldown(dimension, bare.getBlockX(), bare.getBlockZ(), t0), "a bare zombie started a cooldown");

		Zombie hatted = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		MobHats.putOnHat(fx.skins, hatted);
		MobHats.died(fx.skins, hatted, helper.getLevel(), t0);
		int x = hatted.getBlockX(), z = hatted.getBlockZ();
		assertThat(fx.skins.mobHatCooldowns().onCooldown(dimension, x, z, t0.plus(Duration.ofMinutes(59))), "no cooldown within the hour");
		assertThat(fx.skins.mobHatCooldowns().onCooldown(dimension, x + 16, z - 16, t0), "no cooldown in the corner chunk");
		assertThat(!fx.skins.mobHatCooldowns().onCooldown(dimension, x, z, t0.plus(Duration.ofHours(6))), "still on cooldown after 6 hours");
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, hatted.getBoundingBox().inflate(3)).forEach(ItemEntity::discard);
		helper.succeed();
	}

	/**
	 * {@code mob-hat-chance} is 5% by default. Setting it takes at once, keeps the other settings,
	 * and is written to the file, so a reload reads it back; a value outside 0 to 100 in the file is
	 * logged and the default, 5%, applies.
	 */
	@GameTest
	public void theChanceIsASettingThatSaves(GameTestHelper helper) throws Exception {
		SkinFixtures fx = classicBox().settings("{ \"perks-enabled\": false }");
		fx.reload();
		assertThat(fx.skins.settings().mobHatChance() == SkinsSettings.MOB_HAT_CHANCE && SkinsSettings.MOB_HAT_CHANCE == 5,
				"the default: " + fx.skins.settings());
		double before = fx.skins.setMobHatChance(1.5);
		assertThat(before == 5 && fx.skins.settings().mobHatChance() == 1.5 && !fx.skins.settings().perksEnabled(), "after setting 1.5%: " + fx.skins.settings());
		String file = Files.readString(fx.skins.settingsFile());
		assertThat(file.contains("mob-hat-chance") && file.contains("1.5"), "the file: " + file);
		fx.reload();
		assertThat(fx.skins.settings().mobHatChance() == 1.5 && !fx.skins.settings().perksEnabled(), "after a reload: " + fx.skins.settings());
		fx.settings("{ \"mob-hat-chance\": 150 }").reload();
		assertThat(fx.skins.settings().mobHatChance() == SkinsSettings.MOB_HAT_CHANCE, "after 150% in the file: " + fx.skins.settings());
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
		fx.skins.setMobHatChance(0);
		MobHats.spawned(fx.skins, zombie, now);
		assertThat(!MobHats.wearsHat(zombie), "a hat at 0%");
		fx.skins.setMobHatChance(100);
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
			assertDrops(helper, hatted, List.of(MobHats.DROPS), "a zombie with a hat");

			Zombie baby = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			baby.setBaby(true);
			MobHats.putOnHat(fx.skins, baby);
			assertDrops(helper, baby, List.of(MobHats.DROPS), "a baby zombie with a hat");

			Zombie bare = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			assertDrops(helper, bare, List.of(), "a zombie with no hat");
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
