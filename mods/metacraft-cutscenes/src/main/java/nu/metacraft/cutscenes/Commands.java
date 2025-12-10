package nu.metacraft.cutscenes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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

	private static final SuggestionProvider<CommandSourceStack> SUGGEST_CUTSCENES = (ctx, builder) -> SharedSuggestionProvider.suggest(
			CutscenesConfig.getOrCreateConfig(ctx.getSource().getServer()).getCutsceneNames(), builder
	);

	private static final SuggestionProvider<CommandSourceStack> SUGGEST_MULTIPLAYER_CUTSCENES = (ctx, builder) -> SharedSuggestionProvider.suggest(
			MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer()).getCutsceneNames(), builder
	);

	private static int playCutscene(CommandContext<CommandSourceStack> ctx, String cutscene, ServerPlayer player) throws CommandSyntaxException {
		return CutscenesConfig.getOrCreateConfig(ctx.getSource().getServer()).getCutscene(cutscene).map(scene -> {
			if (CutsceneHelper.isInMultiplayerCutscene(player)) {
				ctx.getSource().sendFailure(Component.literal("Player is in a multiplayer cutscene"));
				return 0;
			}
			CutsceneHelper.playPlayerSpecificCutscene(player, scene);
			ctx.getSource().sendSuccess(
					() -> Component.literal("Started playing cutscene " + cutscene + " for " + player.getName().getString()),
					false
			);
			return 1;
		}).orElseThrow(() -> NO_CUTSCENE.create(cutscene));
	}

	private static int forPlayers(
			CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players,
			Predicate<ServerPlayer> playerAction,
			Component zeroPlayerMessage, boolean zeroIsError, Function<String, Component> getMessage
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
				ctx.getSource().sendFailure(zeroPlayerMessage);
			} else {
				ctx.getSource().sendSuccess(
						() -> zeroPlayerMessage,
						true
				);
			}
		} else if (count == 1) {
			String p = firstPlayer;
			ctx.getSource().sendSuccess(
					() -> getMessage.apply(p),
					true
			);
		} else {
			int c = count;
			ctx.getSource().sendSuccess(
					() -> getMessage.apply(c + " players"),
					true
			);
		}
		return count;
	}

	private static int addPlayers(
			CommandContext<CommandSourceStack> ctx,
			MultiplayerCutsceneManager manager, String name, Collection<ServerPlayer> players,
			Component zeroPlayerMessage, boolean zeroIsError, Function<String, Component> getAddMessage
	) throws CommandSyntaxException {
		var scene = manager.getCutscene(name).orElseThrow(() -> NO_MULTIPLAYER_CUTSCENE.create(name));

		return forPlayers(ctx, players, player -> {
			if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
				player.sendSystemMessage(Component.literal("You were not added to cutscene " + name + " because you were busy with another cutscene."));
				return false;
			}
			if (!scene.canAddPlayer(player)) {
				player.sendSystemMessage(Component.literal(
						"You were not added to cutscene " + name + " because you are not in " +
								scene.getCutsceneWorld().getActualWorld().dimension().identifier()
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

	private static int playMultiplayerCutscene(CommandContext<CommandSourceStack> ctx, String cutscene, String name, Collection<ServerPlayer> players) throws CommandSyntaxException {
		var existing = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer()).getCutscene(name);
		if (existing.isPresent()) {
			throw MULTIPLAYER_OCCUPIED.create(name);
		}
		return map(CutscenesConfig.getOrCreateConfig(ctx.getSource().getServer()).getCutscene(cutscene), scene -> {
			var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
			manager.addCutscene(name, scene, ctx.getSource().getLevel());
			return addPlayers(
					ctx, manager, name, players, Component.literal("Prepared multiplayer cutscene " + name), false,
					added -> Component.literal("Started playing multiplayer cutscene " + name + " for " + added)
			);
		}).orElseThrow(() -> NO_CUTSCENE.create(cutscene));
	}

	private static int joinMultiplayerCutscene(CommandContext<CommandSourceStack> ctx, String name, Collection<ServerPlayer> players) throws CommandSyntaxException {
		var existing = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer()).getCutscene(name);
		if (existing.isEmpty()) {
			throw NO_MULTIPLAYER_CUTSCENE.create(name);
		}
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		return addPlayers(
				ctx, manager, name, players, Component.literal("No players could be added to " + name), true,
				added -> Component.literal("Added " + added + " to " + name)
		);
	}

	private static int endCutscene(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
		return forPlayers(ctx, players, player -> {
			if (CutsceneHelper.isInMultiplayerCutscene(player)) {
				return false;
			}
			if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
				CutsceneHelper.stopPlayerSpecificCutscene(player);
			}
			return true;
		}, Component.literal("No players modified"), true, player -> Component.literal("Stopped cutscene for ").append(player));
	}

	private static int endMultiplayerCutscene(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		if (manager.getCutscene(name).isPresent()) {
			manager.endCutscene(name);
			ctx.getSource().sendSuccess(() -> Component.literal("Stopped multiplayer cutscene " + name), true);
			return 1;
		} else {
			throw NO_MULTIPLAYER_CUTSCENE.create(name);
		}
	}

	private static int leaveCutscene(
			CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players
	) throws CommandSyntaxException {
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		return forPlayers(ctx, players, player -> {
					if (CutsceneHelper.isInMultiplayerCutscene(player)) {
						manager.leaveCutscene(player);
						return true;
					}
					return false;
		}, Component.literal("No players affected"), true, player ->
			Component.literal(player + " removed from cutscene")
		);
	}

	private static int leaveCutscene(
			CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		var manager = MultiplayerCutsceneManager.getInstance(ctx.getSource().getServer());
		var player = ctx.getSource().getPlayerOrException();
		if (CutsceneHelper.isInMultiplayerCutscene(player)) {
			var scene = CutsceneHelper.getCutscene(player).orElseThrow();
			if (!scene.getCutscene().isSkippable() && !Permissions.check(ctx.getSource(), "metacraft.cutscenes.multiplayer.leave.non-skippable", 2)) {
				ctx.getSource().sendFailure(Component.literal("You can't leave unskippable cutscenes!"));
				return 0;
			}
			manager.leaveCutscene(player);
			var message = Component.literal("You left the cutscene");
			manager.getCutsceneName(scene).ifPresent(name -> {
				String rejoin = "/cutscene multiplayer join " + name;
				message.append(Component.literal(", you can rejoin it by typing ").append(Component.literal(rejoin).withStyle(
						style -> style.withClickEvent(new ClickEvent.RunCommand(rejoin))
				)));
			});
			ctx.getSource().sendSuccess(() -> message, false);
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("You are not in a multiplayer cutscene!"));
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
										ctx.getSource().getPlayerOrException()
								)
							).then(
								argument("player", EntityArgument.player()).executes(
									ctx -> playCutscene(
											ctx, StringArgumentType.getString(ctx, "cutscene"),
											EntityArgument.getPlayer(ctx, "player")
									)
								)
							)
						)
					).then(
						literal("stop").requires(
								Permissions.require("metacraft.cutscenes.stop", 2)
						).executes(
								ctx -> endCutscene(ctx, List.of(ctx.getSource().getPlayerOrException()))
						).then(
								argument("players", EntityArgument.players()).executes(
										ctx -> endCutscene(ctx, EntityArgument.getPlayers(ctx, "players"))
								)
						)
					).then(
						literal("skip").requires(
								Permissions.require("metacraft.cutscenes.skip", 0)
						).executes(ctx -> {
							var player = ctx.getSource().getPlayerOrException();
							var scene = CutsceneHelper.getCutscene(player);
							if (scene.isPresent()) {
								if (scene.get().getCutscene().isSkippable()) {
									if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
										CutsceneHelper.stopPlayerSpecificCutscene(player);
										ctx.getSource().sendSuccess(
												() -> Component.literal("You skipped the cutscene"), false
										);
									} else  {
										return leaveCutscene(ctx);
									}
									return 1;
								} else {
									ctx.getSource().sendFailure(Component.literal("This cutscene is not skippable!"));
								}
							} else {
								ctx.getSource().sendFailure(Component.literal("You are not in a cutscene!"));
							}
							return 0;
						})
					).then(
						literal("reload").requires(Permissions.require("metacraft.cutscenes.reload", 4)).executes(ctx -> {
							CutscenesConfig.reload(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(
									() -> Component.literal(
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
										argument("players", EntityArgument.players()).executes(
											ctx -> playMultiplayerCutscene(
													ctx, StringArgumentType.getString(ctx, "cutscene"),
													StringArgumentType.getString(ctx, "name"),
													EntityArgument.getPlayers(ctx, "players")
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
												List.of(ctx.getSource().getPlayerOrException())
										)
								).then(
									argument("players", EntityArgument.players()).requires(
											Permissions.require("metacraft.cutscenes.multiplayer.join.others", 2)
									).executes(
										ctx -> joinMultiplayerCutscene(
												ctx, StringArgumentType.getString(ctx, "name"),
												EntityArgument.getPlayers(ctx, "players")
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
									argument("players", EntityArgument.players()).requires(
											Permissions.require("metacraft.cutscenes.multiplayer.leave.others", 2)
									).executes(ctx -> leaveCutscene(ctx, EntityArgument.getPlayers(ctx, "players")))
							)
						)
					)
				);
			}
		);
	}

}
