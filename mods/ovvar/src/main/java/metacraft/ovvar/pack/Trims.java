package metacraft.ovvar.pack;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import net.minecraft.resources.Identifier;

/**
 * The trim channel: an item can wear one armour trim, and a trim is just a texture, so datagen
 * makes one trim pattern per (chest or back cell, patch) — drawn by vanilla, no bits, no pack
 * build — and the top's trim carries one more instant patch. Vanilla cannot squeeze to square
 * pixels the way the shader does, only datagen can, texel by texel; on the chest and back faces
 * (16 art pixels wide) that costs about one column in sixteen, tolerable until the pack catches
 * up, where on a sleeve it cost two in eight — so the channel is chest and back only. The one
 * material is a colour permutation of a key palette (every colour any patch uses) onto itself.
 */
public final class Trims {
	private Trims() {}

	public static final String MATERIAL = "patch";

	/** Can this placement be worn as the top's trim? Chest and back cells only, any patch that fits the cell. */
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
