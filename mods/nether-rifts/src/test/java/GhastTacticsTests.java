import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import nu.metacraft.nether_rifts.mob.GhastTactics;
import nu.metacraft.nether_rifts.mob.GhastTactics.Phase;
import nu.metacraft.nether_rifts.mob.MagmaBomb;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.mob.WarGhast.Crew;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A Chained Ghast fights a player (kept alive and in place) under the tactics_fast config: one attack at a time, its
 * phases in their order, a run's bombs capped, cooldowns kept; and an attack whose crew member is dead is skipped.
 */
public class GhastTacticsTests {

	/** Each phase and the ones it may go to. */
	private static final Map<Phase, EnumSet<Phase>> NEXT = Map.of(
			Phase.CRUISE, EnumSet.of(Phase.TELEGRAPH, Phase.VOLLEY),
			Phase.TELEGRAPH, EnumSet.of(Phase.BOMBING_RUN, Phase.REGROUP),
			Phase.BOMBING_RUN, EnumSet.of(Phase.CLIMB, Phase.REGROUP),
			Phase.CLIMB, EnumSet.of(Phase.VOLLEY, Phase.REGROUP),
			Phase.VOLLEY, EnumSet.of(Phase.REGROUP),
			Phase.REGROUP, EnumSet.of(Phase.CRUISE));

	/** Entities as they come into a world: tests listening for a ghast's shots. */
	private static final List<Consumer<Entity>> SPAWNS = new CopyOnWriteArrayList<>();

	/** A change of phase. */
	record Change(Phase from, Phase to, long at) {}

	/** What a ghast did in a fight: its changes of phase, and each shot with the phase it was shot in. */
	static final class Fight {
		final List<Change> changes = new CopyOnWriteArrayList<>();
		final List<String> wrong = new CopyOnWriteArrayList<>();
		final List<Integer> bombsPerRun = new CopyOnWriteArrayList<>();
		int bombs;
		int fireballs;
		int bolts;

		String log() {
			var out = new StringBuilder();
			for (var c : changes) {
				out.append(c.at()).append(' ').append(c.to()).append("; ");
			}
			return out.toString();
		}

		boolean saw(Phase phase) {
			return changes.stream().anyMatch(c -> c.to() == phase);
		}
	}

