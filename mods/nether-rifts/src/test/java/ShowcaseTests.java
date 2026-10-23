import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.mob.GhastBombardier;
import se.metacraft.nether_rifts.mob.GrenadierSword;
import se.metacraft.nether_rifts.mob.RiftEntities;
import se.metacraft.nether_rifts.mob.RiftModels;
import se.metacraft.nether_rifts.mob.Showcase;
import se.metacraft.nether_rifts.mob.WarDrummer;
import se.metacraft.nether_rifts.mob.WarGhast;
import se.metacraft.nether_rifts.raid.RiftMobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** {@code /rifts showcase}: the set it lays out, still, silent and unhurt, and clearing it away. */
public class ShowcaseTests {

	static void registerTests() {
		NetherRiftsTests.register("showcase/lays_out_and_clears", ctx -> {
			BombardierTests.stoneFloor(ctx);
			var player = StageTests.player(ctx);
			// on the room's centre line: the five ghasts, 5 apart and 4 wide, fill its 24 blocks exactly
			var origin = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 1, 1))).add(-0.5, 0, 0);
			var server = ctx.getLevel().getServer();
			var source = server.createCommandSourceStack().withLevel(ctx.getLevel()).withPosition(origin).withRotation(new Vec2(0, 0));
			// twice: the second clears the first
			server.getCommands().performPrefixedCommand(source, "rifts showcase");
			server.getCommands().performPrefixedCommand(source, "rifts showcase");
			var mobs = Showcase.live();
			int animations = RiftModels.WAR_DRUMMER.animations().size();
			ctx.assertTrue(count(mobs, WarDrummer.class) == animations + 9, "drummers: " + count(mobs, WarDrummer.class));
			ctx.assertTrue(count(mobs, WarGhast.class) == 5, "ghasts: " + count(mobs, WarGhast.class));
			ctx.assertTrue(count(mobs, GhastBombardier.class) == 3, "grenadiers: " + count(mobs, GhastBombardier.class));
			ctx.assertTrue(mobs.stream().filter(m -> m.getType() == EntityTypes.PIGLIN).count() == 1, "no vanilla piglin");
			ctx.assertTrue(mobs.size() == animations + 18, "the showcase has " + mobs.size() + " mobs (run twice)");
			for (var animation : RiftModels.WAR_DRUMMER.animations().keySet()) {
				ctx.assertTrue(mobs.stream().anyMatch(m -> animation.equals(Showcase.animation(m))), "no drummer for " + animation);
			}
			for (var mob : mobs) {
				ctx.assertTrue(mob.isNoAi() && mob.isSilent() && mob.isPermanentlyInvulnerable() && mob.isPersistenceRequired()
						&& mob.isCustomNameVisible() && !RiftMobs.isRiftMob(mob), mob + " isn't a still, silent, unhurt showcase mob of no rift");
				ctx.assertTrue(ctx.getBounds().contains(mob.position()), mob + " is out of the room at " + mob.position());
			}
			var ghasts = mobs.stream().filter(m -> m instanceof WarGhast).map(m -> (WarGhast) m).toList();
			for (var animation : List.of(WarGhast.IDLE, WarGhast.SHOOT, WarGhast.DIVE, WarGhast.CLIMB, WarGhast.DROP_BOMB)) {
				ctx.assertTrue(ghasts.stream().filter(g -> animation.equals(Showcase.animation(g))).count() == 1, "not one ghast for " + animation);
			}
			// one Grenadier with his sword, one per stage of a throw, held there
			var grenadiers = mobs.stream().filter(m -> m instanceof GhastBombardier).toList();
			for (var stage : GrenadierSword.Throw.values()) {
				ctx.assertTrue(grenadiers.stream().filter(g -> Showcase.throwStage(g) == stage
						&& g.getMainHandItem().get(GrenadierSword.THROW) == stage).count() == 1, "not one Grenadier held at " + stage);
			}
			ctx.assertTrue(grenadiers.stream().filter(g -> Showcase.throwStage(g) == null && !g.getMainHandItem().has(GrenadierSword.THROW)).count() == 1,
					"not one Grenadier just holding his sword");
			ctx.assertTrue(ghasts.stream().allMatch(g -> g.fitsAt(g.position())), "a ghast's deck is in the ground: "
					+ ghasts.stream().filter(g -> !g.fitsAt(g.position())).map(g -> g.getCustomName().getString() + " at " + g.position()).toList());

			Map<Mob, Vec3> stood = new HashMap<>();
			mobs.forEach(m -> stood.put(m, m.position()));
			var bigBeat = mobs.stream().filter(m -> "big_beat".equals(Showcase.animation(m))).map(m -> (WarDrummer) m).findFirst().orElseThrow();
			var ended = new AtomicBoolean();
			var again = new AtomicBoolean();
			ctx.onEachTick(() -> {
				boolean playing = bigBeat.getHolder().getAnimator().isPlaying("big_beat");
				if (!playing) {
					ended.set(true);
				} else if (ended.get()) {
					again.set(true);
				}
			});
			ctx.runAtTickTime(5, () -> {
				for (var mob : mobs) {
					var animation = Showcase.animation(mob);
					if (animation != null && !animation.equals("big_beat")) {
						var animator = mob instanceof WarDrummer drummer ? drummer.getHolder().getAnimator() : ((WarGhast) mob).getHolder().getAnimator();
						ctx.assertTrue(animator.isPlaying(animation), mob.getCustomName().getString() + " doesn't play it");
					}
					mob.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 10f);
					ctx.assertTrue(mob.getHealth() == mob.getMaxHealth(), mob + " was hurt");
				}
				for (var ghast : ghasts) {
					for (var crew : WarGhast.Crew.values()) {
						var hitbox = ghast.hitbox(crew);
						hitbox.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 10f);
						ctx.assertTrue(hitbox.getHealth() == hitbox.getMaxHealth(), "a showcase ghast's " + crew.figure + " was hurt");
					}
				}
			});
			ctx.runAtTickTime(150, () -> {
				for (var mob : mobs) {
					ctx.assertTrue(mob.isAlive() && mob.position().distanceTo(stood.get(mob)) < 0.01, mob + " moved from " + stood.get(mob) + " to " + mob.position());
				}
				ctx.assertTrue(again.get(), "the big beat didn't play again");
				ctx.assertTrue(ctx.getEntities(EntityTypes.ARROW).isEmpty() && ctx.getEntities(RiftEntities.MAGMA_BOMB).isEmpty(), "the showcase shot at the player");
				server.getCommands().performPrefixedCommand(source, "rifts showcase clear");
				ctx.assertTrue(Showcase.live().isEmpty() && mobs.stream().allMatch(Mob::isRemoved), "the showcase is still there");
				ctx.runAfterDelay(2, () -> {
					ctx.assertTrue(ctx.getEntities(RiftEntities.CREW_HITBOX).isEmpty() && ctx.getEntities(RiftEntities.DECK_HITBOX).isEmpty(),
							"the showcase ghasts' hitboxes are still there");
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
					ctx.succeed();
				});
			});
		});
	}

	private static long count(java.util.Collection<Mob> mobs, Class<?> type) {
		return mobs.stream().filter(type::isInstance).count();
	}
}
