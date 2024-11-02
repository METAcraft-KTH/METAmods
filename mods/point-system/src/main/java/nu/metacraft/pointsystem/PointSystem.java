package nu.metacraft.pointsystem;

import com.mojang.authlib.GameProfile;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PointSystem {
    // https://open.kattis.com/info/ranklist#combinedscore
    // We choose f=5 because they do the same.
    public static final double F = 5;
    public static final double F_INV = 1 / F;
    public static final String TEAM_POINTS_OBJECTIVE = "pointsystem_team_points";
    public static final String TEAM_POINTS_MINIGAME_OBJECTIVE_PREFIX = "pointsystem_team_points_minigame_";
    public static final String PLAYER_POINTS_OBJECTIVE = "pointsystem_player_points";
    public static final String PLAYER_POINTS_MINIGAME_OBJECTIVE_PREFIX = "pointsystem_player_points_minigame_";
    public static final String COMBINED_POINTS_OBJECTIVE = "pointsystem_combined_points";
    public static final String COMBINED_POINTS_MINIGAME_OBJECTIVE_PREFIX = "pointsystem_combined_points_minigame_";

    private final MinecraftServer server;
    private final ExecutorService executor;
    private Connection databaseConnection;
    private Config config;

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

    public SqlQueries getSql() {
        return this.config.getSqlQueries();
    }

    public void loadConfig() throws IOException {
        Path sqlFolder = Path.of("config/point-system/sql");
        Files.createDirectories(sqlFolder);
        Path configFilePath = Path.of("config/point-system/config.json");
        this.config = new Config(configFilePath, sqlFolder);
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
        return getPlayerPoints(max, null);
    }

    public Map<UUID, Integer> getPlayerPointsByMinigame(int max, int minigameId) throws SQLException {
        return getPlayerPoints(max, minigameId);
    }

    private Map<UUID, Integer> getPlayerPoints(int max, @Nullable Integer minigameId) throws SQLException {
        String sql;
        if (minigameId != null) {
            sql = getSql().getPlayerPointsByMinigame;
        } else {
            sql = getSql().getPlayerPoints;
        }
        try (PreparedStatement statement = getDatabaseConnection().prepareStatement(sql)) {
            if (max <= 0) {
                max = 10000;
            }
            if (minigameId != null) {
                statement.setInt(1, minigameId);
                statement.setInt(2, max);
            } else {
                statement.setInt(1, max);
            }

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
        String sql = getSql().addPoints;
        try (PreparedStatement statement = getDatabaseConnection().prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, points);
            statement.setInt(3, minigameId);
            statement.executeUpdate();
        }
    }

    public Map<Integer, Integer> getTeamPoints() throws SQLException {
        return this.getTeamPoints(null);
    }

    public Map<Integer, Integer> getTeamPointsByMinigame(int minigameId) throws SQLException {
        return this.getTeamPoints(minigameId);
    }

    private Map<Integer, Integer> getTeamPoints(@Nullable Integer minigameId) throws SQLException {
        String sql;
        if (minigameId == null) {
            sql = getSql().getTeamPoints;
        } else {
            sql = getSql().getTeamPointsByMinigame;
        }
        try (PreparedStatement statement = getDatabaseConnection().prepareStatement(sql)) {
            if (minigameId != null) {
                statement.setInt(1, minigameId);
            }
            ResultSet res = statement.executeQuery();

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

    public Points getTotalPoints(int max) throws SQLException {
        Map<Integer, Integer> teamPoints = getTeamPoints();
        Map<UUID, Integer> playerPoints = getPlayerPoints(max);
        return new Points(teamPoints, playerPoints);
    }

    public Points getPointsByMinigame(int max, int minigameId) throws SQLException {
        Map<Integer, Integer> teamPoints = getTeamPointsByMinigame(minigameId);
        Map<UUID, Integer> playerPoints = getPlayerPointsByMinigame(max, minigameId);
        return new Points(teamPoints, playerPoints);
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

    private List<Integer> getMinigameIds() throws SQLException {
        String sql = getSql().getMinigameIds;
        List<Integer> minigameIds = new ArrayList<>();
        Connection connection = getDatabaseConnection();
        try (Statement statement = connection.createStatement()) {
            ResultSet res = statement.executeQuery(sql);
            while (res.next()) {
                minigameIds.add(res.getInt(1));
            }
        }
        return minigameIds;
    }

    private Map<Integer, PointTeam> getTeams() throws SQLException {
        Map<Integer, PointTeam> teams = new HashMap<>();

        String sql = "SELECT id, code, short_name, full_name FROM teams";
        try (Statement statement = getDatabaseConnection().createStatement()) {
            ResultSet res = statement.executeQuery(sql);
            while (res.next()) {
                int teamId = res.getInt("id");
                String code = res.getString("code");
                String shortName = res.getString("short_name");
                String fullName = res.getString("full_name");

                PointTeam pointTeam = new PointTeam(teamId, code, shortName, fullName);
                teams.put(teamId, pointTeam);
            }
        }
        return teams;
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

    private void renderTeamPoints(Map<Integer, PointTeam> teams, Map<Integer, Integer> teamPoints, String objectiveName) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        ScoreboardObjective objective = this.getOrCreateObjective(objectiveName);

        for (Map.Entry<Integer, PointTeam> entry : teams.entrySet()) {
            PointTeam team = entry.getValue();
            int points = teamPoints.getOrDefault(team.id(), 0);

            ScoreAccess score = scoreboard.getOrCreateScore(ScoreHolder.fromName(team.code()), objective);
            score.setScore(points);
            score.setDisplayText(Text.literal(team.shortName()));
        }
    }

    private ScoreHolder getPlayerScoreHolder(UUID uuid) {
        UserCache userCache = this.server.getUserCache();
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            return player;
        }
        if (userCache != null) {
            Optional<GameProfile> opt = userCache.getByUuid(uuid);
            if (opt.isPresent()) {
                return ScoreHolder.fromProfile(opt.get());
            }
        }
        return ScoreHolder.fromName(uuid.toString()); // worst case
    }

    private void renderPlayerPoints(Map<UUID, Integer> playerPoints, String objectiveName) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        ScoreboardObjective objective = this.getOrCreateObjective(objectiveName);

        for (Map.Entry<UUID, Integer> entry : playerPoints.entrySet()) {
            UUID playerUuid = entry.getKey();
             int points = entry.getValue();

            ScoreHolder scoreHolder = getPlayerScoreHolder(playerUuid);
            ScoreAccess score = scoreboard.getOrCreateScore(scoreHolder, objective);
            score.setScore(points);
        }
    }

    private void setScoreLine(int line, Text name, int points, ServerScoreboard scoreboard, ScoreboardObjective objective) {
        ScoreHolder scoreHolder = ScoreHolder.fromName("LINE_" + line);
        ScoreAccess score = scoreboard.getOrCreateScore(scoreHolder, objective);
        score.setScore(100 - line);
        score.setDisplayText(name);
        MutableText numberText = Text.literal(String.valueOf(points)).styled(style -> style.withColor(Formatting.RED));
        score.setNumberFormat(new FixedNumberFormat(numberText));
    }

    private void setTextLine(int line, Text text, ServerScoreboard scoreboard, ScoreboardObjective objective) {
        ScoreHolder scoreHolder = ScoreHolder.fromName("LINE_" + line);
        ScoreAccess score = scoreboard.getOrCreateScore(scoreHolder, objective);
        score.setScore(100 - line);
        score.setDisplayText(text);
        score.setNumberFormat(BlankNumberFormat.INSTANCE);
    }

    private void renderCombinedPoints(Map<Integer, PointTeam> teams, Points points, String objectiveName) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        ScoreboardObjective objective = this.getOrCreateObjective(objectiveName);

        setTextLine(0, this.config.universityScoreText, scoreboard, objective);

        List<Integer> topTeams = points.teamPoints().entrySet()
            .stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // Sort by points descending
            .map(Map.Entry::getKey)
            .toList();

        for (int i = 0; i < 5; i++) {
            int lineNr = i + 1;
            if (topTeams.size() <= i) {
                setTextLine(lineNr, Text.empty(), scoreboard, objective);
                continue;
            }
            int teamId = topTeams.get(i);
            PointTeam team = teams.get(teamId);
            int teamPoints = points.teamPoints().get(teamId);
            setScoreLine(lineNr, Text.literal(team.shortName()), teamPoints, scoreboard, objective);
        }

        setTextLine(6, Text.empty(), scoreboard, objective);
        setTextLine(7, this.config.topPlayersText, scoreboard, objective);

        List<UUID> topPlayers = points.playerPoints().entrySet()
            .stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // Sort by points descending
            .map(Map.Entry::getKey)
            .toList();

        for (int i = 0; i < 5; i++) {
            int lineNr = i + 8;
            if (topPlayers.size() <= i) {
                setTextLine(lineNr, Text.empty(), scoreboard, objective);
                continue;
            }
            UUID playerUuid = topPlayers.get(i);
            int playerPoints = points.playerPoints().get(playerUuid);
            ScoreHolder scoreHolder = getPlayerScoreHolder(playerUuid);
            setScoreLine(lineNr, scoreHolder.getStyledDisplayName(), playerPoints, scoreboard, objective);
        }
    }

    public void renderAll() throws SQLException {
        Map<Integer, PointTeam> teams = getTeams();
        List<Integer> minigameIds = getMinigameIds();

        Points totalPoints = getTotalPoints(20);
        Map<Integer, Points> pointsByMinigame = new HashMap<>();

        for (int minigameId : minigameIds) {
            Map<Integer, Integer> teamPoints = getTeamPointsByMinigame(minigameId);
            Map<UUID, Integer> playerPoints = getPlayerPointsByMinigame(20, minigameId);
            pointsByMinigame.put(minigameId, new Points(teamPoints, playerPoints));
        }

        this.server.executeSync(() -> {
            renderTeamPoints(teams, totalPoints.teamPoints(), TEAM_POINTS_OBJECTIVE);
            renderPlayerPoints(totalPoints.playerPoints(), PLAYER_POINTS_OBJECTIVE);
            renderCombinedPoints(teams, totalPoints, COMBINED_POINTS_OBJECTIVE);

            for (Map.Entry<Integer, Points> entry : pointsByMinigame.entrySet()) {
                int minigameId = entry.getKey();
                Points points = entry.getValue();

                renderTeamPoints(teams, points.teamPoints(), TEAM_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
                renderPlayerPoints(points.playerPoints(), PLAYER_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
                renderCombinedPoints(teams, points, COMBINED_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
            }
        });
    }
}