	static void registerTests() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> SPAWNS.forEach(listener -> listener.accept(entity)));

		NetherRiftsTests.register("ghast_tactics/one_at_a_time", ctx -> {
			fight(ctx, null, 720, (fight, g) -> {
				var tactics = NetherRiftsTests.TACTICS_FAST;
				ctx.assertTrue(fight.wrong.isEmpty(), String.join("; ", fight.wrong) + " | " + fight.log());
				for (var phase : Phase.values()) {
					ctx.assertTrue(fight.saw(phase) || phase == Phase.CRUISE, "never " + phase + ": " + fight.log());
				}
				ctx.assertTrue(fight.bombs > 0, "no bombs: " + fight.log());
				ctx.assertTrue(fight.fireballs > 0, "no fireballs: " + fight.log());
				for (int bombs : fight.bombsPerRun) {
					ctx.assertTrue(bombs <= tactics.maxBombsPerRun(), "a run dropped " + bombs + " bombs: " + fight.bombsPerRun);
				}
				// cooldowns: a run's from the end of the last, a volley's from the end of the last, and the global one
				// from cruising again to the next attack
				long runEnd = Long.MIN_VALUE / 2, volleyEnd = Long.MIN_VALUE / 2, cruising = Long.MIN_VALUE / 2;
				for (var c : fight.changes) {
					if (c.from() == Phase.BOMBING_RUN) {
						runEnd = c.at();
					}
					if (c.from() == Phase.VOLLEY) {
						volleyEnd = c.at();
					}
					if (c.to() == Phase.TELEGRAPH) {
						ctx.assertTrue(c.at() - runEnd >= NetherRiftsTests.RUN_COOLDOWN_FAST, "a run " + (c.at() - runEnd) + " ticks after the last: " + fight.log());
					}
					if (c.to() == Phase.VOLLEY) {
						ctx.assertTrue(c.at() - volleyEnd >= tactics.volleyCooldown(), "a volley " + (c.at() - volleyEnd) + " ticks after the last: " + fight.log());
					}
					if (c.from() == Phase.CRUISE && cruising > Long.MIN_VALUE / 2) {
						ctx.assertTrue(c.at() - cruising >= tactics.globalCooldown(), "an attack " + (c.at() - cruising) + " ticks after regrouping: " + fight.log());
					}
					if (c.to() == Phase.CRUISE) {
						cruising = c.at();
					}
				}
			});
		});
		NetherRiftsTests.register("ghast_tactics/no_grenadier_no_runs", ctx -> fight(ctx, Crew.BOMBARDIER, 400, (fight, ghast) -> {
			ctx.assertTrue(fight.wrong.isEmpty(), String.join("; ", fight.wrong) + " | " + fight.log());
			ctx.assertFalse(fight.saw(Phase.TELEGRAPH) || fight.saw(Phase.BOMBING_RUN), "a bombing run without the Grenadier: " + fight.log());
			ctx.assertTrue(fight.bombs == 0, fight.bombs + " bombs without the Grenadier");
			ctx.assertTrue(fight.saw(Phase.VOLLEY) && fight.fireballs > 0, "no volley in its place: " + fight.log());
		}));
		NetherRiftsTests.register("ghast_tactics/no_spear_no_volleys", ctx -> fight(ctx, Crew.SPEAR, 400, (fight, ghast) -> {
			ctx.assertTrue(fight.wrong.isEmpty(), String.join("; ", fight.wrong) + " | " + fight.log());
			ctx.assertFalse(fight.saw(Phase.VOLLEY), "a volley without the spear piglin: " + fight.log());
			ctx.assertTrue(fight.fireballs == 0, fight.fireballs + " fireballs without the spear piglin");
			ctx.assertTrue(fight.saw(Phase.BOMBING_RUN), "no run: " + fight.log());
		}));
	}

	/**
	 * A summoned Chained Ghast after a player on a stone floor for {@code ticks} ticks (the player kept alive and in
	 * place, and the ghast's target), {@code killed} killed on the deck first if given; then {@code check}.
	 */
	static WarGhast fight(GameTestHelper ctx, Crew killed, int ticks, java.util.function.BiConsumer<Fight, WarGhast> check) {
		BombardierTests.stoneFloor(ctx);
		var player = StageTests.player(ctx);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		// not made invulnerable: a mob drops a target that can't be seen as an enemy; healed every tick instead
		var spot = ctx.absolutePos(new BlockPos(12, 1, 18));
		player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(8, 9, 6));
		var fight = new Fight();
		int[] runBombs = {0};
		// with a crew member to kill, the fight is watched from his death (the ghast may start an attack before it)
		boolean[] watching = {killed == null};
		GhastTactics.PhaseListener phases = (g, from, to, at) -> {
			if (g != ghast || !watching[0]) {
				return;
			}
			if (!NEXT.get(from).contains(to)) {
				fight.wrong.add(from + " went to " + to + " at " + at);
			}
			if (from == Phase.BOMBING_RUN) {
				fight.bombsPerRun.add(runBombs[0]);
				runBombs[0] = 0;
			}
			fight.changes.add(new Change(from, to, at));
		};
		Consumer<Entity> shots = entity -> {
			if (!watching[0]) {
				return;
			}
			var phase = ghast.tactics().phase();
			if (entity instanceof LargeFireball fireball && fireball.getOwner() == ghast) {
				fight.fireballs++;
				if (phase != Phase.VOLLEY) {
					fight.wrong.add("a fireball in " + phase + " at " + ctx.getLevel().getGameTime());
				}
			} else if (entity instanceof MagmaBomb bomb && bomb.getOwner() == ghast) {
				fight.bombs++;
				runBombs[0]++;
				if (phase != Phase.BOMBING_RUN) {
					fight.wrong.add("a bomb in " + phase + " at " + ctx.getLevel().getGameTime());
				}
			} else if (entity instanceof AbstractArrow bolt && bolt.getOwner() != null && bolt.getOwner() == ghast.hitbox(Crew.CROSSBOW)) {
				fight.bolts++;
				if (phase != Phase.CRUISE && phase != Phase.REGROUP) {
					fight.wrong.add("a bolt in " + phase + " at " + ctx.getLevel().getGameTime());
				}
			}
		};
		GhastTactics.PHASE_LISTENERS.add(phases);
		SPAWNS.add(shots);
		ctx.onEachTick(() -> {
			if (killed != null && ghast.hitbox(killed) != null) {
				ghast.hitbox(killed).kill(ctx.getLevel());
			}
			if (killed != null && !ghast.hasCrew(killed)) {
				watching[0] = true;
			}
			if (ghast.isAlive() && watching[0]) {
				ghast.setTarget(player);
			}
			keep(player, spot);
		});
		ctx.runAtTickTime(ticks, () -> {
			GhastTactics.PHASE_LISTENERS.remove(phases);
			SPAWNS.remove(shots);
			check.accept(fight, ghast);
			StageTests.remove(ctx, player);
			ctx.killAllEntities();
			ctx.succeed();
		});
		return ghast;
	}

	/** Alive, unburnt and where it stood. */
	private static void keep(ServerPlayer player, BlockPos spot) {
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		if (player.blockPosition().distManhattan(spot) > 1) {
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}
	}

	private GhastTacticsTests() {}
}
