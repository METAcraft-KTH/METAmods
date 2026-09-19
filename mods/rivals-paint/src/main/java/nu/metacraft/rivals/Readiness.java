package nu.metacraft.rivals;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.rivals.gun.Weapon;
import nu.metacraft.rivals.gun.WeaponChoice;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Is everybody ready to play? One line per online non-spectator player — their name, their side and the
 * weapon they picked — grouped by side, with whoever is on neither side last.
 *
 * <p>A side is a plain vanilla scoreboard team, under the name {@link TeamNames} says that side uses. A
 * player on neither of them has no colour, cannot paint and cannot score, so a match that starts with one
 * is a match with a passenger in it. {@code /rivals ready} therefore <em>fails</em> when anybody is on
 * neither, naming them and the teams they could join, and {@code /rivals match start} refuses on the same
 * check unless the word {@code force} is added.
 *
 * <p>Spectators are left out rather than counted as teamless: a spectator is deliberately not playing.
 */
public final class Readiness {
	private Readiness() {}

	/**
	 * One player's state. {@code team} is empty for a player on neither side's scoreboard team;
	 * {@code weapon} is empty for one who has never opened the picker (they would be handed
	 * {@link WeaponChoice#DEFAULT}).
	 */
	public record Line(ServerPlayer player, Optional<PaintColor> team, Optional<Weapon> weapon) {
		public boolean teamed() {
			return team.isPresent();
		}

		/** The line as it is printed: name, side or "no team", weapon or "none yet". */
		public String text() {
			return player.getScoreboardName() + " — " + team.map(color -> color.displayName).orElse("no team")
					+ ", " + weapon.map(w -> w.displayName).orElse("none yet");
		}
	}

	/** Everybody's state, and who among them is on neither side. */
	public record Report(List<Line> lines, List<ServerPlayer> teamless) {
		/** Ready when somebody is playing and everybody playing is on a side. */
		public boolean ready() {
			return !lines.isEmpty() && teamless.isEmpty();
		}

		/** The teamless players' names, comma-separated, for the refusal that names them. */
		public String teamlessNames() {
			List<String> names = new ArrayList<>();
			for (ServerPlayer player : teamless) names.add(player.getScoreboardName());
			return String.join(", ", names);
		}

		/** How many are on one given side. */
		public int on(PaintColor side) {
			int n = 0;
			for (Line line : lines) {
				if (line.team().orElse(null) == side) n++;
			}
			return n;
		}
	}

	public static Report of(MinecraftServer server) {
		return of(server.getPlayerList().getPlayers());
	}

	/** The same over a given set of players, which is what the tests hand in. */
	public static Report of(Collection<ServerPlayer> players) {
		List<Line> lines = new ArrayList<>();
		List<ServerPlayer> teamless = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (player.isSpectator()) continue;
			Optional<PaintColor> team = PaintColor.byTeam(player.getTeam());
			Line line = new Line(player, team, choice(player));
			lines.add(line);
			if (!line.teamed()) teamless.add(player);
		}
		// Grouped by side, in the sides' own order, with the teamless at the end: a roster read out loud is
		// read one team at a time, and the people who still have to join are the point of the last group.
		lines.sort(Comparator.comparingInt(line -> line.team().map(Enum::ordinal).orElse(PaintColor.values().length)));
		return new Report(lines, teamless);
	}

	/** What this player picked, if the server is up far enough to have a saved choice at all. */
	private static Optional<Weapon> choice(ServerPlayer player) {
		@Nullable MinecraftServer server = player.level().getServer();
		return server == null ? Optional.empty() : WeaponChoice.of(server).get(player);
	}

	/**
	 * Print the report. Returns how many players are ready, or fails (and returns 0) when anybody is on
	 * neither side — the failure is the point: it is what makes {@code /rivals ready} answerable by a
	 * script and what {@code match start} leans on.
	 */
	public static int report(CommandSourceStack source) {
		Report report = of(source.getServer());
		if (report.lines().isEmpty()) {
			source.sendFailure(Component.literal("Nobody is playing: no online player outside spectator mode")
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		for (Line line : report.lines()) {
			source.sendSuccess(() -> Component.literal(line.text()).withStyle(style -> line.team()
					.map(color -> style.withColor(color.teamColor.textColor()))
					.orElse(style.applyFormat(ChatFormatting.GRAY))), false);
		}
		if (!report.ready()) {
			source.sendFailure(Component.literal("Not on a team: " + report.teamlessNames()
					+ " — /team join <" + TeamNames.nameList() + ">. "
					+ "Or start the match anyway with /rivals match start <minutes> force")
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		List<String> counts = new ArrayList<>();
		for (PaintColor side : PaintColor.values()) counts.add(side.displayName + " " + report.on(side));
		source.sendSuccess(() -> Component.literal("Ready: " + report.lines().size() + " player"
				+ (report.lines().size() == 1 ? "" : "s") + " — " + String.join(" · ", counts))
				.withStyle(ChatFormatting.GREEN), false);
		return report.lines().size();
	}
}
