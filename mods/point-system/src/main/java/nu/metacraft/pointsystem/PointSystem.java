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
import net.minecraft.ChatFormatting;
import net.minecraft.TracingExecutor;
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
import nu.metacraft.pointsystem.mixin.UtilAccessor;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

		setTextLine(0, PointSystemConfig.getInstance().universityScoreText(), scoreboard, objective);

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
		setTextLine(7, PointSystemConfig.getInstance().topPlayersText(), scoreboard, objective);

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
		getTeams().thenAccept(teams -> {

			// Total points
			getTeamPoints().thenAccept(totalTeamPoints -> {
				getPlayerPoints().thenAccept(totalPlayerPoints -> {
					server.execute(() -> {
						renderTeamPoints(teams, totalTeamPoints, TEAM_POINTS_OBJECTIVE);
						renderPlayerPoints(totalPlayerPoints, PLAYER_POINTS_OBJECTIVE);
						renderCombinedPoints(teams, totalPlayerPoints, totalTeamPoints, COMBINED_POINTS_OBJECTIVE);
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
								renderCombinedPoints(teams, playerPoints, teamPoints, COMBINED_POINTS_MINIGAME_OBJECTIVE_PREFIX + minigameId);
							});
						});
					});
				}
			});
		});
	}

	@Override
	public void close() throws Exception {
		dbConnection.close();
		executor.shutdownAndAwait(1, TimeUnit.SECONDS);
	}
}
