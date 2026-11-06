package se.metacraft.skins;

/** What a box give or a Roll did. Anything but {@link #DONE} changed nothing. */
public enum BoxResult {
	DONE,
	/** Give: no box file has this id. */
	UNKNOWN_BOX,
	/** Give: the box's file failed to load. Roll: that, or no box file has this id. */
	DORMANT_BOX,
	/** Roll: every skin in the box is unknown or Dormant. */
	NOTHING_TO_ROLL,
	/** Roll: the player's main hand no longer holds a box with this id. */
	BOX_GONE
}
