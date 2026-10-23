import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.rift.RiftShape;
import se.metacraft.nether_rifts.rift.Tier;

public class ShapeTests {

	static final RiftShape.FrameTest OBSIDIAN = RiftShape.FrameTest.of(PortalShape.FRAME);

	static void registerTests() {
		size("shape/small_2x3", 2, 3, Tier.SMALL);
		size("shape/small_4x5", 4, 5, Tier.SMALL);
		size("shape/medium_5x5", 5, 5, Tier.MEDIUM);
		size("shape/large_10x10", 10, 10, Tier.LARGE);
		NetherRiftsTests.register("shape/blob", ctx -> {
			// an 8×8 slab of crying obsidian on the X plane with an L of air carved out of it: 3×5 + 3×2 = 21 cells
			for (int x = 2; x < 10; x++) {
				for (int y = 1; y < 9; y++) {
					ctx.setBlock(new BlockPos(x, y, 5), Blocks.CRYING_OBSIDIAN);
				}
			}
			for (int x = 3; x < 6; x++) {
				for (int y = 2; y < 7; y++) {
					ctx.setBlock(new BlockPos(x, y, 5), Blocks.AIR);
				}
			}
			for (int x = 6; x < 9; x++) {
				for (int y = 2; y < 4; y++) {
					ctx.setBlock(new BlockPos(x, y, 5), Blocks.AIR);
				}
			}
			var shape = RiftShape.fill(ctx.getLevel(), ctx.absolutePos(new BlockPos(3, 2, 5)), RiftShape.FrameTest.of(s -> s.is(Blocks.CRYING_OBSIDIAN)));
			ctx.assertTrue(shape.isPresent(), "no shape found in the L");
			ctx.assertTrue(shape.get().size() == 21, "the L has " + shape.get().size() + " cells, expected 21");
			ctx.succeed();
		});
		NetherRiftsTests.register("shape/open_frame_rejected", ctx -> {
			var inside = frame(ctx, 4, 5, Blocks.OBSIDIAN);
			ctx.setBlock(inside.above(5), Blocks.AIR); // a hole in the top
			ctx.assertTrue(RiftShape.fill(ctx.getLevel(), ctx.absolutePos(inside), OBSIDIAN).isEmpty(), "a frame with a hole made a shape");
			ctx.succeed();
		});
		NetherRiftsTests.register("shape/too_wide_rejected", ctx -> {
			var inside = frame(ctx, 22, 3, Blocks.OBSIDIAN);
			ctx.assertTrue(RiftShape.fill(ctx.getLevel(), ctx.absolutePos(inside), OBSIDIAN).isEmpty(), "a 22-wide frame made a shape");
			ctx.succeed();
		});
		NetherRiftsTests.register("shape/too_wide_from_middle_rejected", ctx -> {
			frame(ctx, 22, 3, Blocks.OBSIDIAN);
			ctx.assertTrue(RiftShape.fill(ctx.getLevel(), ctx.absolutePos(new BlockPos(11, 3, 5)), OBSIDIAN).isEmpty(), "a 22-wide frame lit from its middle made a shape");
			ctx.succeed();
		});
		NetherRiftsTests.register("shape/widest_from_middle_accepted", ctx -> {
			frame(ctx, 21, 3, Blocks.OBSIDIAN);
			var shape = RiftShape.fill(ctx.getLevel(), ctx.absolutePos(new BlockPos(11, 3, 5)), OBSIDIAN);
			ctx.assertTrue(shape.isPresent(), "a 21-wide frame lit from its middle made no shape");
			ctx.assertTrue(shape.get().size() == 21 * 3, "size " + shape.get().size());
			ctx.succeed();
		});
		NetherRiftsTests.register("shape/z_plane", ctx -> {
			var inside = zFrame(ctx, 2, 3, Blocks.OBSIDIAN);
			var shape = RiftShape.fill(ctx.getLevel(), ctx.absolutePos(inside), OBSIDIAN);
			ctx.assertTrue(shape.isPresent(), "no shape in a Z-plane 2×3 frame");
			ctx.assertTrue(shape.get().axis() == Direction.Axis.Z, "axis " + shape.get().axis());
			ctx.assertTrue(shape.get().size() == 6, "size " + shape.get().size());
			ctx.assertTrue(shape.get().frame().size() == 10, "frame " + shape.get().frame().size());
			ctx.succeed();
		});
		NetherRiftsTests.register("shape/frame_intact", ctx -> {
			var inside = frame(ctx, 2, 3, Blocks.OBSIDIAN);
			var shape = RiftShape.fill(ctx.getLevel(), ctx.absolutePos(inside), OBSIDIAN).orElseThrow();
			ctx.assertTrue(shape.frameIntact(ctx.getLevel()), "a whole frame reads as broken");
			ctx.setBlock(inside.below(), Blocks.DIRT);
			ctx.assertFalse(shape.frameIntact(ctx.getLevel()), "a frame with dirt in it reads as whole");
			ctx.succeed();
		});
	}

	static void size(String name, int w, int h, Tier tier) {
		NetherRiftsTests.register(name, ctx -> {
			var inside = frame(ctx, w, h, Blocks.OBSIDIAN);
			var shape = RiftShape.fill(ctx.getLevel(), ctx.absolutePos(inside), OBSIDIAN);
			ctx.assertTrue(shape.isPresent(), "no shape in a " + w + "×" + h + " frame");
			ctx.assertTrue(shape.get().size() == w * h, "size " + shape.get().size());
			ctx.assertTrue(shape.get().frame().size() == 2 * w + 2 * h, "frame " + shape.get().frame().size());
			var got = Tier.of(shape.get().size(), NetherRiftsConfig.DEFAULT.tiers());
			ctx.assertTrue(got == tier, "tier " + got + ", expected " + tier);
			ctx.succeed();
		});
	}

	/**
	 * A frame on the X plane at relative z = 5, its inside w × h starting at relative (1, 1, 5); returns that
	 * bottom-left inside position. Corners are filled like a player's portal.
	 */
	static BlockPos frame(GameTestHelper ctx, int w, int h, Block block) {
		for (int x = 0; x <= w + 1; x++) {
			ctx.setBlock(new BlockPos(x, 1, 5), block);
			ctx.setBlock(new BlockPos(x, h + 2, 5), block);
		}
		for (int y = 1; y <= h + 2; y++) {
			ctx.setBlock(new BlockPos(0, y, 5), block);
			ctx.setBlock(new BlockPos(w + 1, y, 5), block);
		}
		return new BlockPos(1, 2, 5);
	}

	/**
	 * A frame on the Z plane at relative x = 5, its inside w × h starting at relative (5, 2, 1); returns that
	 * bottom-left inside position. Corners are filled like a player's portal.
	 */
	static BlockPos zFrame(GameTestHelper ctx, int w, int h, Block block) {
		for (int z = 0; z <= w + 1; z++) {
			ctx.setBlock(new BlockPos(5, 1, z), block);
			ctx.setBlock(new BlockPos(5, h + 2, z), block);
		}
		for (int y = 1; y <= h + 2; y++) {
			ctx.setBlock(new BlockPos(5, y, 0), block);
			ctx.setBlock(new BlockPos(5, y, w + 1), block);
		}
		return new BlockPos(5, 2, 1);
	}
}
