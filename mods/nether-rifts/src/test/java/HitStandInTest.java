import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;
import nu.metacraft.nether_rifts.mob.CrewHitbox;
import nu.metacraft.nether_rifts.mob.HitStandIn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A hit stand-in is as tall as the box it stands in for. */
public class HitStandInTest {

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void asTallAsTheBox() {
		// the drummer's box is a piglin's, and the Chained Ghast's a ghast's
		assertEquals(1f, HitStandIn.scale(EntityTypes.PIGLIN, 1.95f), 1e-6);
		assertEquals(1f, HitStandIn.scale(EntityTypes.GHAST, 4f), 1e-6);
		// a crew member's roomy box
		assertEquals((float) CrewHitbox.HEIGHT, HitStandIn.scale(EntityTypes.PIGLIN, (float) CrewHitbox.HEIGHT) * EntityTypes.PIGLIN.getHeight(), 1e-5);
	}
}
