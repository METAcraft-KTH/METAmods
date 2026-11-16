package se.metacraft.ovvar.gametest;

import com.mojang.serialization.JavaOps;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import se.metacraft.ovvar.MobOvvarConfig;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.MobOvvar;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.item.OvveFeet;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.content.item.OvveTopItem;
import se.metacraft.ovvar.content.item.PatchItem;
import se.metacraft.ovvar.pack.Combos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.phys.Vec3;
import se.metacraft.ovvar.OvvarConfig;

/**
 * How a mob is dressed in an ovve or a frack (mob ovvar). The garment tests are pure and seeded; the
 * spawn tests set the config, so they take turns on {@link WardrobeTests#BUSY} and put it back.
 */
public final class MobOvvarTests {
	private static final int SEEDS = 2000;

	private static List<ItemStack> garments(EquipmentSlot slot, boolean topCells) {
		List<ItemStack> garments = new ArrayList<>();
		for (int seed = 0; seed < SEEDS; seed++) garments.add(MobOvvar.garment(seeded(seed), slot, topCells));
		return garments;
	}

	/** One generator per seed, its seed spread out: the first draws of RandomSource.create(n) over consecutive n are correlated (16 chapters showed as 4). */
	private static RandomSource seeded(int seed) {
		return RandomSource.create(seed * 0x9E3779B97F4A7C15L);
	}

	private static Chapter chapter(ItemStack garment) {
		return ((OvveItem) garment.getItem()).chapter;
	}

	private static List<Placement> placements(ItemStack garment) {
		return SpotPlacements.asPlacementList(Looks.sewn(garment));
	}

