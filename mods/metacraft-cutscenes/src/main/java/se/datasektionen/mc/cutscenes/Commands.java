package se.datasektionen.mc.cutscenes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	private static final DynamicCommandExceptionType NO_CUTSCENE = new DynamicCommandExceptionType(
			o -> () -> "No cutscene named " + o
	);

	private static final DynamicCommandExceptionType NO_MULTIPLAYER_CUTSCENE = new DynamicCommandExceptionType(
			o -> () -> "No multiplayer cutscene named " + o
	);

	private static final DynamicCommandExceptionType MULTIPLAYER_OCCUPIED = new DynamicCommandExceptionType(
			o -> () -> "Another cutscene is already playing with the name " + o
	);

	private static final SuggestionProvider<ServerCommandSource> SUGGEST_CUTSCENES = (ctx, builder) -> CommandSource.suggestMatching(
			CutscenesConfig.getOrCreateConfig(ctx.getSource().getServer()).getCutsceneNames(), builder
	);

	private static final SuggestionProvider<ServerCommandSource> SUGGEST_MULTIPLAYER_CUTSCENES = (ctx, builder) -> CommandSource.suggestMatching(
			MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer()).getCutsceneNames(), builder
	);

	private static int playCutscene(CommandContext<ServerCommandSource> ctx, String cutscene, ServerPlayerEntity player) throws CommandSyntaxException {
		return CutscenesConfig.getOrCreateConfig(ctx.getSource().getServer()).getCutscene(cutscene).map(scene -> {
			if (CutsceneHelper.isInMultiplayerCutscene(player)) {
				ctx.getSource().sendError(Text.literal("Player is in a multiplayer cutscene"));
				return 0;
			}
			CutsceneHelper.playPlayerSpecificCutscene(player, scene);
			ctx.getSource().sendFeedback(
					() -> Text.literal("Started playing cutscene " + cutscene + " for " + player.getName().getString()),
					false
			);
			return 1;
		}).orElseThrow(() -> NO_CUTSCENE.create(cutscene));
	}

	private static int forPlayers(
			CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players,
			Predicate<ServerPlayerEntity> playerAction,
			Text zeroPlayerMessage, boolean zeroIsError, Function<String, Text> getMessage
	) {
		int count = 0;
		String firstPlayer = "";
		for (var player : players) {
			if (playerAction.test(player)) {
				count++;
				if (count == 1) {
					firstPlayer = player.getName().getString();
				}
			}
		}
		if (count == 0) {
			if (zeroIsError) {
				ctx.getSource().sendError(zeroPlayerMessage);
			} else {
				ctx.getSource().sendFeedback(
						() -> zeroPlayerMessage,
						true
				);
			}
		} else if (count == 1) {
			String p = firstPlayer;
			ctx.getSource().sendFeedback(
					() -> getMessage.apply(p),
					true
			);
		} else {
			int c = count;
			ctx.getSource().sendFeedback(
					() -> getMessage.apply(c + " players"),
					true
			);
		}
		return count;
	}

	private static int addPlayers(
			CommandContext<ServerCommandSource> ctx,
			MultiplayerCutsceneManager manager, String name, Collection<ServerPlayerEntity> players,
			Text zeroPlayerMessage, boolean zeroIsError, Function<String, Text> getAddMessage
	) throws CommandSyntaxException {
		var scene = manager.getCutscene(name).orElseThrow(() -> NO_MULTIPLAYER_CUTSCENE.create(name));

		return forPlayers(ctx, players, player -> {
			if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
				player.sendMessage(Text.literal("You were not added to cutscene " + name + " because you were busy with another cutscene."));
				return false;
			}
			if (!scene.canAddPlayer(player)) {
				player.sendMessage(Text.literal(
						"You were not added to cutscene " + name + " because you are not in " +
								scene.getCutsceneWorld().getActualWorld().getRegistryKey().getValue()
				));
				return false;
			}
			manager.addToCutscene(name, player);
			return true;
		}, zeroPlayerMessage, zeroIsError, getAddMessage);
	}

	@FunctionalInterface
	public interface FunctionWithException<T, R> {
		R apply(T var1) throws CommandSyntaxException;
	}

	private static <T, R> Optional<R> map(Optional<T> opt, FunctionWithException<T, R> func) throws CommandSyntaxException {
		return opt.isPresent() ? Optional.ofNullable(func.apply(opt.get())) : Optional.empty();
	}

	private static int playMultiplayerCutscene(CommandContext<ServerCommandSource> ctx, String cutscene, String name, Collection<ServerPlayerEntity> players) throws CommandSyntaxException {
		var existing = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer()).getCutscene(name);
		if (existing.isPresent()) {
			throw MULTIPLAYER_OCCUPIED.create(name);
		}
		return map(CutscenesConfig.getOrCreateConfig(ctx.getSource().getServer()).getCutscene(cutscene), scene -> {
			var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
			manager.addCutscene(name, scene, ctx.getSource().getWorld());
			return addPlayers(
					ctx, manager, name, players, Text.literal("Prepared multiplayer cutscene " + name), false,
					added -> Text.literal("Started playing multiplayer cutscene " + name + " for " + added)
			);
		}).orElseThrow(() -> NO_CUTSCENE.create(cutscene));
	}

	private static int joinMultiplayerCutscene(CommandContext<ServerCommandSource> ctx, String name, Collection<ServerPlayerEntity> players) throws CommandSyntaxException {
		var existing = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer()).getCutscene(name);
		if (existing.isEmpty()) {
			throw NO_MULTIPLAYER_CUTSCENE.create(name);
		}
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		return addPlayers(
				ctx, manager, name, players, Text.literal("No players could be added to " + name), true,
				added -> Text.literal("Added " + added + " to " + name)
		);
	}

	private static int endCutscene(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players) throws CommandSyntaxException {
		return forPlayers(ctx, players, player -> {
			if (CutsceneHelper.isInMultiplayerCutscene(player)) {
				return false;
			}
			if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
				CutsceneHelper.stopPlayerSpecificCutscene(player);
			}
			return true;
		}, Text.literal("No players modified"), true, player -> Text.literal("Stopped cutscene for ").append(player));
	}

	private static int endMultiplayerCutscene(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		if (manager.getCutscene(name).isPresent()) {
			manager.endCutscene(name);
			ctx.getSource().sendFeedback(() -> Text.literal("Stopped multiplayer cutscene " + name), true);
			return 1;
		} else {
			throw NO_MULTIPLAYER_CUTSCENE.create(name);
		}
	}

	private static int leaveCutscene(
			CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players
	) throws CommandSyntaxException {
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		return forPlayers(ctx, players, player -> {
					if (CutsceneHelper.isInMultiplayerCutscene(player)) {
						manager.leaveCutscene(player);
						return true;
					}
					return false;
		}, Text.literal("No players affected"), true, player ->
			Text.literal(player + " removed from cutscene")
		);
	}

	private static int leaveCutscene(
			CommandContext<ServerCommandSource> ctx
	) throws CommandSyntaxException {
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		var player = ctx.getSource().getPlayerOrThrow();
		if (CutsceneHelper.isInMultiplayerCutscene(player)) {
			var scene = CutsceneHelper.getCutscene(player).orElseThrow();
			if (!scene.getCutscene().isSkippable() && !Permissions.check(ctx.getSource(), "metacraft.cutscenes.multiplayer.leave.non-skippable", 2)) {
				ctx.getSource().sendError(Text.literal("You can't leave unskippable cutscenes!"));
				return 0;
			}
			manager.leaveCutscene(player);
			var message = Text.literal("You left the cutscene");
			manager.getCutsceneName(scene).ifPresent(name -> {
				String rejoin = "/cutscene multiplayer join " + name;
				message.append(Text.literal(", you can rejoin it by typing ").append(Text.literal(rejoin).styled(
						style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, rejoin))
				)));
			});
			ctx.getSource().sendFeedback(() -> message, false);
			return 1;
		}
		ctx.getSource().sendError(Text.literal("You are not in a multiplayer cutscene!"));
		return 0;
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) -> {
				dispatcher.register(
					literal("cutscene").then(
						literal("play").requires(Permissions.require("metacraft.cutscenes.play", 2)).then(
							argument("cutscene", StringArgumentType.word()).suggests(SUGGEST_CUTSCENES).executes(
								ctx -> playCutscene(
										ctx, StringArgumentType.getString(ctx, "cutscene"),
										ctx.getSource().getPlayerOrThrow()
								)
							).then(
								argument("player", EntityArgumentType.player()).executes(
									ctx -> playCutscene(
											ctx, StringArgumentType.getString(ctx, "cutscene"),
											EntityArgumentType.getPlayer(ctx, "player")
									)
								)
							)
						)
					).then(
						literal("stop").requires(
								Permissions.require("metacraft.cutscenes.stop", 2)
						).executes(
								ctx -> endCutscene(ctx, List.of(ctx.getSource().getPlayerOrThrow()))
						).then(
								argument("players", EntityArgumentType.players()).executes(
										ctx -> endCutscene(ctx, EntityArgumentType.getPlayers(ctx, "players"))
								)
						)
					).then(
						literal("skip").requires(
								Permissions.require("metacraft.cutscenes.skip", 0)
						).executes(ctx -> {
							var player = ctx.getSource().getPlayerOrThrow();
							var scene = CutsceneHelper.getCutscene(player);
							if (scene.isPresent()) {
								if (scene.get().getCutscene().isSkippable()) {
									if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
										CutsceneHelper.stopPlayerSpecificCutscene(player);
										ctx.getSource().sendFeedback(
												() -> Text.literal("You skipped the cutscene"), false
										);
									} else  {
										return leaveCutscene(ctx);
									}
									return 1;
								} else {
									ctx.getSource().sendError(Text.literal("This cutscene is not skippable!"));
								}
							} else {
								ctx.getSource().sendError(Text.literal("You are not in a cutscene!"));
							}
							return 0;
						})
					).then(
						literal("reload").requires(Permissions.require("metacraft.cutscenes.reload", 4)).executes(ctx -> {
							CutscenesConfig.reload(ctx.getSource().getServer());
							ctx.getSource().sendFeedback(
									() -> Text.literal(
											"Reloading cutscenes"
									), true
							);
							return 1;
						})
					).then(
						literal("multiplayer").then(
							literal("setup").requires(Permissions.require("metacraft.cutscenes.multiplayer.setup", 2)).then(
								argument("cutscene", StringArgumentType.word()).suggests(SUGGEST_CUTSCENES).then(
									argument("name", StringArgumentType.word()).executes(
										ctx -> playMultiplayerCutscene(
											ctx, StringArgumentType.getString(ctx, "cutscene"),
											StringArgumentType.getString(ctx, "name"), List.of()
										)
									).then(
										argument("players", EntityArgumentType.players()).executes(
											ctx -> playMultiplayerCutscene(
													ctx, StringArgumentType.getString(ctx, "cutscene"),
													StringArgumentType.getString(ctx, "name"),
													EntityArgumentType.getPlayers(ctx, "players")
											)
										)
									)
								)
							)
						).then(
							literal("join").requires(
									Permissions.require("metacraft.cutscenes.multiplayer.join", 0)
							).then(
								argument("name", StringArgumentType.word()).suggests(SUGGEST_MULTIPLAYER_CUTSCENES).executes(
										ctx -> joinMultiplayerCutscene(
												ctx, StringArgumentType.getString(ctx, "name"),
												List.of(ctx.getSource().getPlayerOrThrow())
										)
								).then(
									argument("players", EntityArgumentType.players()).requires(
											Permissions.require("metacraft.cutscenes.multiplayer.join.others", 2)
									).executes(
										ctx -> joinMultiplayerCutscene(
												ctx, StringArgumentType.getString(ctx, "name"),
												EntityArgumentType.getPlayers(ctx, "players")
										)
									)
								)
							)
						).then(
							literal("stop").requires(
									Permissions.require("metacraft.cutscenes.multiplayer.stop", 2)
							).then(
								argument("name", StringArgumentType.string()).suggests(SUGGEST_MULTIPLAYER_CUTSCENES).executes(
										ctx -> endMultiplayerCutscene(ctx, StringArgumentType.getString(ctx, "name"))
								)
							)
						).then(
							literal("leave").requires(
									Permissions.require("metacraft.cutscenes.multiplayer.leave", 0)
							).executes(Commands::leaveCutscene).then(
									argument("players", EntityArgumentType.players()).requires(
											Permissions.require("metacraft.cutscenes.multiplayer.leave.others", 2)
									).executes(ctx -> leaveCutscene(ctx, EntityArgumentType.getPlayers(ctx, "players")))
							)
						)
					)
				);
			}
		);
	}

}
