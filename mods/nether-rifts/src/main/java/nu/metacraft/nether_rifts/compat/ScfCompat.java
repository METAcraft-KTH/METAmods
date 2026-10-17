package nu.metacraft.nether_rifts.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.RiftWorldState;
import nu.metacraft.nether_rifts.rift.RiftForming;
import nu.metacraft.nether_rifts.rift.RiftShape;
import nu.metacraft.simplecustomfeatures.compat.PortalBlockerCompat;
import nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal.DynamicPortalEvents;

/** simple-custom-features dynamic portals listed in scf_nether_portal_types open rifts like nether portals do. */
public final class ScfCompat {

	private ScfCompat() {}

	public static void init() {
		DynamicPortalEvents.CLAIM.register((level, portal, axis, positions) -> {
			var id = BuiltInRegistries.BLOCK.getKey(portal);
			if (!NetherRiftsConfig.getInstance().scfNetherPortalTypes().contains(id)) {
				return null;
			}
			// SCF allows shapes up to 32 across; a rift may not be that big, and RiftShape.of doesn't check.
			if (positions.size() < RiftShape.MIN_AREA || span(positions, BlockPos::getY) > RiftShape.MAX_SIDE
					|| span(positions, pos -> pos.get(axis)) > RiftShape.MAX_SIDE) {
				return null;
			}
			boolean blocked = PortalBlockerCompat.isActivationBlocked(portal, level.getServer(), level.dimension(), positions);
			RiftShape.FrameTest frame = (l, pos) -> portal.isFrameBlock(new BlockInWorld(l, pos, true));
			// Claim only what RiftForming will really turn into a rift, so a claim never swallows a portal.
			// The shape search is a dry run; the rift opens when SCF would have lit the portal.
			if (!blocked || level.dimension() != Level.OVERWORLD || !RiftWorldState.get(level.getServer()).riftMode()
					|| RiftShape.of(level, positions, axis, frame).isEmpty()) {
				return null;
			}
			return () -> RiftForming.tryForm(level, positions, axis, frame, true);
		});
	}

	private static int span(Iterable<BlockPos> positions, java.util.function.ToIntFunction<BlockPos> coordinate) {
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for (var pos : positions) {
			int c = coordinate.applyAsInt(pos);
			min = Math.min(min, c);
			max = Math.max(max, c);
		}
		return max - min + 1;
	}
}
