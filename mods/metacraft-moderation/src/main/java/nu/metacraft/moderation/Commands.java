package nu.metacraft.moderation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import nu.metacraft.moderation.moderator_mode.ModeratorModeDefinition;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static final SuggestionProvider<ServerCommandSource> MODERATION_MODE_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return CommandSource.suggestMatching(
				ModerationData.getInstance(
						ctx.getSource().getServer()).getValidDefinitionNamesFor(ctx.getSource().getPlayerOrThrow()
				), suggestionsBuilder
		);
	};

	public static final SimpleCommandExceptionType MODERATION_MODE_INVALID = new SimpleCommandExceptionType(
			Text.literal("That moderation mode does not exist!")
	);

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			registerModerationModeCommand(dispatcher);
		});
	}

	public static void registerModerationModeCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			literal("mod").requires(Permissions.require("metacraft.mod", 3)).then(
				literal("define").requires(Permissions.require("metacraft.mod.define", 3)).then(
					literal("new").then(
						argument("mod", StringArgumentType.string()).executes(ctx -> {
							var name = StringArgumentType.getString(ctx, "mod").toLowerCase(Locale.ROOT);
							var data = ModerationData.getInstance(ctx.getSource().getServer());
							if (data.getDefinition(name).isPresent()) {
								ctx.getSource().sendError(Text.literal("That mode already exists."));
								return 0;
							}
							data.addModeratorDef(new ModeratorModeDefinition(name));
							ctx.getSource().sendFeedback(() -> Text.literal("Created mod definition " + name), true);
							return 1;
						})
					)
				).then(
					literal("remove").then(
						moderationDefinition("mod").executes(ctx -> {
							var def = getModerationDefinition(ctx, "mod");
							ModerationData.getInstance(ctx.getSource().getServer()).removeModeratorDef(def.getName());
							ctx.getSource().sendFeedback(() -> Text.literal("Removed mod definition " + def.getName()), true);
							return 1;
						})
					)
				).then(
					literal("set-vanish").then(
						moderationDefinition("mod").then(
							argument("vanish", BoolArgumentType.bool()).executes(ctx -> {
								var def = getModerationDefinition(ctx, "mod");
								var state = BoolArgumentType.getBool(ctx, "vanish");
								def.setVanish(state);
								ctx.getSource().sendFeedback(() -> Text.literal("Set vanish for " + def.getName() + " to " + state), true);
								return 1;
							})
						)
					)
				).then(
					literal("set-announce-advancements").then(
						moderationDefinition("mod").then(
							argument("announce-advancements", BoolArgumentType.bool()).executes(ctx -> {
								var def = getModerationDefinition(ctx, "mod");
								var state = BoolArgumentType.getBool(ctx, "announce-advancements");
								def.setAnnounceAdvancements(state);
								ctx.getSource().sendFeedback(() -> Text.literal("Set announce-advancements for " + def.getName() + " to " + state), true);
								return 1;
							})
						)
					)
				).then(
					literal("set-prevent-tamed-mob-follow").then(
						moderationDefinition("mod").then(
							argument("prevent-follow", BoolArgumentType.bool()).executes(ctx -> {
								var def = getModerationDefinition(ctx, "mod");
								var state = BoolArgumentType.getBool(ctx, "prevent-follow");
								def.setPreventTamedMobFollow(state);
								ctx.getSource().sendFeedback(() -> Text.literal("Set prevent-follow for " + def.getName() + " to " + state), true);
								return 1;
							})
						)
					)
				).then(
					literal("set-separate-player-data").then(
						moderationDefinition("mod").then(
							argument("separate-player-data", BoolArgumentType.bool()).executes(ctx -> {
								var def = getModerationDefinition(ctx, "mod");
								var state = BoolArgumentType.getBool(ctx, "separate-player-data");
								def.setSeparatePlayerData(state);
								ctx.getSource().sendFeedback(() -> Text.literal("Set separate player data state for " + def.getName() + " to " + state), true);
								return 1;
							})
						)
					)
				).then(
					literal("set-enter-command").then(
						moderationDefinition("mod").then(
							CommandUtil.command("arg", 10, dispatcher, (ctx, command) -> {
								var mod = getModerationDefinition(ctx, "mod");
								CommandManager.throwException(ctx.getSource().getServer().getCommandManager().getDispatcher().parse(command, ctx.getSource()));
								mod.setEnterCommand(command);
								ctx.getSource().sendFeedback(() -> Text.literal("Set enter command to " + command + " for " + mod.getName()), true);
								return 1;
							})
						)
					)
				).then(
					literal("set-exit-command").then(
						moderationDefinition("mod").then(
							CommandUtil.command("arg", 10, dispatcher, (ctx, command) -> {
								var mod = getModerationDefinition(ctx, "mod");
								CommandManager.throwException(ctx.getSource().getServer().getCommandManager().getDispatcher().parse(command, ctx.getSource()));
								mod.setExitCommand(command);
								ctx.getSource().sendFeedback(() -> Text.literal("Set exit command to " + command + " for " + mod.getName()), true);
								return 1;
							})
						)
					)
				).then(
					literal("clear-enter-command").then(
						moderationDefinition("mod").executes(ctx -> {
							var mod = getModerationDefinition(ctx, "mod");
							mod.setEnterCommand(null);
							ctx.getSource().sendFeedback(() -> Text.literal("Removed enter command from " + mod.getName()), true);
							return 1;
						})
					)
				).then(
					literal("clear-exit-command").then(
						moderationDefinition("mod").executes(ctx -> {
							var mod = getModerationDefinition(ctx, "mod");
							mod.setExitCommand(null);
							ctx.getSource().sendFeedback(() -> Text.literal("Removed exit command from " + mod.getName()), true);
							return 1;
						})
					)
				).then(
					literal("get").then(
						moderationDefinition("mod").executes(ctx -> {
							var def = getModerationDefinition(ctx, "mod");
							ctx.getSource().sendFeedback(def::toText, false);
							return 1;
						})
					)
				)
			).then(
				literal("as").then(
					moderationDefinition("mod").executes(ctx -> {
						var player = ctx.getSource().getPlayerOrThrow();
						var def = getModerationDefinition(ctx, "mod");
						PlayerModerationState.setModeratorMode(player, def.getName());
						ctx.getSource().sendFeedback(() -> Text.literal("You are now in " + def.getName() + " mode."), true);
						return 1;
					})
				)
			).then(
				literal("set-default").then(
					moderationDefinition("mod").requires(ServerCommandSource::isExecutedByPlayer).executes(ctx -> {
						var player = ctx.getSource().getPlayerOrThrow();
						var def = getModerationDefinition(ctx, "mod");
						((ModerationPlayerData) player).METAcraft_Moderation$setDefaultModerationMode(def.getName());
						ctx.getSource().sendFeedback(() -> Text.literal("Set default moderator mode to " + def.getName()), false);
						return 1;
					})
				).executes(ctx -> {
					var player = ctx.getSource().getPlayerOrThrow();
					((ModerationPlayerData) player).METAcraft_Moderation$setDefaultModerationMode(null);
					ctx.getSource().sendFeedback(() -> Text.literal("Removed default moderator mode."), false);
					return 1;
				})
			).then(
				literal("exit").executes(ctx -> {
					var player = ctx.getSource().getPlayerOrThrow();
					PlayerModerationState.removeModeratorMode(player);
					ctx.getSource().sendFeedback(() -> Text.literal("You are no longer in moderator mode."), true);
					return 1;
				})
			).executes(ctx -> {
				var player = ctx.getSource().getPlayerOrThrow();
				if (((ModerationPlayerData) player).METAcraft_Moderation$getModerationMode().isPresent()) {
					PlayerModerationState.removeModeratorMode(player);
					ctx.getSource().sendFeedback(() -> Text.literal("You are no longer in moderator mode."), true);
					return 1;
				}
				var defName = ((ModerationPlayerData) player).METAcraft_Moderation$getDefaultModerationMode();
				if (defName.isEmpty()) {
					ctx.getSource().sendFeedback(() -> Text.literal("You have no default mode set!"), false);
					return 0;
				}
				if (PlayerModerationState.setModeratorMode(player, defName.get())) {
					ctx.getSource().sendFeedback(() -> Text.literal("You are now in " + defName.get() + " mode."), true);
					return 1;
				} else {
					throw MODERATION_MODE_INVALID.create();
				}
			})
		);
	}


	static ArgumentBuilder<ServerCommandSource, ?> moderationDefinition(String arg) {
		return argument(arg, StringArgumentType.string()).suggests(MODERATION_MODE_SUGGESTIONS);
	}

	static ModeratorModeDefinition getModerationDefinition(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var name = StringArgumentType.getString(ctx, arg);
		if (!PlayerModerationState.canEnterModerationMode(ctx.getSource().getPlayerOrThrow(), name)) {
			throw MODERATION_MODE_INVALID.create();
		}
		return ModerationData.getInstance(ctx.getSource().getServer()).getDefinition(name).orElseThrow(
			MODERATION_MODE_INVALID::create
		);
	}

}