	@GameTest
	public void wearsEveryLootPatchAndNothingElse(GameTestHelper helper) {
		helper.assertValueEqual(MobOvvar.patches(), PatchLoot.looted(), "the patches a mob may wear");
		helper.assertValueEqual(MobOvvar.patches().size(), 21, "loot patches");
		Set<String> seen = new HashSet<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
			for (ItemStack garment : garments(slot, true)) {
				for (Placement p : placements(garment)) {
					if (!PatchLoot.LOOT.contains(p.patch().id())) helper.fail("a mob wears " + p.patch().id() + ", which is not loot");
					seen.add(p.patch().id());
				}
			}
		}
		helper.assertValueEqual(seen, PatchLoot.LOOT, "the patches seen over " + 2 * SEEDS + " garments");
		helper.succeed();
	}

	@GameTest
	public void dressedLikeAMob(GameTestHelper helper) {
		for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
			for (ItemStack garment : garments(slot, true)) {
				if (garment.isEmpty()) helper.fail("no garment for " + slot);
				if (chapter(garment).slot != slot) helper.fail(chapter(garment).id + " is not worn in " + slot);
				if (!MobOvvar.is(garment)) helper.fail("a mob garment lacks the marker");
				if (OvveItem.owner(garment) != null) helper.fail("a mob garment has an owner");
				if (!garment.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty()) helper.fail("a mob garment has something in its pockets");
				List<Placement> all = placements(garment);
				if (all.isEmpty() || all.size() > 3) helper.fail("a mob garment has " + all.size() + " patches");
				for (Piece piece : Piece.values()) {
					if (all.stream().filter(p -> p.piece() == piece).count() > Looks.INSTANT) helper.fail("more than " + Looks.INSTANT + " on " + piece);
				}
				if (slot == EquipmentSlot.CHEST && all.stream().anyMatch(p -> p.piece() != Piece.TOP)) helper.fail("a frack with patches off its top");
				SpotPlacements sewn = Looks.sewn(garment).orElseThrow();
				var encoded = SpotPlacements.CODEC.encodeStart(JavaOps.INSTANCE, sewn).getOrThrow();
				var decoded = SpotPlacements.CODEC.parse(JavaOps.INSTANCE, encoded).getOrThrow();
				helper.assertValueEqual(decoded, sewn, "the sewn patches after a round trip");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void everyChapterAndBothTops(GameTestHelper helper) {
		for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
			Set<Chapter> seen = new HashSet<>();
			Set<Chapter> up = new HashSet<>(), down = new HashSet<>();
			Map<Integer, Integer> counts = new HashMap<>();
			for (ItemStack garment : garments(slot, true)) {
				Chapter chapter = chapter(garment);
				seen.add(chapter);
				(OvveItem.topUp(garment) ? up : down).add(chapter);
				counts.merge(placements(garment).size(), 1, Integer::sum);
			}
			helper.assertValueEqual(seen, new HashSet<>(MobOvvar.chapters(slot)), "the chapters seen in " + slot);
			Set<Chapter> rollable = seen.stream().filter(c -> c.rollable).collect(Collectors.toSet());
			if (slot == EquipmentSlot.LEGS) {
				helper.assertValueEqual(down, rollable, "the rollable chapters seen top-down");
				helper.assertValueEqual(up, seen, "the chapters seen top-up");
			}
			for (int n = 1; n <= 3; n++) {
				if (counts.getOrDefault(n, 0) < 400) helper.fail(slot + ": " + n + " patches seen only " + counts.getOrDefault(n, 0) + " times in " + SEEDS);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void hiddenCellsStayBare(GameTestHelper helper) {
		for (ItemStack garment : garments(EquipmentSlot.LEGS, true)) {
			if (!OvveItem.topUp(garment) && placements(garment).stream().anyMatch(p -> p.piece() == Piece.TOP)) helper.fail("a top-down ovve has a patch on its top");
		}
		for (ItemStack garment : garments(EquipmentSlot.LEGS, false)) {
			if (placements(garment).stream().anyMatch(p -> p.piece() == Piece.TOP)) helper.fail("a covered top has a patch");
		}
		helper.succeed();
	}

	@GameTest
	public void spawnPieceFollowsTheConfig(GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		if (zombie == null) helper.fail("no zombie");
		MobOvvarConfig always = new MobOvvarConfig(true, 1f, .5f);
		ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS), chest = new ItemStack(Items.IRON_CHESTPLATE);
		ItemStack legs = MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, RandomSource.create(1), always);
		if (!MobOvvar.is(legs)) helper.fail("leggings at chance 1 stayed " + legs);
		helper.assertValueEqual(zombie.getDropChances().byEquipment(EquipmentSlot.LEGS), 0f, "the legs drop chance");
		ItemStack top = MobOvvar.spawnPiece(zombie, EquipmentSlot.CHEST, chest, RandomSource.create(2), always);
		if (!MobOvvar.is(top)) helper.fail("a chestplate at chance 1 stayed " + top);
		helper.assertValueEqual(chapter(top).slot, EquipmentSlot.CHEST, "the frack's slot");
		helper.assertValueEqual(zombie.getDropChances().byEquipment(EquipmentSlot.CHEST), 0f, "the chest drop chance");

		RandomSource random = RandomSource.create(3);
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, random, new MobOvvarConfig(true, 0f, .5f)) == leggings, "chance 0 keeps vanilla");
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, random, new MobOvvarConfig(false, 1f, .5f)) == leggings, "disabled keeps vanilla");
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.HEAD, helmet, random, always) == helmet, "a helmet is never replaced");
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, ItemStack.EMPTY, random, always).isEmpty(), "nothing stays nothing");
		MobOvvar.wear(zombie, new ItemStack(Items.IRON_BOOTS));
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "wear leaves anything but a garment alone");

		MobOvvarConfig rare = new MobOvvarConfig(true, 0.05f, .5f);
		// One stream: the first float of RandomSource.create(n) over consecutive n is a narrow, ordered band, not a sample.
		RandomSource stream = RandomSource.create(5);
		int made = 0;
		for (int i = 0; i < 10_000; i++) {
			if (MobOvvar.is(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, stream, rare))) made++;
		}
		if (made < 400 || made > 600) helper.fail(made + " garments in 10000 rolls at 0.05");
		helper.succeed();
	}

	@GameTest
	public void patchDropsAreTheWornPatches(GameTestHelper helper) {
		int worn = 0, dropped = 0;
		RandomSource stream = RandomSource.create(7);   // one stream for the odds: consecutive seeds' first floats are correlated
		for (int seed = 0; seed < SEEDS; seed++) {
			ItemStack garment = MobOvvar.garment(seeded(seed), seed % 2 == 0 ? EquipmentSlot.LEGS : EquipmentSlot.CHEST, true);
			List<String> ids = placements(garment).stream().map(p -> p.patch().id()).sorted().toList();
			List<String> all = MobOvvar.patchDrops(garment, seeded(seed), 1f).stream()
					.map(s -> ((PatchItem) s.getItem()).patch.id()).sorted().toList();
			helper.assertValueEqual(all, ids, "the drops at chance 1");
			if (!MobOvvar.patchDrops(garment, seeded(seed), 0f).isEmpty()) helper.fail("a drop at chance 0");
			worn += ids.size();
			dropped += MobOvvar.patchDrops(garment, stream, 0.5f).size();
		}
		double ratio = dropped / (double) worn;
		if (ratio < 0.45 || ratio > 0.55) helper.fail("drop ratio " + ratio + " at 0.5");
		helper.succeed();
	}

	/** Wears the garment on a mannequin and ticks it once, as the server does: an ovve's companion top appears. */
	private static void wearAndTick(GameTestHelper helper, LivingEntity mannequin, ItemStack garment) {
		OvveItem.wear(mannequin, garment);
		garment.inventoryTick(helper.getLevel(), mannequin, ((OvveItem) garment.getItem()).chapter.slot);
	}

	@GameTest
	public void everyViewerSeesItAll(GameTestHelper helper) {
		var mannequin = helper.spawn(EntityTypes.MANNEQUIN, new BlockPos(2, 1, 2));
		int tops = 0;
		for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
			for (int seed = 0; seed < 200; seed++) {
				for (EquipmentSlot s : EquipmentSlot.values()) mannequin.setItemSlot(s, ItemStack.EMPTY);
				ItemStack garment = MobOvvar.garment(seeded(seed), slot, true);
				wearAndTick(helper, mannequin, garment);
				List<Placement> all = placements(garment);
				for (Piece piece : chapter(garment).pieces()) {
					ItemStack half = piece == Piece.TOP && slot == EquipmentSlot.LEGS ? mannequin.getItemBySlot(EquipmentSlot.CHEST) : garment;
					if (piece == Piece.TOP && slot == EquipmentSlot.LEGS) {
						if (!OvveItem.topUp(garment)) continue;
						if (half.isEmpty() || !MobOvvar.is(half)) helper.fail("the companion top of seed " + seed + " lacks the marker: " + half);
						tops++;
					}
					Looks.Look look = Looks.look(half, piece, UUID.randomUUID());
					if (!look.complete()) helper.fail(slot + " seed " + seed + " " + piece + " is not complete");
					if (look.trim() != null) helper.fail(slot + " seed " + seed + " " + piece + " needs a trim");
					if (look.feetDye() != 0) helper.fail(slot + " seed " + seed + " " + piece + " has feet dye");
					boolean any = all.stream().anyMatch(p -> p.piece() == piece);
					if (any && look.dye() == 0) helper.fail(slot + " seed " + seed + " " + piece + " has patches but no dye");
				}
			}
		}
		if (tops == 0) helper.fail("no companion top was ever checked");
		helper.succeed();
	}

	@GameTest
	public void mobOvvarQueueNoCombo(GameTestHelper helper) {
		List<Placement> placements = List.of(Placement.parse("leg_front_top_r.batata"), Placement.parse("leg_out_top_l.batatu"), Placement.parse("seat.pung"));
		Combos.Combo combo = Placement.combo(placements);
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Combos.forgetForTest(Chapter.DATA, Piece.BOTTOM, combo);
		try {
			ItemStack marked = new ItemStack(ModContent.ovve(Chapter.DATA));
			Looks.setSewn(marked, SpotPlacements.fromList(placements).getOrThrow());
			marked.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
			ItemStack control = marked.copy();
			control.remove(ModComponents.MOB_OVVE);

			Looks.look(marked, Piece.BOTTOM, UUID.randomUUID());
			PolymerItemUtils.getPolymerItemStack(marked, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
			if (Combos.isKnown(Chapter.DATA, Piece.BOTTOM, combo)) helper.fail("a mob's garment queued a combination");
			// ...nor do the virtual cuffs a mob's bare feet are shown in.
			ItemStack cuffs = OvveFeet.cuffsFor(marked);
			if (!MobOvvar.is(cuffs)) helper.fail("the cuffs lack the marker");
			PolymerItemUtils.getPolymerItemStack(cuffs, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
			if (Combos.isKnown(Chapter.DATA, Piece.BOTTOM, combo)) helper.fail("a mob's cuffs queued a combination");

			Looks.look(control, Piece.BOTTOM, UUID.randomUUID());
			PolymerItemUtils.getPolymerItemStack(control, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
			if (!Combos.isKnown(Chapter.DATA, Piece.BOTTOM, combo)) helper.fail("control: an unmarked ovve did not queue the combination");
		} finally {
			Combos.forgetForTest(Chapter.DATA, Piece.BOTTOM, combo);
		}
		helper.succeed();
	}

	// ---- the spawn hooks

	private static final int MOBS = 400;
	private static final DifficultyInstance HARD = new DifficultyInstance(Difficulty.HARD, 10_000_000L, 10_000_000L, 1f);

	/** MOBS fresh mobs of this type, positioned in the test and never added, after a natural finalizeSpawn. */
	private static List<Mob> spawned(GameTestHelper helper, EntityType<? extends Mob> type, boolean zombie) {
		List<Mob> mobs = new ArrayList<>();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		for (int i = 0; i < MOBS; i++) {
			Mob mob = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
			if (mob == null) helper.fail("no " + type);
			mob.snapTo(at);
			SpawnGroupData group = zombie ? new Zombie.ZombieGroupData(false, false) : null;
			mob.finalizeSpawn(helper.getLevel(), HARD, EntitySpawnReason.NATURAL, group);
			mobs.add(mob);
		}
		return mobs;
	}

	/** Runs the body with this mob ovvar config, holding the lock; the config and the lock come back whatever happens. */
	private static void withMobOvvar(GameTestHelper helper, MobOvvarConfig config, Runnable body) {
		helper.startSequence()
				.thenWaitUntil(() -> WardrobeTests.assertThat(WardrobeTests.BUSY.compareAndSet(false, true), "another global-state test is running"))
				.thenExecute(() -> {
					MobOvvarConfig before = OvvarConfig.get().mobOvvar();
					try {
						OvvarConfig.modify(c -> c.mobOvvar(config));
						body.run();
					} finally {
						OvvarConfig.modify(c -> c.mobOvvar(before));
						WardrobeTests.BUSY.set(false);
					}
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 1200)
	public void mobsSpawnInOvvar(GameTestHelper helper) {
		withMobOvvar(helper, new MobOvvarConfig(true, 1f, .5f), () -> {
			Map<EntityType<? extends Mob>, Boolean> types = new LinkedHashMap<>();
			types.put(EntityTypes.ZOMBIE, true);
			types.put(EntityTypes.HUSK, true);
			types.put(EntityTypes.SKELETON, false);
			types.put(EntityTypes.STRAY, false);
			types.put(EntityTypes.PIGLIN, false);
			types.forEach((type, zombie) -> {
				int legs = 0, chests = 0;
				for (Mob mob : spawned(helper, type, zombie)) {
					ItemStack leg = mob.getItemBySlot(EquipmentSlot.LEGS), chest = mob.getItemBySlot(EquipmentSlot.CHEST);
					if (!leg.isEmpty()) {
						legs++;
						if (!MobOvvar.is(leg)) helper.fail(type + " spawned in " + leg + " at chance 1");
						helper.assertValueEqual(mob.getDropChances().byEquipment(EquipmentSlot.LEGS), 0f, type + "'s legs drop chance");
						// The chest is rolled first, so an ovve under a chest piece was dressed as covered: no patch on its top.
						if (!chest.isEmpty() && placements(leg).stream().anyMatch(p -> p.piece() == Piece.TOP)) helper.fail(type + "'s covered ovve top has a patch");
					}
					if (!chest.isEmpty()) {
						chests++;
						if (!MobOvvar.is(chest)) helper.fail(type + " spawned in " + chest + " at chance 1");
						helper.assertValueEqual(chapter(chest).slot, EquipmentSlot.CHEST, type + "'s chest garment's slot");
						helper.assertValueEqual(mob.getDropChances().byEquipment(EquipmentSlot.CHEST), 0f, type + "'s chest drop chance");
					}
				}
				if (legs == 0) helper.fail("no " + type + " of " + MOBS + " spawned with anything on its legs");
				if (chests == 0) helper.fail("no " + type + " of " + MOBS + " spawned with anything on its chest");
			});
		});
	}

	@GameTest(maxTicks = 1200)
	public void offMeansVanillaArmour(GameTestHelper helper) {
		withMobOvvar(helper, new MobOvvarConfig(false, 1f, .5f), () -> {
			int legs = 0, chests = 0;
			for (Mob mob : spawned(helper, EntityTypes.ZOMBIE, true)) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					if (MobOvvar.is(mob.getItemBySlot(slot))) helper.fail("a zombie wears a mob garment with mob ovvar off");
				}
				if (!mob.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) legs++;
				if (!mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) chests++;
			}
			if (legs == 0) helper.fail("no zombie of " + MOBS + " spawned in leggings");
			if (chests == 0) helper.fail("no zombie of " + MOBS + " spawned in a chestplate");
		});
	}

	// ---- the garment never leaves the mob

	private static ItemStack marked(RandomSource random, EquipmentSlot slot) {
		ItemStack garment = MobOvvar.garment(random, slot, true);
		if (garment.isEmpty()) throw new IllegalStateException("no garment for " + slot);
		return garment;
	}

	@GameTest
	public void mobNeverSwapsItsGarment(GameTestHelper helper) {
		RandomSource r = RandomSource.create(11);
		var level = helper.getLevel();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
		zombie.setCanPickUpLoot(true);
		ItemStack ovve = marked(r, EquipmentSlot.LEGS);
		MobOvvar.wear(zombie, ovve);
		if (!zombie.equipItemIfPossible(level, new ItemStack(Items.DIAMOND_LEGGINGS)).isEmpty()) helper.fail("a zombie swapped its ovve");
		if (zombie.getItemBySlot(EquipmentSlot.LEGS) != ovve) helper.fail("the zombie's legs changed");

		Zombie frack = helper.spawn(EntityTypes.ZOMBIE, 3, 1, 2);
		frack.setCanPickUpLoot(true);
		ItemStack coat = marked(r, EquipmentSlot.CHEST);
		MobOvvar.wear(frack, coat);
		if (!frack.equipItemIfPossible(level, new ItemStack(Items.DIAMOND_CHESTPLATE)).isEmpty()) helper.fail("a zombie swapped its frack");
		if (frack.getItemBySlot(EquipmentSlot.CHEST) != coat) helper.fail("the zombie's chest changed");

		var piglin = helper.spawn(EntityTypes.PIGLIN, 4, 1, 2);
		piglin.setCanPickUpLoot(true);
		ItemStack pigOvve = marked(r, EquipmentSlot.LEGS);
		MobOvvar.wear(piglin, pigOvve);
		if (!piglin.equipItemIfPossible(level, new ItemStack(Items.GOLDEN_LEGGINGS)).isEmpty()) helper.fail("a piglin swapped its ovve");
		if (piglin.getItemBySlot(EquipmentSlot.LEGS) != pigOvve) helper.fail("the piglin's legs changed");

		Zombie plain = helper.spawn(EntityTypes.ZOMBIE, 5, 1, 2);
		plain.setCanPickUpLoot(true);
		plain.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
		plain.equipItemIfPossible(level, new ItemStack(Items.DIAMOND_LEGGINGS));
		helper.assertValueEqual(plain.getItemBySlot(EquipmentSlot.LEGS).getItem(), Items.DIAMOND_LEGGINGS, "an unmarked zombie's legs");
		helper.succeed();
	}

	@GameTest
	public void lootingNeverDropsTheGarment(GameTestHelper helper) {
		var level = helper.getLevel();
		RandomSource r = RandomSource.create(12);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.LOOTING), 3);
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
		MobOvvar.wear(zombie, marked(r, EquipmentSlot.LEGS));
		MobOvvar.wear(zombie, marked(r, EquipmentSlot.CHEST));
		helper.assertValueEqual(zombie.getDropChances().byEquipment(EquipmentSlot.LEGS), 0f, "legs drop chance");
		helper.assertValueEqual(zombie.getDropChances().byEquipment(EquipmentSlot.CHEST), 0f, "chest drop chance");
		zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
		helper.runAfterDelay(1, () -> {
			var box = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(10);
			for (var item : level.getEntitiesOfClass(ItemEntity.class, box)) {
				if (item.getItem().getItem() instanceof OvveItem || item.getItem().getItem() instanceof OvveTopItem) {
					helper.fail("the garment dropped: " + item.getItem());
				}
			}
			helper.succeed();
		});
	}

	@GameTest
	public void mobOvveNeverLandsAsAnItem(GameTestHelper helper) {
		var level = helper.getLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		ItemStack garment = marked(RandomSource.create(13), EquipmentSlot.LEGS);
		var item = new ItemEntity(level, at.x, at.y, at.z, garment);
		if (level.addFreshEntity(item)) helper.fail("a mob's ovve landed as an item");
		var plain = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ModContent.ovve(Chapter.values()[0])));
		if (!level.addFreshEntity(plain)) helper.fail("an unmarked ovve could not land");
		plain.discard();
		helper.succeed();
	}

	@GameTest
	public void playersCannotKeepOne(GameTestHelper helper) {
		var level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack garment = marked(RandomSource.create(14), EquipmentSlot.LEGS);
		ItemStack plain = new ItemStack(ModContent.ovve(((OvveItem) garment.getItem()).chapter));
		player.getInventory().setItem(0, garment);
		player.getInventory().setItem(1, plain);
		garment.inventoryTick(level, player, null);
		plain.inventoryTick(level, player, null);
		helper.assertValueEqual(garment.getCount(), 0, "the marked ovve's count");
		helper.assertValueEqual(plain.getCount(), 1, "the unmarked ovve's count");
		helper.succeed();
	}

	@GameTest
	public void conversionKeepsTheRules(GameTestHelper helper) {
		RandomSource r = RandomSource.create(15);
		var husk = helper.spawn(EntityTypes.HUSK, 2, 1, 2);
		MobOvvar.wear(husk, marked(r, EquipmentSlot.LEGS));
		Mob zombie = husk.convertTo(EntityTypes.ZOMBIE, ConversionParams.single(husk, true, true), m -> {});
		var piglin = helper.spawn(EntityTypes.PIGLIN, 4, 1, 2);
		MobOvvar.wear(piglin, marked(r, EquipmentSlot.LEGS));
		Mob pigman = piglin.convertTo(EntityTypes.ZOMBIFIED_PIGLIN, ConversionParams.single(piglin, true, true), m -> {});
		for (Mob m : new Mob[] {zombie, pigman}) {
			if (m == null) helper.fail("no conversion");
			if (!MobOvvar.is(m.getItemBySlot(EquipmentSlot.LEGS))) helper.fail(m + " lost the marker");
			helper.assertValueEqual(m.getDropChances().byEquipment(EquipmentSlot.LEGS), 0f, m + "'s legs drop chance");
		}
		helper.succeed();
	}

	// ---- patches drop on death

	private static final Set<String> WORN = Set.of("patch_spiken", "patch_jgs", "patch_pung", "patch_kth");

	/** A zombie in a marked DATA ovve (top up) and a marked MEDIA frack, ready to die. */
	private static Zombie patchedZombie(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		Looks.setSewn(ovve, SpotPlacements.fromList(List.of(Placement.parse("front_top_left.spiken"), Placement.parse("leg_front_top_r.jgs"), Placement.parse("seat.pung"))).getOrThrow());
		ovve.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
		OvveItem.setTopUp(ovve, true);
		MobOvvar.wear(zombie, ovve);
		ItemStack frack = new ItemStack(ModContent.ovve(Chapter.MEDIA));
		Looks.setSewn(frack, SpotPlacements.fromList(List.of(Placement.parse("front_top_right.kth"))).getOrThrow());
		frack.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
		MobOvvar.wear(zombie, frack);
		return zombie;
	}

	private static List<ItemStack> itemsAround(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).stream().map(ItemEntity::getItem).toList();
	}

	private static void killAndCheck(GameTestHelper helper, float chance, boolean byPlayer, Set<String> expected) {
		withMobOvvar(helper, new MobOvvarConfig(true, 0.05f, chance), () -> {
			var level = helper.getLevel();
			Zombie zombie = patchedZombie(helper);
			var source = byPlayer ? level.damageSources().playerAttack(helper.makeMockServerPlayerInLevel()) : level.damageSources().generic();
			zombie.hurtServer(level, source, 1000f);
			// withMobOvvar restores the config when the body returns, so the kill and the item check both happen here.
			// A zombie's own loot (rotten flesh) is random and not what is under test: only garments and patches count.
			for (ItemStack item : itemsAround(helper)) if (item.getItem() instanceof OvveItem) helper.fail("the garment dropped: " + item);
			List<String> ids = itemsAround(helper).stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).filter(id -> id.startsWith("patch_")).sorted().toList();
			helper.assertValueEqual(new TreeSet<>(ids), new TreeSet<>(expected), "the dropped items");
			helper.assertValueEqual(ids.size(), expected.size(), "how many items dropped");
		});
	}

	@GameTest
	public void topUpMobDropsEachPatchOnce(GameTestHelper helper) {
		withMobOvvar(helper, new MobOvvarConfig(true, 0.05f, 1f), () -> {
			var level = helper.getLevel();
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
			ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
			Looks.setSewn(ovve, SpotPlacements.fromList(List.of(Placement.parse("front_top_left.spiken"), Placement.parse("leg_front_top_r.jgs"), Placement.parse("seat.pung"))).getOrThrow());
			ovve.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
			OvveItem.setTopUp(ovve, true);
			MobOvvar.wear(zombie, ovve);
			zombie.tick();
			helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof OvveTopItem, "the companion top is in the chest slot");
			zombie.hurtServer(level, level.damageSources().playerAttack(helper.makeMockServerPlayerInLevel()), 1000f);
			List<String> ids = itemsAround(helper).stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).filter(id -> id.startsWith("patch_")).sorted().toList();
			helper.assertValueEqual(ids, List.of("patch_jgs", "patch_pung", "patch_spiken"), "each worn patch drops exactly once");
		});
	}

	@GameTest
	public void playerKillDropsTheWornPatches(GameTestHelper helper) {
		killAndCheck(helper, 1f, true, WORN);
	}

	@GameTest
	public void noPatchesWithoutAPlayer(GameTestHelper helper) {
		killAndCheck(helper, 1f, false, Set.of());
	}

	@GameTest
	public void dropChanceZeroDropsNothing(GameTestHelper helper) {
		killAndCheck(helper, 0f, true, Set.of());
	}

	@GameTest
	public void curedVillagerKeepsNoGarment(GameTestHelper helper) {
		var level = helper.getLevel();
		var villager = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, 2, 1, 2);
		MobOvvar.wear(villager, marked(RandomSource.create(16), EquipmentSlot.LEGS));
		MobOvvar.wear(villager, marked(RandomSource.create(17), EquipmentSlot.CHEST));
		Mob cured = villager.convertTo(EntityTypes.VILLAGER, ConversionParams.single(villager, false, false), m -> {});
		if (cured == null) helper.fail("no cure");
		helper.runAfterDelay(1, () -> {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (MobOvvar.is(cured.getItemBySlot(slot))) helper.fail("the cured villager kept a garment in " + slot);
			}
			for (ItemStack item : itemsAround(helper)) {
				if (MobOvvar.is(item) || item.getItem() instanceof OvveItem) helper.fail("the cure dropped a garment: " + item);
			}
			helper.succeed();
		});
	}

	@GameTest
	public void killerIsThePlayerTheMobRemembers(GameTestHelper helper) {
		var level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
		helper.assertTrue(MobOvvar.killer(zombie, level.damageSources().generic()) == null, "no killer for a mob nobody hit");
		zombie.setLastHurtByPlayer(player, 100);
		helper.assertTrue(MobOvvar.killer(zombie, level.damageSources().generic()) == player, "a delayed kill credits the player");
		var wolf = helper.spawn(EntityTypes.WOLF, 4, 1, 2);
		helper.assertTrue(MobOvvar.killer(zombie, level.damageSources().mobAttack(wolf)) == player, "a tamed wolf's kill credits the player");
		Zombie fresh = helper.spawn(EntityTypes.ZOMBIE, 3, 1, 3);
		helper.assertTrue(MobOvvar.killer(fresh, level.damageSources().mobAttack(wolf)) == wolf, "a mob nobody hit falls back to the damage's entity");
		helper.succeed();
	}

	@GameTest
	public void noMobDropsNoPatches(GameTestHelper helper) {
		withMobOvvar(helper, new MobOvvarConfig(true, 0.05f, 1f), () -> {
			var level = helper.getLevel();
			var rules = level.getServer().getGameRules();
			boolean before = rules.get(GameRules.MOB_DROPS);
			rules.set(GameRules.MOB_DROPS, false, level.getServer());
			try {
				Zombie zombie = patchedZombie(helper);
				zombie.hurtServer(level, level.damageSources().playerAttack(helper.makeMockServerPlayerInLevel()), 1000f);
				for (ItemStack item : itemsAround(helper)) {
					if (BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().startsWith("patch_")) helper.fail("a patch dropped with mob drops off: " + item);
				}
				// Control: the same kill with mob drops on does drop patches, so the check above can't pass vacuously.
				rules.set(GameRules.MOB_DROPS, true, level.getServer());
				Zombie control = patchedZombie(helper);
				control.hurtServer(level, level.damageSources().playerAttack(helper.makeMockServerPlayerInLevel()), 1000f);
				helper.assertTrue(itemsAround(helper).stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i.getItem()).getPath().startsWith("patch_")),
						"with mob drops on the same kill drops patches");
			} finally {
				rules.set(GameRules.MOB_DROPS, before, level.getServer());
			}
		});
	}

	@GameTest
	public void mobCommandSummonsOne(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var level = helper.getLevel();
		var source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
		List<Zombie> summoned = new ArrayList<>();
		try {
			for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
				var box = player.getBoundingBox().inflate(2);
				List<Zombie> before = level.getEntitiesOfClass(Zombie.class, box);
				level.getServer().getCommands().performPrefixedCommand(source, slot == EquipmentSlot.LEGS ? "ovvar mob" : "ovvar mob frack");
				List<Zombie> added = level.getEntitiesOfClass(Zombie.class, box);
				added.removeAll(before);
				helper.assertValueEqual(added.size(), 1, "zombies the command added for " + slot);
				Zombie zombie = added.getFirst();
				summoned.add(zombie);
				helper.assertTrue(MobOvvar.is(zombie.getItemBySlot(slot)), "the zombie's " + slot + " is a mob ovvar garment");
				helper.assertTrue(zombie.isPersistenceRequired(), "the zombie is persistent");
			}
		} finally {
			for (Zombie z : summoned) z.discard();
		}
		helper.succeed();
	}
}
