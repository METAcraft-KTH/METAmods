package metacraft.ovvar.sewing;

import metacraft.ovvar.content.ModContent;
import net.minecraft.resources.Identifier;

/**
 * The things the wardrobe screen's rows let a player do, and the icon each is drawn with. The art
 * is ours ({@code art/ovvar/wardrobe_<id>.png}, drawn by {@code tools/wardrobe_icons.py}) rather
 * than a vanilla item borrowed for the shape of it, and datagen makes two models of each: the icon,
 * and a dimmed twin with a red slash for an action this server does not allow. A refused action is
 * then plainly the same action, out of use — where the grey glass pane it used to be read as a bug.
 *
 * @param id	the art's name and the model's name; two actions may share one (the page
 *			  arrows are the rotation arrows, pointing the same ways)
 * @param verb  what the button is called ("Take out"); a refused one is "… (not here)"
 * @param does  the one line under it saying what it does
 */
public enum WardrobeAction {
	TAKE_OUT("take_out", "Take out", "Click a patch on the left to take it out as an item"),
	PUT_IN("put_in", "Put held patches in", "Every patch item in your inventory goes into your stash"),
	SEW("sew", "Sew on a stand", "Click a patch on the left to start sewing it on"),
	SEE_3D("see_3d", "See it in 3D", "Stands a mannequin wearing your ovve in front of you"),
	/** The two that turn the preview; their own labels name the side they bring round. */
	ROTATE_LEFT("rotate_left", "Turn it left", "The preview, a quarter turn anticlockwise"),
	ROTATE_RIGHT("rotate_right", "Turn it right", "The preview, a quarter turn clockwise"),
	/** And the two that page the pocket: the same two arrows, pointing the same ways, another job. */
	PAGE_PREVIOUS("rotate_left", "◀ Previous page", "The kinds of patch before these"),
	PAGE_NEXT("rotate_right", "Next page ▶", "The kinds of patch after these");

	/** The art's name, which two actions may share — so {@code id} is not unique, the constant is. */
	public final String id;
	public final String verb, does;

	WardrobeAction(String id, String verb, String does) {
		this.id = id;
		this.verb = verb;
		this.does = does;
	}

	/** The item name (and so the model and texture name) of the icon, or of its dimmed twin. */
	public String itemName(boolean available) {
		return "wardrobe_" + (available ? "" : "disabled_") + id;
	}

	public Identifier model(boolean available) {
		return ModContent.id(itemName(available));
	}

	/** The art datagen cuts both models out of. */
	public String art() {
		return "wardrobe_" + id;
	}
}
