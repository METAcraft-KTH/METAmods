package nu.metacraft.plots;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
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

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	private static final String PROTECTORATE = "protectorate";

	public static final SuggestionProvider<ServerCommandSource> PLOT_NAME_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return CommandSource.suggestMatching(
				ZoneManager.getInstance(ctx.getSource().getServer()).getZones().getZones().stream().filter(
						zone -> zone.get(PlotDataTypes.PLOT).isPresent()
				).map(RealZone::getName),
				suggestionsBuilder
		);
	};

	private static boolean canModifyOtherProtectorates(ServerCommandSource source) {
		return Permissions.check(source, "metacraft.zone.protectorates.modify_members", 2);
	}

	public static final SuggestionProvider<ServerCommandSource> PROTECTORATE_NAME_SUGGESTIONS_ANY = (ctx, suggestionsBuilder) ->
		CommandSource.suggestMatching(
				ZoneManager.getInstance(ctx.getSource().getServer()).getZones().getZones().stream().filter(
						zone -> zone.get(PlotDataTypes.PLAYER_PROTECTORATE).isPresent()
				).map(RealZone::getName),
				suggestionsBuilder
		);
	public static final SuggestionProvider<ServerCommandSource> PROTECTORATE_NAME_SUGGESTIONS_MEMBER = getProvider(PlayerOwnedProtectorate::isAllowed);
	public static final SuggestionProvider<ServerCommandSource> PROTECTORATE_NAME_SUGGESTIONS_ADMIN = getProvider(PlayerOwnedProtectorate::canModifyMembers);
	public static final SuggestionProvider<ServerCommandSource> PROTECTORATE_NAME_SUGGESTIONS_OWNER = getProvider(PlayerOwnedProtectorate::isOwner);

	public static SuggestionProvider<ServerCommandSource> getProvider(
			BiPredicate<PlayerOwnedProtectorate, PlayerEntity> hasPermission
	) {
		return (ctx, suggestionsBuilder) -> {
			boolean allowAll = canModifyOtherProtectorates(ctx.getSource());
			PlayerEntity player = allowAll ? null : ctx.getSource().getPlayerOrThrow();
			return CommandSource.suggestMatching(
					ZoneManager.getInstance(ctx.getSource().getServer()).getZones().getZones().stream().filter(
							zone -> zone.get(PlotDataTypes.PLAYER_PROTECTORATE).map(
									protectorate -> allowAll || hasPermission.test(protectorate, player)
							).orElse(false)
					).map(RealZone::getName),
					suggestionsBuilder
			);
		};
	}

	public static SuggestionProvider<ServerCommandSource> getFriendlyKeyNamesSuggestions(String plotArgument) {
		return (ctx, suggestionsBuilder) -> {
			return CommandSource.suggestMatching(
					getPlot(ctx, plotArgument).getSecondaryKeyNames().stream().map(StringArgumentType::escapeIfRequired),
					suggestionsBuilder
			);
		};
	}

	private static final DynamicCommandExceptionType NOT_A_PLOT = new DynamicCommandExceptionType(
			object -> Text.literal(object + " is not a plot!")
	);

	private static final DynamicCommandExceptionType NOT_A_PROTECTORATE = new DynamicCommandExceptionType(
			object -> Text.literal(object + " is not a protectorate!")
	);

	private static final DynamicCommandExceptionType NOT_MEMBER = new DynamicCommandExceptionType(
			object -> Text.literal("You are not a member of " + object + "!")
	);

	private static final DynamicCommandExceptionType NOT_ADMIN = new DynamicCommandExceptionType(
			object -> Text.literal("You are not an admin of " + object + "!")
	);

	private static final DynamicCommandExceptionType NOT_OWNER = new DynamicCommandExceptionType(
			object -> Text.literal("You are not an owner of " + object + "!")
	);


	private static final DynamicCommandExceptionType FRIENDLY_NAME_TAKE = new DynamicCommandExceptionType(
			object -> Text.literal(object + " is already used by another key!")
	);

	private static void printPlayers(
			PlayerOwnedProtectorate protectorate,
			String prefix,
			CommandContext<ServerCommandSource> ctx,
			Collection<GameProfile> players
	) {
		if (players.isEmpty()) {
			ctx.getSource().sendFeedback(
					() -> Text.literal("No members modified"),
					false
			);
		} else {
			Text message = Text.literal(prefix + " " + players.stream().map(
					GameProfile::getName
			).collect(Collectors.joining(", ")));
			protectorate.getOwnersAndAdmins().filter(
					owner -> ctx.getSource().getPlayer() == null || !ctx.getSource().getPlayer().getUuid().equals(owner)
			).forEach(owner -> {
				Optional.ofNullable(ctx.getSource().getServer().getPlayerManager().getPlayer(owner)).ifPresent(o -> {
					o.sendMessage(message);
				});
			});
			ctx.getSource().sendFeedback(() -> message, false);
		}
	}

	private static Stream<String> getNames(Collection<UUID> ids, ServerCommandSource source) {
		return getNames(ids.stream(), source);
	}

	private static Stream<String> getNames(Stream<UUID> ids, ServerCommandSource source) {
		return ids.map(
			member -> source.getServer().getUserCache().getByUuid(member).map(GameProfile::getName).orElse(null)
		).filter(Objects::nonNull);
	}

	private static SuggestionProvider<ServerCommandSource> getPlayerAddSuggestions(
			String protectorateArg,
			BiPredicateThrowing<PlayerOwnedProtectorate, PlayerEntity> alreadyPresentCheck
	) {
		return (ctx, builder) -> {
			var protectorate = getProtectorateUnsafe(ctx, protectorateArg);
			return CommandSource.suggestMatching(
					ctx.getSource().getServer().getPlayerManager().getPlayerList().stream().filter(
							player -> {
								try {
									return !alreadyPresentCheck.test(protectorate, player);
								} catch (CommandSyntaxException ignored) {
									return false;
								}
							}
					).map(player -> player.getGameProfile().getName()),
					builder
			);
		};
	}

	private static SuggestionProvider<ServerCommandSource> getExistingPlayerSuggestions(
			String protectorateArg,
			BiFunctionThrowing<CommandContext<ServerCommandSource>, PlayerOwnedProtectorate, Stream<UUID>> existingPlayers
	) {
		return (ctx, builder) -> {
			var protectorate = getProtectorateUnsafe(ctx, protectorateArg);
			return CommandSource.suggestMatching(
					existingPlayers.apply(ctx, protectorate).filter(
							player -> ctx.getSource().getServer().getUserCache().getByUuid(player).isPresent()
					).map(
							player -> ctx.getSource().getServer().getUserCache().getByUuid(player).get().getName()
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
							new ProtectorateMenu(ctx.getSource().getPlayerOrThrow()).open();
							return 1;
						})
					).then(
						literal("balance").then(
							protectorateIfMember(PROTECTORATE).executes(ctx -> {
								var protectorate = getProtectorateFromMember(ctx, PROTECTORATE);
								ctx.getSource().sendFeedback(
										() -> Text.literal("Balance: " + protectorate.getBalance()),
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
								ctx.getSource().sendFeedback(() -> Text.literal(
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
										ctx.getSource().sendFeedback(() -> Text.literal(
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
									argument("player", GameProfileArgumentType.gameProfile()).suggests(
										getPlayerAddSuggestions(PROTECTORATE, PlayerOwnedProtectorate::isAllowed)
									).executes(ctx -> {
										var players = new HashSet<>(GameProfileArgumentType.getProfileArgument(ctx, "player"));
										var protectorate = getProtectorateFromAdmin(ctx,PROTECTORATE);
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (protectorate.isAllowed(id.getId())) {
												it.remove();
												continue;
											}
											protectorate.addMember(id.getId());
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
									ctx.getSource().sendFeedback(
											() -> Text.literal(
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
									argument("player", GameProfileArgumentType.gameProfile()).suggests(
										getPlayerAddSuggestions(PROTECTORATE, PlayerOwnedProtectorate::isAllowed)
									).executes(ctx -> {
										var players = new HashSet<>(GameProfileArgumentType.getProfileArgument(ctx, "player"));
										var protectorate = getProtectorateFromOwner(ctx,PROTECTORATE);
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (protectorate.isAllowed(id.getId())) {
												it.remove();
												continue;
											}
											protectorate.addOwner(id.getId());
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
									ctx.getSource().sendFeedback(
										() -> Text.literal(
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
									argument("player", GameProfileArgumentType.gameProfile()).suggests(
											getPlayerAddSuggestions(PROTECTORATE, PlayerOwnedProtectorate::isAdmin)
									).executes(ctx -> {
										var players = new HashSet<>(GameProfileArgumentType.getProfileArgument(ctx, "player"));
										var protectorate = getProtectorateFromOwner(ctx,PROTECTORATE);
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (protectorate.isAllowed(id.getId())) {
												it.remove();
												continue;
											}
											protectorate.addAdmin(id.getId());
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
									ctx.getSource().sendFeedback(
											() -> Text.literal(
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
								argument("player", GameProfileArgumentType.gameProfile()).suggests(
										getExistingPlayerSuggestions(PROTECTORATE, (ctx, protectorate) -> {
											if (
													canModifyOtherProtectorates(ctx.getSource()) ||
													protectorate.isOwner(ctx.getSource().getPlayerOrThrow())
											) {
												return protectorate.getEveryone();
											}
											if (protectorate.isAdmin(ctx.getSource().getPlayerOrThrow())) {
												return protectorate.getMembers().stream();
											}
											return Stream.empty();
										})
								).executes(ctx -> {
									var players = new HashSet<>(GameProfileArgumentType.getProfileArgument(ctx, "player"));
									var protectorate = getProtectorateFromAdmin(ctx, PROTECTORATE);
									boolean isOwner = canModifyOtherProtectorates(ctx.getSource()) ||
											protectorate.isOwner(ctx.getSource().getPlayerOrThrow());
									boolean isAdmin = canModifyOtherProtectorates(ctx.getSource()) ||
											protectorate.isAdmin(ctx.getSource().getPlayerOrThrow());
									if (isAdmin || isOwner) {
										var it = players.iterator();
										while (it.hasNext()) {
											var id = it.next();
											if (
													(
															protectorate.isOwner(id.getId()) ||
															protectorate.isAdmin(id.getId())
													) && !isOwner
											) {
												it.remove();
												continue;
											}
											if (
												ctx.getSource().getPlayer() != null &&
												ctx.getSource().getPlayer().getUuid().equals(id.getId()) &&
												!canModifyOtherProtectorates(ctx.getSource())
											) {
												it.remove();
												ctx.getSource().sendError(Text.literal(
													"Removing yourself from the protectorate is a bad idea, good thing I noticed and stopped you."
												));
												continue;
											}
											if (protectorate.isMember(id.getId())) {
												protectorate.removeMember(id.getId());
											}
											if (protectorate.isAdmin(id.getId())) {
												protectorate.removeAdmin(id.getId());
											}
											if (protectorate.isOwner(id.getId())) {
												protectorate.removeOwner(id.getId());
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
								argument("player", GameProfileArgumentType.gameProfile()).suggests(
									getExistingPlayerSuggestions(PROTECTORATE, (ctx, protectorate) -> {
										if (
											canModifyOtherProtectorates(ctx.getSource()) ||
											protectorate.isOwner(ctx.getSource().getPlayerOrThrow())
										) {
											return protectorate.getNonOwners();
										}
										return Stream.empty();
									})
								).executes(ctx -> {
									var players = GameProfileArgumentType.getProfileArgument(ctx, "player");
									var protectorate = getProtectorateFromOwner(ctx, PROTECTORATE);
									for (var player : players) {
										if (protectorate.isAdmin(player.getId())) {
											protectorate.removeAdmin(player.getId());
											protectorate.addOwner(player.getId());
										}
										if (protectorate.isMember(player.getId())) {
											protectorate.removeMember(player.getId());
											protectorate.addAdmin(player.getId());
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
								argument("player", GameProfileArgumentType.gameProfile()).suggests(
									getExistingPlayerSuggestions(PROTECTORATE, (ctx, protectorate) -> {
										if (
												canModifyOtherProtectorates(ctx.getSource()) ||
												protectorate.isOwner(ctx.getSource().getPlayerOrThrow())
										) {
											return protectorate.getOwnersAndAdmins();
										}
										return Stream.empty();
									})
								).executes(ctx -> {
									var players = new HashSet<>(GameProfileArgumentType.getProfileArgument(ctx, "player"));
									var protectorate = getProtectorateFromOwner(ctx, PROTECTORATE);
									var it = players.iterator();
									while (it.hasNext()) {
										var player = it.next();
										if (
												ctx.getSource().getPlayer() != null &&
												ctx.getSource().getPlayer().getUuid().equals(player.getId()) &&
												!canModifyOtherProtectorates(ctx.getSource())
										) {
											it.remove();
											ctx.getSource().sendError(Text.literal(
												"Demoting yourself is a bad idea, good thing I noticed and stopped you."
											));
											continue;
										}

										if (protectorate.isMember(player.getId())) {
											protectorate.removeMember(player.getId());
										}
										if (protectorate.isAdmin(player.getId())) {
											protectorate.removeAdmin(player.getId());
											protectorate.addMember(player.getId());
										}
										if (protectorate.isOwner(player.getId())) {
											protectorate.removeOwner(player.getId());
											protectorate.addAdmin(player.getId());
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
								ctx.getSource().sendFeedback(() -> Text.literal(
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
									ctx.getSource().sendFeedback(
											() -> Text.literal(
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
								ctx.getSource().sendFeedback(
										() -> Text.literal("Zone " + zone.getName() + " is now a plot!"),
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
										ctx.getSource().sendFeedback(
												() -> Text.literal("Zone " + zone.getName() + " is no longer a plot!"),
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
									ctx.getSource().getPlayerOrThrow().getInventory().insertStack(
											PlotKey.createMasterKey(data)
									);
									ctx.getSource().sendFeedback(
											() -> Text.literal("Obtained master key for plot " + data.getZone().getName()),
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
									ctx.getSource().getPlayerOrThrow().getInventory().insertStack(
											PlotKey.createKey(friendlyName, data).orElseThrow(
													() -> FRIENDLY_NAME_TAKE.create(friendlyName)
											)
									);
									ctx.getSource().sendFeedback(
											() -> Text.literal("Obtained key for plot " + data.getZone().getName()),
											true
									);
									return 1;
								})
							).executes(ctx -> {
								var data = getPlot(ctx, "plot");
								ctx.getSource().getPlayerOrThrow().getInventory().insertStack(
										PlotKey.createKey(data)
								);
								ctx.getSource().sendFeedback(
										() -> Text.literal("Obtained key for plot " + data.getZone().getName()),
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
										ctx.getSource().sendFeedback(
												() -> Text.literal("Revoked key " + friendlyName + " from plot " + data.getZone().getName()),
												true
										);
										return 1;
									} else {
										ctx.getSource().sendError(Text.literal("Invalid friendly key name!"));
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
									ctx.getSource().sendFeedback(
											() -> Text.literal("Revoked all secondary keys from plot " + data.getZone().getName()),
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
									ctx.getSource().sendFeedback(
											() -> Text.literal("Revoked master key from plot " + data.getZone().getName()),
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


	public static RequiredArgumentBuilder<ServerCommandSource, ?> plot(String arg) {
		return CommandManager.argument(arg, StringArgumentType.string()).suggests(PLOT_NAME_SUGGESTIONS);
	}

	public static RequiredArgumentBuilder<ServerCommandSource, ?> protectorateAny(String arg) {
		return CommandManager.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_ANY);
	}
	public static RequiredArgumentBuilder<ServerCommandSource, ?> protectorateIfMember(String arg) {
		return CommandManager.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_MEMBER);
	}
	public static RequiredArgumentBuilder<ServerCommandSource, ?> protectorateIfAdmin(String arg) {
		return CommandManager.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_ADMIN);
	}
	public static RequiredArgumentBuilder<ServerCommandSource, ?> protectorateIfOwner(String arg) {
		return CommandManager.argument(arg, StringArgumentType.string()).suggests(PROTECTORATE_NAME_SUGGESTIONS_OWNER);
	}

	public static PlotData getPlot(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var zone = ZoneCommandUtils.getZone(ctx, arg);
		return zone.get(PlotDataTypes.PLOT).orElseThrow(
				() -> NOT_A_PLOT.create(zone.getName())
		);
	}

	public static PlayerOwnedProtectorate getProtectorateUnsafe(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var zone = ZoneCommandUtils.getZone(ctx, arg);
		return zone.get(PlotDataTypes.PLAYER_PROTECTORATE).orElseThrow(
				() -> NOT_A_PROTECTORATE.create(zone.getName())
		);
	}

	public static PlayerOwnedProtectorate getProtectorateFromMember(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var protectorate = getProtectorateUnsafe(ctx, arg);
		if (canModifyOtherProtectorates(ctx.getSource()) || protectorate.isAllowed(ctx.getSource().getPlayerOrThrow())) {
			return protectorate;
		} else {
			throw NOT_MEMBER.create(protectorate.getZone().getName());
		}
	}

	public static PlayerOwnedProtectorate getProtectorateFromAdmin(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var protectorate = getProtectorateUnsafe(ctx, arg);
		if (canModifyOtherProtectorates(ctx.getSource()) || protectorate.canModifyMembers(ctx.getSource().getPlayerOrThrow())) {
			return protectorate;
		} else {
			throw NOT_ADMIN.create(protectorate.getZone().getName());
		}
	}

	public static PlayerOwnedProtectorate getProtectorateFromOwner(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		var protectorate = getProtectorateUnsafe(ctx, arg);
		if (canModifyOtherProtectorates(ctx.getSource()) || protectorate.isOwner(ctx.getSource().getPlayerOrThrow())) {
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
