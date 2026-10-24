import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import se.metacraft.nether_rifts.raid.RiftRaid;
import se.metacraft.nether_rifts.rift.*;

public class FormingTests {

	static void registerTests() {
		NetherRiftsTests.register("forming/fire_opens_rift", ctx -> {
			var inside = lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				assertInside(ctx, inside, 2, 3, RiftBlocks.RIFT);
				var rift = riftIn(ctx);
				ctx.assertTrue(rift.stage() == Stage.LIT, "stage " + rift.stage());
				ctx.assertTrue(rift.tier() == Tier.SMALL, "tier " + rift.tier());
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/rift_mode_off_makes_portal", ctx -> {
			var inside = lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				assertInside(ctx, inside, 2, 3, Blocks.NETHER_PORTAL);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/activation_allowed_makes_portal", ctx -> {
			// Review focus 1: staff allowed nether lighting (here globally) while rift mode stays on.
			var inside = lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				assertInside(ctx, inside, 2, 3, Blocks.NETHER_PORTAL);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/not_in_nether", ctx -> {
			// Review focus 5: rift mode never reaches into the nether.
			var nether = ctx.getLevel().getServer().getLevel(Level.NETHER);
			var base = new BlockPos(0, 100, 0);
			for (int x = 0; x <= 3; x++) {
				for (int y = 0; y <= 4; y++) {
					boolean edge = x == 0 || x == 3 || y == 0 || y == 4;
					nether.setBlockAndUpdate(base.offset(x, y, 0), edge ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
			nether.setBlockAndUpdate(base.offset(1, 1, 0), Blocks.FIRE.defaultBlockState());
			ctx.runAtTickTime(3, () -> {
				ctx.assertTrue(RiftData.get(nether).all().isEmpty(), "a rift opened in the nether");
				ctx.assertFalse(nether.getBlockState(base.offset(1, 1, 0)).is(RiftBlocks.RIFT), "rift blocks in the nether");
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/visual_attached", ctx -> {
			lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = riftIn(ctx);
				ctx.assertTrue(rift.visual() != null, "no visual");
				// a 2×3 rectangle: one panel and four edge strips
				ctx.assertTrue(rift.visual().elementCount() == 5, rift.visual().elementCount() + " elements");
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/z_plane_opens_rift", ctx -> {
			var inside = ShapeTests.zFrame(ctx, 2, 3, Blocks.OBSIDIAN);
			ctx.setBlock(inside, Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				for (int z = 0; z < 2; z++) {
					for (int y = 0; y < 3; y++) {
						ctx.assertBlockPresent(RiftBlocks.RIFT, inside.offset(0, y, z));
					}
				}
				var rift = riftIn(ctx);
				ctx.assertTrue(rift.shape().axis() == net.minecraft.core.Direction.Axis.Z, "axis " + rift.shape().axis());
				ctx.assertTrue(rift.visual() != null, "no visual");
				// a 2×3 rectangle: one panel and four edge strips
				ctx.assertTrue(rift.visual().elementCount() == 5, rift.visual().elementCount() + " elements");
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/clients_see_portal", ctx -> {
			// Vanilla clients see a nether portal turned the rift's way and draw its swirl themselves; the server's block
			// stays a rift, so nothing teleports.
			var alongX = lightFrame(ctx, 2, 3);
			var alongZ = ShapeTests.zFrame(ctx, 2, 3, Blocks.OBSIDIAN);
			ctx.setBlock(alongZ, Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				assertClientPortal(ctx, alongX, Direction.Axis.X);
				assertClientPortal(ctx, alongZ, Direction.Axis.Z);
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/broken_frame_fades", ctx -> {
			// Review focus 2: a waiting rift whose frame is broken fades, leaving no invisible blocks.
			var inside = lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> ctx.setBlock(inside.below(), Blocks.AIR));
			ctx.runAtTickTime(30, () -> {
				ctx.assertTrue(riftsIn(ctx).isEmpty(), "the rift outlived its frame");
				assertInside(ctx, inside, 2, 3, Blocks.AIR);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/visual_reattaches", ctx -> {
			// Polymer drops a chunk's attachments when it unloads; a rift must draw itself again when it ticks again.
			lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var visual = riftIn(ctx).visual();
				ctx.assertTrue(visual != null && visual.getAttachment() != null, "no attached visual to start with");
				visual.getAttachment().destroy();
				ctx.assertTrue(visual.getAttachment() == null, "destroying the attachment left it attached");
			});
			ctx.runAtTickTime(8, () -> {
				var visual = riftIn(ctx).visual();
				ctx.assertTrue(visual != null, "the visual was dropped");
				ctx.assertTrue(visual.getAttachment() != null, "the visual stayed detached");
				ctx.assertTrue(visual.elementCount() == 5, visual.elementCount() + " elements");
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/shared_frame_indexed", ctx -> {
			// Two portals sharing an obsidian column: the column belongs to both, whichever one goes first.
			ShapeTests.frame(ctx, 2, 3, Blocks.OBSIDIAN);
			for (int x = 3; x <= 6; x++) {
				ctx.setBlock(new BlockPos(x, 1, 5), Blocks.OBSIDIAN);
				ctx.setBlock(new BlockPos(x, 5, 5), Blocks.OBSIDIAN);
			}
			for (int y = 1; y <= 5; y++) {
				ctx.setBlock(new BlockPos(6, y, 5), Blocks.OBSIDIAN);
			}
			ctx.setBlock(new BlockPos(4, 2, 5), Blocks.FIRE);
			ctx.setBlock(new BlockPos(1, 2, 5), Blocks.FIRE);
			var shared = ctx.absolutePos(new BlockPos(3, 3, 5));
			ctx.runAtTickTime(3, () -> {
				var data = RiftData.get(ctx.getLevel());
				var rifts = riftsIn(ctx);
				ctx.assertTrue(rifts.size() == 2, rifts.size() + " rifts");
				ctx.assertTrue(data.at(shared).isPresent(), "the shared column isn't indexed");
				// the one opened last is the one whose index entry used to win; take it away
				var newer = rifts.getLast();
				var older = rifts.getFirst();
				newer.fade(ctx.getLevel(), data);
				var found = data.at(shared);
				ctx.assertTrue(found.isPresent(), "the shared column lost its rift when the other faded");
				ctx.assertTrue(found.get().id().equals(older.id()), "the shared column points at the wrong rift");
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("forming/water_does_not_wash_rift", ctx -> {
			var inside = lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				assertInside(ctx, inside, 2, 3, RiftBlocks.RIFT);
				// sources on both faces of the plane, level with the rift's middle row: they spread sideways into it
				ctx.setBlock(inside.offset(0, 1, -1), Blocks.WATER);
				ctx.setBlock(inside.offset(1, 1, 1), Blocks.WATER);
			});
			ctx.runAtTickTime(45, () -> {
				ctx.assertBlockPresent(Blocks.WATER, inside.offset(0, 1, -2));
				assertInside(ctx, inside, 2, 3, RiftBlocks.RIFT);
				ctx.setBlock(inside.offset(0, 1, -1), Blocks.AIR);
				ctx.setBlock(inside.offset(1, 1, 1), Blocks.AIR);
				cleanUp(ctx);
				ctx.succeed();
			});
		});
	}

	/** Builds the frame from {@link ShapeTests#frame} and sets fire inside it; returns the inside's bottom-left. */
	static BlockPos lightFrame(GameTestHelper ctx, int w, int h) {
		var inside = ShapeTests.frame(ctx, w, h, Blocks.OBSIDIAN);
		ctx.setBlock(inside, Blocks.FIRE);
		return inside;
	}

	static void assertInside(GameTestHelper ctx, BlockPos inside, int w, int h, net.minecraft.world.level.block.Block block) {
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				ctx.assertBlockPresent(block, inside.offset(x, y, 0));
			}
		}
	}

	static void assertClientPortal(GameTestHelper ctx, BlockPos pos, Direction.Axis axis) {
		var state = ctx.getBlockState(pos);
		ctx.assertTrue(state.is(RiftBlocks.RIFT), "no rift block at " + pos + ": " + state);
		var client = ((PolymerBlock) state.getBlock()).getPolymerBlockState(state, PacketContext.get());
		ctx.assertTrue(client.is(Blocks.NETHER_PORTAL), "clients see " + client + " at " + pos);
		ctx.assertTrue(client.getValue(NetherPortalBlock.AXIS) == axis, "clients see a portal along " + client.getValue(NetherPortalBlock.AXIS) + ", not " + axis);
	}

	/** The one rift inside this test's room (tests in a batch share the level and its RiftData). */
	static Rift riftIn(GameTestHelper ctx) {
		return riftsIn(ctx).stream().findFirst().orElseThrow(() -> new AssertionError("no rift in this test's room"));
	}

	static java.util.List<Rift> riftsIn(GameTestHelper ctx) {
		return RiftData.get(ctx.getLevel()).all().stream().filter(r -> ctx.getBounds().contains(r.centre())).toList();
	}

	/** Rifts are saved data, not part of the room: drop this level's rifts that sit inside this test's bounds. */
	static void cleanUp(GameTestHelper ctx) {
		var data = RiftData.get(ctx.getLevel());
		for (var rift : data.all()) {
			if (ctx.getBounds().contains(rift.centre())) {
				if (rift.stage().raiding()) {
					RiftRaid.end(ctx.getLevel(), rift, data, true);
				} else {
					rift.fade(ctx.getLevel(), data);
				}
			}
		}
	}
}
