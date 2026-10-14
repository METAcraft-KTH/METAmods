package metacraft.ovvar.pack;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import net.minecraft.resources.Identifier;

/**
 * The trim channel: an item can wear one armour trim, and a trim is just a texture, so datagen
 * makes one trim pattern per (body cell, patch) — drawn by vanilla, no bits, no pack
 * build — and the top's trim carries one more instant patch.
 *
 * <p><b>The channel is the body's own faces, and it cannot be anything else.</b> Two reasons, and
 * the second is the binding one:
 * <ul>
 *   <li>Vanilla cannot squeeze to square pixels the way the shader does, only datagen can, texel by
 *	   texel; on the chest and back faces (16 art pixels wide) that costs about one column in
 *	   sixteen, tolerable until the pack catches up, where on a sleeve it cost two in eight.
 *   <li>A trim texture carries none of our marker texels, because vanilla draws it and our shader
 *	   must leave it alone — so there is no {@code side} for {@code ovvar.glsl} to hide it on the
 *	   other limb by, and no mirroring correction either. A trim for a cell on one arm or one leg
 *	   would be drawn on <em>both</em> of them, one of the two mirrored. The body is one box, so a
 *	   body cell has no other limb to leak onto; every limb cell has, the shoulders included.
 * </ul>
 * The one material is a colour permutation of a key palette (every colour any patch uses) onto itself.
 */
public final class Trims {
	private Trims() {}

	public static final String MATERIAL = "patch";

	/**
	 * Can this placement be worn as the top's trim? Chest and back cells only — the body's own box —
	 * any patch that fits the cell. Not a limb cell, shoulders included: vanilla draws a trim on
	 * both limbs and nothing of ours can stop it (see above).
	 */
	public static boolean fits(Placement p) {
		return p.piece() == Piece.TOP && p.spot().side == Spot.Side.BODY;
	}

	public static String patternName(Placement p) {
		return p.spot().id() + "_" + p.patch().id();
	}

	public static Identifier pattern(Placement p) {
		return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, patternName(p));
	}

	public static Identifier material() {
		return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, MATERIAL);
	}
}
