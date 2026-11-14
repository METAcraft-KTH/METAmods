package se.metacraft.east_vs_west;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.npc.villager.Villager;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /evw}: the teams' scores and your own. {@code /evw top}: the best players. The leaderboard proper is
 * on the website; these read the copy of the standings the server refreshes every few seconds.
 * {@code /evw join}: the team selection dialog, for a player on no team yet.
 *
 * <p>{@code /evw admin ...} (game masters, or the {@code east-vs-west:admin} permission):
 * <ul>
 *   <li>{@code team set <players> <east|west|rest>}: puts players on a team, online or not; what they have
 *       registered moves with them. {@code team get <player>} shows a player's team and item count.</li>
 *   <li>{@code collector spawn}: a collector villager where you stand. {@code collector tag|untag <villager>}:
 *       makes an existing villager a collector, or an ordinary villager again.</li>
 *   <li>{@code open} / {@code close}: whether collectors take items; saved in the config.</li>
 *   <li>{@code refresh}: reads the standings from the database now.</li>
 * </ul>
 */
public final class EvwCommand {

	private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Component.literal(
			"The event isn't running: set enabled in config/" + EastVsWest.MODID + ".json5 and /reload"));
	private static final SimpleCommandExceptionType NOT_A_VILLAGER = new SimpleCommandExceptionType(Component.literal("That isn't a villager"));

	private EvwCommand() {}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		RequiredArgumentBuilder<CommandSourceStack, GameProfileArgument.Result> teamSet = argument("players", GameProfileArgument.gameProfile());
		for (var team : Team.values()) {
			teamSet.then(literal(team.id()).executes(ctx -> setTeam(ctx, team)));
		}
		dispatcher.register(
				literal("evw").executes(ctx -> status(ctx.getSource()))
						.then(literal("top").executes(ctx -> top(ctx.getSource())))
						.then(literal("join").executes(ctx -> join(ctx.getSource())))
						.then(literal("admin").requires(PermissionPredicates.require(EastVsWest.getID("admin"), PermissionLevel.GAMEMASTERS))
								.then(literal("team")
										.then(literal("set").then(teamSet))
										.then(literal("get").then(argument("player", GameProfileArgument.gameProfile()).executes(EvwCommand::getTeam))))
								.then(literal("collector")
										.then(literal("spawn").executes(ctx -> spawnCollector(ctx.getSource())))
										.then(literal("tag").then(argument("villager", EntityArgument.entity()).executes(ctx -> tagCollector(ctx, true))))
										.then(literal("untag").then(argument("villager", EntityArgument.entity()).executes(ctx -> tagCollector(ctx, false)))))
								.then(literal("open").executes(ctx -> setOpen(ctx.getSource(), true)))
								.then(literal("close").executes(ctx -> setOpen(ctx.getSource(), false)))
								.then(literal("refresh").executes(ctx -> refresh(ctx.getSource()))))
		);
	}

	private static EventService running() throws CommandSyntaxException {
		var service = EastVsWest.service();
		if (service == null || !service.isRunning()) {
			throw NOT_RUNNING.create();
		}
		return service;
	}

	private static int status(CommandSourceStack source) throws CommandSyntaxException {
		var service = running();
		var config = EvwConfig.get();
		var standings = service.standings();
		MutableComponent message = Component.literal("East vs West vs Rest").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (var team : Team.values()) {
			message.append(Component.literal("\n  ").withStyle(style -> style.withBold(false).withColor(ChatFormatting.WHITE)).append(team.displayName())
					.append(": " + standings.score(team) + " unique items"));
		}
		var player = source.getPlayer();
		var state = player == null ? null : service.state(player.getUUID());
		if (state != null) {
			MutableComponent own = Component.literal("\n").withStyle(style -> style.withBold(false).withColor(ChatFormatting.GRAY));
			if (state.team() == null) {
				own.append("You aren't on a team yet: /evw join, or right-click a collector.");
			} else {
				own.append("You are on ").append(state.team().displayName()).append(" with " + state.items() + " items. ");
				int missing = config.participationThreshold() - state.items();
				if (service.isLegendary(player.getUUID())) {
					own.append("You wear the " + config.legendary().hat().name() + ".");
				} else if (missing > 0) {
					own.append(missing + " more for the " + config.team(state.team()).hat().name() + " hat.");
				} else {
					own.append("You wear the " + config.team(state.team()).hat().name() + " hat.");
				}
			}
			message.append(own);
		}
		if (!config.submissionsOpen()) {
			message.append(Component.literal("\nThe collector isn't taking items right now.").withStyle(style -> style.withBold(false).withColor(ChatFormatting.RED)));
		}
		source.sendSuccess(() -> message, false);
		return 1;
	}

	private static int join(CommandSourceStack source) throws CommandSyntaxException {
		var service = running();
		var player = source.getPlayerOrException();
		var state = service.state(player.getUUID());
		if (state != null && state.team() != null) {
			source.sendFailure(Component.literal("You are already on team ").append(state.team().displayName()));
			return 0;
		}
		TeamSelect.open(player);
		return 1;
	}

	private static int top(CommandSourceStack source) throws CommandSyntaxException {
		var service = running();
		var top = service.standings().top();
		if (top.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Nobody has registered an item yet."), false);
			return 0;
		}
		MutableComponent message = Component.literal("Top collectors").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (int i = 0; i < top.size(); i++) {
			var score = top.get(i);
			message.append(Component.literal("\n  " + (i + 1) + ". ").withStyle(style -> style.withBold(false).withColor(ChatFormatting.GRAY))
					.append(Component.literal(score.username()).withStyle(score.team().color()))
					.append(": " + score.items()));
		}
		source.sendSuccess(() -> message, false);
		return top.size();
	}

	private static int setTeam(CommandContext<CommandSourceStack> ctx, Team team) throws CommandSyntaxException {
		var service = running();
		var source = ctx.getSource();
		var profiles = GameProfileArgument.getGameProfiles(ctx, "players");
		for (NameAndId profile : profiles) {
			service.setTeam(profile.id(), profile.name(), team, (items, error) -> {
				if (error != null) {
					EastVsWest.LOGGER.warn("Could not put {} on team {}: {}", profile.name(), team.id(), error.toString());
					source.sendFailure(Component.literal("Could not put " + profile.name() + " on a team: the event database can't be reached"));
				} else {
					source.sendSuccess(() -> Component.literal(profile.name() + " is on team ").append(team.displayName())
							.append(" (" + items + " items registered)"), true);
				}
			});
		}
		return profiles.size();
	}

	private static int getTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var service = running();
		var source = ctx.getSource();
		var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
		for (NameAndId profile : profiles) {
			service.lookUp(profile.id(), profile.name(), (info, error) -> {
				if (error != null) {
					EastVsWest.LOGGER.warn("Could not look up {}: {}", profile.name(), error.toString());
					source.sendFailure(Component.literal("Could not look up " + profile.name() + ": the event database can't be reached"));
				} else if (info.isEmpty()) {
					source.sendSuccess(() -> Component.literal(profile.name() + " isn't on a team"), false);
				} else {
					source.sendSuccess(() -> Component.literal(profile.name() + " is on team ").append(info.get().team().displayName())
							.append(" with " + info.get().items() + " items registered"), false);
				}
			});
		}
		return profiles.size();
	}

	private static int spawnCollector(CommandSourceStack source) {
		// Facing whoever spawned it: the way they look, turned around.
		var villager = Collector.spawn(source.getLevel(), source.getPosition(), source.getRotation().y + 180f);
		if (villager == null) {
			source.sendFailure(Component.literal("Could not spawn a villager here"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned a collector villager"), true);
		return 1;
	}

	private static int tagCollector(CommandContext<CommandSourceStack> ctx, boolean collector) throws CommandSyntaxException {
		if (!(EntityArgument.getEntity(ctx, "villager") instanceof Villager villager)) {
			throw NOT_A_VILLAGER.create();
		}
		boolean changed = Collector.setCollector(villager, collector);
		if (!changed) {
			ctx.getSource().sendFailure(Component.literal(collector ? "That villager is already a collector" : "That villager isn't a collector"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal(collector ? "That villager is now a collector" : "That villager is an ordinary villager again"), true);
		return 1;
	}

	private static int setOpen(CommandSourceStack source, boolean open) {
		EvwConfig.setSubmissionsOpen(open);
		source.sendSuccess(() -> Component.literal(open ? "Collectors take items again" : "Collectors no longer take items"), true);
		return 1;
	}

	private static int refresh(CommandSourceStack source) throws CommandSyntaxException {
		running().requestRefresh();
		source.sendSuccess(() -> Component.literal("Reading the standings from the database"), false);
		return 1;
	}
}
