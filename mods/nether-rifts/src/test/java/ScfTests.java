import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import nu.metacraft.nether_rifts.rift.RiftBlocks;

public class ScfTests {

	static void registerTests() {
		NetherRiftsTests.register("scf/blob_opens_rift", ctx -> {
			// A blackstone slab on the X plane with an L of air: 3×4 + 2×2 = 16 cells.
			for (int x = 2; x < 9; x++) {
				for (int y = 1; y < 8; y++) {
					ctx.setBlock(new BlockPos(x, y, 5), Blocks.BLACKSTONE);
				}
			}
			for (int x = 3; x < 6; x++) {
				for (int y = 2; y < 6; y++) {
					ctx.setBlock(new BlockPos(x, y, 5), Blocks.AIR);
				}
			}
			for (int x = 6; x < 8; x++) {
				for (int y = 2; y < 4; y++) {
					ctx.setBlock(new BlockPos(x, y, 5), Blocks.AIR);
				}
			}
			var player = StageTests.player(ctx);
			StageTests.use(ctx, player, new BlockPos(3, 1, 5), new ItemStack(Items.FIRE_CHARGE));
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				ctx.assertTrue(rift.shape().size() == 16, "the rift has " + rift.shape().size() + " blocks, expected the L's 16");
				ctx.assertBlockPresent(RiftBlocks.RIFT, new BlockPos(7, 3, 5));
				StageTests.remove(ctx, player);
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("scf/unlisted_type_left_alone", ctx -> {
			// Same frame in the default environment: the type isn't listed, so SCF behaves as before (blocked: nothing).
			for (int x = 2; x < 6; x++) {
				for (int y = 1; y < 6; y++) {
					boolean edge = x == 2 || x == 5 || y == 1 || y == 5;
					ctx.setBlock(new BlockPos(x, y, 5), edge ? Blocks.BLACKSTONE : Blocks.AIR);
				}
			}
			var player = StageTests.player(ctx);
			StageTests.use(ctx, player, new BlockPos(3, 1, 5), new ItemStack(Items.FIRE_CHARGE));
			ctx.runAtTickTime(3, () -> {
				ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "an unlisted SCF portal opened a rift");
				StageTests.remove(ctx, player);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("scf/oversized_left_alone", ctx -> {
			// A closed frame whose inside is 22 wide and 3 tall: SCF would accept it, a rift may not be wider than 21.
			for (int x = 0; x < 24; x++) {
				for (int y = 1; y < 6; y++) {
					boolean edge = x == 0 || x == 23 || y == 1 || y == 5;
					ctx.setBlock(new BlockPos(x, y, 5), edge ? Blocks.BLACKSTONE : Blocks.AIR);
				}
			}
			var player = StageTests.player(ctx);
			StageTests.use(ctx, player, new BlockPos(1, 1, 5), new ItemStack(Items.FIRE_CHARGE));
			ctx.runAtTickTime(3, () -> {
				ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "an oversized SCF portal opened a rift");
				StageTests.remove(ctx, player);
				ctx.succeed();
			});
		});
	}
}
