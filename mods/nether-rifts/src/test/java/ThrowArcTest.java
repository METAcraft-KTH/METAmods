import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.mob.GhastBombardier;
import se.metacraft.nether_rifts.mob.MagmaBomb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Ghast Bombardier's hand-thrown bombs: the arc lands where it was aimed, when it was meant to, and no sooner. */
public class ThrowArcTest {

	/** Loading the bomb's class loads the entity classes. */
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static final Vec3 HAND = new Vec3(0.5, 65.6, 0.5);

	/** Level, down a step, up a ledge, far down, short and long; one sideways. */
	static final List<Vec3> GROUNDS = List.of(
			new Vec3(12.5, 64, 0.5), new Vec3(4.5, 63, 3.5), new Vec3(8.5, 66, 0.5), new Vec3(8.5, 54, -4.5), new Vec3(-4.5, 64, 0.5)
	);

	@Test
	public void landsOnTheGroundAfterTheTicksAskedFor() {
		for (var ground : GROUNDS) {
			for (int ticks : new int[] {10, 20, 33, 60}) {
				var path = MagmaBomb.path(HAND, MagmaBomb.throwSpeed(HAND, ground, ticks), ticks);
				assertEquals(ticks, path.size());
				assertTrue(path.getLast().distanceTo(ground) < 1e-6, "aimed at " + ground + " over " + ticks + " ticks, came down at " + path.getLast());
			}
		}
	}

	@Test
	public void staysAboveTheGroundTillItLands() {
		for (var ground : GROUNDS) {
			if (ground.y > HAND.y) {
				continue;
			}
			var path = MagmaBomb.path(HAND, MagmaBomb.throwSpeed(HAND, ground, 20), 20);
			for (int tick = 0; tick < path.size() - 1; tick++) {
				assertTrue(path.get(tick).y > ground.y, "aimed at " + ground + ", it is down at tick " + (tick + 1));
			}
		}
	}

	/** Thrown straight up and down, the arc agrees with the deck's own drop arithmetic. */
	@Test
	public void agreesWithTicksToLand() {
		var ground = new Vec3(HAND.x, 60, HAND.z);
		for (int ticks : new int[] {10, 20, 40}) {
			var speed = MagmaBomb.throwSpeed(HAND, ground, ticks);
			int landed = MagmaBomb.ticksToLand(HAND.y - ground.y, speed.y);
			// the arc comes down exactly on the ground at its last tick; rounding may leave it a hair above till the next
			assertTrue(landed == ticks || landed == ticks + 1, "aimed at " + ticks + " ticks, ticksToLand says " + landed);
		}
	}

	/**
	 * Someone standing where it lands is no sooner in its way than the ground is: on its way down the bomb is no lower
	 * than their height over the ground before the warning time is up.
	 */
	@Test
	public void clearsAStandingPlayerForTheWarningTime() {
		double height = 1.8;
		for (var ground : GROUNDS) {
			for (int warn : new int[] {GhastBombardier.flightTicks(0), GhastBombardier.flightTicks(20), GhastBombardier.flightTicks(40)}) {
				int ticks = MagmaBomb.lobTicks(HAND, ground, height, warn);
				assertTrue(ticks >= warn, "a " + ticks + "-tick flight for a " + warn + "-tick warning");
				var path = MagmaBomb.path(HAND, MagmaBomb.throwSpeed(HAND, ground, ticks), ticks);
				double before = HAND.y;
				for (int tick = 1; tick < warn; tick++) {
					double y = path.get(tick - 1).y;
					assertTrue(y >= before || y > ground.y + height,
							"aimed at " + ground + " for a " + warn + "-tick warning, it is down at head height at tick " + tick);
					before = y;
				}
			}
		}
	}

	@Test
	public void flightIsTheWarningTimeOrAMinimum() {
		assertEquals(20, GhastBombardier.flightTicks(20));
		assertEquals(GhastBombardier.MIN_FLIGHT, GhastBombardier.flightTicks(0));
	}
}
