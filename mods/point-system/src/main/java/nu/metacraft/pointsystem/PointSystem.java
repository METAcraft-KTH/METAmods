package nu.metacraft.pointsystem;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.TracingExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import nu.metacraft.player_specific_scoreboards.PlayerScoreboards;
import nu.metacraft.player_specific_scoreboards.util.PlayerScoreboard;
import nu.metacraft.pointsystem.mixin.UtilAccessor;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PointSystem implements AutoCloseable {
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

	public static final String POINTS_TABLE = "points";
	public static final String TEAMS_TABLE = "teams";
	public static final String PLAYER_TEAMS_TABLE = "player_teams";
	public static final String MINIGAMES_TABLE = "minigames";

	public static final String PLAYER_ID = "player_id";
	public static final String MINIGAME_ID = "minigame_id";
	public static final String TEAM_ID = "team_id";

	private static final String SQLITE = "SQLite JDBC";
	private static final String POSTGRESQL = "PostgreSQL JDBC Driver";

	private final TracingExecutor executor = UtilAccessor.callMakeExecutor("PointSystemDatabaseHandler");

	private static final String POINTS_TEAM_JOIN = PLAYER_TEAMS_TABLE + " inner join " + POINTS_TABLE + " on " +
			PLAYER_TEAMS_TABLE + "." + PLAYER_ID + " = " + POINTS_TABLE + "." + PLAYER_ID;

	private static final String ONLY_NOT_EXCLUDED_CONDITION = "left outer join " + MINIGAMES_TABLE + " on " + MINIGAMES_TABLE + "." + MINIGAME_ID + " = " + POINTS_TABLE + "." + MINIGAME_ID +
			" where excluded is null or excluded = false";

	private final MinecraftServer server;
	private final Connection dbConnection;

	private boolean showOwnScores = false;

	private void setUUID(PreparedStatement statement, int index, UUID uuid) throws SQLException {
		statement.setObject(index, uuid);
	}

	private UUID getUUID(ResultSet result, int column) throws SQLException {
		return switch (result.getObject(column)) {
			case UUID uuid -> uuid;
			case String string -> {
				try {
					yield UUID.fromString(string);
				} catch (IllegalArgumentException e) {
					throw new SQLException(e);
				}
			}
			default -> throw new SQLException(result.getObject(column) + " is not a UUID!");
		};
	}

	private String getUUIDType() throws SQLException {
		return switch (dbConnection.getMetaData().getDriverName()) {
			case SQLITE -> "BLOB";
			default -> "UUID";
		};
	}

	private String createIntPrimaryKeyElement(String name) throws SQLException {
		return switch (dbConnection.getMetaData().getDriverName()) {
			case POSTGRESQL -> name + " serial primary key";
			default -> name + " integer primary key";
		};
	}

	public PointSystem(MinecraftServer server) {
		this.server = server;
		try {
			this.dbConnection = DriverManager.getConnection(
					PointSystemConfig.getInstance().dbURL(),
					PointSystemConfig.getInstance().username(),
					PointSystemConfig.getInstance().password()
			);
			try (
					var statement = dbConnection.prepareStatement(
							"create table if not exists " + MINIGAMES_TABLE + " (" +
									MINIGAME_ID + " int primary key," +
									"excluded bool not null" +
							")"
					)
			) {
				statement.execute();
			}
			try (
					var statement = dbConnection.prepareStatement(
							"create table if not exists " + POINTS_TABLE + " (" +
									PLAYER_ID + " " + getUUIDType() + " not null," +
									MINIGAME_ID + " int not null," +
									"points int not null," +
									"constraint PK_" + POINTS_TABLE + " primary key (" + PLAYER_ID + ", " + MINIGAME_ID + ")" +
							")"
					);
			) {
				statement.execute();
			}
			try (
					var statement = dbConnection.prepareStatement(
							"create table if not exists " + TEAMS_TABLE + " (" +
									createIntPrimaryKeyElement(TEAM_ID) + "," +
									"type varchar(255) not null," +
									"code varchar(255) not null," +
									"short_name varchar(255) not null," +
									"full_name varchar(255) not null" +
							")"
					);
			) {
				statement.execute();
			}
			try (
					var statement = dbConnection.prepareStatement(
							"create table if not exists " + PLAYER_TEAMS_TABLE + " (" +
									PLAYER_ID + " " + getUUIDType() + " not null," +
									TEAM_ID + " int not null," +
									"constraint PK_" + PLAYER_TEAMS_TABLE + " primary key (" + PLAYER_ID + ", " + TEAM_ID + ")," +
									"foreign key (" + TEAM_ID + ") references " + TEAMS_TABLE + "(" + TEAM_ID + ")" +
							")"
					)
			) {
				statement.execute();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public MinecraftServer getServer() {
		return server;
	}

	public CompletableFuture<IntSet> getExcludedMinigameIds() {
		return CompletableFuture.supplyAsync(
				() -> {
					try (var statement = dbConnection.prepareStatement("select " + MINIGAME_ID + " from " + MINIGAMES_TABLE + " where excluded = true")) {
						var result = statement.executeQuery();
						IntSet set = new IntOpenHashSet();
						while (result.next()) {
							set.add(result.getInt(1));
						}
						return set;
					} catch (SQLException e) {
						throw new RuntimeException(e);
					}

				}, executor
		);
	}

	public void addExcludedMinigame(int id) {
		executor.execute(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							 createUpsert(
									 "insert into " + MINIGAMES_TABLE + " (" + MINIGAME_ID + ", excluded) values (?, true)",
									 MINIGAME_ID,
									 "update set excluded = true"
							 )
					)
			) {
				statement.setInt(1, id);
				statement.execute();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public void removeExcludedMinigame(int id) {
		executor.execute(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							"update " + MINIGAMES_TABLE + " set excluded = false where " + MINIGAME_ID + " = ?"
					)
			) {
				statement.setInt(1, id);
				statement.execute();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public void resetPoints(int minigame) {
		executor.execute(() -> {
			try (
					var statement = dbConnection.prepareStatement("delete from " + POINTS_TABLE + " where " + MINIGAME_ID + " = ?")
			) {
				statement.setInt(1, minigame);
				statement.execute();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static String prepareList(int elements) {
		return "(" + IntStream.range(0, elements).mapToObj(i -> "?").collect(Collectors.joining(",")) + ")";
	}

	public void resetPoints(Collection<UUID> players) {
		if (players.isEmpty()) return;
		executor.execute(() -> {
			try (
					var statement = dbConnection.prepareStatement("delete from " + POINTS_TABLE + " where " + PLAYER_ID + " in " + prepareList(players.size()))
			) {
				int index = 1;
				for (var uuid : players) {
					setUUID(statement, index, uuid);
					index++;
				}
				statement.execute();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public void resetPoints(int minigame, Collection<UUID> players) {
		if (players.isEmpty()) return;
		executor.execute(() -> {
			try (
					var statement = dbConnection.prepareStatement("delete from " + POINTS_TABLE + " where " + MINIGAME_ID + " = ? and " + PLAYER_ID + " in " + prepareList(players.size()))
			) {
				statement.setInt(1, minigame);
				int index = 2;
				for (var uuid : players) {
					setUUID(statement, index, uuid);
					index++;
				}
				statement.execute();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public void resetPoints() {
		executor.execute(() -> {
			try {
				dbConnection.createStatement().execute("delete from " + POINTS_TABLE);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private CompletableFuture<PlayerPointStorage> getPlayerPoints(String condition, SQLConsumer<PreparedStatement> valueCapture) {
		return CompletableFuture.supplyAsync(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							"select player_id, points from " + POINTS_TABLE + " " + condition
					)
			) {
				valueCapture.accept(statement);
				var result = statement.executeQuery();
				PlayerPointStorage storage = new PlayerPointStorage();
				while (result.next()) {
					storage.addPoints(getUUID(result, 1), result.getInt(2));
				}
				return storage;
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}, executor);
	}

	public CompletableFuture<PlayerPointStorage> getPlayerPoints() {
		return getPlayerPoints(
				ONLY_NOT_EXCLUDED_CONDITION,
				s -> {}
		);
	}

	public CompletableFuture<PlayerPointStorage> getPlayerPointsByMinigame(int minigameId) {
		return getPlayerPoints(" where " + MINIGAME_ID + " = ?", s -> s.setInt(1, minigameId));
	}

	private String createUpsert(String insert, String conflict, String update) {
		return insert + " on conflict (" + conflict +  ") do " + update;
	}

	public void addPoints(UUID playerUuid, int points, int minigameId) {
		executor.execute(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							createUpsert(
									"insert into " + POINTS_TABLE + " (" + PLAYER_ID + ", " + MINIGAME_ID + ", points) values (?, ?, ?)",
									PLAYER_ID + ", " + MINIGAME_ID,
									"update set points = " + POINTS_TABLE + ".points + ?"
							)
					)
			) {
				setUUID(statement, 1, playerUuid);
				statement.setInt(2, minigameId);
				statement.setInt(3, points);
				statement.setInt(4, points);
				statement.execute();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public void setShowOwnScores(boolean showOwnScores) {
		this.showOwnScores = showOwnScores;
	}

	@FunctionalInterface
	public interface SQLConsumer<T> {
		void accept(T var1) throws SQLException;
	}

	public CompletableFuture<Int2IntMap> computeTeamPoints(
			String condition, SQLConsumer<PreparedStatement> valueCapture
	) {
		return CompletableFuture.supplyAsync(
			() -> {
				try (
						var statement = dbConnection.prepareStatement(
								"select team_id, points from " + POINTS_TEAM_JOIN + " " + condition
						)
				) {
					Int2ObjectMap<IntList> allPointsByTeam = new Int2ObjectOpenHashMap<>();

					valueCapture.accept(statement);

					var result = statement.executeQuery();
					while (result.next()) {
						int teamId = result.getInt(1);
						int points = result.getInt(2);
						IntList teamPoints = allPointsByTeam.computeIfAbsent(teamId, (id) -> new IntArrayList());
						teamPoints.add(points);
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
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}, executor
		);
	}

	public CompletableFuture<Int2IntMap> getTeamPoints() {
		return computeTeamPoints(ONLY_NOT_EXCLUDED_CONDITION, s -> {});
	}

	public CompletableFuture<Int2IntMap> getTeamPointsByMinigame(int minigameId) {
		return computeTeamPoints(
				"where " + MINIGAME_ID + " = ?", s -> s.setInt(1, minigameId)
		);
	}

	private static Objective getOrCreateObjective(MinecraftServer server, String name) {
		ServerScoreboard scoreboard = server.getScoreboard();
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

	private CompletableFuture<IntSet> getMinigameIds() {
		return CompletableFuture.supplyAsync(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							"select distinct " + MINIGAME_ID + " from " + POINTS_TABLE
					)
			) {
				IntSet minigameIds = new IntOpenHashSet();
				var result = statement.executeQuery();
				while (result.next()) {
					minigameIds.add(result.getInt(1));
				}
				return minigameIds;
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}, executor);
	}

	private CompletableFuture<List<PointTeam>> getTeamsForPlayer(UUID playerID) {
		return CompletableFuture.supplyAsync(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							"select " + TEAMS_TABLE + "." + TEAM_ID + ", type, code, short_name, full_name from " +
								TEAMS_TABLE + " inner join " + PLAYER_TEAMS_TABLE + " on " +
								PLAYER_TEAMS_TABLE + "." + TEAM_ID + " = " + TEAMS_TABLE + "." + TEAM_ID +
								" where " + PLAYER_ID + " = ?"
					)
			) {
				setUUID(statement, 1, playerID);
				List<PointTeam> teams = new ArrayList<>();
				var result = statement.executeQuery();
				while (result.next()) {
					teams.add(new PointTeam(
							result.getInt(1), result.getString(2), result.getString(3),
							result.getString(4), result.getString(5)
					));
				}
				return teams;
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}, executor);
	}

	private CompletableFuture<Map<Integer, PointTeam>> getTeams() {
		return CompletableFuture.supplyAsync(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							"select team_id, type, code, short_name, full_name from " + TEAMS_TABLE
					)
			) {
				Map<Integer, PointTeam> teams = new HashMap<>();
				var result = statement.executeQuery();
				while (result.next()) {
					var team = new PointTeam(
							result.getInt(1), result.getString(2), result.getString(3),
							result.getString(4), result.getString(5)
					);
					teams.put(team.id(), team);
				}
				return teams;
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}, executor);
	}

	public void addTeam(String type, String code, String shortName, String fullName) {
		executor.execute(() -> {
			try (
					var statement = dbConnection.prepareStatement(
							"insert into " + TEAMS_TABLE + " (type, code, short_name, full_name) values (?, ?, ?, ?)"
					)
			) {
				statement.setString(1, type);
				statement.setString(2, code);
				statement.setString(3, shortName);
				statement.setString(4, fullName);
				statement.execute();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public void joinOnlyTeams(UUID playerUuid, String[] codes) {
		executor.execute(() -> {
			try {
				try (
						var remove = dbConnection.prepareStatement(
								"delete from " + PLAYER_TEAMS_TABLE + " where " + PLAYER_ID + "= ?"
						)
				) {
					setUUID(remove, 1, playerUuid);
					remove.execute();
				}
				try (
						var add = dbConnection.prepareStatement(
								"insert into " + PLAYER_TEAMS_TABLE + " (" + PLAYER_ID + ", " + TEAM_ID + ")" +
										" select ?, " + TEAM_ID + " from " + TEAMS_TABLE + " where code = ?"
						)
				) {
					setUUID(add, 1, playerUuid);
					for (var code : codes) {
						add.setString(2, code);
						add.execute();
					}
				}

				try (
						var getNames = dbConnection.prepareStatement(
								"select full_name from " + TEAMS_TABLE + " inner join " + PLAYER_TEAMS_TABLE + " on " +
										TEAMS_TABLE + "." + TEAM_ID + "=" + PLAYER_TEAMS_TABLE + "." + TEAM_ID + " where " + PLAYER_ID + " = ?"
						)
				) {
					setUUID(getNames, 1, playerUuid);
					var result = getNames.executeQuery();
					List<String> teamNames = new ArrayList<>();
					while (result.next()) {
						teamNames.add(result.getString(1));
					}

					server.execute(() -> {
						ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
						if (player != null) {
							player.sendSystemMessage(Component.empty()
									.append(Component.literal("You selected ")
											.append(Component.literal(String.join(", ", teamNames)).withStyle(style -> style.applyFormat(ChatFormatting.YELLOW))))
							);
						}
					});
				}
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private void renderTeamPoints(Map<Integer, PointTeam> teams, Map<Integer, Integer> teamPoints, String objectiveName) {
		ServerScoreboard scoreboard = this.server.getScoreboard();
		Objective objective = getOrCreateObjective(server, objectiveName);

		for (Map.Entry<Integer, PointTeam> entry : teams.entrySet()) {
			PointTeam team = entry.getValue();
			int points = teamPoints.getOrDefault(team.id(), 0);

			ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(team.code()), objective);
			score.set(points);
			score.display(Component.literal(team.shortName()));
		}
		updateAllPlayerScores();
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
		Objective objective = getOrCreateObjective(server, objectiveName);

		for (Object2IntMap.Entry<UUID> entry : playerPoints.getData().object2IntEntrySet()) {
			UUID playerUuid = entry.getKey();
			 int points = entry.getIntValue();

			ScoreHolder scoreHolder = getPlayerScoreHolder(playerUuid);
			ScoreAccess score = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
			score.set(points);
		}
		updateAllPlayerScores();
	}



	private void setScoreLine(int line, Component name, int points, LineWriter lineWriter) {
		MutableComponent numberText = Component.literal(String.valueOf(points)).withStyle(style -> style.withColor(ChatFormatting.RED));
		lineWriter.writeLine(line, name, new FixedFormat(numberText));
	}

	private void setTextLine(int line, Component text, LineWriter lineWriter) {
		lineWriter.writeLine(line, text, BlankFormat.INSTANCE);
	}
	private void renderCombinedPoints(
			Map<Integer, PointTeam> teams, PlayerPointStorage playerPoints, Int2IntMap teamPoints, LineWriter lineWriter
	) {
		renderCombinedPoints(
				teams, playerPoints, teamPoints, lineWriter,
				(name, team) -> name,
				team -> false,
				(name, player) -> name,
				player -> false
		);
	}

	private <T> Stream<T> sort(Stream<T> stream, ToIntFunction<T> getIntValue) {
		return stream.sorted((a, b) -> Integer.compare(getIntValue.applyAsInt(b), getIntValue.applyAsInt(a)));
	}

	private void renderCombinedPoints(
			Map<Integer, PointTeam> teams, PlayerPointStorage playerPoints, Int2IntMap teamPoints, LineWriter lineWriter,
			BiFunction<Component, PointTeam, Component> teamHighlighter, Predicate<PointTeam> teamToAlwaysInclude,
			BiFunction<Component, UUID, Component> playerHighlighter, Predicate<UUID> playerToAlwaysInclude
	) {
		setTextLine(0, PointSystemConfig.getInstance().universityScoreText(), lineWriter);

		Int2IntMap alwaysIncludedTeams = new Int2IntOpenHashMap();
		Int2IntMap regularTeams = new Int2IntOpenHashMap();
		for (var team : teams.entrySet()) {
			if (!teamPoints.containsKey(team.getKey())) continue;
			if (teamToAlwaysInclude.test(team.getValue())) {
				alwaysIncludedTeams.put(team.getKey(), teamPoints.get(team.getKey()));
			} else {
				regularTeams.put(team.getKey(), teamPoints.get(team.getKey()));
			}
		}

		IntList topTeams = IntArrayList.toList(
				sort(
						Stream.concat(
								alwaysIncludedTeams.int2IntEntrySet().stream(),
								sort(regularTeams.int2IntEntrySet().stream(), Int2IntMap.Entry::getIntValue).limit(5 - alwaysIncludedTeams.size())
						),
						Int2IntMap.Entry::getIntValue
				).mapToInt(Int2IntMap.Entry::getIntKey)
		);

		for (int i = 0; i < 5; i++) {
			int lineNr = i + 1;
			if (topTeams.size() <= i) {
				setTextLine(lineNr, Component.empty(), lineWriter);
				continue;
			}
			int teamId = topTeams.getInt(i);
			PointTeam team = teams.get(teamId);
			int points = teamPoints.get(teamId);
			setScoreLine(lineNr, teamHighlighter.apply(Component.literal(team.shortName()), team), points, lineWriter);
		}

		setTextLine(6, Component.empty(), lineWriter);
		setTextLine(7, PointSystemConfig.getInstance().topPlayersText(), lineWriter);

		Object2IntMap<UUID> alwaysIncludedPlayers = new Object2IntOpenHashMap<>();
		Object2IntMap<UUID> regularPlayers = new Object2IntOpenHashMap<>();

		for (var player : playerPoints.getData().object2IntEntrySet()) {
			if (playerToAlwaysInclude.test(player.getKey())) {
				alwaysIncludedPlayers.put(player.getKey(), player.getIntValue());
			} else {
				regularPlayers.put(player.getKey(), player.getIntValue());
			}
		}

		List<UUID> topPlayers = sort(
				Stream.concat(
						alwaysIncludedPlayers.object2IntEntrySet().stream(),
						sort(
								regularPlayers.object2IntEntrySet().stream(),
								Object2IntMap.Entry::getIntValue
						).limit(5 - alwaysIncludedPlayers.size())
				),
				Object2IntMap.Entry::getIntValue
		).map(Map.Entry::getKey).toList();

		for (int i = 0; i < 5; i++) {
			int lineNr = i + 8;
			if (topPlayers.size() <= i) {
				setTextLine(lineNr, Component.empty(), lineWriter);
				continue;
			}
			UUID playerUuid = topPlayers.get(i);
			int points = playerPoints.getPoints(playerUuid);
			ScoreHolder scoreHolder = getPlayerScoreHolder(playerUuid);
			setScoreLine(lineNr, playerHighlighter.apply(scoreHolder.getFeedbackDisplayName(), playerUuid), points, lineWriter);
		}
	}

	private void updateAllPlayerScores() {
		for (var player : server.getPlayerList().getPlayers()) {
			updatePlayerScore(player);
		}
	}

	private boolean matchesAnyTeam(List<PointTeam> teams, String code) {
		for (var team : teams) {
			if (code.equals(team.code())) {
				return true;
			}
		}
		return false;
	}

	private static Component highLightPlayer(Component playerName) {
		return playerName.copy().withStyle(style -> style.withBold(true).withColor(ChatFormatting.GREEN));
	}

	private static Component highLightTeam(Component teamName) {
		return teamName.copy().withStyle(style -> style.withBold(true).withColor(ChatFormatting.AQUA));
	}

	private static int getMinigameIDFromScoreboardObjectiveName(String name) {
		try {
			return Integer.parseInt(name.substring(COMBINED_POINTS_MINIGAME_OBJECTIVE_PREFIX.length()));
		} catch (NumberFormatException e) {
			PointSystemMod.LOGGER.error(e.getMessage(), e);
			return 0;
		}
	}

	private void renderCombinedPointsForPlayer(
			ServerPlayer player, PlayerPointStorage playerPoints, Int2IntMap teamPoints, Objective objective
	) {
		getTeamsForPlayer(player.getUUID()).thenAccept(playerTeams -> {
			getTeams().thenAccept(teams -> {
				server.execute(() -> {
					var writer = new PlayerSpecificLineWriter();
					renderCombinedPoints(
							teams, playerPoints, teamPoints, writer,
							(name, team) -> {
								if (matchesAnyTeam(playerTeams, team.code())) {
									return highLightTeam(name);
								}
								return name;
							},
							team -> matchesAnyTeam(playerTeams, team.code()),
							(name, playerID) -> {
								if (playerID.equals(player.getUUID())) {
									return highLightPlayer(name);
								}
								return name;
							},
							playerID -> playerID.equals(player.getUUID())
					);
					PlayerScoreboards.setPlayerScoreboard(
							player, new PlayerScoreboard(
									objective.getDisplayName(),
									Optional.ofNullable(objective.numberFormat()),
									writer.getEntries()
							)
					);
				});
			});
		});
	}

	public void updatePlayerScore(ServerPlayer player) {
		if (player.getTags().contains("metacraft.has_another_player_sidebar")) return;
		if (showOwnScores) {
			var scoreboard = player.level().getScoreboard();
			var sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (sidebar != null) {
				if (sidebar.getName().equals(COMBINED_POINTS_OBJECTIVE)) {
					getTeamPoints().thenAccept(totalTeamPoints -> {
						getPlayerPoints().thenAccept(totalPlayerPoints -> {
							renderCombinedPointsForPlayer(player, totalPlayerPoints, totalTeamPoints, sidebar);
						});
					});
					return;
				}
				if (sidebar.getName().startsWith(COMBINED_POINTS_MINIGAME_OBJECTIVE_PREFIX)) {
					int minigameId = getMinigameIDFromScoreboardObjectiveName(sidebar.getName());
					getTeamPointsByMinigame(minigameId).thenAccept(teamPoints -> {
						getPlayerPointsByMinigame(minigameId).thenAccept(playerPoints -> {
							renderCombinedPointsForPlayer(player, playerPoints, teamPoints, sidebar);
						});
					});
					return;
				}
				List<PlayerScoreboard.Entry> myEntries = new ArrayList<>();
				List<PlayerScoreboard.Entry> otherEntries = new ArrayList<>();
				getTeamsForPlayer(player.getUUID()).thenAccept(teams -> {
					server.execute(() -> {
						for (var score : scoreboard.listPlayerScores(sidebar)) {
							if (score.owner().equals(player.getScoreboardName())) {
								myEntries.add(new PlayerScoreboard.Entry(
										highLightPlayer(score.ownerName()), Optional.of(score.owner()),
										score.value(), Optional.ofNullable(score.numberFormatOverride())
								));
							} else if (matchesAnyTeam(teams, score.owner())) {
								myEntries.add(new PlayerScoreboard.Entry(
										highLightTeam(score.ownerName()), Optional.of(score.owner()),
										score.value(), Optional.ofNullable(score.numberFormatOverride())
								));
							} else {
								otherEntries.add(new PlayerScoreboard.Entry(
										score.ownerName(), Optional.of(score.owner()),
										score.value(), Optional.ofNullable(score.numberFormatOverride())
								));
							}
						}
						PlayerScoreboards.setPlayerScoreboard(
								player,
								new PlayerScoreboard(
										sidebar.getDisplayName(), Optional.ofNullable(sidebar.numberFormat()),
										Stream.concat(
												myEntries.stream(),
												otherEntries.stream().sorted(
														Comparator.comparing(PlayerScoreboard.Entry::value)
												).limit(15 - myEntries.size())
										).toList()
								)
						);
					});
				});

			} else {
				PlayerScoreboards.clearPlayerScoreboard(player);
			}
		}
	}

	public void renderAll() {
		getTeams().thenAccept(teams -> {

			// Total points
			getTeamPoints().thenAccept(totalTeamPoints -> {
				getPlayerPoints().thenAccept(totalPlayerPoints -> {
					server.execute(() -> {
						renderTeamPoints(teams, totalTeamPoints, TEAM_POINTS_OBJECTIVE);
						renderPlayerPoints(totalPlayerPoints, PLAYER_POINTS_OBJECTIVE);
						renderCombinedPoints(teams, totalPlayerPoints, totalTeamPoints, ScoreboardLineWriter.create(server, COMBINED_POINTS_OBJECTIVE));
					});
				});
			});

			// Points per minigame
			getMinigameIds().thenAccept(minigameIds -> {
				for (int minigameId : minigameIds) {
					getTeamPointsByMinigame(minigameId).thenAccept(teamPoints -> {
						getPlayerPointsByMinigame(minigameId).thenAccept(playerPoints -> {
							server.execute(() -> {
								renderTeamPoints(teams, teamPoints, TEAM_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
								renderPlayerPoints(playerPoints, PLAYER_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
								renderCombinedPoints(teams, playerPoints, teamPoints, ScoreboardLineWriter.create(server, COMBINED_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId));
							});
						});
					});
				}
			});
		});
	}

	interface LineWriter {
		void writeLine(int line, Component name, NumberFormat numberFormat);
	}

	public record ScoreboardLineWriter(ServerScoreboard scoreboard, Objective objective) implements LineWriter {

		public static ScoreboardLineWriter create(MinecraftServer server, String objectiveName) {
			return new ScoreboardLineWriter(server.getScoreboard(), getOrCreateObjective(server, objectiveName));
		}

		@Override
		public void writeLine(int line, Component name, NumberFormat numberFormat) {
			ScoreHolder scoreHolder = ScoreHolder.forNameOnly("LINE_" + line);
			ScoreAccess score = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
			score.set(100 - line);
			score.display(name);
			score.numberFormatOverride(numberFormat);
		}
	}

	public static class PlayerSpecificLineWriter implements LineWriter {

		private final List<PlayerScoreboard.Entry> entries = new ArrayList<>();

		@Override
		public void writeLine(int line, Component name, NumberFormat numberFormat) {
			entries.add(new PlayerScoreboard.Entry(name, Optional.of("LINE_" + line), 100 - line, Optional.of(numberFormat)));
		}

		public List<PlayerScoreboard.Entry> getEntries() {
			return entries;
		}
	}

	@Override
	public void close() throws Exception {
		dbConnection.close();
		executor.shutdownAndAwait(1, TimeUnit.SECONDS);
	}
}
