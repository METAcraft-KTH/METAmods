package nu.metacraft.pointsystem;

import net.minecraft.server.MinecraftServer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PointSystem {
    private final MinecraftServer server;
    private final ExecutorService executor;
    private Connection databaseConnection;

    public PointSystem(MinecraftServer server) {
        this.server = server;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void shutdown() {
        this.executor.shutdown();
        if (this.databaseConnection != null) {
            PointSystemMod.LOGGER.info("Closing database connection.");
            try {
                this.databaseConnection.close();
            } catch (SQLException e) {
                PointSystemMod.LOGGER.error("Failed to close database", e);
            }
        }
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Executor getExecutor() {
        return this.executor;
    }

    /**
     * Get an active database connection. This method may block to connect to the
     * database, so do not run it on the main server thread.
     * <p>
     * Prefer calling this method and performing all other database operations on the
     * point system executor {@link #getExecutor()}.
     *
     * @return The database connection.
     * @throws SQLException If an SQL exception occurs when connecting to checking validity.
     */
    public Connection getDatabaseConnection() throws SQLException {
        if (this.databaseConnection == null || !this.databaseConnection.isValid(5)) {
            this.databaseConnection = DriverManager.getConnection("jdbc:sqlite:pointsystem.db");
            this.initDatabase(this.databaseConnection);
        }
        return this.databaseConnection;
    }

    private void initDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS points (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    player_uuid VARCHAR(50),
                    points INTEGER,
                    minigame_id INTEGER
                );
                """
            );
            statement.execute("""
                CREATE TABLE IF NOT EXISTS teams (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code VARCHAR(50) NOT NULL,
                    short_name VARCHAR(255) NOT NULL,
                    full_name VARCHAR(255)
                );
                """
            );
            statement.execute("""
                CREATE TABLE IF NOT EXISTS player_teams (
                    player_uuid VARCHAR(50),
                    team_id INTEGER,
                    PRIMARY KEY (player_uuid, team_id),
                    FOREIGN KEY (team_id) REFERENCES teams(id)
                );
                """
            );
        }
    }

    public Map<UUID, Integer> getPlayerPoints(int max) throws SQLException {
        String sql = "SELECT player_uuid, SUM(points) AS total_points FROM points GROUP BY player_uuid ORDER BY total_points LIMIT ?";
        try (PreparedStatement statement = getDatabaseConnection().prepareStatement(sql)) {
            if (max <= 0) {
                max = 10000;
            }
            statement.setInt(1, max);

            ResultSet res = statement.executeQuery();

            Map<UUID, Integer> points = new HashMap<>();
            while (res.next()) {
                String uuidStr = res.getString("player_uuid");
                int totalPoints = res.getInt("total_points");
                UUID uuid = UUID.fromString(uuidStr);
                points.put(uuid, totalPoints);
            }
            return points;
        }
    }

    public void addPoints(UUID playerUuid, int points, int minigameId) throws SQLException {
        String sql = """
            INSERT INTO points (player_uuid, points, minigame_id) VALUES (?, ?, ?)
            """;
        try (PreparedStatement statement = getDatabaseConnection().prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, points);
            statement.setInt(3, minigameId);
            statement.executeUpdate();
        }
    }

}
