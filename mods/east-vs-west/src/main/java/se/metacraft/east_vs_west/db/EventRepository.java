package se.metacraft.east_vs_west.db;

import org.jetbrains.annotations.Nullable;
import se.metacraft.east_vs_west.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The event's SQL, and nothing else: every method blocks on the database, so they are only ever called from
 * {@link Database}'s threads. Two tables, shared with the website (see {@link #schema}):
 * <ul>
 *   <li>{@code players}: one row per player on a team, with their current name. A player has no row until
 *       they pick a team (or an admin puts them on one);</li>
 *   <li>{@code submissions}: one row per item a player has registered. Its primary key
 *       {@code (player_uuid, item_id)} is what stops a player registering an item twice, whatever two
 *       clicks or two servers do at once; any number of players may hold the same item.</li>
 * </ul>
 * A submission's {@code team} is its player's team: {@link #setTeam} moves both in one transaction. A team's
 * score is its {@code COUNT(DISTINCT item_id)}.
 */
public final class EventRepository {

	/** The longest item id the {@code item_id} column holds. */
	public static final int MAX_ITEM_ID_LENGTH = 128;
	/** The longest name the {@code username} column holds: Minecraft's limit. */
	private static final int MAX_USERNAME_LENGTH = 16;

	@FunctionalInterface
	public interface ConnectionSource {
		Connection get() throws SQLException;
	}

	/** A player on a team and how many items they have submitted. */
	public record PlayerInfo(Team team, int items) {}

	/** A row of the leaderboard. */
	public record PlayerScore(UUID uuid, String username, Team team, int items) {}

	/**
	 * @param uniqueItems each team's score: the distinct items its players have submitted
	 * @param top         the best players across all teams, best first
	 */
	public record Standings(Map<Team, Integer> uniqueItems, List<PlayerScore> top) {
		public static final Standings EMPTY = new Standings(Map.of(), List.of());

		public int score(Team team) {
			return uniqueItems.getOrDefault(team, 0);
		}
	}

	public enum Outcome { REGISTERED, ALREADY_SUBMITTED, NO_TEAM }

	/**
	 * @param team         the player's team; null for {@link Outcome#NO_TEAM}
	 * @param firstForTeam whether nobody on the team had the item before, so the team's score went up
	 * @param playerItems  the items the player has submitted, this one included
	 * @param teamItems    the team's score, this item included
	 */
	public record SubmitResult(Outcome outcome, @Nullable Team team, boolean firstForTeam, int playerItems, int teamItems) {}

	private final ConnectionSource connections;
	private final String tablePrefix;
	private final String players;
	private final String submissions;

	public EventRepository(ConnectionSource connections, String tablePrefix) {
		if (!tablePrefix.matches("[A-Za-z0-9_]{0,32}")) {
			throw new IllegalArgumentException("table prefix is not a plain identifier: " + tablePrefix);
		}
		this.connections = connections;
		this.tablePrefix = tablePrefix;
		this.players = tablePrefix + "players";
		this.submissions = tablePrefix + "submissions";
	}

	/** The {@code CREATE TABLE} statements for a table prefix, in the order they must run. */
	public static List<String> schema(String tablePrefix) {
		String players = tablePrefix + "players";
		String submissions = tablePrefix + "submissions";
		String teams = Arrays.stream(Team.values()).map(team -> "'" + team.id() + "'").collect(Collectors.joining(", "));
		return List.of(
				"CREATE TABLE IF NOT EXISTS " + players + " (\n"
						+ "  uuid CHAR(36) NOT NULL,\n"
						+ "  username VARCHAR(16) NOT NULL,\n"
						+ "  team ENUM(" + teams + ") NOT NULL,\n"
						+ "  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
						+ "  PRIMARY KEY (uuid),\n"
						+ "  KEY idx_" + players + "_team (team)\n"
						+ ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
				"CREATE TABLE IF NOT EXISTS " + submissions + " (\n"
						+ "  player_uuid CHAR(36) NOT NULL,\n"
						+ "  item_id VARCHAR(" + MAX_ITEM_ID_LENGTH + ") NOT NULL,\n"
						+ "  team ENUM(" + teams + ") NOT NULL,\n"
						+ "  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
						+ "  PRIMARY KEY (player_uuid, item_id),\n"
						+ "  KEY idx_" + submissions + "_team_item (team, item_id),\n"
						+ "  CONSTRAINT fk_" + submissions + "_player FOREIGN KEY (player_uuid) REFERENCES " + players + " (uuid) ON DELETE CASCADE\n"
						+ ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
		);
	}

	/** Creates the tables that are missing. */
	public void createTables() throws SQLException {
		try (Connection connection = connections.get(); Statement statement = connection.createStatement()) {
			for (String sql : schema(tablePrefix)) {
				statement.executeUpdate(sql);
			}
		}
	}

	/**
	 * A player's team and item count, or empty for one on no team. Their stored name is brought up to date,
	 * which is how a renamed player reaches the website.
	 */
	public Optional<PlayerInfo> loadPlayer(UUID uuid, String username) throws SQLException {
		try (Connection connection = connections.get()) {
			Team team = teamOf(connection, uuid, username);
			if (team == null) {
				return Optional.empty();
			}
			return Optional.of(new PlayerInfo(team, countPlayerItems(connection, uuid)));
		}
	}

	/**
	 * Registers an item for a player, for their current team. The insert either adds the one row the primary
	 * key allows or fails on it, so two submissions racing each other end as one {@link Outcome#REGISTERED}
	 * and one {@link Outcome#ALREADY_SUBMITTED}.
	 */
	public SubmitResult submit(UUID uuid, String username, String itemId) throws SQLException {
		if (itemId.length() > MAX_ITEM_ID_LENGTH) {
			throw new SQLException("item id longer than " + MAX_ITEM_ID_LENGTH + " characters: " + itemId);
		}
		try (Connection connection = connections.get()) {
			Team team = teamOf(connection, uuid, username);
			if (team == null) {
				return new SubmitResult(Outcome.NO_TEAM, null, false, 0, 0);
			}
			boolean teamHadIt;
			try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + submissions + " WHERE team = ? AND item_id = ? LIMIT 1")) {
				statement.setString(1, team.id());
				statement.setString(2, itemId);
				try (ResultSet rows = statement.executeQuery()) {
					teamHadIt = rows.next();
				}
			}
			Outcome outcome = Outcome.REGISTERED;
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + submissions + " (player_uuid, item_id, team) VALUES (?, ?, ?)")) {
				statement.setString(1, uuid.toString());
				statement.setString(2, itemId);
				statement.setString(3, team.id());
				statement.executeUpdate();
			} catch (SQLException e) {
				if (!isDuplicateKey(e)) {
					throw e;
				}
				outcome = Outcome.ALREADY_SUBMITTED;
			}
			return new SubmitResult(outcome, team, outcome == Outcome.REGISTERED && !teamHadIt,
					countPlayerItems(connection, uuid), countTeamItems(connection, team));
		}
	}

	/**
	 * Puts a player who is on no team on the one they picked. One who already has a team stays on it, whatever
	 * they picked now: the insert fails on the primary key, as for two picks racing each other.
	 *
	 * @return the team they are on after this, and their items
	 */
	public PlayerInfo joinTeam(UUID uuid, String username, Team team) throws SQLException {
		try (Connection connection = connections.get()) {
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + players + " (uuid, username, team) VALUES (?, ?, ?)")) {
				statement.setString(1, uuid.toString());
				statement.setString(2, clip(username));
				statement.setString(3, team.id());
				statement.executeUpdate();
				return new PlayerInfo(team, 0);
			} catch (SQLException e) {
				if (!isDuplicateKey(e)) {
					throw e;
				}
			}
			Team current = teamOf(connection, uuid, username);
			if (current == null) {
				throw new SQLException("player " + uuid + " has a row with an unknown team");
			}
			return new PlayerInfo(current, countPlayerItems(connection, uuid));
		}
	}

	/**
	 * Puts a player on a team, creating their row or moving them and everything they have submitted, in one
	 * transaction.
	 *
	 * @return the items the player has submitted
	 */
	public int setTeam(UUID uuid, String username, Team team) throws SQLException {
		try (Connection connection = connections.get()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + players + " (uuid, username, team) VALUES (?, ?, ?)"
						+ " ON DUPLICATE KEY UPDATE username = VALUES(username), team = VALUES(team)")) {
					statement.setString(1, uuid.toString());
					statement.setString(2, clip(username));
					statement.setString(3, team.id());
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("UPDATE " + submissions + " SET team = ? WHERE player_uuid = ? AND team <> ?")) {
					statement.setString(1, team.id());
					statement.setString(2, uuid.toString());
					statement.setString(3, team.id());
					statement.executeUpdate();
				}
				int items = countPlayerItems(connection, uuid);
				connection.commit();
				return items;
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			} finally {
				connection.setAutoCommit(true);
			}
		}
	}

	/** The teams' scores and the {@code topCount} best players: most items first, then whoever got there first. */
	public Standings standings(int topCount) throws SQLException {
		try (Connection connection = connections.get()) {
			var scores = new EnumMap<Team, Integer>(Team.class);
			try (PreparedStatement statement = connection.prepareStatement("SELECT team, COUNT(DISTINCT item_id) FROM " + submissions + " GROUP BY team");
					ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					Team team = Team.byId(rows.getString(1));
					if (team != null) {
						scores.put(team, rows.getInt(2));
					}
				}
			}
			var top = new ArrayList<PlayerScore>();
			try (PreparedStatement statement = connection.prepareStatement("SELECT p.uuid, p.username, p.team, COUNT(*) AS items, MAX(s.submitted_at) AS last_submitted"
					+ " FROM " + submissions + " s JOIN " + players + " p ON p.uuid = s.player_uuid"
					+ " GROUP BY p.uuid, p.username, p.team ORDER BY items DESC, last_submitted ASC, p.uuid ASC LIMIT ?")) {
				statement.setInt(1, topCount);
				try (ResultSet rows = statement.executeQuery()) {
					while (rows.next()) {
						Team team = Team.byId(rows.getString(3));
						if (team != null) {
							top.add(new PlayerScore(UUID.fromString(rows.getString(1).trim()), rows.getString(2), team, rows.getInt(4)));
						}
					}
				}
			}
			return new Standings(Map.copyOf(scores), List.copyOf(top));
		}
	}

	/** The player's team, or null for one with no row; a name that changed is written back. */
	@Nullable
	private Team teamOf(Connection connection, UUID uuid, String username) throws SQLException {
		String name = clip(username);
		String storedName;
		Team team;
		try (PreparedStatement statement = connection.prepareStatement("SELECT team, username FROM " + players + " WHERE uuid = ?")) {
			statement.setString(1, uuid.toString());
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				team = Team.byId(rows.getString(1));
				storedName = rows.getString(2);
			}
		}
		if (team != null && !name.equals(storedName)) {
			try (PreparedStatement statement = connection.prepareStatement("UPDATE " + players + " SET username = ? WHERE uuid = ?")) {
				statement.setString(1, name);
				statement.setString(2, uuid.toString());
				statement.executeUpdate();
			}
		}
		return team;
	}

	private int countPlayerItems(Connection connection, UUID uuid) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + submissions + " WHERE player_uuid = ?")) {
			statement.setString(1, uuid.toString());
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt(1) : 0;
			}
		}
	}

	private int countTeamItems(Connection connection, Team team) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(DISTINCT item_id) FROM " + submissions + " WHERE team = ?")) {
			statement.setString(1, team.id());
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt(1) : 0;
			}
		}
	}

	/** SQLSTATE class 23 is an integrity constraint violation; here that can only be the primary key. */
	private static boolean isDuplicateKey(SQLException e) {
		return e instanceof SQLIntegrityConstraintViolationException || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
	}

	private static String clip(String username) {
		return username.length() > MAX_USERNAME_LENGTH ? username.substring(0, MAX_USERNAME_LENGTH) : username;
	}
}
