package se.metacraft.skins;

/** What a grant, revoke, remove, Claim, Extract, token give or tint clear did. Anything but {@link #DONE} changed nothing, in memory, on disk or in an inventory. */
public enum OwnershipChange {
	DONE,
	/** Grant, Claim: the player already owns the skin. */
	ALREADY_OWNED,
	/** Revoke, Extract: the player doesn't own the skin. */
	NOT_OWNED,
	/** Remove: the skin is on no item (in this world). */
	NOT_APPLIED,
	/** Grant, Claim, give: no skin folder has this id. */
	UNKNOWN_SKIN,
	/** Grant, Claim, give, Extract: the skin's definition failed to load. */
	DORMANT_SKIN,
	/** Extract: the skin is on an item; the Owner removes it first. */
	STILL_APPLIED,
	/** Claim: the stack isn't a Skin Token the player carries. */
	NO_TOKEN,
	/** Clear tint: the skin is on an item that isn't with its Owner, which would keep the colour. */
	ITEM_AWAY,
	/** The player's file can't be read; it is left untouched until someone fixes it. */
	DAMAGED_FILE,
	/** The player's file couldn't be written; the change was not made. */
	SAVE_FAILED
}
