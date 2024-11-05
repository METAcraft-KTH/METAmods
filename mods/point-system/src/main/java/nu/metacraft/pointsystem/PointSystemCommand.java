package nu.metacraft.pointsystem;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.ScoreHolderArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PointSystemCommand {
    private static final SimpleCommandExceptionType NO_POINT_SYSTEM = new SimpleCommandExceptionType(Text.literal("No point system found."));
    private static final DynamicCommandExceptionType PLAYER_NOT_FOUND = new DynamicCommandExceptionType(playerName -> Text.literal("Player with name '" + playerName + "' not found."));
    private final PointSystemMod mod;

    public PointSystemCommand(PointSystemMod mod) {
        this.mod = mod;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("pointsystem")
                .requires(obj -> obj.hasPermissionLevel(2))
                .then(
                    literal("reload")
                        .executes(this::reload)
                )
                .then(
                    literal("save")
                        .executes(this::save)
                )
                .then(
                    literal("load")
                        .then(
                            literal("i-confirm-that-this-is-dangerous")
                                .executes(this::load)
                        )
                )
                .then(
                    literal("addpoints")
                        .then(
                            argument("player", ScoreHolderArgumentType.scoreHolder())
                                .then(
                                    argument("points", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            ScoreHolder player = ScoreHolderArgumentType.getScoreHolder(ctx, "player");
                                            UUID playerUuid = getUuid(ctx, player);
                                            int points = IntegerArgumentType.getInteger(ctx, "points");
                                            int minigameId = getCurrentMinigameId(ctx);
                                            return addPoints(ctx, playerUuid, points, minigameId);
                                        })
                                        .then(
                                            argument("minigame_id", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    ScoreHolder player = ScoreHolderArgumentType.getScoreHolder(ctx, "player");
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
                )
                .then(
                    literal("excludedMinigameIds")
                        .then(
                            literal("list")
                                .executes(ctx -> {
                                    PointSystem pointSystem = getPointSystem(ctx);
                                    ctx.getSource().sendMessage(Text.literal("The following minigame ids are excluded: " + pointSystem.getExcludedMinigameIds()
                                        .intStream()
                                        .mapToObj(String::valueOf)
                                        .collect(Collectors.joining())
                                    ));
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
                                            pointSystem.getExcludedMinigameIds().add(id);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Minigame id " + id +  " will now be excluded."), true);
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
                                            pointSystem.getExcludedMinigameIds().remove(id);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Minigame id " + id +  " will no longer be excluded."), true);
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
                                            argument("player", ScoreHolderArgumentType.scoreHolder())
                                                .then(
                                                    argument("teams", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            ScoreHolder player = ScoreHolderArgumentType.getScoreHolder(ctx, "player");
                                                            UUID playerUuid = getUuid(ctx, player);
                                                            String input = StringArgumentType.getString(ctx, "teams");

                                                            return this.joinOnlyTeams(ctx, playerUuid, input);
                                                        })
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private UUID getUuid(CommandContext<ServerCommandSource> ctx, ScoreHolder player) throws CommandSyntaxException {
        if (player instanceof Entity entity) {
            return entity.getUuid();
        }
        String string = player.getNameForScoreboard();
        if (string.length() > 16) {
            try {
                return UUID.fromString(string);
            } catch (IllegalArgumentException e) {
                // continue
            }
        }
        UserCache userCache = ctx.getSource().getServer().getUserCache();
        if (userCache == null) {
            throw PLAYER_NOT_FOUND.create(string);
        }
        Optional<GameProfile> profile = userCache.findByName(string);
        if (profile.isEmpty()) {
            throw PLAYER_NOT_FOUND.create(string);
        }
        return profile.get().getId();
    }

    private PointSystem getPointSystem(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = this.mod.getPointSystem(ctx.getSource().getServer());
        if (pointSystem != null) {
            return pointSystem;
        }
        throw NO_POINT_SYSTEM.create();
    }

    private int getCurrentMinigameId(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerScoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("GLOBAL");
        if (objective == null) {
            return -1;
        }
        ScoreHolder scoreHolder = ScoreHolder.fromName("game.id");
        ReadableScoreboardScore score = scoreboard.getScore(scoreHolder, objective);
        if (score == null) {
            return -1;
        }
        return score.getScore();
    }

    private CompletableFuture<Suggestions> suggestTeamType(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        builder.suggest("uni");
        builder.suggest("city");
        return builder.buildFuture();
    }

    private int reload(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        try {
            pointSystem.loadConfig();
            source.sendFeedback(() -> Text.literal("Point system config reloaded."), true);
        } catch (IOException e) {
            source.sendError(Text.literal(e.getMessage()));
            PointSystemMod.LOGGER.error("Failed to load config", e);
        }
        return 1;
    }

    private int save(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        try {
            pointSystem.saveData();
            source.sendFeedback(() -> Text.literal("Point system data saved."), true);
        } catch (Throwable e) {
            source.sendError(Text.literal(e.getMessage()));
            PointSystemMod.LOGGER.error("Failed to save data", e);
        }
        return 1;
    }

    private int load(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        try {
            pointSystem.loadData();
            source.sendFeedback(() -> Text.literal("Point system data loaded from disk."), true);
        } catch (Throwable e) {
            source.sendError(Text.literal(e.getMessage()));
            PointSystemMod.LOGGER.error("Failed to load data", e);
        }
        return 1;
    }

    private int addPoints(CommandContext<ServerCommandSource> ctx, UUID playerUuid, int points, int minigameId) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        pointSystem.addPoints(playerUuid, points, minigameId);
        source.sendFeedback(() -> Text.literal("Added points"), false);
        return points;
    }

    private String displayUuid(UUID playerUuid, MinecraftServer server) {
        UserCache userCache = server.getUserCache();
        if (userCache != null) {
            Optional<String> name = userCache.getByUuid(playerUuid).map(GameProfile::getName);
            if (name.isPresent()) {
                return name.get();
            }
        }
        return playerUuid.toString();
    }

    private int topPlayers(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        var players = pointSystem.getPlayerPoints();
        source.sendMessage(Text.literal("Top 10:"));
        MinecraftServer server = source.getServer();
        var entries = players.getData().object2IntEntrySet()
            .stream()
            .sorted((a, b) -> b.getIntValue() - a.getIntValue())
            .toList();
        for (Map.Entry<UUID, Integer> entry : entries) {
            String name = displayUuid(entry.getKey(), server);
            ctx.getSource().sendMessage(Text.literal(name + ": " + entry.getValue()));
        }
        return 1;
    }

    private int renderAll(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        pointSystem.renderAll();
        source.sendFeedback(() -> Text.literal("Rendering"), false);
        return 1;
    }

    private int addTeam(CommandContext<ServerCommandSource> ctx, String type, String code, String shortName, String fullName) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        pointSystem.addTeam(type, code, shortName, fullName);
        source.sendFeedback(() -> Text.literal("Added team"), true);
        return 1;
    }

    private int joinOnlyTeams(CommandContext<ServerCommandSource> ctx, UUID playerUuid, String input) throws CommandSyntaxException {
        String[] codes = input.split(" ");

        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        pointSystem.joinOnlyTeams(playerUuid, codes, source);
        return 1;
    }
}
