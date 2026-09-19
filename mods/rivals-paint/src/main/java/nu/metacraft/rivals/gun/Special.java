package nu.metacraft.rivals.gun;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The three things F can be, and what separates them. A weapon is what a player fires; the special is
 * the one thing they throw, and until round 10 there was only one of it — so F was the splat bomb and
 * that was the whole of the choice. It is picked now the way a weapon is: {@link SpecialChoice}
 * remembers it, {@link SpecialDialog} is the screen, and {@link PaintWeapon#special} throws whichever
 * one the thrower picked.
 *
 * <p>All three are Splatoon 1 sub weapons, on this module's hundred-unit ink tank, and they differ in
 * the one way a sub weapon can: <em>when</em> it goes off. The splat bomb lands and waits, so it is a
 * question about where somebody will be in a second; the burst bomb goes off on contact, so it is a
 * question about where they are now; the curling bomb goes off somewhere else entirely, because it
 * slides there first painting the floor as it goes. {@link Mode} is that difference, and it is the only
 * branch {@link PaintBall} takes on which special it is carrying.
 *
 * <p>Every number below is a <em>default</em>: {@link SpecialTuning} is built from these and the throw
 * reads that, so {@code /rivals tune special <id> <param> <value>} moves any of them for the rest of
 * the session. The splat bomb's are still the {@code Weapon.SPECIAL_*} constants they have always
 * been — the same numbers in the same place, now as one special's column rather than every weapon's.
 */
public enum Special {
	/**
	 * Today's bomb, unchanged: a slow lob that bounces where it is thrown, counts
	 * {@link Weapon#SPECIAL_FUSE} ticks down on the ground and then takes a 7×7 patch and everyone
	 * standing in it.
	 */
	SPLAT_BOMB("splat_bomb", "Splat bomb", "A slow lob that lands, waits a second and then takes the room with it",
			Mode.FUSE),
	/**
	 * The cheap one: thrown flat and fast, and it bursts on whatever it touches first. Half the ink and
	 * half the wait of a splat bomb for a smaller blast, which makes it the special you can afford to
	 * throw at somebody rather than at a place.
	 */
	BURST_BOMB("burst_bomb", "Burst bomb", "Thrown fast and bursts the instant it touches anything — cheap, small, now",
			Mode.IMPACT),
	/**
	 * The paint one: thrown low, it slides along the floor painting a line under itself and bursts at the
	 * end of the slide. It is the only special that is worth throwing at nobody at all.
	 */
	CURLING_BOMB("curling_bomb", "Curling bomb", "Slides along the floor painting a line, then bursts at the end of it",
			Mode.CURL);

	/**
	 * When a special goes off, which is the whole of what separates the three. {@link PaintBall} reads
	 * this and nothing else about which special it is.
	 */
	public enum Mode {
		/** Bounce where it is thrown, count down on the ground, then go off. The splat bomb. */
		FUSE,
		/** Go off on the first thing it touches, block or body. The burst bomb. */
		IMPACT,
		/** Slide along the floor painting under itself, then go off. The curling bomb. */
		CURL
	}

	// The burst bomb. Splatoon's is the cheapest sub in the game and does almost nothing at the edge of
	// its blast: it is a finisher and a poke, not a room-clearer.

	/** Burst bomb: 40 ink of a hundred, and two seconds of its own. */
	public static final int BURST_INK = 40;
	public static final int BURST_COOLDOWN = 40;
	/** Burst bomb: its own wait before own paint refills the tank. Less ink out, less of a beat. */
	public static final int BURST_REFILL_DELAY = 12;
	/** Burst bomb: 5×5 where it bursts. */
	public static final int BURST_RADIUS = 2;
	/** Burst bomb: 25 at the centre down to 5 at two blocks. */
	public static final float BURST_DAMAGE = 25.0f;
	public static final float BURST_EDGE_DAMAGE = 5.0f;
	public static final double BURST_BLAST = 2.0;
	public static final double BURST_CORE = 0.3;
	/** Burst bomb: thrown nearly flat and nearly twice as fast as a splat bomb, because it is aimed at a body. */
	public static final float BURST_VELOCITY = 1.4f;
	public static final double BURST_GRAVITY = 0.05;
	public static final float BURST_PITCH = -10.0f;
	/** Burst bomb: it hits something almost at once, so the lifetime is only for one thrown off a cliff. */
	public static final int BURST_LIFETIME = 60;
	/** Burst bomb: a small blob. */
	public static final float BURST_SCALE = 1.0f;

	// The curling bomb. The slide is the weapon: it is thrown to cover ground, and the blast at the end
	// of it is what makes throwing it down a corridor somebody is in a threat rather than a decoration.

	/** Curling bomb: 55 ink, and three and a half seconds of its own. */
	public static final int CURLING_INK = 55;
	public static final int CURLING_COOLDOWN = 70;
	public static final int CURLING_REFILL_DELAY = 16;
	/** Curling bomb: 5×5 where it stops. */
	public static final int CURLING_RADIUS = 2;
	/** Curling bomb: 18 at the centre down to 4 at two and a half blocks — the softest of the three. */
	public static final float CURLING_DAMAGE = 18.0f;
	public static final float CURLING_EDGE_DAMAGE = 4.0f;
	public static final double CURLING_BLAST = 2.5;
	public static final double CURLING_CORE = 0.4;
	/** Curling bomb: thrown level and a shade downwards, so it reaches the floor and stays on it. */
	public static final float CURLING_VELOCITY = 1.1f;
	public static final double CURLING_GRAVITY = 0.06;
	public static final float CURLING_PITCH = 5.0f;
	/**
	 * Curling bomb: the lifetime is only for one that never finds a floor at all — a slide that has
	 * started is governed by {@link #CURLING_SLIDE_TICKS} instead, so a long slide cannot be cut short
	 * by it.
	 */
	public static final int CURLING_LIFETIME = 120;
	/** Curling bomb: a middling blob. */
	public static final float CURLING_SCALE = 1.2f;
	/** Curling bomb: two seconds of sliding at the most, then it bursts wherever it got to. */
	public static final int CURLING_SLIDE_TICKS = 40;
	/**
	 * Curling bomb: how much of its speed the slide keeps each tick. At 0.94 a throw covers some sixteen
	 * blocks before it is down to a crawl, which is a line worth the 55 ink — and it is a taper rather
	 * than a stop, so where a curling bomb ends up is a thing a thrower learns rather than measures.
	 */
	public static final double CURLING_FRICTION = 0.94;

	/** Registry-free id: what a player types and what {@link SpecialChoice} writes. */
	public final String id;
	public final String displayName;
	/** The one line under its picture in {@link SpecialDialog}: what it is for. */
	public final String blurb;
	/** When it goes off. */
	public final Mode mode;

	Special(String id, String displayName, String blurb, Mode mode) {
		this.id = id;
		this.displayName = displayName;
		this.blurb = blurb;
		this.mode = mode;
	}

	/** What {@code /rivals special pick} takes and what the tuning file is keyed by; the same as {@link #id}. */
	public String commandId() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Either name a special answers to. They are the same string today, and both are accepted anyway. */
	public static Optional<Special> byId(String id) {
		String wanted = id.toLowerCase(Locale.ROOT);
		for (Special special : values()) {
			if (special.id.equals(wanted) || special.commandId().equals(wanted)) return Optional.of(special);
		}
		return Optional.empty();
	}

	/** The names players type, comma-separated, for command help and failure messages. */
	public static String idList() {
		return Stream.of(values()).map(Special::commandId).collect(Collectors.joining(", "));
	}
}
