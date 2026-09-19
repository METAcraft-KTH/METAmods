package metacraft.ovvar.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code designs} block of {@code config/ovvar.json}: where the players' wardrobes (their
 * sewn patches per chapter and their stash of unsewn ones) live, and what sewing does when that
 * store cannot be reached. Every key has a default, so an older file without the block still loads.
 *
 * @param backend				{@code file}: one JSON per design under {@link #fileDirectory}; {@code jdbc}: a table
 * @param fileDirectory		  for the file backend: an absolute directory, or "" for {@code <world>/ovvar/wardrobes}
 * @param jdbc				   the JDBC settings; only read when the backend is {@code jdbc}
 * @param bindOnPickup		   an ovve with no owner becomes owned by the first player whose inventory ticks it
 * @param othersOvve			 what happens with an ovve owned by somebody else: {@code block} (the default:
 *							   it cannot be worn — the armour slot refuses it and a forced one is taken off
 *							   again — and nothing may be sewn on or off it), {@code rebind} (it becomes the
 *							   holder's, showing their design) or {@code allow} (anyone may wear it and it
 *							   keeps showing its owner's design, how it was before)
 * @param editRequiresOwner	  only an owned ovve's owner may sew on it or unpick from it (the default);
 *							   false lets anyone with shears change somebody else's design
 * @param sewWhenUnreachable	 with the store down, a sew still goes on the ovve and the write is queued
 *							   (retried every {@link #retrySeconds}); false refuses and keeps the patch in hand
 * @param unpickWhenUnreachable  with the store down, an unpick still hands the patch back and the write
 *							   is queued. Riskier than sewing (it is the dupe direction); off by default
 * @param retrySeconds		   how often a failed load or a queued write is retried
 * @param logQueries			 log every load and store at INFO (debugging)
 */
public record DesignStoreConfig(
		Backend backend, String fileDirectory, Jdbc jdbc, boolean bindOnPickup, OthersOvve othersOvve,
		boolean editRequiresOwner, boolean sewWhenUnreachable, boolean unpickWhenUnreachable, int retrySeconds,
		boolean logQueries
) {
	/** Written into the file as {@code _help}, one line per key, since JSON has no comments. */
	public static final Map<String, String> HELP = new LinkedHashMap<>();
	static {
		HELP.put("_about", "Where every player's wardrobe (their sewn patches per chapter and their stash of unsewn patches) is kept. All servers should point at the same store.");
		HELP.put("backend", "\"file\": one JSON file per player in file_directory. \"jdbc\": a table in a shared database (MariaDB/MySQL or PostgreSQL drivers are bundled). Use jdbc for a network of servers.");
		HELP.put("file_directory", "For the file backend: an absolute directory, or \"\" for <world>/ovvar/wardrobes.");
		HELP.put("jdbc", "For the jdbc backend: the connection settings. See its own _help.");
		HELP.put("bind_on_pickup", "An ovve nobody owns becomes the property of the first player whose inventory holds it.");
		HELP.put("others_ovve", "An ovve owned by someone else in a player's inventory: \"block\" (the default; it cannot be worn — the armour slot refuses it, and a forced one is taken off again — and nothing may be sewn on or off it), \"rebind\" (it becomes the holder's, showing THEIR design; the previous owner keeps their patches), or \"allow\" (anyone may wear it, still showing its owner's design).");
		HELP.put("edit_requires_owner", "Only an owned ovve's owner may sew on it or unpick from it (the default). false lets anyone with shears change somebody else's design.");
		HELP.put("sew_when_unreachable", "If the store is down, still sew and write it later (retried every retry_seconds). false: refuse and keep the patch in hand. Default false.");
		HELP.put("unpick_when_unreachable", "If the store is down, still hand the patch back and write it later. This is the direction a duplicate could sneak in, so default false.");
		HELP.put("retry_seconds", "How often failed loads and queued writes are retried.");
		HELP.put("log_queries", "Log every load and store at INFO. For debugging.");
	}

	/** What somebody else's ovve is to this player: unwearable (the default), theirs to take over, or free to wear. */
	public enum OthersOvve implements StringRepresentable {
		BLOCK("block"), REBIND("rebind"), ALLOW("allow");

		public static final Codec<OthersOvve> CODEC = StringRepresentable.fromEnum(OthersOvve::values);
		private final String name;

		OthersOvve(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public enum Backend implements StringRepresentable {
		FILE("file"), JDBC("jdbc");

		public static final Codec<Backend> CODEC = StringRepresentable.fromEnum(Backend::values);
		private final String name;

		Backend(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/**
	 * @param url				   e.g. {@code jdbc:mariadb://db.example:3306/metacraft} or {@code jdbc:postgresql://db.example/metacraft}
	 * @param user				  database user
	 * @param password			  its password, in the file; leave "" to use {@link #passwordEnv} instead
	 * @param passwordEnv		   name of an environment variable holding the password (preferred over the file)
	 * @param table				 the table; created if missing
	 * @param driverClass		   forces a driver class ("" lets the URL pick one; MariaDB/MySQL and PostgreSQL are bundled)
	 * @param connectTimeoutSeconds login timeout
	 * @param queryTimeoutSeconds   per-statement timeout
	 */
	public record Jdbc(
			String url, String user, String password, String passwordEnv, String table, String driverClass,
			int connectTimeoutSeconds, int queryTimeoutSeconds
	) {
		/** Written into the file as {@code _help}, since JSON has no comments. */
		public static final Map<String, String> HELP = new LinkedHashMap<>();
		static {
			HELP.put("_about", "The jdbc backend's connection settings; only read when designs.backend is \"jdbc\".");
			HELP.put("url", "jdbc:mariadb://host:3306/db or jdbc:postgresql://host/db (drivers for both are bundled).");
			HELP.put("user", "The database user.");
			HELP.put("password", "Its password, in this file; leave \"\" to use password_env instead.");
			HELP.put("password_env", "Name of an environment variable holding the password (preferred over the file).");
			HELP.put("table", "The table; created if missing: owner CHAR(36) PRIMARY KEY, version, data (JSON), updated_at.");
			HELP.put("driver_class", "Forces a driver class; \"\" lets the URL pick one.");
			HELP.put("connect_timeout_seconds", "Login timeout.");
			HELP.put("query_timeout_seconds", "Per-statement timeout.");
		}

		public static final Jdbc DEFAULT = new Jdbc("jdbc:mariadb://localhost:3306/metacraft", "metacraft", "", "OVVAR_DB_PASSWORD",
				"ovve_wardrobes", "", 5, 5);
		public static final Codec<Jdbc> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("_help", Map.<String, String>of()).forGetter(c -> HELP),
				Codec.STRING.optionalFieldOf("url", DEFAULT.url).forGetter(Jdbc::url),
				Codec.STRING.optionalFieldOf("user", DEFAULT.user).forGetter(Jdbc::user),
				Codec.STRING.optionalFieldOf("password", DEFAULT.password).forGetter(Jdbc::password),
				Codec.STRING.optionalFieldOf("password_env", DEFAULT.passwordEnv).forGetter(Jdbc::passwordEnv),
				Codec.STRING.optionalFieldOf("table", DEFAULT.table).forGetter(Jdbc::table),
				Codec.STRING.optionalFieldOf("driver_class", DEFAULT.driverClass).forGetter(Jdbc::driverClass),
				Codec.intRange(1, 600).optionalFieldOf("connect_timeout_seconds", DEFAULT.connectTimeoutSeconds).forGetter(Jdbc::connectTimeoutSeconds),
				Codec.intRange(1, 600).optionalFieldOf("query_timeout_seconds", DEFAULT.queryTimeoutSeconds).forGetter(Jdbc::queryTimeoutSeconds)
		).apply(instance, (help, url, user, password, passwordEnv, table, driverClass, connectTimeout, queryTimeout) ->
				new Jdbc(url, user, password, passwordEnv, table, driverClass, connectTimeout, queryTimeout)));

		/** The password to use: the environment variable when named and set, else the file's. */
		public String resolvedPassword() {
			if (!passwordEnv.isEmpty()) {
				String env = System.getenv(passwordEnv);
				if (env != null && !env.isEmpty()) return env;
			}
			return password;
		}
	}

	public static final DesignStoreConfig DEFAULT = new DesignStoreConfig(Backend.FILE, "", Jdbc.DEFAULT, true,
			OthersOvve.BLOCK, true, false, false, 15, false);

	/** The same settings with another {@code others_ovve} (a test, a command). */
	public DesignStoreConfig othersOvve(OthersOvve value) {
		return new DesignStoreConfig(backend, fileDirectory, jdbc, bindOnPickup, value, editRequiresOwner,
				sewWhenUnreachable, unpickWhenUnreachable, retrySeconds, logQueries);
	}

	/** The same settings with another {@code bind_on_pickup}. */
	public DesignStoreConfig bindOnPickup(boolean value) {
		return new DesignStoreConfig(backend, fileDirectory, jdbc, value, othersOvve, editRequiresOwner,
				sewWhenUnreachable, unpickWhenUnreachable, retrySeconds, logQueries);
	}

	/** The same settings with another {@code edit_requires_owner}. */
	public DesignStoreConfig editRequiresOwner(boolean value) {
		return new DesignStoreConfig(backend, fileDirectory, jdbc, bindOnPickup, othersOvve, value,
				sewWhenUnreachable, unpickWhenUnreachable, retrySeconds, logQueries);
	}

	public static final MapCodec<DesignStoreConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("_help", Map.<String, String>of()).forGetter(c -> HELP),
			Backend.CODEC.optionalFieldOf("backend", DEFAULT.backend).forGetter(DesignStoreConfig::backend),
			Codec.STRING.optionalFieldOf("file_directory", DEFAULT.fileDirectory).forGetter(DesignStoreConfig::fileDirectory),
			// optionalFieldOf(key) (no default) always encodes the Optional it's given, unlike
			// optionalFieldOf(key, default) which omits a value that equals the default — jdbc is a
			// record and (unlike the scalar keys below) is never "equal to DEFAULT" by coincidence,
			// but we want it written even when it genuinely is the default, so its own _help shows.
			Jdbc.CODEC.optionalFieldOf("jdbc").xmap(o -> o.orElse(Jdbc.DEFAULT), Optional::of).forGetter(DesignStoreConfig::jdbc),
			Codec.BOOL.optionalFieldOf("bind_on_pickup", DEFAULT.bindOnPickup).forGetter(DesignStoreConfig::bindOnPickup),
			OthersOvve.CODEC.optionalFieldOf("others_ovve", DEFAULT.othersOvve).forGetter(DesignStoreConfig::othersOvve),
			Codec.BOOL.optionalFieldOf("edit_requires_owner", DEFAULT.editRequiresOwner).forGetter(DesignStoreConfig::editRequiresOwner),
			Codec.BOOL.optionalFieldOf("sew_when_unreachable", DEFAULT.sewWhenUnreachable).forGetter(DesignStoreConfig::sewWhenUnreachable),
			Codec.BOOL.optionalFieldOf("unpick_when_unreachable", DEFAULT.unpickWhenUnreachable).forGetter(DesignStoreConfig::unpickWhenUnreachable),
			Codec.intRange(1, 3600).optionalFieldOf("retry_seconds", DEFAULT.retrySeconds).forGetter(DesignStoreConfig::retrySeconds),
			Codec.BOOL.optionalFieldOf("log_queries", DEFAULT.logQueries).forGetter(DesignStoreConfig::logQueries)
	).apply(instance, (help, backend, dir, jdbc, bind, others, edit, sew, unpick, retry, log) ->
			new DesignStoreConfig(backend, dir, jdbc, bind, others, edit, sew, unpick, retry, log)));
}
