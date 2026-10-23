package se.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class DynamicPortalEvents {

	/**
	 * Asked once a dynamic portal's shape is found, before it's checked against portal-blocker and lit.
	 * A listener that returns an action takes the portal over: no portal blocks are placed, and the action
	 * runs instead when the portal would have lit. (nether-rifts uses this to open a rift.)
	 */
	public static final Event<Claim> CLAIM = EventFactory.createArrayBacked(Claim.class, listeners -> (level, portal, axis, positions) -> {
		for (var listener : listeners) {
			var action = listener.claim(level, portal, axis, positions);
			if (action != null) {
				return action;
			}
		}
		return null;
	});

	private DynamicPortalEvents() {}

	@FunctionalInterface
	public interface Claim {
		@Nullable
		Runnable claim(ServerLevel level, DynamicPortalBlock portal, Direction.Axis axis, Set<BlockPos> positions);
	}
}
