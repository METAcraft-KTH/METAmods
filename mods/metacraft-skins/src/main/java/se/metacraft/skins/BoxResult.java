package se.metacraft.skins;

/** What a box give did. Anything but {@link #DONE} changed nothing. */
public enum BoxResult {
	DONE,
	/** No box file has this id. */
	UNKNOWN_BOX,
	/** The box's file failed to load. */
	DORMANT_BOX
}
