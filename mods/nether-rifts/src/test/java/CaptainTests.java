import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import se.metacraft.nether_rifts.raid.RiftMobs;
import se.metacraft.nether_rifts.raid.RiftRaid;
import se.metacraft.nether_rifts.rift.Stage;

public class CaptainTests {

	static void registerTests() {
		NetherRiftsTests.register("captain/has_its_own_bar", ctx -> {
			var inside = ShapeTests.frame(ctx, 10, 10, Blocks.OBSIDIAN);
			ctx.setBlock(inside, Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var player = RaidTests.participant(ctx);
				player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				rift.tear(ctx.getLevel());
				// raiding, as the torn rift's countdown leaves it; still torn, the countdown would start wave 1 on top
				rift.setStage(ctx.getLevel(), Stage.RAID);
				RiftRaid.startWave(ctx.getLevel(), rift, 7);
				var captain = (LivingEntity) RiftRaid.aliveMobs(ctx.getLevel(), rift).stream()
						.filter(e -> e.entityTags().contains(RiftMobs.CAPTAIN_TAG)).findFirst()
						.orElseThrow(() -> new AssertionError("no tagged Captain in Large wave 7"));
				// the rest of the wave goes: a trooper's spear charge or a ghast's fireball would move the Captain's health
				RiftRaid.aliveMobs(ctx.getLevel(), rift).stream().filter(e -> e != captain).forEach(e -> e.discard());
				ctx.runAfterDelay(25, () -> {
					ctx.assertTrue(rift.wave() == 7, "wave 7 gave way to wave " + rift.wave());
					var bar = rift.captainBar();
					ctx.assertTrue(bar != null && bar.getPlayers().contains(player), "the participant can't see the Captain's bar");
					captain.setHealth(captain.getMaxHealth() / 2);
					ctx.runAfterDelay(2, () -> {
						ctx.assertTrue(Math.abs(rift.captainBar().getProgress() - 0.5f) < 0.05f, "the bar shows " + rift.captainBar().getProgress());
						captain.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 1000f);
						ctx.runAfterDelay(2, () -> {
							ctx.assertTrue(rift.captainBar() == null || rift.captainBar().getPlayers().isEmpty(), "the Captain's bar outlived the Captain");
							StageTests.remove(ctx, player);
							ctx.killAllEntities();
							FormingTests.cleanUp(ctx);
							ctx.succeed();
						});
					});
				});
			});
		});
	}
}
