package metacraft.ovvar.sewing;

import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Spot;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Function;

/**
 * Where a look ray meets an armour stand's clothes, in cells. The stand's armour is the humanoid
 * model posed like the stand, so the ray is taken into model space (the game draws a model
 * flipped in x and y, translated 1.501 up and turned 180° − yaw) and then into each part's own
 * frame (its pivot and pose rotation, {@code rotationZYX} like {@code ModelPart}), where the
 * part is an axis-aligned box. The entered face and the point on it map to the texture strip the
 * way the model unwraps a box: the −x face, then the front (−z), the +x face, the back (+z), with
 * u running back→front, right→left, front→back, left→right. Mirrored (left) limbs are handled
 * by mirroring x into right-limb terms, which is what their cells are defined in.
 *
 * {@link #cell} is the forward mapping, a cell's centre and outward normal on the posed stand,
 * written from the same conventions but independently, so the game tests can check one against
 * the other.
 */
public final class StandAim {
	private StandAim() {}

	/** The armour model's parts: pivot and undilated box in model sixteenths, the armour's inflation, and the pose. */
	record Part(String name, Piece piece, Spot.Side side, Vector3f pivot, float x1, float x2, float y1, float y2, float z1, float z2,
				float inflate, int strip, int faceWidth, Function<ArmorStand, Rotations> pose) {
		boolean mirrored() {
			return side == Spot.Side.LEFT;
		}

		Quaternionf rotation(ArmorStand stand) {
			Rotations r = pose.apply(stand);
			float d = (float) (Math.PI / 180);
			return new Quaternionf().rotationZYX(r.z() * d, r.y() * d, r.x() * d);
		}
	}

	static final List<Part> PARTS = List.of(
			new Part("body", Piece.TOP, Spot.Side.BODY, new Vector3f(0, 0, 0), -4, 4, 0, 12, -2, 2, 1.0f, 16, 8, ArmorStand::getBodyPose),
			new Part("right arm", Piece.TOP, Spot.Side.RIGHT, new Vector3f(-5, 2, 0), -3, 1, -2, 10, -2, 2, 1.0f, 40, 4, ArmorStand::getRightArmPose),
			new Part("left arm", Piece.TOP, Spot.Side.LEFT, new Vector3f(5, 2, 0), -1, 3, -2, 10, -2, 2, 1.0f, 40, 4, ArmorStand::getLeftArmPose),
			new Part("right leg", Piece.BOTTOM, Spot.Side.RIGHT, new Vector3f(-1.9f, 12, 0), -2, 2, 0, 12, -2, 2, 0.5f, 0, 4, ArmorStand::getRightLegPose),
			new Part("left leg", Piece.BOTTOM, Spot.Side.LEFT, new Vector3f(1.9f, 12, 0), -2, 2, 0, 12, -2, 2, 0.5f, 0, 4, ArmorStand::getLeftLegPose));

	/** @param spot null when the aimed face carries no cell (a box top or bottom) */
	public record Hit(String part, Spot spot, Vec3 where) {}

	/**
	 * The cell under a look ray, or null if it misses the clothes.
	 *
	 * @param far the far face of the first part hit instead of the near one (sneaking): the inner
	 *			side of an arm or leg, the back of the body
	 */
	public static Hit aim(Vec3 eye, Vec3 view, ArmorStand stand, boolean far, double reach) {
		Vector3f origin = toModel(stand, eye), dir = toModelDir(stand, view);
		Part best = null;
		double bestT = reach;
		int bestAxis = -1;
		double bestSign = 0;
		Vector3f bestLocal = null;
		for (Part part : PARTS) {
			Quaternionf inverse = part.rotation(stand).conjugate();
			Vector3f o = new Vector3f(origin).sub(part.pivot).rotate(inverse);
			Vector3f d = new Vector3f(dir).rotate(inverse);
			double[] hit = slab(o, d, part, far);
			if (hit == null || hit[0] >= bestT) continue;
			bestT = hit[0];
			best = part;
			bestAxis = (int) hit[1];
			bestSign = hit[2];
			bestLocal = new Vector3f(o).add(new Vector3f(d).mul((float) hit[0]));
		}
		if (best == null) return null;
		Vec3 where = eye.add(view.scale(bestT));
		if (bestAxis == 1) return new Hit(best.name, null, where);   // top or bottom of a box
		return new Hit(best.name, cellAt(best, bestLocal, bestAxis, bestSign), where);
	}

	/** The cell a local point on a face belongs to (the nearest one in that column). */
	private static Spot cellAt(Part part, Vector3f local, int axis, double sign) {
		double lx = part.mirrored() ? -local.x : local.x, lz = local.z;
		double x1 = part.mirrored() ? -part.x2 : part.x1, x2 = part.mirrored() ? -part.x1 : part.x2;
		if (part.mirrored() && axis == 0) sign = -sign;   // only the left/right axis mirrors
		double along;
		int strip;
		if (axis == 0 && sign < 0) { strip = part.strip; along = part.z2 - lz; }					   // outer / right side: back → front
		else if (axis == 2 && sign < 0) { strip = part.strip + 4; along = lx - x1; }					// front: right → left
		else if (axis == 0) { strip = part.strip + 4 + part.faceWidth; along = lz - part.z1; }		  // inner / left side: front → back
		else { strip = part.strip + 8 + part.faceWidth; along = x2 - lx; }							  // back: left → right
		int col = (int) Math.max(0, Math.min(part.faceWidth / 4 - 1, Math.floor(along / 4)));
		double v = 20 + Math.max(0, Math.min(11.999, local.y - part.y1));
		return Spot.nearest(part.piece, strip + col * 4, v, part.side);
	}

