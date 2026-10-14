import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.MagmaBomb;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;

public class BombTests {

	static void registerTests() {
		NetherRiftsTests.register("bomb/hurts_and_burns", ctx -> {
			var player = StageTests.player(ctx);
			// A mock player counts as "client not loaded yet", which makes it immune to all damage.
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			var at = ctx.absolutePos(new BlockPos(12, 1, 12));
			player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			var bomb = new MagmaBomb(ctx.getLevel(), null);
			bomb.setPos(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5);
			bomb.setDeltaMovement(0, -0.3, 0);
			ctx.getLevel().addFreshEntity(bomb);
			float before = player.getHealth();
			ctx.runAtTickTime(25, () -> {
				ctx.assertTrue(player.getHealth() < before, "the bomb's blast didn't hurt the player under it");
				ctx.assertTrue(player.getRemainingFireTicks() > 0, "the burning patch didn't set the player on fire");
				StageTests.remove(ctx, player);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bomb/no_block_changes", ctx -> {
			// Review focus 4: TNT, glass, crops, planks around the impact all stay put; nothing primes or burns.
			ctx.setBlock(new BlockPos(12, 1, 12), Blocks.TNT);
			ctx.setBlock(new BlockPos(13, 1, 12), Blocks.GLASS);
			ctx.setBlock(new BlockPos(11, 1, 12), Blocks.FARMLAND);
			ctx.setBlock(new BlockPos(11, 2, 12), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
			ctx.setBlock(new BlockPos(12, 1, 13), Blocks.OAK_PLANKS);
			var at = ctx.absolutePos(new BlockPos(12, 4, 12));
			var bomb = new MagmaBomb(ctx.getLevel(), null);
			bomb.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			bomb.setDeltaMovement(0, -0.3, 0);
			ctx.getLevel().addFreshEntity(bomb);
			ctx.runAtTickTime(30, () -> {
				// Without these, a bomb that never went off would pass the block checks below.
				ctx.assertTrue(ctx.getEntities(RiftEntities.MAGMA_BOMB).isEmpty(), "the bomb never landed");
				ctx.assertTrue(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> !p.warning()
						&& p.at().closerThan(Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 1, 12))), 2.5)), "no burning patch near the impact");
			});
			ctx.runAtTickTime(60, () -> {
				ctx.assertBlockPresent(Blocks.TNT, new BlockPos(12, 1, 12));
				ctx.assertBlockPresent(Blocks.GLASS, new BlockPos(13, 1, 12));
				ctx.assertBlockPresent(Blocks.WHEAT, new BlockPos(11, 2, 12));
				ctx.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(12, 1, 13));
				ctx.assertTrue(ctx.getEntities(EntityTypes.TNT).isEmpty(), "the bomb primed the TNT");
				for (var pos : BlockPos.betweenClosed(ctx.absolutePos(new BlockPos(9, 1, 9)), ctx.absolutePos(new BlockPos(15, 4, 15)))) {
					ctx.assertFalse(ctx.getLevel().getBlockState(pos).is(Blocks.FIRE), "fire at " + pos.toShortString());
				}
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bomb/ground_below_under_roof", ctx -> {
			ctx.setBlock(new BlockPos(12, 1, 12), Blocks.STONE);
			ctx.setBlock(new BlockPos(12, 6, 12), Blocks.STONE);
			var column = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 0, 12)));
			double floorTop = ctx.absolutePos(new BlockPos(12, 2, 12)).getY();
			double roofTop = ctx.absolutePos(new BlockPos(12, 7, 12)).getY();
			var above = BurningPatches.groundBelow(ctx.getLevel(), column.add(0, 10, 0));
			ctx.assertTrue(Math.abs(above.y - roofTop) < 1e-6, "from above the roof it should land on the roof top (" + roofTop + "), got " + above.y);
			var between = BurningPatches.groundBelow(ctx.getLevel(), column.add(0, 4, 0));
			ctx.assertTrue(Math.abs(between.y - floorTop) < 1e-6, "from under the roof it should land on the floor (" + floorTop + "), got " + between.y);
			ctx.succeed();
		});
		NetherRiftsTests.register("bomb/spares_rift_mobs", ctx -> {
			// a floor, so nothing falls and any movement is the blast's
			BombardierTests.stoneFloor(ctx);
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var at = ctx.absolutePos(new BlockPos(12, 1, 12));
				var piglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, at, Vec3.ZERO).getFirst();
				// a block and a half off the blast's centre, where vanilla would push it hard; still, so any push shows
				piglin.setNoAi(true);
				piglin.teleportTo(at.getX() + 2.0, at.getY(), at.getZ() + 0.5);
				piglin.setDeltaMovement(Vec3.ZERO);
				var piglinAt = piglin.position();
				var player = StageTests.player(ctx);
				player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				var bomb = new MagmaBomb(ctx.getLevel(), null);
				bomb.setPos(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5);
				bomb.setDeltaMovement(0, -0.3, 0);
				ctx.getLevel().addFreshEntity(bomb);
				float playerBefore = player.getHealth();
				float piglinBefore = piglin.getHealth();
				ctx.runAfterDelay(25, () -> {
					ctx.assertTrue(player.getHealth() < playerBefore, "the blast didn't hurt the player, so the test proves nothing");
					ctx.assertTrue(piglin.getHealth() == piglinBefore, "the blast hurt a rift piglin");
					ctx.assertTrue(piglin.getDeltaMovement().horizontalDistance() < 0.01, "the blast pushed a rift piglin: " + piglin.getDeltaMovement());
					ctx.assertTrue(piglin.position().distanceTo(piglinAt) < 0.01, "the blast moved a rift piglin from " + piglinAt + " to " + piglin.position());
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("bomb/spares_things", ctx -> {
			// Rift loot on the ground and players' armour stands outlast a bombing run: no damage, no push.
			BombardierTests.stoneFloor(ctx);
			var at = ctx.absolutePos(new BlockPos(12, 1, 12));
			var player = StageTests.player(ctx);
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			var stand = ctx.spawn(EntityTypes.ARMOR_STAND, new BlockPos(13, 1, 12));
			stand.setDeltaMovement(Vec3.ZERO);
			var item = new ItemEntity(ctx.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 1.5, new ItemStack(Items.GOLD_INGOT));
			item.setDeltaMovement(Vec3.ZERO);
			ctx.getLevel().addFreshEntity(item);
			var bomb = new MagmaBomb(ctx.getLevel(), null);
			bomb.setPos(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5);
			bomb.setDeltaMovement(0, -0.3, 0);
			ctx.getLevel().addFreshEntity(bomb);
			float before = player.getHealth();
			ctx.runAtTickTime(25, () -> {
				ctx.assertTrue(player.getHealth() < before, "the blast didn't hurt the player, so the test proves nothing");
				ctx.assertTrue(stand.isAlive(), "the blast broke an armour stand");
				ctx.assertTrue(item.isAlive(), "the blast destroyed an item on the ground");
				ctx.assertTrue(stand.getDeltaMovement().horizontalDistance() < 0.01 && Math.abs(stand.getX() - (at.getX() + 1.5)) < 0.01,
						"the blast pushed an armour stand: " + stand.position());
				ctx.assertTrue(Math.abs(item.getZ() - (at.getZ() + 1.5)) < 0.01 && Math.abs(item.getX() - (at.getX() + 0.5)) < 0.01,
						"the blast pushed an item: " + item.position());
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bomb/warning_then_gone", ctx -> {
			var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 1, 12)));
			BurningPatches.warn(ctx.getLevel(), at, null);
			ctx.assertTrue(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> p.warning() && p.at().equals(at)), "no warning recorded");
			ctx.runAtTickTime(BurningPatches.WARN_TICKS + 2, () -> {
				ctx.assertFalse(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> p.at().equals(at)), "the warning outlived its 20 ticks");
				ctx.succeed();
			});
		});
	}
}
