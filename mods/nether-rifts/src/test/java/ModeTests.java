import net.minecraft.gametest.framework.GameTestHelper;
import nu.metacraft.nether_rifts.RiftWorldState;
import nu.metacraft.nether_rifts.Stabilisation;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

public class ModeTests {

	static void registerTests() {
		NetherRiftsTests.register("mode/default_on", ctx -> {
			ctx.assertTrue(RiftWorldState.get(ctx.getLevel().getServer()).riftMode(), "rift mode is off in a new world");
			ctx.succeed();
		});
		NetherRiftsTests.register("mode/stabilise_toggles", ctx -> {
			var server = ctx.getLevel().getServer();
			Stabilisation.stabilise(server);
			ctx.assertFalse(RiftWorldState.get(server).riftMode(), "still in rift mode after stabilise");
			ctx.assertFalse(netherBlocked(ctx, PortalState.BlockingType.ACTIVATION), "nether lighting still blocked");
			ctx.assertFalse(netherBlocked(ctx, PortalState.BlockingType.TRAVEL), "nether travel still blocked");
			Stabilisation.destabilise(server);
			ctx.assertTrue(RiftWorldState.get(server).riftMode(), "rift mode off after destabilise");
			ctx.assertTrue(netherBlocked(ctx, PortalState.BlockingType.ACTIVATION), "nether lighting allowed after destabilise");
			ctx.assertTrue(netherBlocked(ctx, PortalState.BlockingType.TRAVEL), "nether travel allowed after destabilise");
			ctx.succeed();
		});
	}

	static boolean netherBlocked(GameTestHelper ctx, PortalState.BlockingType type) {
		return PortalBlockerSettings.getInstance(ctx.getLevel().getServer()).isPortalBlockedGlobally(PortalTypeRegistry.NETHER, type);
	}
}
