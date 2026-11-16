package se.metacraft.museum_collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.util.EnumMap;
import java.util.Map;

/**
 * {@code config/museum-collection.json5}. Everything but {@code database} applies at once on {@code /reload};
 * a changed {@code database} block (or {@code enabled}) makes the module reconnect within a second.
 */
public record MuseumConfig(
		boolean enabled, boolean submissionsOpen, Database database, Map<Team, TeamSettings> teams,
		int participationThreshold, String participationRewardCommand, String collectorName, int interactionCooldownMs,
		int standingsRefreshSeconds
) {

	/**
	 * Where the event's tables live.
	 *
	 * @param passwordEnv name of an environment variable holding the password, used instead of {@code password} when set
	 * @param tablePrefix put before {@code players} and {@code submissions}
	 */
	public record Database(
			String host, int port, String database, String username, String password, String passwordEnv,
			String tablePrefix, int poolSize, int connectTimeoutSeconds
	) {
		public static final Database DEFAULT = new Database("localhost", 3306, "metacraft", "metacraft", "", "MUSEUM_DB_PASSWORD", "museum_", 3, 5);

		public static final Codec<Database> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(Codec.STRING.fieldOf("host"), "The MySQL (or MariaDB) server.").forGetter(Database::host),
				Codec.intRange(1, 65535).fieldOf("port").forGetter(Database::port),
				CommentCodec.comment(Codec.STRING.fieldOf("database"), "The database (schema) holding the event's tables. It must exist; the tables are created.").forGetter(Database::database),
				Codec.STRING.fieldOf("username").forGetter(Database::username),
				CommentCodec.comment(Codec.STRING.fieldOf("password"), "The user's password, in this file; leave \"\" to use password_env instead.").forGetter(Database::password),
				CommentCodec.comment(Codec.STRING.fieldOf("password_env"), "Name of an environment variable holding the password (preferred over this file).").forGetter(Database::passwordEnv),
				CommentCodec.comment(Codec.STRING.validate(Database::validatePrefix).fieldOf("table_prefix"), "Put before the table names: \"museum_\" gives museum_players and museum_submissions. Letters, digits and _ only.").forGetter(Database::tablePrefix),
				CommentCodec.comment(Codec.intRange(1, 16).fieldOf("pool_size"), "Connections kept to the database, and threads running queries on them. Never the server thread.").forGetter(Database::poolSize),
				CommentCodec.comment(Codec.intRange(1, 60).fieldOf("connect_timeout_seconds"), "How long a query waits for a connection before it fails and the player is told to try again.").forGetter(Database::connectTimeoutSeconds)
		).apply(i, Database::new));

		private static DataResult<String> validatePrefix(String prefix) {
			return prefix.matches("[A-Za-z0-9_]{0,32}")
					? DataResult.success(prefix)
					: DataResult.error(() -> "table_prefix may only hold letters, digits and _: " + prefix);
		}

		public String jdbcUrl() {
			return "jdbc:mariadb://" + host + ":" + port + "/" + database;
		}

		/** The password to use: the environment variable when named and set, else the file's. */
		public String resolvedPassword() {
			if (!passwordEnv.isEmpty()) {
				String env = System.getenv(passwordEnv);
				if (env != null && !env.isEmpty()) {
					return env;
				}
			}
			return password;
		}

		/** Never the password. */
		@Override
		public String toString() {
			return jdbcUrl() + " as " + username;
		}
	}

	public record TeamSettings(String displayName) {
		public static final Codec<TeamSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(ExtraCodecs.NON_EMPTY_STRING.fieldOf("display_name"), "The team's name in chat and in the team selection dialog.").forGetter(TeamSettings::displayName)
		).apply(i, TeamSettings::new));
	}

	public static final String DEFAULT_REWARD_COMMAND = "itemskins grant {player} chest_lid_hat";

	public static final MapCodec<MuseumConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			CommentCodec.comment(Codec.BOOL.fieldOf("enabled"),
					"Turns the event on. While false the module connects to nothing and collector villagers are ordinary villagers."
			).forGetter(MuseumConfig::enabled),
			CommentCodec.comment(Codec.BOOL.fieldOf("submissions_open"),
					"Whether the collector takes items. false keeps the scores and commands but refuses new items (/museum admin open|close)."
			).forGetter(MuseumConfig::submissionsOpen),
			CommentCodec.comment(Database.CODEC.fieldOf("database"), "The MySQL database shared with the website.").forGetter(MuseumConfig::database),
			CommentCodec.comment(Codec.unboundedMap(Team.CODEC, TeamSettings.CODEC).fieldOf("teams"),
					"The teams' names, by team: east, west and south. The keys are fixed; they are what the database stores."
			).forGetter(MuseumConfig::teams),
			CommentCodec.comment(ExtraCodecs.POSITIVE_INT.fieldOf("participation_threshold"),
					"Items a player must have registered to earn the participation reward."
			).forGetter(MuseumConfig::participationThreshold),
			CommentCodec.comment(Codec.STRING.fieldOf("participation_reward_command"),
					"The command the server runs (as the console) when a player registers their participation_threshold-th item;",
					"{player} is replaced by their name. \"\" for none. Every other reward is handed out by hand."
			).forGetter(MuseumConfig::participationRewardCommand),
			CommentCodec.comment(Codec.STRING.fieldOf("collector_name"), "The name over a villager spawned with /museum admin collector spawn; \"\" for none.").forGetter(MuseumConfig::collectorName),
			CommentCodec.comment(Codec.intRange(0, 10_000).fieldOf("interaction_cooldown_ms"), "Milliseconds a player must wait between two clicks on a collector; 0 for none. A repeat of an item already registered is answered without the database either way.").forGetter(MuseumConfig::interactionCooldownMs),
			CommentCodec.comment(Codec.intRange(5, 3600).fieldOf("standings_refresh_seconds"), "How often the team scores and the leaderboard (/museum, /museum top) are read from the database.").forGetter(MuseumConfig::standingsRefreshSeconds)
	).apply(i, MuseumConfig::new));

	// The file holds the database password, so it stays out of the in-game config screen (as kth-auth's does).
	private static final ConfigContainer<MuseumConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, MuseumConfig::createDefault
	).reloadAfterServer().disableConfigScreen().build(MuseumCollection.MODID);

	public static MuseumConfig get() {
		return CONTAINER.get();
	}

	/** Opens or closes submissions and writes it to the file. */
	public static void setSubmissionsOpen(boolean open) {
		CONTAINER.modify(config -> config.submissionsOpen == open ? config : new MuseumConfig(
				config.enabled, open, config.database, config.teams, config.participationThreshold, config.participationRewardCommand,
				config.collectorName, config.interactionCooldownMs, config.standingsRefreshSeconds
		));
	}

	public static MuseumConfig createDefault() {
		return new MuseumConfig(false, true, Database.DEFAULT, defaultTeams(), 10, DEFAULT_REWARD_COMMAND, "Item Collector", 0, 30);
	}

	private static Map<Team, TeamSettings> defaultTeams() {
		var teams = new EnumMap<Team, TeamSettings>(Team.class);
		for (var team : Team.values()) {
			teams.put(team, defaultTeam(team));
		}
		return teams;
	}

	private static TeamSettings defaultTeam(Team team) {
		return switch (team) {
			case EAST -> new TeamSettings("East");
			case WEST -> new TeamSettings("West");
			case SOUTH -> new TeamSettings("South");
		};
	}

	/** A team's settings; the defaults for one the file leaves out. */
	public TeamSettings team(Team team) {
		var settings = teams.get(team);
		return settings != null ? settings : defaultTeam(team);
	}
}
