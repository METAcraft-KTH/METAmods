package nu.metacraft.moderation.exile;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import nu.metacraft.moderation.CommandUtil;
import nu.metacraft.moderation.exile.rules.ZoneRule;
import nu.metacraft.moderation.exile.rules.ZoneRuleRegistry;
import nu.metacraft.zones.util.ZoneCommandUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ExileInit {

	public static final SimpleCommandExceptionType ZONE_RULE_WRONG = new SimpleCommandExceptionType(
		Component.literal("Invalid Zone Rule")
	);

	public static final SimpleCommandExceptionType EXILE_DEFINITION_DOES_NOT_EXIST = new SimpleCommandExceptionType(
		Component.literal("That exile definition does not exist.")
	);

	public static final SuggestionProvider<CommandSourceStack> ZONE_RULE_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return SharedSuggestionProvider.suggest(
			ZoneRuleRegistry.REGISTRY.registryKeySet().stream().map(key -> {
				var id = key.identifier();
				if (id.getNamespace().equals("minecraft")) {
					return id.getPath();
				} else {
					return id.toString();
				}
			}), suggestionsBuilder
		);
	};

	public static final SuggestionProvider<CommandSourceStack> EXILE_DEFINITION_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return SharedSuggestionProvider.suggest(
				ExileData.getInstance(ctx.getSource().getServer()).getAll().stream().map(ExileDefinition::getName), suggestionsBuilder
		);
	};

	static Optional<String> getPlayerNameFromUUID(MinecraftServer server, UUID player) {
		return server.services().nameToIdCache().get(player).map(NameAndId::name);
	}

	public static void init() {
		ZoneRuleRegistry.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("exile").requires(Permissions.require("metacraft.exile", 3)).then(
					literal("define").requires(Permissions.require("metacraft.exile.define", 3)).then(
						literal("new").then(
							argument("name", StringArgumentType.string()).executes(ctx -> {
								String name = StringArgumentType.getString(ctx,"name");
								ExileData.getInstance(ctx.getSource().getServer()).add(new ExileDefinition(
									ctx.getSource().getServer(), name
								));
								ctx.getSource().sendSuccess(() -> Component.literal("Added new exile definition " + name), true);
								return 1;
							})
						)
					).then(
						literal("remove").then(
							exileDefinition("exile_name").executes(ctx -> {
								ExileDefinition def = getExileDefinition(ctx, "exile_name");
								ExileData.getInstance(ctx.getSource().getServer()).remove(def.getName());
								ctx.getSource().sendSuccess(() -> Component.literal("Removed exile definition " + def.getName()), true);
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
									ctx.getSource().sendSuccess(
											() -> Component.literal("Set exile command for " + def.getName() + " to " + command), true
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
									ctx.getSource().sendSuccess(
											() -> Component.literal("Set pardon command for " + def.getName() + " to " + command), true
									);
									return 1;
								})
							)
						)
					).then(
						literal("get").then(
							exileDefinition("exile_def").executes(ctx -> {
								var def = getExileDefinition(ctx, "exile_def");
								ctx.getSource().sendSuccess(
									def::toText, false
								);
								return 1;
							})
						)
					)
				).then(
					literal("player").then(
						literal("exile").then(
							argument("player", GameProfileArgument.gameProfile()).then(
								exileDefinition("exile_definition").executes(ctx -> {
									var exile = getExileDefinition(ctx, "exile_definition");
									return addRemovePlayers("player", ctx, player -> {
												var actualPlayer = ctx.getSource().getServer().getPlayerList().getPlayer(player);
												if (actualPlayer != null) {
													ExileData.getInstance(ctx.getSource().getServer()).setExile(actualPlayer, exile);
												} else {
													ExileData.getInstance(ctx.getSource().getServer()).setExile(player, exile);
												}

												return true;
											},
											name -> {
												return Component.literal("Exiled ").append(name).append(Component.literal(" into " + exile.getName()));
											},
											players -> {
												return Component.literal("Exiled " + players + " players into " + exile.getName());
											}
									);
								})
							)
						)
					).then(
						literal("pardon").then(
							argument("player", GameProfileArgument.gameProfile()).executes(ctx -> {
								return addRemovePlayers("player", ctx, player -> {
											var data = ExileData.getInstance(ctx.getSource().getServer());
											if (data.getExile(player).isPresent()) {
												var actualPlayer = ctx.getSource().getServer().getPlayerList().getPlayer(player);
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
											return Component.literal("Pardoned ").append(name);
										},
										players -> {
											return Component.literal("Pardoned " + players + " players");
										}
								);
							})
						)
					).then(
						literal("get").then(
							argument("player", GameProfileArgument.gameProfile()).executes(ctx -> {
								var data = ExileData.getInstance(ctx.getSource().getServer());
								var players = GameProfileArgument.getGameProfiles(ctx, "player").stream().map(
									profile -> data.getExile(profile.id()).map(
										def -> Component.literal("Player " + getPlayerNameFromUUID(
												ctx.getSource().getServer(), profile.id()
										).orElse("missingno") + " is exiled to " + def.getName())
									).orElse(Component.literal("Player " + getPlayerNameFromUUID(
											ctx.getSource().getServer(), profile.id()
									).orElse("missingno") + " is not in exile"))
								).toList();
								players.forEach(message -> ctx.getSource().sendSuccess(() -> message, false));
								return players.size();
							})
						)
					)
				)
			);
		});
	}

	static ArgumentBuilder<CommandSourceStack, ?> addRemoveCommand(ArgumentBuilder<CommandSourceStack, ?> name, boolean add) {
		ArgumentBuilder<CommandSourceStack, ?> zoneCommandPoint;
		name.then(
			exileDefinition("exile_name").then(
				zoneCommandPoint = ZoneCommandUtils.zone("zone").then(
					zoneRule("rule").executes(ctx -> {
						var def = getExileDefinition(ctx, "exile_name");
						var zone = ZoneCommandUtils.getZone(ctx, "zone");
						var rule = getZoneRule(ctx, "rule");
						if (add) {
							def.addRule(zone, rule);
							ctx.getSource().sendSuccess(
								() -> Component.literal(
										"Added rule " + rule.getID() + " to zone " + zone.getName() + " in exile definition " + def.getName()
								), true
							);
						} else {
							def.removeRule(zone, rule);
							ctx.getSource().sendSuccess(
									() -> Component.literal(
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
				ctx.getSource().sendSuccess(
						() -> Component.literal(
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
		String arg, CommandContext<CommandSourceStack> ctx, PlayerAction action,
		Function<String, Component> getSinglePlayerMessage, Int2ObjectFunction<Component> getMultiPlayerMessage
	) throws CommandSyntaxException {
		MutableInt total = new MutableInt(0);
		Mutable<String> name = new MutableObject<>(null);
		for (var player : GameProfileArgument.getGameProfiles(ctx, "player")) {
			if (action.apply(player.id())) {
				total.increment();
				if (total.getValue() == 1) {
					name.setValue(player.name());
				}
			}
		}
		switch (total.getValue()) {
			case 0 -> ctx.getSource().sendSuccess(() -> Component.literal("No player was affected"), false);
			case 1 -> ctx.getSource().sendSuccess(() -> getSinglePlayerMessage.apply(name.getValue()),true);
			default -> ctx.getSource().sendSuccess(() -> getMultiPlayerMessage.apply(total.getValue()),true);
		}
		return total.getValue();
	}

	static ArgumentBuilder<CommandSourceStack, ?> exileDefinition(String arg) {
		return argument(arg, StringArgumentType.string()).suggests(EXILE_DEFINITION_SUGGESTIONS);
	}

	static ExileDefinition getExileDefinition(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		var def = ExileData.getInstance(ctx.getSource().getServer()).get(StringArgumentType.getString(ctx, arg));
		if (def != null) {
			return def;
		} else {
			throw EXILE_DEFINITION_DOES_NOT_EXIST.create();
		}
	}

	static ArgumentBuilder<CommandSourceStack, ?> zoneRule(String arg) {
		return argument(arg, IdentifierArgument.id()).suggests(ZONE_RULE_SUGGESTIONS);
	}

	static ZoneRule getZoneRule(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		var rule = ZoneRuleRegistry.REGISTRY.getValue(IdentifierArgument.getId(ctx, arg));
		if (rule != null) {
			return rule;
		} else {
			throw ZONE_RULE_WRONG.create();
		}
	}

}
