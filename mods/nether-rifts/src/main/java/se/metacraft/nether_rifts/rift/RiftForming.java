package se.metacraft.nether_rifts.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import se.metacraft.nether_rifts.RiftMessages;
import se.metacraft.nether_rifts.RiftWorldState;

import java.util.Collection;

/** A nether portal that portal-blocker wouldn't let light, in the overworld, while rift mode is on, opens a rift instead. */
public final class RiftForming {

	private RiftForming() {}

	/** True if a rift took the portal's place; false leaves the portal to vanilla and portal-blocker. */
	public static boolean tryForm(ServerLevel level, Collection<BlockPos> positions, Direction.Axis axis,
			RiftShape.FrameTest frame, boolean activationBlocked) {
		if (!activationBlocked || level.dimension() != Level.OVERWORLD || !RiftWorldState.get(level.getServer()).riftMode()) {
			return false;
		}
		var shape = RiftShape.of(level, positions, axis, frame);
		if (shape.isEmpty()) {
			return false;
		}
		var rift = RiftData.get(level).open(level, shape.get(), false);
		RiftMessages.warn(level, rift);
		return true;
	}
}
