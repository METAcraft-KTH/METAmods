import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import nu.metacraft.nether_rifts.RiftWorldState;
import nu.metacraft.nether_rifts.Stabilisation;
import nu.metacraft.nether_rifts.rift.Rift;
import nu.metacraft.nether_rifts.rift.Stage;

public class StabiliseTests {

	static void registerTests() {
		NetherRiftsTests.register("stabilise/waiting_rifts_become_portals", ctx -> {
			var inside = FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				Stabilisation.stabilise(ctx.getLevel().getServer());
				FormingTests.assertInside(ctx, inside, 2, 3, Blocks.NETHER_PORTAL);
				ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "the rift is still tracked after becoming a portal");
				ctx.assertFalse(RiftWorldState.get(ctx.getLevel().getServer()).riftMode(), "rift mode still on");
				for (int x = 0; x < 2; x++) {
					for (int y = 0; y < 3; y++) {
						ctx.setBlock(inside.offset(x, y, 0), Blocks.AIR);
					}
				}
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("stabilise/crying_refused_in_rift_mode", ctx -> {
			var inside = ShapeTests.frame(ctx, 2, 3, Blocks.CRYING_OBSIDIAN);
			var player = StageTests.player(ctx);
			StageTests.use(ctx, player, inside.below(), new ItemStack(Items.FLINT_AND_STEEL));
			ctx.runAtTickTime(3, () -> {
				ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "a crying-obsidian rift opened during rift mode");
				StageTests.remove(ctx, player);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("stabilise/crying_opens_after", ctx -> {
			var inside = ShapeTests.frame(ctx, 2, 3, Blocks.CRYING_OBSIDIAN);
			var player = StageTests.player(ctx);
			StageTests.use(ctx, player, inside.below(), new ItemStack(Items.FLINT_AND_STEEL));
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				ctx.assertTrue(rift.crying(), "not marked as a crying-obsidian rift");
				ctx.assertTrue(rift.stage() == Stage.LIT, "stage " + rift.stage());
				StageTests.remove(ctx, player);
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("stabilise/crying_frame_crumbles", ctx -> {
			var inside = ShapeTests.frame(ctx, 2, 3, Blocks.CRYING_OBSIDIAN);
			var player = RaidTests.participant(ctx);
			var torn = new Rift[1];
			// Up front, as in raid/victory_converts_frame: onEachTick from inside a scheduled runnable crashes 26.3.
			ctx.onEachTick(() -> {
				if (torn[0] != null && torn[0].stage() == Stage.RAID) {
					RaidTests.killAll(ctx, torn[0], player);
				}
			});
			StageTests.use(ctx, player, inside.below(), new ItemStack(Items.FLINT_AND_STEEL));
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				torn[0] = rift;
				rift.tear(ctx.getLevel());
				ctx.succeedWhen(() -> {
					ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "the rift is still open");
					ctx.assertBlockPresent(Blocks.AIR, inside.below());
					ctx.assertBlockPresent(Blocks.AIR, inside.offset(-1, 0, 0));
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
				});
			});
		});
	}
}
