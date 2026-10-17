import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.qol.void_anchor.AnchorBinding;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import nu.metacraft.qol.void_anchor.VoidAnchorBlocks;
import nu.metacraft.qol.void_anchor.rift.RiftTracker;

import java.util.UUID;
import java.util.function.Consumer;

public class VoidAnchorTests {

	// ---- helpers ----

	static BlockState anchor(int charges) {
		return VoidAnchorBlocks.VOID_ANCHOR.value().defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, charges);
	}

	static int charge(ServerLevel level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return state.is(VoidAnchorBlocks.VOID_ANCHOR.value()) ? state.getValue(RespawnAnchorBlock.CHARGE) : -1;
	}

	static ServerLevel end(GameTestHelper ctx) {
		return ctx.getLevel().getServer().getLevel(Level.END);
	}

	/** An anchor on a 3×3 obsidian pad at (x, 64, 0) in the End, with air above; each test uses its own x. */
	static BlockPos endAnchor(GameTestHelper ctx, int x, int charges) {
		var end = end(ctx);
		var pos = new BlockPos(x, 64, 0);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				end.setBlockAndUpdate(pos.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState());
				for (int dy = 0; dy <= 3; dy++) {
					end.setBlockAndUpdate(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		end.setBlockAndUpdate(pos, anchor(charges));
		return pos;
	}

	static ServerPlayer survivalPlayer(GameTestHelper ctx) {
		var player = TestHelper.addMockPlayer(ctx);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	static void moveTo(ServerPlayer player, ServerLevel level, Vec3 pos) {
		player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
	}

	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack inHand) {
		player.setItemInHand(InteractionHand.MAIN_HAND, inHand);
		return player.gameMode.useItemOn(
				player, level, inHand, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
		);
	}

	/** A player bound to the anchor at {@code anchor}, dropped into the End void 30 blocks south of it. */
	static ServerPlayer fallingPlayer(GameTestHelper ctx, BlockPos anchor, String name) {
		var player = TestHelper.addMockPlayer(ctx, name, UUID.randomUUID());
		player.setGameMode(GameType.SURVIVAL);
		AnchorBinding.bind(player, GlobalPos.of(Level.END, anchor));
		moveTo(player, end(ctx), new Vec3(anchor.getX() + 0.5, -2, 30.5));
		return player;
	}

	static boolean rescued(ServerPlayer player, BlockPos anchor) {
		return player.level().dimension() == Level.END && player.getY() >= anchor.getY() - 1
				&& player.position().distanceTo(Vec3.atCenterOf(anchor)) < 3;
	}

	/** Mock players don't move, so a rescue waits out the rift's opening (a crack, at most 12 ticks) and then rift_ticks. */
	static final int RESCUED_BY = 70;

	static boolean stillFalling(ServerPlayer player) {
		return player.getY() < 0;
	}

	static void register(String name, Consumer<GameTestHelper> test) {
		QolTests.register("void_anchor/" + name, test);
	}

	// ---- tests ----

	/** Registers the void anchor's game tests; {@link QolTests} runs them with every other feature's. */
	static void registerTests() {
		register("off_no_rescue", ctx -> {
			var pos = endAnchor(ctx, 400, 2);
			var player = fallingPlayer(ctx, pos, "off");
			ctx.runAtTickTime(5, () -> ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "a rift opened while void anchors are off"));
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(stillFalling(player), "moved to " + player.position());
				ctx.assertTrue(charge(end(ctx), pos) == 2, "a charge was spent while void anchors are off");
				ctx.succeed();
			});
		});
		register("off_use_does_nothing", ctx -> {
			var end = end(ctx);
			var pos = endAnchor(ctx, 420, 0);
			var player = survivalPlayer(ctx);
			moveTo(player, end, Vec3.atBottomCenterOf(pos.east()));
			var crystals = new ItemStack(Items.END_CRYSTAL, 2);
			use(player, end, pos, crystals);
			use(player, end, pos, ItemStack.EMPTY);
			ctx.assertTrue(charge(end, pos) == 0, "charged to " + charge(end, pos) + " while void anchors are off");
			ctx.assertTrue(crystals.getCount() == 2, "an end crystal was used up while void anchors are off");
			ctx.assertTrue(AnchorBinding.get(player) == null, "bound while void anchors are off");
			ctx.succeed();
		});
		register("charge_to_four", ctx -> {
			var level = ctx.getLevel();
			var pos = ctx.absolutePos(new BlockPos(2, 1, 2));
			level.setBlockAndUpdate(pos, anchor(0));
			var player = survivalPlayer(ctx);
			var crystals = new ItemStack(Items.END_CRYSTAL, 6);
			for (int i = 0; i < 5; i++) {
				use(player, level, pos, crystals);
			}
			ctx.assertTrue(charge(level, pos) == 4, "charge is " + charge(level, pos) + ", expected 4");
			ctx.assertTrue(crystals.getCount() == 2, crystals.getCount() + " end crystals left, expected 2");
			ctx.succeed();
		});
		register("wrong_item_does_not_charge", ctx -> {
			var level = ctx.getLevel();
			var pos = ctx.absolutePos(new BlockPos(2, 1, 2));
			level.setBlockAndUpdate(pos, anchor(0));
			var player = survivalPlayer(ctx);
			var glowstone = new ItemStack(Items.GLOWSTONE, 4);
			use(player, level, pos, glowstone);
			ctx.assertTrue(charge(level, pos) == 0, "glowstone charged the anchor to " + charge(level, pos));
			ctx.assertTrue(glowstone.getCount() == 4, "glowstone was used up");
			var shards = new ItemStack(Items.ECHO_SHARD, 4);
			use(player, level, pos, shards);
			ctx.assertTrue(charge(level, pos) == 0, "an echo shard charged the anchor to " + charge(level, pos));
			ctx.assertTrue(shards.getCount() == 4, "the echo shard was used up");
			ctx.succeed();
		});
		register("no_explode_outside_end", ctx -> {
			var level = ctx.getLevel();
			var pos = ctx.absolutePos(new BlockPos(2, 1, 2));
			level.setBlockAndUpdate(pos, anchor(2));
			var player = survivalPlayer(ctx);
			use(player, level, pos, ItemStack.EMPTY);
			use(player, level, pos, new ItemStack(Items.GLOWSTONE, 4));
			ctx.assertTrue(charge(level, pos) == 2, "the anchor is gone or changed: charge " + charge(level, pos));
			ctx.assertTrue(player.getRespawnConfig() == null, "using the anchor set the player's spawn");
			ctx.assertTrue(AnchorBinding.get(player) == null, "the anchor bound outside the End");
			ctx.succeed();
		});
		register("bind_in_end", ctx -> {
			var end = end(ctx);
			var pos = endAnchor(ctx, 100, 0);
			var player = survivalPlayer(ctx);
			moveTo(player, end, Vec3.atBottomCenterOf(pos.east()));
			use(player, end, pos, ItemStack.EMPTY);
			ctx.assertTrue(
					GlobalPos.of(Level.END, pos).equals(AnchorBinding.get(player)),
					"bound to " + AnchorBinding.get(player)
			);
			ctx.succeed();
		});
		register("resolve_states", ctx -> {
			var end = end(ctx);
			var pos = endAnchor(ctx, 120, 0);
			var player = survivalPlayer(ctx);
			ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Unbound, "not Unbound");
			AnchorBinding.bind(player, GlobalPos.of(Level.END, pos));
			ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Empty, "not Empty");
			end.setBlockAndUpdate(pos, anchor(1));
			if (!(AnchorBinding.resolve(player) instanceof AnchorBinding.Ready ready)) {
				throw ctx.assertionException("not Ready: " + AnchorBinding.resolve(player));
			}
			ctx.assertTrue(ready.standUp().distanceTo(Vec3.atCenterOf(pos)) < 2.5, "stands up at " + ready.standUp());
			end.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Gone, "not Gone");
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 3; dy++) {
						end.setBlockAndUpdate(pos.offset(dx, dy, dz), Blocks.OBSIDIAN.defaultBlockState());
					}
				}
			}
			end.setBlockAndUpdate(pos, anchor(1));
			ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Blocked, "not Blocked: " + AnchorBinding.resolve(player));
			ctx.succeed();
		});
		register("rescue", ctx -> {
			var pos = endAnchor(ctx, 200, 2);
			var player = fallingPlayer(ctx, pos, "rescue");
			ctx.runAtTickTime(5, () -> ctx.assertTrue(RiftTracker.isRifting(player.getUUID()), "no rift opened"));
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "still in the rift");
				ctx.assertTrue(rescued(player, pos), "not at the anchor: " + player.position());
				ctx.assertTrue(charge(end(ctx), pos) == 1, "charge " + charge(end(ctx), pos) + ", expected 1");
				ctx.succeed();
			});
		});
		register("hangs_then_sinks", ctx -> {
			var pos = endAnchor(ctx, 440, 2);
			var player = fallingPlayer(ctx, pos, "hangs");
			ctx.runAtTickTime(5, () -> {
				ctx.assertTrue(RiftTracker.isRifting(player.getUUID()), "no rift opened");
				double y = player.getDeltaMovement().y;
				ctx.assertTrue(y < 0 && y > -0.05, "not hanging while the rift opens: " + y);
			});
			ctx.runAtTickTime(30, () -> {
				double y = player.getDeltaMovement().y;
				double sink = VoidAnchorConfig.getInstance().descentSpeed();
				ctx.assertTrue(Math.abs(y + sink) < 1e-6, "not sinking at " + sink + " once open: " + y);
				ctx.succeed();
			});
		});
		register("bind_command", ctx -> {
			var pos = endAnchor(ctx, 460, 1);
			var player = TestHelper.addMockPlayer(ctx, "bound", UUID.randomUUID());
			var server = ctx.getLevel().getServer();
			var source = server.createCommandSourceStack().withLevel(end(ctx)).withPermission(LevelBasedPermissionSet.OWNER);
			server.getCommands().performPrefixedCommand(source, "voidanchor bind bound %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ()));
			ctx.assertTrue(GlobalPos.of(Level.END, pos).equals(AnchorBinding.get(player)), "not bound: " + AnchorBinding.get(player));
			ctx.succeed();
		});
		register("glider_stops_gliding", ctx -> {
			var pos = endAnchor(ctx, 480, 2);
			var player = fallingPlayer(ctx, pos, "glider");
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
			player.startFallFlying();
			ctx.assertTrue(player.isFallFlying(), "the mock player never started gliding");
			ctx.runAtTickTime(5, () -> {
				ctx.assertTrue(RiftTracker.isRifting(player.getUUID()), "no rift opened for a glider");
				ctx.assertTrue(!player.isFallFlying(), "still gliding in the rift");
				ctx.succeed();
			});
		});
		register("empty_anchor", ctx -> {
			var pos = endAnchor(ctx, 220, 0);
			var player = fallingPlayer(ctx, pos, "empty");
			ctx.runAtTickTime(5, () -> ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "a rift opened for an empty anchor"));
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(stillFalling(player), "moved to " + player.position());
				ctx.succeed();
			});
		});
		register("unbound_player", ctx -> {
			var player = survivalPlayer(ctx);
			moveTo(player, end(ctx), new Vec3(240.5, -2, 30.5));
			ctx.runAtTickTime(5, () -> ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "a rift opened for an unbound player"));
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(stillFalling(player), "moved to " + player.position());
				ctx.succeed();
			});
		});
		register("flying_player_ignored", ctx -> {
			var pos = endAnchor(ctx, 260, 2);
			var player = fallingPlayer(ctx, pos, "flying");
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			ctx.runAtTickTime(5, () -> ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "a rift opened for a flying player"));
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(charge(end(ctx), pos) == 2, "a charge was spent");
				ctx.succeed();
			});
		});
		register("disconnect_mid_rift", ctx -> {
			var pos = endAnchor(ctx, 280, 2);
			var player = fallingPlayer(ctx, pos, "leaver");
			ctx.runAtTickTime(5, () -> {
				ctx.assertTrue(RiftTracker.isRifting(player.getUUID()), "no rift opened");
				ctx.getLevel().getServer().getPlayerList().remove(player);
			});
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "the rift outlived its player");
				ctx.assertTrue(charge(end(ctx), pos) == 2, "a charge was spent on a player who left");
				ctx.succeed();
			});
		});
		register("anchor_broken_mid_rift", ctx -> {
			var pos = endAnchor(ctx, 300, 2);
			var player = fallingPlayer(ctx, pos, "broken");
			ctx.runAtTickTime(5, () -> {
				ctx.assertTrue(RiftTracker.isRifting(player.getUUID()), "no rift opened");
				end(ctx).setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			});
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "still in the rift");
				ctx.assertTrue(stillFalling(player), "taken to a broken anchor: " + player.position());
				ctx.succeed();
			});
		});
		register("shared_last_charge", ctx -> {
			var pos = endAnchor(ctx, 320, 1);
			var first = fallingPlayer(ctx, pos, "first");
			var second = fallingPlayer(ctx, pos, "second");
			ctx.runAtTickTime(RESCUED_BY, () -> {
				int saved = (rescued(first, pos) ? 1 : 0) + (rescued(second, pos) ? 1 : 0);
				ctx.assertTrue(saved == 1, saved + " players rescued by one charge");
				ctx.assertTrue(stillFalling(first) || stillFalling(second), "nobody was left falling");
				ctx.assertTrue(charge(end(ctx), pos) == 0, "charge " + charge(end(ctx), pos) + ", expected 0");
				ctx.succeed();
			});
		});
		register("saved_mid_rift", ctx -> {
			var pos = endAnchor(ctx, 340, 2);
			var player = fallingPlayer(ctx, pos, "saved");
			ctx.runAtTickTime(5, () -> {
				ctx.assertTrue(RiftTracker.isRifting(player.getUUID()), "no rift opened");
				// An ender pearl back up onto an island.
				moveTo(player, end(ctx), new Vec3(pos.getX() + 0.5, 70, 30.5));
			});
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "the rift kept a player who saved themselves");
				ctx.assertTrue(charge(end(ctx), pos) == 2, "a charge was spent on a player who saved themselves");
				ctx.assertTrue(!rescued(player, pos), "taken to the anchor after saving themselves");
				ctx.succeed();
			});
		});
		register("creative_player_ignored", ctx -> {
			var pos = endAnchor(ctx, 360, 2);
			var player = fallingPlayer(ctx, pos, "creative");
			player.setGameMode(GameType.CREATIVE);
			player.getAbilities().flying = false;
			ctx.runAtTickTime(5, () -> ctx.assertTrue(!RiftTracker.isRifting(player.getUUID()), "a rift opened for a creative player"));
			ctx.runAtTickTime(RESCUED_BY, () -> {
				ctx.assertTrue(charge(end(ctx), pos) == 2, "a charge was spent on a creative player");
				ctx.succeed();
			});
		});
		register("dispenser_refill", ctx -> dispenserTest(ctx, 0, 1, 1));
		register("dispenser_full_anchor", ctx -> dispenserTest(ctx, 4, 4, 2));
	}

	/** A dispenser holding two end crystals faces an anchor; a redstone block fires it once. */
	static void dispenserTest(GameTestHelper ctx, int charges, int expectedCharge, int expectedCrystals) {
		var level = ctx.getLevel();
		var dispenserPos = ctx.absolutePos(new BlockPos(1, 1, 2));
		var anchorPos = dispenserPos.east();
		level.setBlockAndUpdate(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(anchorPos, anchor(charges));
		var dispenser = (DispenserBlockEntity) level.getBlockEntity(dispenserPos);
		dispenser.setItem(0, new ItemStack(Items.END_CRYSTAL, 2));
		ctx.runAtTickTime(2, () -> level.setBlockAndUpdate(dispenserPos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState()));
		ctx.runAtTickTime(12, () -> {
			ctx.assertTrue(charge(level, anchorPos) == expectedCharge, "charge " + charge(level, anchorPos) + ", expected " + expectedCharge);
			int crystals = dispenser.getItem(0).getCount();
			ctx.assertTrue(crystals == expectedCrystals, crystals + " end crystals left, expected " + expectedCrystals);
			ctx.succeed();
		});
	}


}
