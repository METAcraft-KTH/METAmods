package se.metacraft.ovvar.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.enchantment.Keyring;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;
import se.metacraft.ovvar.content.item.OvveItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Keyring: the right tool comes out of the worn ovve's pockets while mining, and goes back afterwards. */
public final class KeyringTests {
	private static final BlockPos REL = new BlockPos(1, 1, 1);

	private static ItemStack damaged(Item item, int damage) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue(damage);
		return stack;
	}

	private static ItemStack keyringOvve(GameTestHelper helper, ItemStack... pockets) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(OvveEnchantments.KEYRING), 1);
		BundleContents.Mutable contents = ovve.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		for (ItemStack stack : pockets) contents.tryInsert(stack);
		ovve.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
		return ovve;
	}

	/** A survival player one block from the stone at {@link #REL}, wearing a Keyring ovve with an iron pickaxe, 5 torches in slot 0. */
	private static ServerPlayer miner(GameTestHelper helper) {
		return miner(helper, Blocks.STONE, Items.IRON_PICKAXE);
	}

	/** The same, with {@code block} at {@link #REL} and {@code tool} in the pockets. */
	private static ServerPlayer miner(GameTestHelper helper, Block block, Item tool) {
		helper.setBlock(REL, block);
		BlockPos pos = helper.absolutePos(REL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
		player.setItemSlot(EquipmentSlot.LEGS, keyringOvve(helper, new ItemStack(tool)));
		player.getInventory().setSelectedSlot(0);
		player.getInventory().setItem(0, new ItemStack(Items.TORCH, 5));
		return player;
	}

	private static void act(GameTestHelper helper, ServerPlayer player, ServerboundPlayerActionPacket.Action action, int sequence) {
		player.gameMode.handleBlockBreakAction(helper.absolutePos(REL), action, Direction.UP, helper.getLevel().getMaxY(), sequence);
	}

	private static List<ItemStack> pockets(ItemStack ovve) {
		BundleContents contents = ovve.get(DataComponents.BUNDLE_CONTENTS);
		return contents == null ? List.of() : contents.itemCopies().toList();
	}

	private static void assertStack(GameTestHelper helper, ItemStack got, Item item, int count, String what) {
		if (!got.is(item) || got.getCount() != count) helper.fail(what + ": want " + count + " " + item + ", got " + got);
	}

	private static void assertPockets(GameTestHelper helper, ServerPlayer player, Item item, int count) {
		List<ItemStack> in = pockets(OvveItem.worn(player));
		if (in.size() != 1) helper.fail("the pockets hold one stack: " + in);
		assertStack(helper, in.getFirst(), item, count, "the pockets");
	}

	/** {@code item} counted across the whole inventory (equipment included) and every ovve's pockets in it. */
	private static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) n += stack.getCount();
			for (ItemStack inside : pockets(stack)) if (inside.is(item)) n += inside.getCount();
		}
		return n;
	}

	@GameTest
	public void keyringPicksTheRightTool(GameTestHelper helper) {
		BlockState stone = Blocks.STONE.defaultBlockState();
		ItemStack stick = new ItemStack(Items.STICK);
		List<ItemStack> pockets = List.of(new ItemStack(Items.WOODEN_SHOVEL), new ItemStack(Items.IRON_PICKAXE), damaged(Items.DIAMOND_PICKAXE, 1530));
		helper.assertValueEqual(Keyring.pick(pockets, stick, stone), 1, "31 left on the diamond: below the floor, the iron one");
		List<ItemStack> fresher = List.of(new ItemStack(Items.WOODEN_SHOVEL), new ItemStack(Items.IRON_PICKAXE), damaged(Items.DIAMOND_PICKAXE, 1529));
		helper.assertValueEqual(Keyring.pick(fresher, stick, stone), 2, "32 left on the diamond: the diamond");
		helper.assertValueEqual(Keyring.pick(fresher, new ItemStack(Items.NETHERITE_PICKAXE), stone), -1, "a netherite pickaxe in hand beats them all");
		helper.assertValueEqual(Keyring.pick(pockets, stick, Blocks.DIRT.defaultBlockState()), 0, "dirt: the shovel");
		helper.assertValueEqual(Keyring.pick(List.of(), stick, stone), -1, "empty pockets");
		helper.succeed();
	}

	@GameTest
	public void durabilityFloor(GameTestHelper helper) {
		helper.assertTrue(Keyring.usable(damaged(Items.IRON_PICKAXE, 245)), "an iron pickaxe with 5 left is usable");
		helper.assertFalse(Keyring.usable(damaged(Items.IRON_PICKAXE, 246)), "an iron pickaxe with 4 left is not");
		helper.assertTrue(Keyring.usable(new ItemStack(Items.STICK)), "a stick is usable");
		helper.assertTrue(Keyring.DURABILITY_FLOOR == 0.02f, "the floor is 2%");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void keyringSwapsOnStartAndBack(GameTestHelper helper) {
		ServerPlayer player = miner(helper);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand after START");
		assertPockets(helper, player, Items.TORCH, 5);
		act(helper, player, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, 2);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand after ABORT");
		assertPockets(helper, player, Items.TORCH, 5);
		helper.runAfterDelay(21, () -> {
			assertStack(helper, player.getInventory().getItem(0), Items.TORCH, 5, "the hand after 21 ticks");
			assertPockets(helper, player, Items.IRON_PICKAXE, 1);
			helper.assertTrue(Keyring.swapOf(player) == null, "the swap is over");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void keyringNeverLosesAnItem(GameTestHelper helper) {
		ServerPlayer player = miner(helper);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand after START");
		player.getInventory().setItem(10, OvveItem.takeOff(player));
		helper.runAfterDelay(21, () -> {
			helper.assertValueEqual(count(player, Items.TORCH), 5, "torches");
			helper.assertValueEqual(count(player, Items.IRON_PICKAXE), 1, "iron pickaxes");
			helper.assertTrue(Keyring.swapOf(player) == null, "the swap is gone");
			helper.succeed();
		});
	}

	/** Swapped mid-swap for a garment without Keyring (pockets that also hold torches): nothing is filed into it. */
	@GameTest
	public void swapBackNeedsKeyringOnTheGarment(GameTestHelper helper) {
		ServerPlayer player = miner(helper);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand after START");
		ItemStack keyring = OvveItem.takeOff(player);
		ItemStack plain = new ItemStack(ModContent.ovve(Chapter.DATA));
		BundleContents.Mutable contents = plain.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		contents.tryInsert(new ItemStack(Items.TORCH, 5));
		plain.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
		player.setItemSlot(EquipmentSlot.LEGS, plain);
		Keyring.swapBack(player);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the tool stays in hand");
		assertPockets(helper, player, Items.TORCH, 5);
		helper.assertTrue(Keyring.swapOf(player) == null, "the swap is dropped");
		List<ItemStack> kept = pockets(keyring);
		helper.assertTrue(kept.size() == 1 && kept.getFirst().is(Items.TORCH) && kept.getFirst().getCount() == 5, "the Keyring ovve still holds the torches: " + kept);
		helper.succeed();
	}

	@GameTest
	public void belowFloorSwapsBack(GameTestHelper helper) {
		ServerPlayer player = miner(helper);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand after START");
		player.getInventory().getItem(0).setDamageValue(246);
		Keyring.afterBreak(player);
		assertStack(helper, player.getInventory().getItem(0), Items.TORCH, 5, "the hand after a break below the floor");
		helper.assertTrue(Keyring.swapOf(player) == null, "the swap is over");
		helper.succeed();
	}

	@GameTest
	public void creativeDoesNotSwap(GameTestHelper helper) {
		ServerPlayer player = miner(helper);
		player.setGameMode(GameType.CREATIVE);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.TORCH, 5, "the hand in creative");
		assertPockets(helper, player, Items.IRON_PICKAXE, 1);
		helper.assertTrue(Keyring.swapOf(player) == null, "no swap in creative");
		// The same player and START in survival does swap: creative was the only reason.
		player.setGameMode(GameType.SURVIVAL);
		helper.setBlock(REL, Blocks.STONE);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 2);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand back in survival");
		assertPockets(helper, player, Items.TORCH, 5);
		helper.succeed();
	}

	@GameTest
	public void deathMidSwapLosesNothing(GameTestHelper helper) {
		ServerPlayer player = miner(helper);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand after START");
		Set<UUID> before = new HashSet<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))) before.add(e.getUUID());
		var where = player.getBoundingBox().inflate(8);
		player.die(helper.getLevel().damageSources().generic());
		Keyring.tick(helper.getLevel().getServer());
		helper.assertTrue(Keyring.swapOf(player) == null, "no swap remains");

		helper.succeedWhen(() -> {
			int pickaxes = 0, ovves = 0, torchesLoose = 0;
			for (ItemStack s : droppedOrBuried(helper, player, where, before)) {
				if (s.is(Items.IRON_PICKAXE)) pickaxes += s.getCount();
				if (s.is(Items.TORCH)) torchesLoose += s.getCount();
				if (s.is(ModContent.ovve(Chapter.DATA))) {
					ovves += s.getCount();
					List<ItemStack> in = pockets(s);
					if (in.size() != 1) helper.fail("the dropped ovve's pockets hold one stack: " + in);
					assertStack(helper, in.getFirst(), Items.TORCH, 5, "the dropped ovve's pockets");
				}
			}
			helper.assertValueEqual(pickaxes, 1, "iron pickaxes dropped");
			helper.assertValueEqual(ovves, 1, "ovves dropped");
			helper.assertValueEqual(torchesLoose, 0, "loose torches");
		});
	}

	/** What a death left behind: new item entities near the player, and, with Universal Graves (-PwithGraves), its grave's items. */
	private static List<ItemStack> droppedOrBuried(GameTestHelper helper, ServerPlayer player, net.minecraft.world.phys.AABB where, Set<UUID> before) {
		List<ItemStack> out = new java.util.ArrayList<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, where)) {
			if (!before.contains(e.getUUID())) out.add(e.getItem());
		}
		if (EnchantmentTests.GRAVES) out.addAll(EnchantmentTests.graveItems(helper, player.getUUID()));
		return out;
	}

	@GameTest(maxTicks = 200)
	public void slowDigKeepsTheTool(GameTestHelper helper) {
		ServerPlayer player = miner(helper, Blocks.OBSIDIAN, Items.DIAMOND_PICKAXE);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.DIAMOND_PICKAXE, 1, "the hand after START on obsidian");
		helper.runAfterDelay(40, () -> {
			// A held button sends nothing until STOP: 40 ticks of digging are not 40 ticks idle.
			assertStack(helper, player.getInventory().getItem(0), Items.DIAMOND_PICKAXE, 1, "the hand 40 ticks into the dig");
			assertPockets(helper, player, Items.TORCH, 5);
			act(helper, player, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, 2);
			helper.runAfterDelay(21, () -> {
				assertStack(helper, player.getInventory().getItem(0), Items.TORCH, 5, "the hand 21 ticks after the dig ended");
				assertPockets(helper, player, Items.DIAMOND_PICKAXE, 1);
				helper.assertTrue(Keyring.swapOf(player) == null, "the swap is over");
				helper.succeed();
			});
		});
	}

	@GameTest
	public void keepsakeDeathMidSwapLosesNothing(GameTestHelper helper) {
		ServerPlayer player = miner(helper);
		OvveItem.worn(player).enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(OvveEnchantments.KEEPSAKE), 1);
		act(helper, player, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
		assertStack(helper, player.getInventory().getItem(0), Items.IRON_PICKAXE, 1, "the hand after START");
		Set<UUID> before = new HashSet<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))) before.add(e.getUUID());
		var where = player.getBoundingBox().inflate(8);
		player.die(helper.getLevel().damageSources().generic());

		ServerPlayer fresh = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		try {
			Keyring.tick(helper.getLevel().getServer());
			ItemStack legs = OvveItem.worn(fresh);
			helper.assertTrue(legs.is(ModContent.ovve(Chapter.DATA)), "the respawned player wears the ovve: " + legs);
			List<ItemStack> in = pockets(legs);
			if (in.size() != 1) helper.fail("the respawned ovve's pockets hold one stack: " + in);
			assertStack(helper, in.getFirst(), Items.TORCH, 5, "the respawned ovve's pockets");
			helper.assertValueEqual(count(fresh, Items.IRON_PICKAXE), 0, "iron pickaxes on the respawned player");
			helper.assertTrue(Keyring.swapOf(fresh) == null, "no swap remains");
		} finally {
			if (fresh != player) fresh.discard();
		}
		helper.succeedWhen(() -> {
			int pickaxes = 0, ovves = 0, torchesLoose = 0;
			for (ItemStack s : droppedOrBuried(helper, player, where, before)) {
				if (s.is(Items.IRON_PICKAXE)) pickaxes += s.getCount();
				if (s.is(Items.TORCH)) torchesLoose += s.getCount();
				if (s.is(ModContent.ovve(Chapter.DATA))) ovves += s.getCount();
			}
			helper.assertValueEqual(pickaxes, 1, "iron pickaxes dropped");
			helper.assertValueEqual(ovves, 0, "Keepsake ovves dropped");
			helper.assertValueEqual(torchesLoose, 0, "loose torches");
		});
	}
}
