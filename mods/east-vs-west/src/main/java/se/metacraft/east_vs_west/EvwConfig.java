package se.metacraft.east_vs_west;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code config/east-vs-west.json5}. Everything but {@code database} applies at once on {@code /reload};
 * a changed {@code database} block (or {@code enabled}) makes the module reconnect within a second.
 */
public record EvwConfig(
		boolean enabled, boolean submissionsOpen, Database database, Map<Team, TeamSettings> teams,
		int participationThreshold, Legendary legendary, String collectorName, int interactionCooldownMs,
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
		public static final Database DEFAULT = new Database("localhost", 3306, "metacraft", "metacraft", "", "EVW_DB_PASSWORD", "evw_", 3, 5);

		public static final Codec<Database> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(Codec.STRING.optionalFieldOf("host", DEFAULT.host), "The MySQL (or MariaDB) server.").forGetter(Database::host),
				Codec.intRange(1, 65535).optionalFieldOf("port", DEFAULT.port).forGetter(Database::port),
				CommentCodec.comment(Codec.STRING.optionalFieldOf("database", DEFAULT.database), "The database (schema) holding the event's tables. It must exist; the tables are created.").forGetter(Database::database),
				Codec.STRING.optionalFieldOf("username", DEFAULT.username).forGetter(Database::username),
				CommentCodec.comment(Codec.STRING.optionalFieldOf("password", DEFAULT.password), "The user's password, in this file; leave \"\" to use password_env instead.").forGetter(Database::password),
				CommentCodec.comment(Codec.STRING.optionalFieldOf("password_env", DEFAULT.passwordEnv), "Name of an environment variable holding the password (preferred over this file).").forGetter(Database::passwordEnv),
				CommentCodec.comment(Codec.STRING.validate(Database::validatePrefix).optionalFieldOf("table_prefix", DEFAULT.tablePrefix), "Put before the table names: \"evw_\" gives evw_players and evw_submissions. Letters, digits and _ only.").forGetter(Database::tablePrefix),
				CommentCodec.comment(Codec.intRange(1, 16).optionalFieldOf("pool_size", DEFAULT.poolSize), "Connections kept to the database, and threads running queries on them. Never the server thread.").forGetter(Database::poolSize),
				CommentCodec.comment(Codec.intRange(1, 60).optionalFieldOf("connect_timeout_seconds", DEFAULT.connectTimeoutSeconds), "How long a query waits for a connection before it fails and the player is told to try again.").forGetter(Database::connectTimeoutSeconds)
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

	/**
	 * A cosmetic hat: an item display riding the wearer's head, seen by vanilla clients. Nothing is put in the
	 * wearer's inventory.
	 *
	 * @param name      what the hat is called in chat
	 * @param item      the item shown, by registry id
	 * @param itemModel an item model from a resource pack to show instead of the item's own
	 * @param scale     the display's size on each axis; 1 is a full block
	 * @param yOffset   blocks the display is moved up from the top of the wearer's head. A display is centred
	 *                  on the middle of its model, so a full block needs about half its height
	 */
	public record Hat(String name, Identifier item, Optional<Identifier> itemModel, Vector3fc scale, float yOffset) {
		public static final Codec<Hat> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(ExtraCodecs.NON_EMPTY_STRING.fieldOf("name"), "What the hat is called in chat.").forGetter(Hat::name),
				CommentCodec.comment(Identifier.CODEC.fieldOf("item"), "The item shown on the head, by registry id.").forGetter(Hat::item),
				CommentCodec.comment(Identifier.CODEC.optionalFieldOf("item_model"), "An item model from a resource pack to show instead of the item's own.").forGetter(Hat::itemModel),
				CommentCodec.comment(ExtraCodecs.VECTOR3F.optionalFieldOf("scale", new Vector3f(0.6f)), "The size on each axis [x, y, z]; 1 is a full block.").forGetter(Hat::scale),
				CommentCodec.comment(Codec.floatRange(-2f, 2f).optionalFieldOf("y_offset", 0f), "Blocks to move it up from the top of the head. A model is centred on its middle.").forGetter(Hat::yOffset)
		).apply(i, Hat::new));

		/** A wool cap pulled over the top of the head. */
		private static Hat cap(String name, String wool) {
			return new Hat(name, Identifier.withDefaultNamespace(wool), Optional.empty(), new Vector3f(0.6f, 0.3f, 0.6f), 0.1f);
		}

		/** The item to display, or an empty stack while {@link #item} isn't registered. */
		public ItemStack stack() {
			return BuiltInRegistries.ITEM.getOptional(item).map(ItemStack::new).orElse(ItemStack.EMPTY);
		}
	}

	/** @param hat the team's participation hat */
	public record TeamSettings(String displayName, Hat hat) {
		public static final Codec<TeamSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(ExtraCodecs.NON_EMPTY_STRING.fieldOf("display_name"), "The team's name in chat and in the team selection dialog.").forGetter(TeamSettings::displayName),
				CommentCodec.comment(Hat.CODEC.fieldOf("hat"), "The hat its players wear from participation_threshold items on.").forGetter(TeamSettings::hat)
		).apply(i, TeamSettings::new));
	}

	/**
	 * The reward of the best players across all teams, by number of items submitted.
	 *
	 * @param topCount how many players wear it
	 */
	public record Legendary(boolean enabled, int topCount, Hat hat, boolean particles, int particleIntervalTicks) {
		public static final Legendary DEFAULT = new Legendary(true, 5, new Hat(
				"Ender Crown", Identifier.withDefaultNamespace("ender_chest"), Optional.of(EastVsWest.getID("ender_chest_lid")),
				new Vector3f(0.62f), 0f
		), true, 8);

		public static final Codec<Legendary> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled), "Whether the top players wear the legendary hat. It replaces their team hat.").forGetter(Legendary::enabled),
				CommentCodec.comment(Codec.intRange(1, EventService.LEADERBOARD_SIZE).optionalFieldOf("top_count", DEFAULT.topCount), "How many players wear it: the top of the leaderboard, ties going to who got there first.").forGetter(Legendary::topCount),
				CommentCodec.comment(Hat.CODEC.optionalFieldOf("hat", DEFAULT.hat), "The hat: by default the lid of an ender chest, a model in this mod's resource pack.").forGetter(Legendary::hat),
				CommentCodec.comment(Codec.BOOL.optionalFieldOf("particles", DEFAULT.particles), "Portal particles drifting in to the wearer, as around an ender chest.").forGetter(Legendary::particles),
				CommentCodec.comment(Codec.intRange(1, 200).optionalFieldOf("particle_interval_ticks", DEFAULT.particleIntervalTicks), "Ticks between two puffs of particles.").forGetter(Legendary::particleIntervalTicks)
		).apply(i, Legendary::new));
	}

	public static final MapCodec<EvwConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			CommentCodec.comment(Codec.BOOL.optionalFieldOf("enabled", false),
					"Turns the event on. While false the module connects to nothing and collector villagers are ordinary villagers."
			).forGetter(EvwConfig::enabled),
			CommentCodec.comment(Codec.BOOL.optionalFieldOf("submissions_open", true),
					"Whether the collector takes items. false keeps the scores, hats and commands but refuses new items (/evw admin open|close)."
			).forGetter(EvwConfig::submissionsOpen),
			CommentCodec.comment(Database.CODEC.optionalFieldOf("database", Database.DEFAULT), "The MySQL database shared with the website.").forGetter(EvwConfig::database),
			CommentCodec.comment(Codec.unboundedMap(Team.CODEC, TeamSettings.CODEC).optionalFieldOf("teams", defaultTeams()),
					"The teams' names and hats, by team: east, west and rest. The keys are fixed; they are what the database stores."
			).forGetter(EvwConfig::teams),
			CommentCodec.comment(ExtraCodecs.POSITIVE_INT.optionalFieldOf("participation_threshold", 10), "Items a player must have submitted to wear their team's hat.").forGetter(EvwConfig::participationThreshold),
			CommentCodec.comment(Legendary.CODEC.optionalFieldOf("legendary", Legendary.DEFAULT), "The hat of the best players across all teams.").forGetter(EvwConfig::legendary),
			CommentCodec.comment(Codec.STRING.optionalFieldOf("collector_name", "Item Collector"), "The name over a villager spawned with /evw admin collector spawn; \"\" for none.").forGetter(EvwConfig::collectorName),
			CommentCodec.comment(Codec.intRange(0, 10_000).optionalFieldOf("interaction_cooldown_ms", 500), "Milliseconds a player must wait between two clicks on a collector.").forGetter(EvwConfig::interactionCooldownMs),
			CommentCodec.comment(Codec.intRange(5, 3600).optionalFieldOf("standings_refresh_seconds", 30), "How often the team scores and the leaderboard (so the legendary hats) are read from the database.").forGetter(EvwConfig::standingsRefreshSeconds)
	).apply(i, EvwConfig::new));

	// The file holds the database password, so it stays out of the in-game config screen (as kth-auth's does).
	private static final ConfigContainer<EvwConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, EvwConfig::createDefault
	).reloadAfterServer().disableConfigScreen().build(EastVsWest.MODID);

	public static EvwConfig get() {
		return CONTAINER.get();
	}

	/** Opens or closes submissions and writes it to the file. */
	public static void setSubmissionsOpen(boolean open) {
		CONTAINER.modify(config -> config.submissionsOpen == open ? config : new EvwConfig(
				config.enabled, open, config.database, config.teams, config.participationThreshold, config.legendary,
				config.collectorName, config.interactionCooldownMs, config.standingsRefreshSeconds
		));
	}

	public static EvwConfig createDefault() {
		return new EvwConfig(false, true, Database.DEFAULT, defaultTeams(), 10, Legendary.DEFAULT, "Item Collector", 500, 30);
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
			case EAST -> new TeamSettings("East", Hat.cap("Humble east", "red_wool"));
			case WEST -> new TeamSettings("West", Hat.cap("West is best", "blue_wool"));
			case REST -> new TeamSettings("Rest", Hat.cap("Sophisticated south", "lime_wool"));
		};
	}

	/** A team's settings; the defaults for one the file leaves out. */
	public TeamSettings team(Team team) {
		var settings = teams.get(team);
		return settings != null ? settings : defaultTeam(team);
	}
}
