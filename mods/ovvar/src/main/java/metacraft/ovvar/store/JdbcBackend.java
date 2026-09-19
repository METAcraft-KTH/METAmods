package metacraft.ovvar.store;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import metacraft.ovvar.Ovvar;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

/**
 * Wardrobes in one table shared by every server:
 * <pre>
 * owner CHAR(36) PRIMARY KEY, version BIGINT, data TEXT (the wardrobe as JSON: designs per chapter
 * and the stash), updated_at BIGINT (epoch millis)
 * </pre>
 * Standard SQL only, so MariaDB, MySQL, PostgreSQL, H2 and SQLite all take it. The compare-and-set
 * is an {@code INSERT} for a row that must not exist (a duplicate key is the conflict) or an
 * {@code UPDATE ... WHERE version = ?} whose row count says whether it won. One connection, reopened
 * after any failure; a single store thread means no pool is needed.
 */
public final class JdbcBackend implements WardrobeBackend {
	private final DesignStoreConfig.Jdbc config;
	private final String table;
	private Connection connection;
	private boolean connectedBefore;

	public JdbcBackend(DesignStoreConfig.Jdbc config) {
		this.config = config;
		if (!config.table().matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("designs.jdbc.table is not a plain identifier: " + config.table());
		this.table = config.table();
	}

	private Connection connection() throws SQLException {
		if (connection != null) {
			try {
				if (connection.isValid(config.connectTimeoutSeconds())) return connection;
			} catch (SQLException ignored) {
				// fall through and reopen
			}
			closeQuietly();
		}
		if (!config.driverClass().isEmpty()) {
			try {
				Class.forName(config.driverClass());
			} catch (ClassNotFoundException e) {
				throw new SQLException("designs.jdbc.driver_class not found: " + config.driverClass(), e);
			}
		}
		DriverManager.setLoginTimeout(config.connectTimeoutSeconds());
		connection = DriverManager.getConnection(config.url(), config.user(), config.resolvedPassword());
		connection.setAutoCommit(true);
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(config.queryTimeoutSeconds());
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
					+ "owner CHAR(36) NOT NULL PRIMARY KEY, version BIGINT NOT NULL, data TEXT NOT NULL, updated_at BIGINT NOT NULL)");
		}
		if (connectedBefore) Ovvar.LOGGER.debug("[ovvar] wardrobe store reconnected: {}", describe());
		else Ovvar.LOGGER.info("[ovvar] wardrobe store connected: {}", describe());
		connectedBefore = true;
		return connection;
	}

	private void closeQuietly() {
		if (connection == null) return;
		try {
			connection.close();
		} catch (SQLException ignored) {
			// closing a broken connection
		}
		connection = null;
	}

	private PreparedStatement prepare(String sql) throws SQLException {
		PreparedStatement statement = connection().prepareStatement(sql);
		statement.setQueryTimeout(config.queryTimeoutSeconds());
		return statement;
	}

	@Override
	public Optional<Wardrobe> load(UUID owner) throws IOException {
		try (PreparedStatement statement = prepare("SELECT version, data FROM " + table + " WHERE owner = ?")) {
			statement.setString(1, owner.toString());
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) return Optional.empty();
				return Optional.of(decode(rows.getString(2)).withVersion(rows.getLong(1)));
			}
		} catch (SQLException e) {
			closeQuietly();
			throw new IOException("wardrobe store: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean store(UUID owner, Wardrobe next, long expectedVersion) throws IOException {
		String data = encode(next);
		long now = System.currentTimeMillis();
		try {
			if (expectedVersion == 0) {
				try (PreparedStatement statement = prepare("INSERT INTO " + table + " (owner, version, data, updated_at) VALUES (?, ?, ?, ?)")) {
					statement.setString(1, owner.toString());
					statement.setLong(2, next.version());
					statement.setString(3, data);
					statement.setLong(4, now);
					statement.executeUpdate();
					return true;
				} catch (SQLIntegrityConstraintViolationException e) {
					return false;   // the row exists: someone else wrote first
				} catch (SQLException e) {
					// PostgreSQL reports a duplicate key with SQLSTATE 23505 but not that subclass.
					if (e.getSQLState() != null && e.getSQLState().startsWith("23")) return false;
					throw e;
				}
			}
			try (PreparedStatement statement = prepare("UPDATE " + table + " SET version = ?, data = ?, updated_at = ? WHERE owner = ? AND version = ?")) {
				statement.setLong(1, next.version());
				statement.setString(2, data);
				statement.setLong(3, now);
				statement.setString(4, owner.toString());
				statement.setLong(5, expectedVersion);
				return statement.executeUpdate() == 1;
			}
		} catch (SQLException e) {
			closeQuietly();
			throw new IOException("wardrobe store: " + e.getMessage(), e);
		}
	}

	private static String encode(Wardrobe wardrobe) throws IOException {
		return Wardrobe.CODEC.encodeStart(JsonOps.INSTANCE, wardrobe).getOrThrow(IOException::new).toString();
	}

	private static Wardrobe decode(String json) throws IOException {
		try {
			return Wardrobe.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(IOException::new);
		} catch (RuntimeException e) {
			throw new IOException("wardrobe store: bad data column " + json, e);
		}
	}

	@Override
	public String describe() {
		return "jdbc " + config.url().replaceAll("(?i)password=[^&;]*", "password=***") + " table " + table
				+ (connection == null ? " (not connected)" : "");
	}

	@Override
	public void close() {
		closeQuietly();
	}
}
