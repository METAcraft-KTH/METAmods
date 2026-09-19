package nu.metacraft.rivals.paint;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.rivals.PaintColor;

/**
 * Paint of one colour on more than one face of a cell: the corner fallback behind
 * {@link ConnectedPaintBlock} (spec §3). A vanilla multiface block on the server (six independent face
 * flags, no collision, faces drop off when their support goes), mapped to a client state through
 * {@link PaintStates#splat}. One block per cell, so one colour per cell.
 *
 * <p>It carries no connection bits: a corner is where the sheet ends anyway, so the client shows the
 * plain splat for these and keeps the bit-carrying states for the single-face cells.
 *
 * <p>Paint blocks only ever attach to full faces — {@link Painter} sends every other shape to
 * {@link PaintDisplays} quads — so vanilla's own survival rule is exactly the rule paint wants.
 */
public final class PaintBlock extends MultifaceBlock implements PolymerBlock, Paint {
	private final PaintColor color;

	public PaintBlock(Properties properties, PaintColor color) {
		super(properties);
		this.color = color;
	}

	@Override
	public PaintColor color() {
		return color;
	}

	@Override
	public int faceMask(BlockState state) {
		int mask = 0;
		for (Direction d : DIRECTIONS) {
			if (state.getValue(getFaceProperty(d))) mask |= 1 << d.ordinal();
		}
		return mask;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		int mask = faceMask(state);
		// The faceless state has no client state to stand for — vanilla drops such a block the moment
		// its last face goes, so it never reaches a player — but Polymer may still ask about it.
		return mask == 0 ? PaintStates.connected(color, Direction.DOWN, 0) : PaintStates.splat(color, mask);
	}
}
