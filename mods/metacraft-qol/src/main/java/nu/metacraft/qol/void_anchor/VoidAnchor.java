package nu.metacraft.qol.void_anchor;

import nu.metacraft.qol.void_anchor.rift.RiftTracker;

/**
 * The void anchor: a respawn anchor for the End void. A bound player who falls below the End
 * gets a rift beneath them and comes out beside their anchor, which loses one charge.
 */
public final class VoidAnchor {

	private VoidAnchor() {}

	public static void init() {
		VoidAnchorBlocks.init();
		VoidAnchorItems.init();
		AnchorBinding.init();
		VoidAnchorCommand.init();
		RiftTracker.init();
	}

}
