package nu.metacraft.pointsystem;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PointSystemCommand {
	public static final SimpleCommandExceptionType NO_POINT_SYSTEM = new SimpleCommandExceptionType(Component.literal("No point system found."));
	private static final DynamicCommandExceptionType PLAYER_NOT_FOUND = new DynamicCommandExceptionType(playerName -> Component.literal("Player with name '" + playerName + "' not found."));
	private final PointSystemMod mod;

	public PointSystemCommand(PointSystemMod mod) {
		this.mod = mod;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			literal("pointsystem")
				.requires(obj -> obj.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(
					literal("reload")
						.executes(this::reload)
				)
				.then(
					literal("reset")
						.then(
							literal("i-confirm-that-this-is-dangerous-and-will-reset-all-points")
								.executes(this::reset)
						)
				)
				.then(
					literal("addpoints")
						.then(
							argument("player", ScoreHolderArgument.scoreHolder())
								.then(
									argument("points", IntegerArgumentType.integer())
										.executes(ctx -> {
											ScoreHolder player = ScoreHolderArgument.getName(ctx, "player");
											UUID playerUuid = getUuid(ctx, player);
											int points = IntegerArgumentType.getInteger(ctx, "points");
											int minigameId = getCurrentMinigameId(ctx);
											return addPoints(ctx, playerUuid, points, minigameId);
										})
										.then(
											argument("minigame_id", IntegerArgumentType.integer())
												.executes(ctx -> {
													ScoreHolder player = ScoreHolderArgument.getName(ctx, "player");
													UUID playerUuid = getUuid(ctx, player);
													int points = IntegerArgumentType.getInteger(ctx, "points");
													int minigameId = IntegerArgumentType.getInteger(ctx, "minigame_id");
													return addPoints(ctx, playerUuid, points, minigameId);
												})
										)
								)
						)
				)
				.then(
					literal("topplayers")
						.executes(this::topPlayers)
				)
				.then(
					literal("renderAll")
						.executes(this::renderAll)
				).then(
					literal("show-own-scores").then(
						argument("value", BoolArgumentType.bool()).executes(
								ctx -> showOwnScores(ctx, BoolArgumentType.getBool(ctx, "value"))
						)
					)
				)
				.then(
					literal("excludedMinigameIds")
						.then(
							literal("list")
								.executes(ctx -> {
									PointSystem pointSystem = getPointSystem(ctx);
									pointSystem.getExcludedMinigameIds().thenAccept(excluded -> {
										ctx.getSource().getServer().execute(() -> {
											ctx.getSource().sendSystemMessage(Component.literal("The following minigame ids are excluded: " + excluded
													.intStream()
													.mapToObj(String::valueOf)
													.collect(Collectors.joining())
											));
										});
									});

									return 1;
								})
						)
						.then(
							literal("add")
								.then(
									argument("id", IntegerArgumentType.integer())
										.executes(ctx -> {
											int id = IntegerArgumentType.getInteger(ctx, "id");
											PointSystem pointSystem = getPointSystem(ctx);
											pointSystem.addExcludedMinigame(id);
											ctx.getSource().sendSuccess(() -> Component.literal("Minigame id " + id +  " will now be excluded."), true);
											return 1;
										})
								)
						)
						.then(
							literal("remove")
								.then(
									argument("id", IntegerArgumentType.integer())
										.executes(ctx -> {
											int id = IntegerArgumentType.getInteger(ctx, "id");
											PointSystem pointSystem = getPointSystem(ctx);
											pointSystem.removeExcludedMinigame(id);
											ctx.getSource().sendSuccess(() -> Component.literal("Minigame id " + id +  " will no longer be excluded."), true);
											return 1;
										})
								)
						)
				)
				.then(
					literal("team")
						.then(
							literal("add")
								.then(
									argument("type", StringArgumentType.word())
										.suggests(this::suggestTeamType)
										.then(
											argument("code", StringArgumentType.word())
												.then(
													argument("short_name", StringArgumentType.string())
														.executes(ctx -> {
															String type = StringArgumentType.getString(ctx, "type");
															String code = StringArgumentType.getString(ctx, "code");
															String shortName = StringArgumentType.getString(ctx, "short_name");
															return this.addTeam(ctx, type, code, shortName, null);
														})
														.then(
															argument("full_name", StringArgumentType.string())
																.executes(ctx -> {
																	String type = StringArgumentType.getString(ctx, "type");
																	String code = StringArgumentType.getString(ctx, "code");
																	String shortName = StringArgumentType.getString(ctx, "short_name");
																	String fullName = StringArgumentType.getString(ctx, "full_name");
																	return this.addTeam(ctx, type, code, shortName, fullName);
																})
														)
												)
										)
								)
						)
						.then(
							literal("join")
								.then(
									literal("only")
										.then(
											argument("player", ScoreHolderArgument.scoreHolder())
												.then(
													argument("teams", StringArgumentType.greedyString())
														.executes(ctx -> {
															ScoreHolder player = ScoreHolderArgument.getName(ctx, "player");
															UUID playerUuid = getUuid(ctx, player);
															String input = StringArgumentType.getString(ctx, "teams");

															return this.joinOnlyTeams(ctx, playerUuid, input);
														})
												)
										)
								)
						)
				)
				.then(
					literal("join-scoreboard-teams-balanced")
						.then(
							argument("players", EntityArgument.players())
								.then(
									argument("teams", StringArgumentType.greedyString())
										.executes(ctx -> {
											Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
											String input = StringArgumentType.getString(ctx, "teams");
											return this.joinScoreboardTeamsBalanced(ctx, players, input);
										})
								)
						)
				)
		);
	}

	private UUID getUuid(CommandContext<CommandSourceStack> ctx, ScoreHolder player) throws CommandSyntaxException {
		if (player instanceof Entity entity) {
			return entity.getUUID();
		}
		String string = player.getScoreboardName();
		if (string.length() > 16) {
			try {
				return UUID.fromString(string);
			} catch (IllegalArgumentException e) {
				// continue
			}
		}
		var userCache = ctx.getSource().getServer().services().nameToIdCache();
		Optional<NameAndId> profile = userCache.get(string);
		if (profile.isEmpty()) {
			throw PLAYER_NOT_FOUND.create(string);
		}
		return profile.get().id();
	}

	private PointSystem getPointSystem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		PointSystem pointSystem = this.mod.getPointSystem(ctx.getSource().getServer());
		if (pointSystem != null) {
			return pointSystem;
		}
		throw NO_POINT_SYSTEM.create();
	}

	private int getCurrentMinigameId(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		ServerScoreboard scoreboard = server.getScoreboard();
		Objective objective = scoreboard.getObjective("GLOBAL");
		if (objective == null) {
			return -1;
		}
		ScoreHolder scoreHolder = ScoreHolder.forNameOnly("game.id");
		ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
		if (score == null) {
			return -1;
		}
		return score.value();
	}

	private CompletableFuture<Suggestions> suggestTeamType(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		builder.suggest("uni");
		builder.suggest("city");
		return builder.buildFuture();
	}

	private int reload(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		PointSystemConfig.reload();
		source.sendSuccess(() -> Component.literal("Point system config reloaded."), true);
		return 1;
	}

	private int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		PointSystem pointSystem = getPointSystem(ctx);
		CommandSourceStack source = ctx.getSource();
		try {
			pointSystem.resetPoints();
			source.sendSuccess(() -> Component.literal("Point system data loaded from disk."), true);
		} catch (Throwable e) {
			source.sendFailure(Component.literal(e.getMessage()));
			PointSystemMod.LOGGER.error("Failed to load data", e);
		}
		return 1;
	}

	private int addPoints(CommandContext<CommandSourceStack> ctx, UUID playerUuid, int points, int minigameId) throws CommandSyntaxException {
		PointSystem pointSystem = getPointSystem(ctx);
		CommandSourceStack source = ctx.getSource();
		pointSystem.addPoints(playerUuid, points, minigameId);
		source.sendSuccess(() -> Component.literal("Added points"), false);
		return points;
	}

	private String displayUuid(UUID playerUuid, MinecraftServer server) {
		var userCache = server.services().nameToIdCache();
		Optional<String> name = userCache.get(playerUuid).map(NameAndId::name);
		return name.orElseGet(playerUuid::toString);
	}

	private int topPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		PointSystem pointSystem = getPointSystem(ctx);
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		pointSystem.getPlayerPoints().thenAccept(players -> {
			var entries = players.getData().object2IntEntrySet()
					.stream()
					.sorted((a, b) -> b.getIntValue() - a.getIntValue())
					.toList();
			server.execute(() -> {
				source.sendSystemMessage(Component.literal("Top 10:"));
				for (Map.Entry<UUID, Integer> entry : entries) {
					String name = displayUuid(entry.getKey(), server);
					ctx.getSource().sendSystemMessage(Component.literal(name + ": " + entry.getValue()));
				}
			});
		});

		return 1;
	}

	private int showOwnScores(CommandContext<CommandSourceStack> ctx, boolean value) throws CommandSyntaxException {
		PointSystem pointSystem = getPointSystem(ctx);
		pointSystem.setShowOwnScores(value);
		return 1;
	}

	private int renderAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		PointSystem pointSystem = getPointSystem(ctx);
		CommandSourceStack source = ctx.getSource();
		pointSystem.renderAll();
		source.sendSuccess(() -> Component.literal("Rendering"), false);
		return 1;
	}

	private int addTeam(CommandContext<CommandSourceStack> ctx, String type, String code, String shortName, String fullName) throws CommandSyntaxException {
		PointSystem pointSystem = getPointSystem(ctx);
		CommandSourceStack source = ctx.getSource();
		pointSystem.addTeam(type, code, shortName, fullName);
		source.sendSuccess(() -> Component.literal("Added team"), true);
		return 1;
	}

	private int joinOnlyTeams(CommandContext<CommandSourceStack> ctx, UUID playerUuid, String input) throws CommandSyntaxException {
		String[] codes = input.split(" ");

		PointSystem pointSystem = getPointSystem(ctx);
		pointSystem.joinOnlyTeams(playerUuid, codes);
		return 1;
	}

	private int joinScoreboardTeamsBalanced(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String input) throws CommandSyntaxException {
		PointSystem pointSystem = getPointSystem(ctx);
		String[] teamNames = input.split(" ");
		MinecraftServer server = ctx.getSource().getServer();
		ServerScoreboard scoreboard = server.getScoreboard();
		PlayerTeam[] teams = Arrays.stream(teamNames).map(scoreboard::getPlayerTeam).toArray(PlayerTeam[]::new);

		pointSystem.getPlayerPoints().thenAccept(playerPoints -> {
			var entries = players.stream()
					.map(player -> Map.entry(player, playerPoints.getData().getOrDefault(player.getUUID(), 0)))
					.sorted((a, b) -> b.getValue() - a.getValue())
					.toList();

			server.execute(() -> {
				int i = 0;
				for (Map.Entry<ServerPlayer, Integer> entry : entries) {
					ServerPlayer player = entry.getKey();
					PlayerTeam team = teams[i % teams.length];
					scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
					i++;
				}
			});
		});

		return players.size();
	}

}
