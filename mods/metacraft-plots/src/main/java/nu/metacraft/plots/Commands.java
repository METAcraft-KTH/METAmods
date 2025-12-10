package nu.metacraft.plots;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.plots.gui.ProtectorateMenu;
import nu.metacraft.plots.item.PlotKey;
import nu.metacraft.plots.zone.PlayerOwnedProtectorate;
import nu.metacraft.plots.zone.PlotData;
import nu.metacraft.plots.zone.PlotDataTypes;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.util.ZoneCommandUtils;
import nu.metacraft.zones.zone.RealZone;
import nu.metacraft.zones.zone.Zone;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Commands {

	private static final String PROTECTORATE = "protectorate";

	public static final SuggestionProvider<CommandSourceStack> PLOT_NAME_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return SharedSuggestionProvider.suggest(
				ZoneManager.getInstance(ctx.getSource().getServer()).getZones().getZones().stream().filter(
						zone -> zone.get(PlotDataTypes.PLOT).isPresent()
				).map(RealZone::getName),
				suggestionsBuilder
		);
	};

	private static boolean canModifyOtherProtectorates(CommandSourceStack source) {
		return Permissions.check(source, "metacraft.zone.protectorates.modify_members", 2);
	}

	public static final SuggestionProvider<CommandSourceStack> PROTECTORATE_NAME_SUGGESTIONS_ANY = (ctx, suggestionsBuilder) ->
		SharedSuggestionProvider.suggest(
				ZoneManager.getInstance(ctx.getSource().getServer()).getZones().getZones().stream().filter(
						zone -> zone.get(PlotDataTypes.PLAYER_PROTECTORATE).isPresent()
				).map(RealZone::getName),
				suggestionsBuilder
		);
	public static final SuggestionProvider<CommandSourceStack> PROTECTORATE_NAME_SUGGESTIONS_MEMBER = getProvider(PlayerOwnedProtectorate::isAllowed);
	public static final SuggestionProvider<CommandSourceStack> PROTECTORATE_NAME_SUGGESTIONS_ADMIN = getProvider(PlayerOwnedProtectorate::canModifyMembers);
	public static final SuggestionProvider<CommandSourceStack> PROTECTORATE_NAME_SUGGESTIONS_OWNER = getProvider(PlayerOwnedProtectorate::isOwner);

	public static SuggestionProvider<CommandSourceStack> getProvider(
			BiPredicate<PlayerOwnedProtectorate, Player> hasPermission
	) {
		return (ctx, suggestionsBuilder) -> {
			boolean allowAll = canModifyOtherProtectorates(ctx.getSource());
			Player player = allowAll ? null : ctx.getSource().getPlayerOrException();
			return SharedSuggestionProvider.suggest(
					ZoneManager.getInstance(ctx.getSource().getServer()).getZones().getZones().stream().filter(
							zone -> zone.get(PlotDataTypes.PLAYER_PROTECTORATE).map(
									protectorate -> allowAll || hasPermission.test(protectorate, player)
							).orElse(false)
					).map(RealZone::getName),
					suggestionsBuilder
			);
		};
	}

	public static SuggestionProvider<CommandSourceStack> getFriendlyKeyNamesSuggestions(String plotArgument) {
		return (ctx, suggestionsBuilder) -> {
			return SharedSuggestionProvider.suggest(
					getPlot(ctx, plotArgument).getSecondaryKeyNames().stream().map(StringArgumentType::escapeIfRequired),
					suggestionsBuilder
			);
		};
	}

	private static final DynamicCommandExceptionType NOT_A_PLOT = new DynamicCommandExceptionType(
			object -> Component.literal(object + " is not a plot!")
	);

	private static final DynamicCommandExceptionType NOT_A_PROTECTORATE = new DynamicCommandExceptionType(
			object -> Component.literal(object + " is not a protectorate!")
	);

	private static final DynamicCommandExceptionType NOT_MEMBER = new DynamicCommandExceptionType(
			object -> Component.literal("You are not a member of " + object + "!")
	);

	private static final DynamicCommandExceptionType NOT_ADMIN = new DynamicCommandExceptionType(
			object -> Component.literal("You are not an admin of " + object + "!")
	);

	private static final DynamicCommandExceptionType NOT_OWNER = new DynamicCommandExceptionType(
			object -> Component.literal("You are not an owner of " + object + "!")
	);


	private static final DynamicCommandExceptionType FRIENDLY_NAME_TAKE = new DynamicCommandExceptionType(
			object -> Component.literal(object + " is already used by another key!")
	);

	private static void printPlayers(
			PlayerOwnedProtectorate protectorate,
			String prefix,
			CommandContext<CommandSourceStack> ctx,
			Collection<NameAndId> players
	) {
		if (players.isEmpty()) {
			ctx.getSource().sendSuccess(
					() -> Component.literal("No members modified"),
					false
			);
		} else {
			Component message = Component.literal(prefix + " " + players.stream().map(
					NameAndId::name
			).collect(Collectors.joining(", ")));
			protectorate.getOwnersAndAdmins().filter(
					owner -> ctx.getSource().getPlayer() == null || !ctx.getSource().getPlayer().getUUID().equals(owner)
			).forEach(owner -> {
				Optional.ofNullable(ctx.getSource().getServer().getPlayerList().getPlayer(owner)).ifPresent(o -> {
					o.sendSystemMessage(message);
				});
			});
			ctx.getSource().sendSuccess(() -> message, false);
		}
	}

	private static Stream<String> getNames(Collection<UUID> ids, CommandSourceStack source) {
		return getNames(ids.stream(), source);
	}

	private static Stream<String> getNames(Stream<UUID> ids, CommandSourceStack source) {
		return ids.map(
			member -> source.getServer().services().nameToIdCache().get(member).map(NameAndId::name).orElse(null)
		).filter(Objects::nonNull);
	}

	private static SuggestionProvider<CommandSourceStack> getPlayerAddSuggestions(
			String protectorateArg,
			BiPredicateThrowing<PlayerOwnedProtectorate, Player> alreadyPresentCheck
	) {
		return (ctx, builder) -> {
			var protectorate = getProtectorateUnsafe(ctx, protectorateArg);
			return SharedSuggestionProvider.suggest(
					ctx.getSource().getServer().getPlayerList().getPlayers().stream().filter(
							player -> {
								try {
									return !alreadyPresentCheck.test(protectorate, player);
								} catch (CommandSyntaxException ignored) {
									return false;
								}
							}
					).map(player -> player.getGameProfile().name()),
					builder
			);
		};
	}

	private static SuggestionProvider<CommandSourceStack> getExistingPlayerSuggestions(
			String protectorateArg,
			BiFunctionThrowing<CommandContext<CommandSourceStack>, PlayerOwnedProtectorate, Stream<UUID>> existingPlayers
	) {
		return (ctx, builder) -> {
			var protectorate = getProtectorateUnsafe(ctx, protectorateArg);
			return SharedSuggestionProvider.suggest(
					existingPlayers.apply(ctx, protectorate).filter(
							player -> ctx.getSource().getServer().services().nameToIdCache().get(player).isPresent()
					).map(
							player -> ctx.getSource().getServer().services().nameToIdCache().get(player).get().name()
					),
					builder
			);
		};
	}

	public static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("land-ownership").then(
						literal("gui").executes(ctx -> {
							new ProtectorateMenu(ctx.getSource().getPlayerOrException()).open();
							return 1;
						})
					).then(
						literal("balance").then(
							protectorateIfMember(PROTECTORATE).executes(ctx -> {
								var protectorate = getProtectorateFromMember(ctx, PROTECTORATE);
								ctx.getSource().sendSuccess(
										() -> Component.literal("Balance: " + protectorate.getBalance()),
										false
								);
								return 1;
							})
						)
					).then(
						literal("make-protectorate").requires(
							Permissions.require("metacraft.zone.protectorates.create", 2)
						).then(
							ZoneCommandUtils.zone("zone").executes(ctx -> {
								Zone zone = ZoneCommandUtils.getZone(ctx, "zone");
								zone.getOrCreate(PlotDataTypes.PLAYER_PROTECTORATE);
								ctx.getSource().sendSuccess(() -> Component.literal(
										zone.getName() + " is now a protectorate"
								), true);
								return 1;
							})
						)
					).then(
						literal("revoke-protectorate").requires(
							Permissions.require("metacraft.zone.protectorates.remove", 2)
						).then(
							literal("yes-im-sure").then(
								literal("yes-im-really-sure").then(
									protectorateAny(PROTECTORATE).executes(ctx -> {
										RealZone zone = ZoneCommandUtils.getZone(ctx, PROTECTORATE);
										zone.removeZoneData(PlotDataTypes.PLAYER_PROTECTORATE);
										ctx.getSource().sendSuccess(() -> Component.literal(
												zone.getName() + " is no longer a protectorate"
										), true);
										return 1;
									})
								)
							)
						)
					).then(
						literal("member").then(
							literal("add").then(
								protectorateIfAdmin(PROTECTORATE).then(
									argument("player", GameProfileArgument.gameProfile()).suggests(
										getPlayerAddSuggestions(PROTECTORATE, PlayerOwnedProtectorate::isAllowed)
									).executes(ctx -> {
										var players = new HashSet<>(GameProfileArgument.getGameProfiles(ctx, "player"));
										var protectorate = getProtectorateFromAdmin(ctx,PROTECTORATE);
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (protectorate.isAllowed(id.id())) {
												it.remove();
												continue;
											}
											protectorate.addMember(id.id());
										}
										printPlayers(protectorate, "Added new members:", ctx, players);
										return players.size();
									})
								)
							)
						).then(
							literal("list").then(
								protectorateIfMember(PROTECTORATE).executes(ctx -> {
									var protectorate = getProtectorateFromMember(ctx,PROTECTORATE);
									ctx.getSource().sendSuccess(
											() -> Component.literal(
												"Members:\n" + getNames(
														protectorate.getMembers(), ctx.getSource()
												).collect(Collectors.joining("\n"))
											), false
									);
									return 1;
								})
							)
						)
					).then(
						literal("owner").then(
							literal("add").then(
								protectorateIfOwner(PROTECTORATE).then(
									argument("player", GameProfileArgument.gameProfile()).suggests(
										getPlayerAddSuggestions(PROTECTORATE, PlayerOwnedProtectorate::isAllowed)
									).executes(ctx -> {
										var players = new HashSet<>(GameProfileArgument.getGameProfiles(ctx, "player"));
										var protectorate = getProtectorateFromOwner(ctx,PROTECTORATE);
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (protectorate.isAllowed(id.id())) {
												it.remove();
												continue;
											}
											protectorate.addOwner(id.id());
										}
										printPlayers(protectorate, "Added new owners:", ctx, players);
										return players.size();
									})
								)
							)
						).then(
							literal("list").then(
								protectorateIfMember(PROTECTORATE).executes(ctx -> {
									var protectorate = getProtectorateFromMember(ctx,PROTECTORATE);
									ctx.getSource().sendSuccess(
										() -> Component.literal(
											"Owners:\n" + getNames(
													protectorate.getOwners(), ctx.getSource()
											).collect(Collectors.joining("\n"))
										), false
									);
									return 1;
								})
							)
						)
					).then(
						literal("admin").then(
							literal("add").then(
								protectorateIfOwner(PROTECTORATE).then(
									argument("player", GameProfileArgument.gameProfile()).suggests(
											getPlayerAddSuggestions(PROTECTORATE, PlayerOwnedProtectorate::isAdmin)
									).executes(ctx -> {
										var players = new HashSet<>(GameProfileArgument.getGameProfiles(ctx, "player"));
										var protectorate = getProtectorateFromOwner(ctx,PROTECTORATE);
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (protectorate.isAllowed(id.id())) {
												it.remove();
												continue;
											}
											protectorate.addAdmin(id.id());
										}
										printPlayers(protectorate, "Added new admins:", ctx, players);
										return players.size();
									})
								)
							)
						).then(
							literal("list").then(
								protectorateIfMember(PROTECTORATE).executes(ctx -> {
									var protectorate = getProtectorateFromMember(ctx,PROTECTORATE);
									ctx.getSource().sendSuccess(
											() -> Component.literal(
												"Members:\n" + getNames(
														protectorate.getAdmins(), ctx.getSource()
												).collect(Collectors.joining("\n"))
											), false
									);
									return 1;
								})
							)
						)
					).then(
						literal("remove").then(
							protectorateIfAdmin(PROTECTORATE).then(
								argument("player", GameProfileArgument.gameProfile()).suggests(
										getExistingPlayerSuggestions(PROTECTORATE, (ctx, protectorate) -> {
											if (
													canModifyOtherProtectorates(ctx.getSource()) ||
													protectorate.isOwner(ctx.getSource().getPlayerOrException())
											) {
												return protectorate.getEveryone();
											}
											if (protectorate.isAdmin(ctx.getSource().getPlayerOrException())) {
												return protectorate.getMembers().stream();
											}
											return Stream.empty();
										})
								).executes(ctx -> {
									var players = new HashSet<>(GameProfileArgument.getGameProfiles(ctx, "player"));
									var protectorate = getProtectorateFromAdmin(ctx, PROTECTORATE);
									boolean isOwner = canModifyOtherProtectorates(ctx.getSource()) ||
											protectorate.isOwner(ctx.getSource().getPlayerOrException());
									boolean isAdmin = canModifyOtherProtectorates(ctx.getSource()) ||
											protectorate.isAdmin(ctx.getSource().getPlayerOrException());
									if (isAdmin || isOwner) {
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (
													(
															protectorate.isOwner(id.id()) ||
															protectorate.isAdmin(id.id())
													) && !isOwner
											) {
												it.remove();
												continue;
											}
											if (
												ctx.getSource().getPlayer() != null &&
												ctx.getSource().getPlayer().getUUID().equals(id.id()) &&
												!canModifyOtherProtectorates(ctx.getSource())
											) {
												it.remove();
												ctx.getSource().sendFailure(Component.literal(
													"Removing yourself from the protectorate is a bad idea, good thing I noticed and stopped you."
												));
												continue;
											}
											if (protectorate.isMember(id.id())) {
												protectorate.removeMember(id.id());
											}
											if (protectorate.isAdmin(id.id())) {
												protectorate.removeAdmin(id.id());
											}
											if (protectorate.isOwner(id.id())) {
												protectorate.removeOwner(id.id());
											}
										}
									}
									printPlayers(protectorate, "Removed members:", ctx, players);
									return players.size();
								})
							)

						)
					).then(
						literal("promote").then(
							protectorateIfOwner(PROTECTORATE).then(
								argument("player", GameProfileArgument.gameProfile()).suggests(
									getExistingPlayerSuggestions(PROTECTORATE, (ctx, protectorate) -> {
										if (
											canModifyOtherProtectorates(ctx.getSource()) ||
											protectorate.isOwner(ctx.getSource().getPlayerOrException())
										) {
											return protectorate.getNonOwners();
										}
										return Stream.empty();
									})
								).executes(ctx -> {
									var players = GameProfileArgument.getGameProfiles(ctx, "player");
									var protectorate = getProtectorateFromOwner(ctx, PROTECTORATE);
									for (var player : players) {
										if (protectorate.isAdmin(player.id())) {
											protectorate.removeAdmin(player.id());
											protectorate.addOwner(player.id());
										}
										if (protectorate.isMember(player.id())) {
											protectorate.removeMember(player.id());
											protectorate.addAdmin(player.id());
										}
									}
									printPlayers(protectorate, "Promoted:", ctx, players);
									return players.size();
								})
							)
						)
					).then(
						literal("demote").then(
							protectorateIfOwner(PROTECTORATE).then(
								argument("player", GameProfileArgument.gameProfile()).suggests(
									getExistingPlayerSuggestions(PROTECTORATE, (ctx, protectorate) -> {
										if (
												canModifyOtherProtectorates(ctx.getSource()) ||
												protectorate.isOwner(ctx.getSource().getPlayerOrException())
										) {
											return protectorate.getOwnersAndAdmins();
										}
										return Stream.empty();
									})
								).executes(ctx -> {
									var players = new HashSet<>(GameProfileArgument.getGameProfiles(ctx, "player"));
									var protectorate = getProtectorateFromOwner(ctx, PROTECTORATE);
									var it = players.iterator();
									while (it.hasNext()) {
										var player = it.next();
										if (
												ctx.getSource().getPlayer() != null &&
												ctx.getSource().getPlayer().getUUID().equals(player.id()) &&
												!canModifyOtherProtectorates(ctx.getSource())
										) {
											it.remove();
											ctx.getSource().sendFailure(Component.literal(
												"Demoting yourself is a bad idea, good thing I noticed and stopped you."
											));
											continue;
										}

										if (protectorate.isMember(player.id())) {
											protectorate.removeMember(player.id());
										}
										if (protectorate.isAdmin(player.id())) {
											protectorate.removeAdmin(player.id());
											protectorate.addMember(player.id());
										}
										if (protectorate.isOwner(player.id())) {
											protectorate.removeOwner(player.id());
											protectorate.addAdmin(player.id());
										}
									}
									printPlayers(protectorate, "Demoted:", ctx, players);
									return players.size();
								})
							)
						)
					).then(
						literal("list").then(
							literal("protectorates").executes(ctx -> {
								ctx.getSource().sendSuccess(() -> Component.literal(
										"Protectorates:\n" + ZoneManager.getInstance(ctx.getSource().getServer()).getZones().getZones().stream().filter(
												zone -> zone.get(PlotDataTypes.PLAYER_PROTECTORATE).isPresent()
										).map(RealZone::getName).collect(Collectors.joining("\n"))
								), false);
								return 1;
							})
						).then(
							literal("members").then(
								protectorateAny(PROTECTORATE).executes(ctx -> {
									var protectorate = getProtectorateUnsafe(ctx,PROTECTORATE);
									ctx.getSource().sendSuccess(
											() -> Component.literal(
												"Members:\n" + getNames(
														protectorate.getEveryone(), ctx.getSource()
												).collect(Collectors.joining("\n"))
											), false
									);
									return 1;
								})
							)
						)
					)
			);
			dispatcher.register(
					literal("plots").requires(
							Permissions.require("metacraft.zone.plots", 2)
					).then(
						literal("make-plot").then(
							ZoneCommandUtils.zone("zone").executes(ctx -> {
								var zone = ZoneCommandUtils.getZone(ctx, "zone");
								zone.getOrCreate(PlotDataTypes.PLOT);
								ctx.getSource().sendSuccess(
										() -> Component.literal("Zone " + zone.getName() + " is now a plot!"),
										true
								);
								return 1;
							})
						)
					).then(
						literal("revoke-plot").then(
							plot("plot").then(
								literal("yes-im-sure").then(
									literal("yes-im-really-sure").executes(ctx -> {
										var zone = ZoneCommandUtils.getZone(ctx, "plot");
										zone.removeZoneData(PlotDataTypes.PLOT);
										ctx.getSource().sendSuccess(
												() -> Component.literal("Zone " + zone.getName() + " is no longer a plot!"),
												true
										);
										return 1;
									})
								)
							)
						)
					).then(
						literal("create-master-key").then(
								plot("plot").executes(ctx -> {
									var data = getPlot(ctx, "plot");
									ctx.getSource().getPlayerOrException().getInventory().add(
											PlotKey.createMasterKey(data)
									);
									ctx.getSource().sendSuccess(
											() -> Component.literal("Obtained master key for plot " + data.getZone().getName()),
											true
									);
									return 1;
								})
						)
					).then(
						literal("create-secondary-key").then(
							plot("plot").then(
								argument("friendly-name", StringArgumentType.string()).executes(ctx -> {
									var data = getPlot(ctx, "plot");
									var friendlyName = StringArgumentType.getString(ctx, "friendly-name");
									ctx.getSource().getPlayerOrException().getInventory().add(
											PlotKey.createKey(friendlyName, data).orElseThrow(
													() -> FRIENDLY_NAME_TAKE.create(friendlyName)
											)
									);
									ctx.getSource().sendSuccess(
											() -> Component.literal("Obtained key for plot " + data.getZone().getName()),
											true
									);
									return 1;
								})
							).executes(ctx -> {
								var data = getPlot(ctx, "plot");
								ctx.getSource().getPlayerOrException().getInventory().add(
										PlotKey.createKey(data)
								);
								ctx.getSource().sendSuccess(
										() -> Component.literal("Obtained key for plot " + data.getZone().getName()),
										true
								);
								return 1;
							})
						)
					).then(
						literal("revoke-secondary-key").then(
							plot("plot").then(
								argument("friendly-name", StringArgumentType.string()).suggests(
										getFriendlyKeyNamesSuggestions("plot")
								).executes(ctx -> {
									var data = getPlot(ctx, "plot");
									var friendlyName = StringArgumentType.getString(ctx, "friendly-name");
									if (data.revokeSecondarySecret(friendlyName)) {
										ctx.getSource().sendSuccess(
												() -> Component.literal("Revoked key " + friendlyName + " from plot " + data.getZone().getName()),
												true
										);
										return 1;
									} else {
										ctx.getSource().sendFailure(Component.literal("Invalid friendly key name!"));
										return 0;
									}
								})
							)
						)
					).then(
						literal("revoke-all-secondary-keys").then(
							plot("plot").then(
								literal("yes-im-sure").executes(ctx -> {
									var data = getPlot(ctx, "plot");
									data.revokeAllSecondarySecrets();
									ctx.getSource().sendSuccess(
											() -> Component.literal("Revoked all secondary keys from plot " + data.getZone().getName()),
											true
									);
									return 1;
								})
							)
						)
					).then(
						literal("revoke-master-key").then(
							plot("plot").then(
								literal("yes-im-sure").executes(ctx -> {
									var data = getPlot(ctx,"plot");
									data.regeneratePlotSecret();
									ctx.getSource().sendSuccess(
											() -> Component.literal("Revoked master key from plot " + data.getZone().getName()),
											true
									);
									return 1;
								})
							)
						)
					)
			);
		});
	}


	public static RequiredArgumentBuilder<CommandSourceStack, ?> plot(String arg) {
		return net.minecraft.commands.Commands.argument(arg, StringArgumentType.string()).suggests(PLOT_NAME_SUGGESTIONS);
	}

	public static RequiredArgumentBuilder<CommandSourceStack, ?> protectorateAny(String arg) {
		return net.minecraft.commands.Commands.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_ANY);
	}
	public static RequiredArgumentBuilder<CommandSourceStack, ?> protectorateIfMember(String arg) {
		return net.minecraft.commands.Commands.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_MEMBER);
	}
	public static RequiredArgumentBuilder<CommandSourceStack, ?> protectorateIfAdmin(String arg) {
		return net.minecraft.commands.Commands.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_ADMIN);
	}
	public static RequiredArgumentBuilder<CommandSourceStack, ?> protectorateIfOwner(String arg) {
		return net.minecraft.commands.Commands.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_OWNER);
	}

	public static PlotData getPlot(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		var zone = ZoneCommandUtils.getZone(ctx, arg);
		return zone.get(PlotDataTypes.PLOT).orElseThrow(
				() -> NOT_A_PLOT.create(zone.getName())
		);
	}

	public static PlayerOwnedProtectorate getProtectorateUnsafe(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		var zone = ZoneCommandUtils.getZone(ctx, arg);
		return zone.get(PlotDataTypes.PLAYER_PROTECTORATE).orElseThrow(
				() -> NOT_A_PROTECTORATE.create(zone.getName())
		);
	}

	public static PlayerOwnedProtectorate getProtectorateFromMember(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		var protectorate = getProtectorateUnsafe(ctx, arg);
		if (canModifyOtherProtectorates(ctx.getSource()) || protectorate.isAllowed(ctx.getSource().getPlayerOrException())) {
			return protectorate;
		} else {
			throw NOT_MEMBER.create(protectorate.getZone().getName());
		}
	}

	public static PlayerOwnedProtectorate getProtectorateFromAdmin(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		var protectorate = getProtectorateUnsafe(ctx, arg);
		if (canModifyOtherProtectorates(ctx.getSource()) || protectorate.canModifyMembers(ctx.getSource().getPlayerOrException())) {
			return protectorate;
		} else {
			throw NOT_ADMIN.create(protectorate.getZone().getName());
		}
	}

	public static PlayerOwnedProtectorate getProtectorateFromOwner(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		var protectorate = getProtectorateUnsafe(ctx, arg);
		if (canModifyOtherProtectorates(ctx.getSource()) || protectorate.isOwner(ctx.getSource().getPlayerOrException())) {
			return protectorate;
		} else {
			throw NOT_OWNER.create(protectorate.getZone().getName());
		}
	}


	@FunctionalInterface
	public interface BiPredicateThrowing<T, E> {
		boolean test(T var1, E var2) throws CommandSyntaxException;
	}

	@FunctionalInterface
	public interface BiFunctionThrowing<T, E, F> {
		F apply(T var1, E var2) throws CommandSyntaxException;
	}
}
