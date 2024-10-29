package nu.metacraft.pointsystem;

import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PointSystem {
    // https://open.kattis.com/info/ranklist#combinedscore
    // We choose f=5 because they do the same.
    public static final double F = 5;
    public static final double F_INV = 1 / F;

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
                    full_name VARCHAR(255),
                    type VARCHAR(50) NOT NULL
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
                if (totalPoints < 0) {
                    totalPoints = 0;
                }
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

    /**
     * Get team points.
     *
     * @return A map of (team id -> points).
     * @throws SQLException If an SQL error occurs.
     */
    public Map<Integer, Integer> getTeamPoints() throws SQLException {
        String sql = """
            SELECT
                points.player_uuid, team_id, SUM(points) AS total_points
            FROM points
            JOIN player_teams
                ON player_teams.player_uuid = points.player_uuid
            GROUP BY
                points.player_uuid, team_id
            ORDER BY
                team_id, total_points DESC
            """;
        try (Statement statement = getDatabaseConnection().createStatement()) {
            ResultSet res = statement.executeQuery(sql);

            record PlayerPoints(UUID playerUuid, int points) {}

            Map<Integer, List<PlayerPoints>> pointsPerTeam = new HashMap<>();
            while (res.next()) {
                String uuidStr = res.getString("player_uuid");
                int teamId = res.getInt("team_id");
                int totalPoints = res.getInt("total_points");
                if (totalPoints < 0) {
                    totalPoints = 0;
                }
                UUID uuid = UUID.fromString(uuidStr);
                PlayerPoints pp = new PlayerPoints(uuid, totalPoints);

                List<PlayerPoints> playerPoints = pointsPerTeam.computeIfAbsent(teamId, k -> new ArrayList<>());
                playerPoints.add(pp);
            }

            Map<Integer, Integer> teamScores = new HashMap<>();
            for (Map.Entry<Integer, List<PlayerPoints>> entry : pointsPerTeam.entrySet()) {
                int teamId = entry.getKey();
                List<PlayerPoints> points = entry.getValue();

                // Sort biggest to lowest
                points.sort((a, b) -> b.points - a.points);

                double totalScore = 0;
                for (int i = 0; i < points.size(); i++) {
                    int score = points.get(i).points;
                    double factor = Math.pow(1 - F_INV, i);
                    totalScore += factor * score;
                }

                double teamScore = F_INV * totalScore;
                teamScores.put(teamId, (int) teamScore);
            }
            return teamScores;
        }
    }

    private ScoreboardObjective getOrCreateObjective(String name) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(name);
        if (objective != null) {
            return objective;
        }
        return scoreboard.addObjective(
            name,
            ScoreboardCriterion.DUMMY,
            Text.literal(name),
            ScoreboardCriterion.RenderType.INTEGER,
            true,
            null
        );
    }

    public void renderTeamPoints() throws SQLException {
        Map<Integer, Integer> teamPoints = getTeamPoints();

        ServerScoreboard scoreboard = this.server.getScoreboard();
        ScoreboardObjective objective = this.getOrCreateObjective("pointsystem_team_points");

        String sql = "SELECT id, code, short_name FROM teams";
        try (Statement statement = getDatabaseConnection().createStatement()) {
            ResultSet res = statement.executeQuery(sql);
            while (res.next()) {
                int teamId = res.getInt("id");
                String code = res.getString("code");
                String shortName = res.getString("short_name");
                int points = teamPoints.getOrDefault(teamId, 0);

                ScoreAccess score = scoreboard.getOrCreateScore(ScoreHolder.fromName(code), objective);
                score.setScore(points);
                score.setDisplayText(Text.literal(shortName));

            }
        }
    }

    public void addTeam(String type, String code, String shortName, String fullName) throws SQLException {
        String sql = """
            INSERT INTO teams (type, code, short_name, full_name) VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement statement = getDatabaseConnection().prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setString(2, code);
            statement.setString(3, shortName);
            statement.setString(4, fullName);
            statement.executeUpdate();
        }
    }

    public void joinOnlyTeams(UUID playerUuid, String[] codes) throws SQLException {
        Connection connection = getDatabaseConnection();
        // Remove old teams
        String deleteSql = """
            DELETE FROM player_teams WHERE player_uuid = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
        for (String code : codes) {
            if (code.isEmpty() || code.isBlank()) {
                continue;
            }
            String insertSql = """
                INSERT INTO player_teams (player_uuid, team_id) VALUES (?, (SELECT id FROM teams WHERE code = ?))
                """;
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, code);
                statement.executeUpdate();
            }
        }
    }
}
