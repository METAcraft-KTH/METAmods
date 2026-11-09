package se.metacraft.skins.gametest;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.MobHats;
import se.metacraft.skins.Skins;
import se.metacraft.skins.SkinsSettings;
import se.metacraft.skins.item.MobHat;
import se.metacraft.skins.item.SkinBox;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Mob Hats ({@link MobHats}): the chance by local difficulty, the {@code mob-hat-chance} setting
 * behind it, which hats a mob can wear, what a hat-wearing mob drops, and what clients are sent of the hat.
 */
public final class MobHatTests {
	/** Hats in the box, and a sword that isn't one. */
	private static SkinFixtures classicBox() {
		return SkinFixtures.create().skin("pumpkin_hat", "helmet", "Pumpkin Hat", null).skin("top_hat", "helmet", "Top Hat", null).skin("cleaver", "sword")
				.box(MobHats.HATS_FROM + ".json5", "{ name: \"Hats\", skins: [ { skin: \"pumpkin_hat\", weight: 10 }, { skin: \"top_hat\", weight: 1 },"
						+ " { skin: \"cleaver\", weight: 5 }, { skin: \"nowhere\", weight: 1 } ] }");
	}

	/** The base percent at local difficulty 0, half that halfway, none at 6.75 or above. */
	@GameTest
	public void theChanceFallsInAStraightLineWithLocalDifficulty(GameTestHelper helper) {
		assertThat(MobHats.chance(0.2, 0) == 0.002, "at 0: " + MobHats.chance(0.2, 0));
		assertThat(Math.abs(MobHats.chance(0.2, 3.375f) - 0.001) < 1e-9, "at 3.375: " + MobHats.chance(0.2, 3.375f));
		assertThat(Math.abs(MobHats.chance(0.2, 1.5f) - 0.002 * (1 - 1.5 / 6.75)) < 1e-9, "at 1.5: " + MobHats.chance(0.2, 1.5f));
		assertThat(MobHats.chance(0.2, 6.75f) == 0 && MobHats.chance(0.2, 7) == 0, "at the top: " + MobHats.chance(0.2, 6.75f) + ", " + MobHats.chance(0.2, 7));
		assertThat(MobHats.chance(100, 0) == 1 && MobHats.chance(0, 0) == 0, "at 100% and 0%: " + MobHats.chance(100, 0) + ", " + MobHats.chance(0, 0));
		helper.succeed();
	}

	/**
	 * {@code mob-hat-chance} is 0.2% by default. Setting it takes at once, keeps the other settings,
	 * and is written to the file, so a reload reads it back; a value outside 0 to 100 in the file is
	 * logged and the default, 0.2%, applies.
	 */
	@GameTest
	public void theChanceIsASettingThatSaves(GameTestHelper helper) throws Exception {
		SkinFixtures fx = classicBox().settings("{ \"perks-enabled\": false }");
		fx.reload();
		assertThat(fx.skins.settings().mobHatChance() == SkinsSettings.MOB_HAT_CHANCE && SkinsSettings.MOB_HAT_CHANCE == 0.2,
				"the default: " + fx.skins.settings());
		double before = fx.skins.setMobHatChance(5);
		assertThat(before == 0.2 && fx.skins.settings().mobHatChance() == 5 && !fx.skins.settings().perksEnabled(), "after setting 5%: " + fx.skins.settings());
		String file = Files.readString(fx.skins.settingsFile());
		assertThat(file.contains("mob-hat-chance") && file.contains("5"), "the file: " + file);
		fx.reload();
		assertThat(fx.skins.settings().mobHatChance() == 5 && !fx.skins.settings().perksEnabled(), "after a reload: " + fx.skins.settings());
		fx.settings("{ \"mob-hat-chance\": 150 }").reload();
		assertThat(fx.skins.settings().mobHatChance() == SkinsSettings.MOB_HAT_CHANCE, "after 150% in the file: " + fx.skins.settings());
		helper.succeed();
	}

	/** A spawning mob puts on a hat by the setting as it is now: always at 100%, never at 0% (local difficulty 0). */
	@GameTest
	public void aSpawningMobUsesTheChanceSetNow(GameTestHelper helper) {
		SkinFixtures fx = classicBox();
		fx.reload();
		DifficultyInstance peaceful = new DifficultyInstance(Difficulty.PEACEFUL, 0, 0, 0);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		fx.skins.setMobHatChance(0);
		MobHats.spawned(fx.skins, zombie, peaceful);
		assertThat(!MobHats.wearsHat(zombie), "a hat at 0%");
		fx.skins.setMobHatChance(100);
		MobHats.spawned(fx.skins, zombie, peaceful);
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
