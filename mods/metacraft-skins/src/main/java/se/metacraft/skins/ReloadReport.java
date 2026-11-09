package se.metacraft.skins;

import java.nio.file.Path;
import java.util.List;

/**
 * What {@link Skins#reload()} did: the skins that loaded (ids, sorted), the ones rejected and why,
 * the warnings for skins that loaded anyway, the resulting pack's SHA-1 and whether it differs
 * from the pack before (only then is it pushed to players), and whether it took a Look away from
 * items that showed one: a skin that loaded before gone Dormant or missing, a Kind change, a
 * narrowed {@code materials} list, or {@code dyeable} turned off (only then are players' items sent
 * to them again). {@code boxes} is what the same reload did with the Skin Box files.
 */
public record ReloadReport(List<String> loaded, List<Rejection> rejections, List<Warning> warnings, String sha1, boolean changed,
		boolean lookTakenAway, Boxes boxes) {

	/**
	 * The Skin Boxes part: the boxes that loaded (ids, sorted), the ones rejected (Dormant) and why,
	 * and the warnings: each skin a box left out (unknown or Dormant), each file in {@code boxes/} ignored.
	 */
	public record Boxes(List<String> loaded, List<BoxRejection> rejections, List<BoxWarning> warnings) {}

	/** A box set aside as Dormant: its file and the reason, in words for an admin. */
	public record BoxRejection(String boxId, Path file, String reason) {}

	/** One warning line as an admin reads it ("classic: cat_ears is Dormant, left out"), and the file it's about. */
	public record BoxWarning(Path file, String message) {}

	/** A skin set aside as Dormant: the file at fault and the reason, in words for an admin. */
	public record Rejection(String skinId, Path file, String reason) {}

	/**
	 * Something an admin should fix in a skin that loaded anyway. {@code prominent} ones show in
	 * the inventory (a missing gui transform, or head on a helmet); the rest are minor.
	 */
	public record Warning(String skinId, Path file, String message, boolean prominent) {}
}
