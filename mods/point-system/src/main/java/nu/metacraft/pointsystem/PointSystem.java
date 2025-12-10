package nu.metacraft.pointsystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private Config config;
    private Int2ObjectMap<PlayerPointStorage> minigamePoints = new Int2ObjectOpenHashMap<>();
    private Int2ObjectMap<PointTeam> teams = new Int2ObjectOpenHashMap<>();
    private Map<UUID, IntSet> playerTeams = new HashMap<>();
    private IntSet excludedMinigameIds = new IntOpenHashSet();

    public PointSystem(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public IntSet getExcludedMinigameIds() {
        return this.excludedMinigameIds;
    }
    public void loadConfig() throws IOException {
        Path configFolder = Path.of("config/point-system/");
        Files.createDirectories(configFolder);
        Path configFilePath = configFolder.resolve("config.json");
        this.config = new Config(configFilePath);
    }

    public void loadData() throws IOException {
        Path path = Path.of("config/point-system/data.json");
        if (Files.notExists(path)) {
            return;
        }
        JsonElement jsonElement = JsonParser.parseReader(Files.newBufferedReader(path));
        JsonObject json = jsonElement.getAsJsonObject();
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(PlayerPointStorage.class, PlayerPointStorage.GSON_DESERIALIZER)
            .create();
        this.teams = new Int2ObjectOpenHashMap<>(
            gson.fromJson(json.get("teams"), new TypeToken<Map<Integer, PointTeam>>() {})
        );
        this.playerTeams = gson.fromJson(json.get("playerTeams"), new TypeToken< Map<UUID, Set<Integer>>>() {})
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new IntOpenHashSet(entry.getValue())
            ));
        this.minigamePoints = new Int2ObjectOpenHashMap<>(
            gson.fromJson(json.get("minigamePoints"), new TypeToken<Map<Integer, PlayerPointStorage>>() {})
        );
        this.excludedMinigameIds = new IntOpenHashSet(
            gson.fromJson(json.get("excludedMinigameIds"), new TypeToken<Set<Integer>>() {})
        );
    }

    public void saveData() throws IOException {
        Path path = Path.of("config/point-system/data.json");
        Files.createDirectories(path.getParent());
        Gson gson = new Gson();
        JsonObject json = new JsonObject();
        json.add("teams", gson.toJsonTree(this.teams));
        json.add("playerTeams", gson.toJsonTree(this.playerTeams));
        json.add("minigamePoints", gson.toJsonTree(this.minigamePoints));
        json.add("excludedMinigameIds", gson.toJsonTree(this.excludedMinigameIds));
        Files.writeString(path, gson.toJson(json));
    }

    public void resetPoints() {
        this.minigamePoints.clear();
    }

    public PlayerPointStorage getPlayerPoints() {
        PlayerPointStorage playerPoints = new PlayerPointStorage();
        for (Int2ObjectMap.Entry<PlayerPointStorage> entry : this.minigamePoints.int2ObjectEntrySet()) {
            int minigameId = entry.getIntKey();
            if (this.excludedMinigameIds.contains(minigameId)) {
                continue;
            }
            PlayerPointStorage storage = entry.getValue();
            playerPoints.merge(storage);
        }
        return playerPoints;
    }

    public PlayerPointStorage getPlayerPointsByMinigame(int minigameId) {
        return this.minigamePoints.get(minigameId);
    }

    public void addPoints(UUID playerUuid, int points, int minigameId) {
        PlayerPointStorage storage = this.minigamePoints.computeIfAbsent(minigameId, (id) -> new PlayerPointStorage());
        storage.addPoints(playerUuid, points);
    }

    public Int2IntMap computeTeamPoints(PlayerPointStorage playerPoints) {
        Int2ObjectMap<IntList> allPointsByTeam = new Int2ObjectOpenHashMap<>();
        for (Object2IntMap.Entry<UUID> entry : playerPoints.getData().object2IntEntrySet()) {
            UUID playerUuid = entry.getKey();
            int points = entry.getIntValue();
            IntSet teamIds = this.playerTeams.getOrDefault(playerUuid, IntSets.emptySet());
            for (int teamId : teamIds) {
                IntList teamPoints = allPointsByTeam.computeIfAbsent(teamId, (id) -> new IntArrayList());
                teamPoints.add(points);
            }
        }
        Int2IntMap teamPoints = new Int2IntOpenHashMap();
        for (Int2ObjectMap.Entry<IntList> entry : allPointsByTeam.int2ObjectEntrySet()) {
            int teamId = entry.getIntKey();
            IntList points = entry.getValue();

            // Sort biggest to lowest
            points.sort((a, b) -> b - a);

            double totalScore = 0;
            for (int i = 0; i < points.size(); i++) {
                int score = points.getInt(i);
                double factor = Math.pow(1 - F_INV, i);
                totalScore += factor * score;
            }

            double teamScore = F_INV * totalScore;
            teamPoints.put(teamId, (int) teamScore);
        }
        return teamPoints;
    }

    public Int2IntMap getTeamPoints() {
        PlayerPointStorage totalPlayerPoints = getPlayerPoints();
        return computeTeamPoints(totalPlayerPoints);
    }

    public Int2IntMap getTeamPointsByMinigame(int minigameId) {
        PlayerPointStorage playerPoints = getPlayerPointsByMinigame(minigameId);
        return computeTeamPoints(playerPoints);
    }

    private Objective getOrCreateObjective(String name) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        Objective objective = scoreboard.getObjective(name);
        if (objective != null) {
            return objective;
        }
        return scoreboard.addObjective(
            name,
            ObjectiveCriteria.DUMMY,
            Component.literal(name),
            ObjectiveCriteria.RenderType.INTEGER,
            true,
            null
        );
    }

    private IntSet getMinigameIds() {
        return this.minigamePoints.keySet();
    }

    private Map<Integer, PointTeam> getTeams() {
        return this.teams;
    }

    @Nullable
    private PointTeam getTeamByCode(String code) {
        for (PointTeam team : this.teams.values()) {
            if (team.code().equals(code)) {
                return team;
            }
        }
        return null;
    }

    public void addTeam(String type, String code, String shortName, String fullName) {
        int teamId = 1;
        while (this.teams.containsKey(teamId)) {
            teamId++;
        }
        this.teams.put(teamId, new PointTeam(teamId, type, code, shortName, fullName));
    }

    public void joinOnlyTeams(UUID playerUuid, String[] codes) {
        List<String> teamNames = new ArrayList<>();
        IntSet teamIds = new IntOpenHashSet(codes.length);
        for (String code : codes) {
            PointTeam team = getTeamByCode(code);
            if (team == null) {
                continue;
            }
            teamIds.add(team.id());
            teamNames.add(team.fullName());
        }
        this.playerTeams.put(playerUuid, teamIds);
        ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            player.sendSystemMessage(Component.empty()
                .append(Component.literal("You selected ")
                    .append(Component.literal(String.join(", ", teamNames)).withStyle(style -> style.applyFormat(ChatFormatting.YELLOW))))
            );
        }
    }

    private void renderTeamPoints(Map<Integer, PointTeam> teams, Map<Integer, Integer> teamPoints, String objectiveName) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        Objective objective = this.getOrCreateObjective(objectiveName);

        for (Map.Entry<Integer, PointTeam> entry : teams.entrySet()) {
            PointTeam team = entry.getValue();
            int points = teamPoints.getOrDefault(team.id(), 0);

            ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(team.code()), objective);
            score.set(points);
            score.display(Component.literal(team.shortName()));
        }
    }

    private ScoreHolder getPlayerScoreHolder(UUID uuid) {
        var userCache = this.server.services().nameToIdCache();
        ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player;
        }
	    Optional<NameAndId> opt = userCache.get(uuid);
	    return opt.map(
				nameAndId -> ScoreHolder.fromGameProfile(
						new GameProfile(nameAndId.id(), nameAndId.name())
				)
	    ).orElseGet(
				() -> ScoreHolder.forNameOnly(uuid.toString())
	    );
    }

    private void renderPlayerPoints(PlayerPointStorage playerPoints, String objectiveName) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        Objective objective = this.getOrCreateObjective(objectiveName);

        for (Object2IntMap.Entry<UUID> entry : playerPoints.getData().object2IntEntrySet()) {
            UUID playerUuid = entry.getKey();
             int points = entry.getIntValue();

            ScoreHolder scoreHolder = getPlayerScoreHolder(playerUuid);
            ScoreAccess score = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
            score.set(points);
        }
    }

    private void setScoreLine(int line, Component name, int points, ServerScoreboard scoreboard, Objective objective) {
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly("LINE_" + line);
        ScoreAccess score = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
        score.set(100 - line);
        score.display(name);
        MutableComponent numberText = Component.literal(String.valueOf(points)).withStyle(style -> style.withColor(ChatFormatting.RED));
        score.numberFormatOverride(new FixedFormat(numberText));
    }

    private void setTextLine(int line, Component text, ServerScoreboard scoreboard, Objective objective) {
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly("LINE_" + line);
        ScoreAccess score = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
        score.set(100 - line);
        score.display(text);
        score.numberFormatOverride(BlankFormat.INSTANCE);
    }

    private void renderCombinedPoints(Map<Integer, PointTeam> teams, PlayerPointStorage playerPoints, Int2IntMap teamPoints, String objectiveName) {
        ServerScoreboard scoreboard = this.server.getScoreboard();
        Objective objective = this.getOrCreateObjective(objectiveName);

        setTextLine(0, this.config.universityScoreText, scoreboard, objective);

        List<Integer> topTeams = teamPoints.int2IntEntrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue())) // Sort by points descending
            .map(Map.Entry::getKey)
            .toList();

        for (int i = 0; i < 5; i++) {
            int lineNr = i + 1;
            if (topTeams.size() <= i) {
                setTextLine(lineNr, Component.empty(), scoreboard, objective);
                continue;
            }
            int teamId = topTeams.get(i);
            PointTeam team = teams.get(teamId);
            int points = teamPoints.get(teamId);
            setScoreLine(lineNr, Component.literal(team.shortName()), points, scoreboard, objective);
        }

        setTextLine(6, Component.empty(), scoreboard, objective);
        setTextLine(7, this.config.topPlayersText, scoreboard, objective);

        List<UUID> topPlayers = playerPoints.getData().object2IntEntrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue())) // Sort by points descending
            .map(Map.Entry::getKey)
            .toList();

        for (int i = 0; i < 5; i++) {
            int lineNr = i + 8;
            if (topPlayers.size() <= i) {
                setTextLine(lineNr, Component.empty(), scoreboard, objective);
                continue;
            }
            UUID playerUuid = topPlayers.get(i);
            int points = playerPoints.getPoints(playerUuid);
            ScoreHolder scoreHolder = getPlayerScoreHolder(playerUuid);
            setScoreLine(lineNr, scoreHolder.getFeedbackDisplayName(), points, scoreboard, objective);
        }
    }

    public void renderAll() {
        Map<Integer, PointTeam> teams = getTeams();
        IntSet minigameIds = getMinigameIds();

        // Total points
        Int2IntMap totalTeamPoints = getTeamPoints();
        PlayerPointStorage totalPlayerPoints = getPlayerPoints();
        renderTeamPoints(teams, totalTeamPoints, TEAM_POINTS_OBJECTIVE);
        renderPlayerPoints(totalPlayerPoints, PLAYER_POINTS_OBJECTIVE);
        renderCombinedPoints(teams, totalPlayerPoints, totalTeamPoints, COMBINED_POINTS_OBJECTIVE);

        // Points per minigame
        for (int minigameId : minigameIds) {
            Int2IntMap teamPoints = getTeamPointsByMinigame(minigameId);
            PlayerPointStorage playerPoints = getPlayerPointsByMinigame(minigameId);

            renderTeamPoints(teams, teamPoints, TEAM_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
            renderPlayerPoints(playerPoints, PLAYER_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
            renderCombinedPoints(teams, playerPoints, teamPoints, COMBINED_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
        }
    }
}
