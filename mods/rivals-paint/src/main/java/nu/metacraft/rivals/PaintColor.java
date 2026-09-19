package nu.metacraft.rivals;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The two team colours: the ovves of the DATA and IT chapters, sampled the way ovvar's mockups do.
 * Which vanilla blockstates a client is shown for paint of a colour is no longer the colour's own
 * business — {@link nu.metacraft.rivals.paint.PaintStates} hands out client states from a shared pool
 * of donors — so all a colour carries is how it looks to people: its ink, its team and its bar. The id
 * doubles as the vanilla team name.
 */
public enum PaintColor implements StringRepresentable {
	/** The Data chapter's ovve, sampled from art/ovvar/data.png the way ovvar's mockups do. */
	DATA("data", "DATA", 0xBD3754, TeamColor.RED, BossEvent.BossBarColor.RED),
	/** The IT chapter's ovve, from art/ovvar/it.png. */
	IT("it", "IT", 0x8A57BD, TeamColor.DARK_PURPLE, BossEvent.BossBarColor.PURPLE);

	public static final Codec<PaintColor> CODEC = StringRepresentable.fromEnum(PaintColor::values);

	public final String id;
	public final String displayName;
	public final int rgb;
	public final TeamColor teamColor;
	public final BossEvent.BossBarColor barColor;

	PaintColor(String id, String displayName, int rgb, TeamColor teamColor, BossEvent.BossBarColor barColor) {
		this.id = id;
		this.displayName = displayName;
		this.rgb = rgb;
		this.teamColor = teamColor;
		this.barColor = barColor;
	}

	/** Every colour's id, comma-separated, for messages that list the teams. */
	public static String idList() {
		List<String> ids = new ArrayList<>();
		for (PaintColor color : values()) ids.add(color.id);
		return String.join(", ", ids);
	}

	public static Optional<PaintColor> byId(String id) {
		for (PaintColor color : values()) {
			if (color.id.equals(id)) return Optional.of(color);
		}
		return Optional.empty();
	}

	/**
	 * The colour of a vanilla scoreboard team; empty for no team, or for a team that is neither side's.
	 *
	 * <p>Matched through {@link TeamNames}, not on the colour's own id: which scoreboard team each side is
	 * is configurable, so a server can point a side at a team it already runs.
	 */
	public static Optional<PaintColor> byTeam(@Nullable PlayerTeam team) {
		return team == null ? Optional.empty() : TeamNames.slotOf(team.getName());
	}

	@Override
	public @NonNull String getSerializedName() {
		return id;
	}
}
