package se.datasektionen.mc.metacraft_moderation.exile;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import se.datasektionen.mc.metacraft_moderation.CommandUtil;
import se.datasektionen.mc.metacraft_moderation.exile.rules.ZoneRule;
import se.datasektionen.mc.metacraft_moderation.exile.rules.ZoneRuleRegistry;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ExileInit {

	public static final SimpleCommandExceptionType ZONE_RULE_WRONG = new SimpleCommandExceptionType(
		Text.literal("Invalid Zone Rule")
	);

	public static final SimpleCommandExceptionType EXILE_DEFINITION_DOES_NOT_EXIST = new SimpleCommandExceptionType(
		Text.literal("That exile definition does not exist.")
	);

	public static final SuggestionProvider<ServerCommandSource> ZONE_RULE_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return CommandSource.suggestMatching(
			ZoneRuleRegistry.REGISTRY.getKeys().stream().map(key -> {
				var id = key.getValue();
				if (id.getNamespace().equals("minecraft")) {
					return id.getPath();
				} else {
					return id.toString();
				}
			}), suggestionsBuilder
		);
	};

	public static final SuggestionProvider<ServerCommandSource> EXILE_DEFINITION_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return CommandSource.suggestMatching(
				ExileData.getInstance(ctx.getSource().getServer()).getAll().stream().map(ExileDefinition::getName), suggestionsBuilder
		);
	};

	static Optional<String> getPlayerNameFromUUID(MinecraftServer server, UUID player) {
		return Optional.ofNullable(server.getUserCache()).flatMap(cache -> cache.getByUuid(player)).map(GameProfile::getName);
	}

	public static void init() {
		ZoneRuleRegistry.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("exile").requires(Permissions.require("se.datasektionen.mc.exile", 3)).then(
					literal("define").requires(Permissions.require("se.datasektionen.mc.exile.define", 3)).then(
						literal("new").then(
							argument("name", StringArgumentType.string()).executes(ctx -> {
								String name = StringArgumentType.getString(ctx,"name");
								ExileData.getInstance(ctx.getSource().getServer()).add(new ExileDefinition(
									ctx.getSource().getServer(), name
								));
								ctx.getSource().sendFeedback(() -> Text.literal("Added new exile definition " + name), true);
								return 1;
							})
						)
					).then(
						literal("remove").then(
							exileDefinition("exile_name").executes(ctx -> {
								ExileDefinition def = getExileDefinition(ctx, "exile_name");
								ExileData.getInstance(ctx.getSource().getServer()).remove(def.getName());
								ctx.getSource().sendFeedback(() -> Text.literal("Removed exile definition " + def.getName()), true);
								return 1;
							})
						)
					).then(
						addRemoveCommand(literal("add-zone-rule"), true)
					).then(
						addRemoveCommand(literal("remove-zone-rule"), false)
					).then(
						literal("set-exile-command").then(
							exileDefinition("exile_def").then(
								CommandUtil.command("arg", 10, dispatcher, (ctx, command) -> {
									var def = getExileDefinition(ctx, "exile_def");
									def.setExileCommand(command);
									ctx.getSource().sendFeedback(
											() -> Text.literal("Set exile command for " + def.getName() + " to " + command), true
									);
									return 1;
								})
							)
						)
					).then(
						literal("set-pardon-command").then(
							exileDefinition("exile_def").then(
								CommandUtil.command("arg", 10, dispatcher, (ctx, command) -> {
									var def = getExileDefinition(ctx, "exile_def");
									def.setPardonCommand(command);
									ctx.getSource().sendFeedback(
											() -> Text.literal("Set pardon command for " + def.getName() + " to " + command), true
									);
									return 1;
								})
							)
						)
					).then(
						literal("get").then(
							exileDefinition("exile_def").executes(ctx -> {
								var def = getExileDefinition(ctx, "exile_def");
								ctx.getSource().sendFeedback(
									def::toText, false
								);
								return 1;
							})
						)
					)
				).then(
					literal("player").then(
						literal("exile").then(
							argument("player", GameProfileArgumentType.gameProfile()).then(
								exileDefinition("exile_definition").executes(ctx -> {
									var exile = getExileDefinition(ctx, "exile_definition");
									return addRemovePlayers("player", ctx, player -> {
												var actualPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(player);
												if (actualPlayer != null) {
													ExileData.getInstance(ctx.getSource().getServer()).setExile(actualPlayer, exile);
												} else {
													ExileData.getInstance(ctx.getSource().getServer()).setExile(player, exile);
												}

												return true;
											},
											name -> {
												return Text.literal("Exiled ").append(name).append(Text.literal(" into " + exile.getName()));
											},
											players -> {
												return Text.literal("Exiled " + players + " players into " + exile.getName());
											}
									);
								})
							)
						)
					).then(
						literal("pardon").then(
							argument("player", GameProfileArgumentType.gameProfile()).executes(ctx -> {
								return addRemovePlayers("player", ctx, player -> {
											var data = ExileData.getInstance(ctx.getSource().getServer());
											if (data.getExile(player).isPresent()) {
												var actualPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(player);
												if (actualPlayer != null) {
													data.removeExile(actualPlayer);
												} else {
													data.removeExile(player);
												}
												return true;
											}
											return false;
										},
										name -> {
											return Text.literal("Pardoned ").append(name);
										},
										players -> {
											return Text.literal("Pardoned " + players + " players");
										}
								);
							})
						)
					).then(
						literal("get").then(
							argument("player", GameProfileArgumentType.gameProfile()).executes(ctx -> {
								var data = ExileData.getInstance(ctx.getSource().getServer());
								var players = GameProfileArgumentType.getProfileArgument(ctx, "player").stream().map(
									profile -> data.getExile(profile.getId()).map(
										def -> Text.literal("Player " + getPlayerNameFromUUID(
												ctx.getSource().getServer(), profile.getId()
										).orElse("missingno") + " is exiled to " + def.getName())
									).orElse(Text.literal("Player " + getPlayerNameFromUUID(
											ctx.getSource().getServer(), profile.getId()
									).orElse("missingno") + " is not in exile"))
								).toList();
								players.forEach(message -> ctx.getSource().sendFeedback(() -> message, false));
								return players.size();
							})
						)
					)
				)
			);
		});
	}

	static ArgumentBuilder<ServerCommandSource, ?> addRemoveCommand(ArgumentBuilder<ServerCommandSource, ?> name, boolean add) {
		ArgumentBuilder<ServerCommandSource, ?> zoneCommandPoint;
		name.then(
			exileDefinition("exile_name").then(
				zoneCommandPoint = ZoneCommandUtils.zone("zone").then(
					zoneRule("rule").executes(ctx -> {
						var def = getExileDefinition(ctx, "exile_name");
						var zone = ZoneCommandUtils.getZone(ctx, "zone");
						var rule = getZoneRule(ctx, "rule");
						if (add) {
							def.addRule(zone, rule);
							ctx.getSource().sendFeedback(
								() -> Text.literal(
										"Added rule " + rule.getID() + " to zone " + zone.getName() + " in exile definition " + def.getName()
								), true
							);
						} else {
							def.removeRule(zone, rule);
							ctx.getSource().sendFeedback(
									() -> Text.literal(
											"Removed rule " + rule.getID() + " from zone " + zone.getName() + " in exile definition " + def.getName()
									), true
							);
						}
						return 1;
					})
				)
			)
		);
		if (!add) {
			zoneCommandPoint.executes(ctx -> {
				var def = getExileDefinition(ctx, "exile_name");
				var zone = ZoneCommandUtils.getZone(ctx, "zone");
				def.removeZone(zone);
				ctx.getSource().sendFeedback(
						() -> Text.literal(
								"Removed zone " + zone.getName() + " in exile definition " + def.getName()
						), true
				);
				return 1;
			});
		}
		return name;
	}

	@FunctionalInterface
	public interface PlayerAction {
		boolean apply(UUID player) throws CommandSyntaxException;
	}

	static int addRemovePlayers(
		String arg, CommandContext<ServerCommandSource> ctx, PlayerAction action,
		Function<String, Text> getSinglePlayerMessage, Int2ObjectFunction<Text> getMultiPlayerMessage
	) throws CommandSyntaxException {
		MutableInt total = new MutableInt(0);
		Mutable<String> name = new MutableObject<>(null);
		for (var player : GameProfileArgumentType.getProfileArgument(ctx, "player")) {
			if (action.apply(player.getId())) {
				total.increment();
				if (total.getValue() == 1) {
					name.setValue(player.getName());
				}
			}
		}
		switch (total.getValue()) {
			case 0 -> ctx.getSource().sendFeedback(() -> Text.literal("No player was affected"), false);
			case 1 -> ctx.getSource().sendFeedback(() -> getSinglePlayerMessage.apply(name.getValue()),true);
			default -> ctx.getSource().sendFeedback(() -> getMultiPlayerMessage.apply(total.getValue()),true);
		}
		return total.getValue();
	}

	static ArgumentBuilder<ServerCommandSource, ?> exileDefinition(String arg) {
		return argument(arg, StringArgumentType.string()).suggests(EXILE_DEFINITION_SUGGESTIONS);
	}

	static ExileDefinition getExileDefinition(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var def = ExileData.getInstance(ctx.getSource().getServer()).get(StringArgumentType.getString(ctx, arg));
		if (def != null) {
			return def;
		} else {
			throw EXILE_DEFINITION_DOES_NOT_EXIST.create();
		}
	}

	static ArgumentBuilder<ServerCommandSource, ?> zoneRule(String arg) {
		return argument(arg, IdentifierArgumentType.identifier()).suggests(ZONE_RULE_SUGGESTIONS);
	}

	static ZoneRule getZoneRule(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var rule = ZoneRuleRegistry.REGISTRY.get(IdentifierArgumentType.getIdentifier(ctx, arg));
		if (rule != null) {
			return rule;
		} else {
			throw ZONE_RULE_WRONG.create();
		}
	}

}
