package nu.metacraft.rivals.paint;

import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.rivals.PaintColor;

/**
 * A paint block of either kind: its colour, and which faces of the cell carry paint. A cell painted on
 * one face is a {@link ConnectedPaintBlock}; a cell painted on more than one is the {@link PaintBlock}
 * splat fallback. Everything that only wants to know whether a cell is painted, in what colour and on
 * which side asks through here and never has to care which of the two it found.
 */
public interface Paint {
	PaintColor color();

	/** Bit i set = paint on the {@code Direction.values()[i]} side of the cell (the attach direction). */
	int faceMask(BlockState state);
}