	/** Ray/box slab test in a part's frame: [t, axis, sign of the face's outward normal] for the entered (or, far, exited) face. */
	private static double[] slab(Vector3f o, Vector3f d, Part p, boolean far) {
		double[] min = {p.x1 - p.inflate, p.y1 - p.inflate, p.z1 - p.inflate}, max = {p.x2 + p.inflate, p.y2 + p.inflate, p.z2 + p.inflate};
		double[] origin = {o.x, o.y, o.z}, dir = {d.x, d.y, d.z};
		double tNear = 0, tFar = Double.MAX_VALUE;
		int nearAxis = -1, farAxis = -1;
		double nearSign = 0, farSign = 0;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(dir[i]) < 1e-9) {
				if (origin[i] < min[i] || origin[i] > max[i]) return null;
				continue;
			}
			double t1 = (min[i] - origin[i]) / dir[i], t2 = (max[i] - origin[i]) / dir[i];
			double enterSign = -1, exitSign = 1;
			if (t1 > t2) { double t = t1; t1 = t2; t2 = t; enterSign = 1; exitSign = -1; }
			if (t1 > tNear) { tNear = t1; nearAxis = i; nearSign = enterSign; }
			if (t2 < tFar) { tFar = t2; farAxis = i; farSign = exitSign; }
			if (tNear > tFar) return null;
		}
		if (far) return farAxis < 0 ? null : new double[]{tFar, farAxis, farSign};   // from inside too
		return nearAxis < 0 ? null : new double[]{tNear, nearAxis, nearSign};
	}

	// ------------------------------------------------------------ the forward mapping

	/** A cell's centre on the posed stand, the face's outward normal and its up (the art's), in world space. */
	public record CellPoint(Vec3 centre, Vec3 normal, Vec3 up) {}

	public static CellPoint cell(ArmorStand stand, Spot spot) {
		Part part = PARTS.stream().filter(p -> p.piece == spot.piece && p.side == (spot == Spot.SEAT ? Spot.Side.RIGHT : spot.side)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("no part for " + spot));
		int u = spot.u - part.strip;
		int face = u < 4 ? 0 : u < 4 + part.faceWidth ? 1 : u < 8 + part.faceWidth ? 2 : 3;   // −x, front, +x, back
		double along = (u - new int[]{0, 4, 4 + part.faceWidth, 8 + part.faceWidth}[face]) + 2;   // centre of the 4-wide column
		double y = part.y1 + (spot.v - 20) + 2;
		double x1 = part.mirrored() ? -part.x2 : part.x1, x2 = part.mirrored() ? -part.x1 : part.x2;
		double lx, lz;
		Vector3f normal;
		switch (face) {
			case 0 -> { lx = x1 - part.inflate; lz = part.z2 - along; normal = new Vector3f(-1, 0, 0); }
			case 1 -> { lx = x1 + along; lz = part.z1 - part.inflate; normal = new Vector3f(0, 0, -1); }
			case 2 -> { lx = x2 + part.inflate; lz = part.z1 + along; normal = new Vector3f(1, 0, 0); }
			default -> { lx = x2 - along; lz = part.z2 + part.inflate; normal = new Vector3f(0, 0, 1); }
		}
		if (part.mirrored()) { lx = -lx; normal.x = -normal.x; }   // back from right-limb terms
		Quaternionf rotation = part.rotation(stand);
		Vector3f local = new Vector3f((float) lx, (float) y, (float) lz).rotate(rotation).add(part.pivot);
		Vector3f n = normal.rotate(rotation);
		Vector3f up = new Vector3f(0, -1, 0).rotate(rotation);   // texture v runs down the part; the model is drawn y-flipped
		return new CellPoint(toWorld(stand, local), toWorldDir(stand, n), toWorldDir(stand, up));
	}

	// ------------------------------------------------------------ model space

	private static float bodyYaw(ArmorStand stand) {
		return stand.yBodyRot;
	}

	/** World → model sixteenths: the inverse of translate(pos), rotate(180 − yaw), scale(−1, −1, 1), translate(0, −1.501, 0), /16. */
	static Vector3f toModel(ArmorStand stand, Vec3 world) {
		Vector3f v = new Vector3f((float) (world.x - stand.getX()), (float) (world.y - stand.getY()), (float) (world.z - stand.getZ()));
		v.rotateY((float) Math.toRadians(-(180 - bodyYaw(stand))));
		v.mul(-1, -1, 1);
		v.add(0, 1.501f, 0);
		return v.mul(16);
	}

	static Vector3f toModelDir(ArmorStand stand, Vec3 dir) {
		Vector3f v = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
		v.rotateY((float) Math.toRadians(-(180 - bodyYaw(stand))));
		v.mul(-1, -1, 1);
		return v.mul(16);
	}

	static Vec3 toWorld(ArmorStand stand, Vector3f model) {
		Vector3f v = new Vector3f(model).mul(1 / 16f).add(0, -1.501f, 0).mul(-1, -1, 1);
		v.rotateY((float) Math.toRadians(180 - bodyYaw(stand)));
		return new Vec3(stand.getX() + v.x, stand.getY() + v.y, stand.getZ() + v.z);
	}

	static Vec3 toWorldDir(ArmorStand stand, Vector3f dir) {
		Vector3f v = new Vector3f(dir).mul(-1, -1, 1);
		v.rotateY((float) Math.toRadians(180 - bodyYaw(stand)));
		return new Vec3(v.x, v.y, v.z).normalize();
	}
}
