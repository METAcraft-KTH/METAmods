package nu.metacraft.lib.util;

public class PositionFinder {

	public record XZ(double x, double z) {}
	public static XZ findPosAroundOrigin(long index, double squareSize) {
		long gridSize = (int) Math.ceil(Math.sqrt(index+1));
		if (gridSize % 2 == 1) {
			gridSize++;
		}
		double radius = (gridSize/2.0) * squareSize;
		long slots = gridSize*4 - 4;
		long ringPos = index % slots;

		long quadrantSlots = slots / 4;
		int quadrant = (int) (ringPos / quadrantSlots);
		long posInQuadrant = ringPos % quadrantSlots;
		double factor = (posInQuadrant + 1.0) / (quadrantSlots + 1.0);
		double x = radius;
		double z = radius;
		if (factor < 0.5) {
			factor *= 2;
			z *= factor;
		} else if (factor > 0.5) {
			factor = (factor - 0.5) * 2;
			x *= factor;
		}
		switch (quadrant) {
			case 0 -> {
				x -= 0.5 * squareSize;
				z -= 0.5 * squareSize;
			}
			case 1 -> {
				x = -x + 0.5 * squareSize;
				z -= 0.5 * squareSize;
			}
			case 2 -> {
				x = -x + 0.5 * squareSize;
				z = -z + 0.5 * squareSize;
			}
			case 3 -> {
				x -= 0.5 * squareSize;
				z = -z + 0.5 * squareSize;
			}
		}
		return new XZ(x, z);
	}

}
