package nu.metacraft.pointsystem;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
                    literal("db")
                        .then(
                            literal("close")
                                .executes(ctx -> {
                                    PointSystem pointSystem = getPointSystem(ctx);
                                    ServerCommandSource source = ctx.getSource();
                                    source.sendMessage(Text.literal("Closing database connection"));
                                    pointSystem.getExecutor().execute(() -> {
                                        try {
                                            pointSystem.getDatabaseConnection().close();
                                            source.sendMessage(Text.literal("Requested close"));
                                        } catch (SQLException e) {
                                            source.sendMessage(Text.literal(e.getMessage()));
                                            PointSystemMod.LOGGER.error("Failed to close database", e);
                                        }
                                    });
                                    return 1;
                                })
                        )
                        .then(
                            literal("ping")
                                .executes(ctx -> {
                                    PointSystem pointSystem = getPointSystem(ctx);
                                    ServerCommandSource source = ctx.getSource();
                                    source.sendMessage(Text.literal("Checking database connection"));
                                    pointSystem.getExecutor().execute(() -> {
                                        try {
                                            pointSystem.getDatabaseConnection();
                                            source.sendMessage(Text.literal("Pinged"));
                                        } catch (SQLException e) {
                                            source.sendMessage(Text.literal(e.getMessage()));
                                            PointSystemMod.LOGGER.error("Failed to get connection", e);
                                        }
                                    });
                                    return 1;
                                })
                        )
                )
                .then(
                    literal("addpoints")
                        .then(
                            literal("player")
                                .then(
                                    argument("player", EntityArgumentType.player())
                                        .then(
                                            argument("points", IntegerArgumentType.integer())
                                                .then(
                                                    argument("minigame_id", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                            int points = IntegerArgumentType.getInteger(ctx, "points");
                                                            int minigameId = IntegerArgumentType.getInteger(ctx, "minigame_id");
                                                            return addPoints(ctx, player.getUuid(), points, minigameId);
                                                        })
                                                )
                                        )

                                )

                        )
                        .then(
                            literal("name")
                                .then(
                                    argument("playername", StringArgumentType.word())
                                        .then(
                                            argument("points", IntegerArgumentType.integer())
                                                .then(
                                                    argument("minigame_id", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            String playerName = StringArgumentType.getString(ctx, "playername");
                                                            MinecraftServer server = ctx.getSource().getServer();
                                                            UserCache userCache = server.getUserCache();
                                                            if (userCache == null) {
                                                                throw PLAYER_NOT_FOUND.create(playerName);
                                                            }
                                                            Optional<GameProfile> profile = userCache.findByName(playerName);
                                                            if (profile.isEmpty()) {
                                                                throw PLAYER_NOT_FOUND.create(playerName);
                                                            }
                                                            UUID uuid = profile.get().getId();
                                                            int points = IntegerArgumentType.getInteger(ctx, "points");
                                                            int minigameId = IntegerArgumentType.getInteger(ctx, "minigame_id");
                                                            return addPoints(ctx, uuid, points, minigameId);
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(
                            literal("uuid")
                                .then(
                                    argument("playeruuid", UuidArgumentType.uuid())
                                        .then(
                                            argument("points", IntegerArgumentType.integer())
                                                .then(
                                                    argument("minigame_id", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            UUID uuid = UuidArgumentType.getUuid(ctx, "playeruuid");
                                                            int points = IntegerArgumentType.getInteger(ctx, "points");
                                                            int minigameId = IntegerArgumentType.getInteger(ctx, "minigame_id");
                                                            return addPoints(ctx, uuid, points, minigameId);
                                                        })
                                                )
                                        )

                                )

                        )
                )
                .then(
                    literal("topplayers")
                        .executes(this::topPlayers)
                )
        );
    }

    private PointSystem getPointSystem(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = this.mod.getPointSystem(ctx.getSource().getServer());
        if (pointSystem != null) {
            return pointSystem;
        }
        throw NO_POINT_SYSTEM.create();
    }

    private int reload(CommandContext<ServerCommandSource> ctx) {
        return 1;
    }

    private int addPoints(CommandContext<ServerCommandSource> ctx, UUID playerUuid, int points, int minigameId) throws CommandSyntaxException {
        PointSystem pointSystem = getPointSystem(ctx);
        ServerCommandSource source = ctx.getSource();
        source.sendMessage(Text.literal("Sending to database..."));
        pointSystem.getExecutor().execute(() -> {
            try {
                pointSystem.addPoints(playerUuid, points, minigameId);
                source.sendMessage(Text.literal("Points added"));
            } catch (SQLException e) {
                source.sendMessage(Text.literal(e.getMessage()));
                PointSystemMod.LOGGER.error("Failed to add points", e);
            }
        });
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
        Map<UUID, Integer> players;
        try {
            players = pointSystem.getPlayerPoints(10);
        } catch (SQLException e) {
            source.sendMessage(Text.literal(e.getMessage()));
            PointSystemMod.LOGGER.error("Failed to get points", e);
            return 0;
        }
        source.sendMessage(Text.literal("Top 10:"));
        MinecraftServer server = source.getServer();
        List<Map.Entry<UUID, Integer>> entries = players.entrySet()
            .stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .toList();
        for (Map.Entry<UUID, Integer> entry : entries) {
            String name = displayUuid(entry.getKey(), server);
            ctx.getSource().sendMessage(Text.literal(name + ": " + entry.getValue()));
        }
        return 1;
    }

}
