package se.metacraft.skins.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import se.metacraft.skins.DrawingCheck;
import se.metacraft.skins.Material;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.OwnedSkin;
import se.metacraft.skins.OwnershipChange;
import se.metacraft.skins.PlayerSkins;
import se.metacraft.skins.ReloadReport;
import se.metacraft.skins.SkinSummary;
import se.metacraft.skins.item.SkinToken;
import se.metacraft.skins.Skins;
import se.metacraft.skins.pack.PackDelivery;
import se.metacraft.skins.ui.Feedback;
import se.metacraft.skins.ui.SkinMenu;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@code /itemskins}: one permission node per command (Fabric's Permission API, so LuckPerms can
 * delegate each), with a vanilla level to fall back on. Skin ids tab-complete everywhere. The
 * subcommands so far:
 * <ul>
 *   <li>{@code /itemskins} alone ({@code metacraft-skins:menu}, everyone): opens the player's Skin Menu;</li>
 *   <li>{@code list} ({@code metacraft-skins:list}, gamemasters): for a player, opens "All Skins", the Skin Menu with every
 *       skin on the server; anywhere else (the console), every skin in chat, its Kind, any Materials it's limited to and whether
 *       it's a Dyeable Hat ({@code cowboy_hat (helmet: leather, dyeable)}), and why a Dormant one failed;</li>
 *   <li>{@code reload} ({@code metacraft-skins:reload}, admins): re-read the skins and settings and rebuild the pack;
 *       a changed pack is pushed to everyone online, and a reload that took a Look away sends everyone online their items
 *       again. Vanilla {@code /reload} never touches skins;</li>
 *   <li>{@code give <targets> <skin> [count]} ({@code metacraft-skins:give}, gamemasters): Skin Tokens for online players,
 *       1 to 64 of them; what doesn't fit drops at the player's feet;</li>
 *   <li>{@code info <player>} ({@code metacraft-skins:info}, gamemasters): what a player owns, with claim dates and states;</li>
 *   <li>{@code grant <player> <skin>} and {@code revoke <player> <skin>} ({@code metacraft-skins:grant}/{@code revoke},
 *       admins): make a player own a skin without a token, or take it away. An online player is told.</li>
 * </ul>
 * {@code <player>} may be offline: a name is looked up in the server's user cache, as {@code /whitelist} does.
 */
public final class SkinsCommands {
	private SkinsCommands() {}

	private static final SimpleCommandExceptionType NOT_RUNNING =
			new SimpleCommandExceptionType(Component.literal("Skins haven't loaded yet"));
	private static final SimpleCommandExceptionType ONE_PLAYER =
			new SimpleCommandExceptionType(Component.literal("Name just one player"));
	private static final SimpleCommandExceptionType NO_MENU =
			new SimpleCommandExceptionType(Component.literal("You don't have permission to use the Skin Menu"));
	private static final Predicate<CommandSourceStack> MENU = PermissionPredicates.require(MetacraftSkins.getID("menu"), PermissionLevel.ALL);
	private static final DateTimeFormatter CLAIM_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

	public static void init(Supplier<Skins> skins) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("itemskins")
						// Not a requires on the root, which would hide every subcommand from a player denied the menu.
						.executes(ctx -> menu(ctx, get(skins)))
						.then(Commands.literal("list").requires(PermissionPredicates.require(MetacraftSkins.getID("list"), PermissionLevel.GAMEMASTERS))
								.executes(ctx -> list(ctx, get(skins))))
						.then(Commands.literal("reload").requires(PermissionPredicates.require(MetacraftSkins.getID("reload"), PermissionLevel.ADMINS))
								.executes(ctx -> reload(ctx, get(skins))))
						.then(Commands.literal("give").requires(PermissionPredicates.require(MetacraftSkins.getID("give"), PermissionLevel.GAMEMASTERS))
								.then(Commands.argument("targets", EntityArgument.players())
										.then(Commands.argument("skin", StringArgumentType.word()).suggests(skinIds(skins, false))
												.executes(ctx -> give(ctx, get(skins), 1))
												.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
														.executes(ctx -> give(ctx, get(skins), IntegerArgumentType.getInteger(ctx, "count")))))))
						.then(Commands.literal("info").requires(PermissionPredicates.require(MetacraftSkins.getID("info"), PermissionLevel.GAMEMASTERS))
								.then(Commands.argument("player", GameProfileArgument.gameProfile())
										.executes(ctx -> info(ctx, get(skins)))))
						.then(Commands.literal("grant").requires(PermissionPredicates.require(MetacraftSkins.getID("grant"), PermissionLevel.ADMINS))
								.then(Commands.argument("player", GameProfileArgument.gameProfile())
										.then(Commands.argument("skin", StringArgumentType.word()).suggests(skinIds(skins, false))
												.executes(ctx -> grant(ctx, get(skins))))))
						.then(Commands.literal("revoke").requires(PermissionPredicates.require(MetacraftSkins.getID("revoke"), PermissionLevel.ADMINS))
								.then(Commands.argument("player", GameProfileArgument.gameProfile())
										.then(Commands.argument("skin", StringArgumentType.word()).suggests(skinIds(skins, true))
												.executes(ctx -> revoke(ctx, get(skins))))))));
	}

	/** Every skin's id; with {@code dormantToo} false, only the ones that loaded (a Dormant skin can't be granted). */
	private static SuggestionProvider<CommandSourceStack> skinIds(Supplier<Skins> skins, boolean dormantToo) {
		return (ctx, builder) -> {
			Skins current = skins.get();
			if (current == null) return builder.buildFuture();
			return SharedSuggestionProvider.suggest(current.allSkins().stream().filter(s -> dormantToo || !s.dormant()).map(SkinSummary::id), builder);
		};
	}

	private static NameAndId onePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<NameAndId> named = GameProfileArgument.getGameProfiles(ctx, "player");
		if (named.size() != 1) throw ONE_PLAYER.create();
		return named.iterator().next();
	}

	private static Skins get(Supplier<Skins> skins) throws CommandSyntaxException {
		Skins current = skins.get();
		if (current == null) throw NOT_RUNNING.create();
		return current;
	}

	private static int menu(CommandContext<CommandSourceStack> ctx, Skins skins) throws CommandSyntaxException {
		if (!MENU.test(ctx.getSource())) throw NO_MENU.create();
		SkinMenu.open(ctx.getSource().getPlayerOrException(), skins);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, Skins skins) {
		List<SkinSummary> all = skins.allSkins();
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		if (player != null) {
			SkinMenu.openAll(player, skins);
			return all.size();
		}
		long dormant = all.stream().filter(SkinSummary::dormant).count();
		source.sendSuccess(() -> Component.literal(all.size() + " skin" + (all.size() == 1 ? "" : "s") + " (" + dormant + " Dormant) in "
				+ skins.skinsFolder()).withStyle(ChatFormatting.GOLD), false);
		for (SkinSummary skin : all) {
			var line = Component.literal(skin.id()).withStyle(skin.dormant() ? ChatFormatting.GRAY : ChatFormatting.WHITE)
					.append(Component.literal(" (" + skin.kind().map(k -> k.id()).orElse("unknown Kind")
							+ skin.materials().map(m -> ": " + m.stream().map(Material::id).collect(Collectors.joining(", "))).orElse("")
							+ (skin.dyeable() ? ", dyeable" : "") + ")")
							.withStyle(ChatFormatting.GRAY));
			skin.dormantReason().ifPresent(reason -> line.append(Component.literal(" Dormant: " + reason).withStyle(ChatFormatting.RED)));
			source.sendSuccess(() -> line, false);
		}
		return all.size();
	}

	private static int info(CommandContext<CommandSourceStack> ctx, Skins skins) throws CommandSyntaxException {
		NameAndId player = onePlayer(ctx);
		PlayerSkins owned = skins.ownedSkins(player.id());
		CommandSourceStack source = ctx.getSource();
		if (owned.damage().isPresent()) {
			source.sendFailure(Component.literal(player.name() + "'s skins file is damaged, so they have no skins and their skin actions are refused "
					+ "until it's fixed. It is never overwritten. " + owned.damage().get()));
			return 0;
		}
		int count = owned.skins().size();
		source.sendSuccess(() -> Component.literal(player.name() + " owns " + (count == 0 ? "no skins" : count + " skin" + (count == 1 ? "" : "s") + ":"))
				.withStyle(ChatFormatting.GOLD), false);
		for (OwnedSkin skin : owned.skins()) {
			String name = skins.skin(skin.id()).map(SkinSummary::displayName).orElse(skin.id());
			var line = Component.literal(skin.id()).withStyle(skin.state() == OwnedSkin.State.DORMANT ? ChatFormatting.GRAY : ChatFormatting.WHITE)
					.append(Component.literal(name.equals(skin.id()) ? "" : " (" + name + ")").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" claimed " + CLAIM_DATE.format(skin.claimed()) + ", ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(skin.state().label)
							.withStyle(skin.state() == OwnedSkin.State.APPLIED ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			source.sendSuccess(() -> line, false);
		}
		return count;
	}

	private static int give(CommandContext<CommandSourceStack> ctx, Skins skins, int count) throws CommandSyntaxException {
		List<ServerPlayer> targets = List.copyOf(EntityArgument.getPlayers(ctx, "targets"));
		String id = StringArgumentType.getString(ctx, "skin");
		OwnershipChange result = skins.giveTokens(targets, id, count);
		if (result != OwnershipChange.DONE) {
			ctx.getSource().sendFailure(Component.literal(result == OwnershipChange.UNKNOWN_SKIN ? "There's no skin " + id + ". /itemskins list shows them all."
					: id + " is Dormant, so it has no tokens: " + skins.skin(id).flatMap(SkinSummary::dormantReason).orElse("its definition didn't load") + "."));
			return 0;
		}
		String name = skins.skin(id).map(SkinSummary::displayName).orElse(id);
		String to = targets.size() == 1 ? targets.getFirst().getGameProfile().name() : targets.size() + " players";
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + " Skin Token" + (count == 1 ? "" : "s") + ": " + name + " to " + to + "."), true);
		return targets.size();
	}

	private static int grant(CommandContext<CommandSourceStack> ctx, Skins skins) throws CommandSyntaxException {
		NameAndId player = onePlayer(ctx);
		String id = StringArgumentType.getString(ctx, "skin");
		OwnershipChange result = skins.grant(player, id);
		if (result != OwnershipChange.DONE) return refused(ctx, skins, player, id, result);
		String name = skins.skin(id).map(SkinSummary::displayName).orElse(id);
		ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(player.id());
		if (online != null) {
			Feedback.claimed(online, name);
			SkinToken.resend(online);   // their tokens for it now say it's owned
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Granted " + name + " (" + id + ") to " + player.name()
				+ (online == null ? " (offline)" : "") + "."), true);
		return 1;
	}

	private static int revoke(CommandContext<CommandSourceStack> ctx, Skins skins) throws CommandSyntaxException {
		NameAndId player = onePlayer(ctx);
		String id = StringArgumentType.getString(ctx, "skin");
		OwnershipChange result = skins.revoke(player, id);
		if (result != OwnershipChange.DONE) return refused(ctx, skins, player, id, result);
		String name = skins.skin(id).map(SkinSummary::displayName).orElse(id);
		ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(player.id());
		if (online != null) {
			skins.tidy(online);   // the item it was on, if they have it
			Feedback.revokedByAdmin(online, name);
			SkinToken.resend(online);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Revoked " + name + " (" + id + ") from " + player.name()
				+ (online == null ? " (offline). The item it was on is drawn plain wherever it is."
						: ". Their own items were tidied, and the item anywhere else is drawn plain.")), true);
		return 1;
	}

	private static int refused(CommandContext<CommandSourceStack> ctx, Skins skins, NameAndId player, String id, OwnershipChange result) {
		String why = Feedback.whyNot(result, id, new Feedback.Audience.Admin(player.name(), skins.skin(id).flatMap(SkinSummary::dormantReason)));
		ctx.getSource().sendFailure(Component.literal(why));
		return 0;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx, Skins skins) {
		CommandSourceStack source = ctx.getSource();
		ReloadReport report = skins.reload();
		int pushed = report.changed() ? PackDelivery.pushToAll(source.getServer(), skins) : 0;
		if (report.lookTakenAway()) DrawingCheck.redrawEveryone(source.getServer());
		int loaded = report.loaded().size();
		String pack = report.changed()
				? "The pack changed (" + report.sha1().substring(0, 8) + ") and was sent to " + pushed + " player" + (pushed == 1 ? "" : "s") + " online."
				: "The pack is unchanged; nothing was sent.";
		source.sendSuccess(() -> Component.literal("Reloaded skins: " + loaded + " skin" + (loaded == 1 ? "" : "s") + " loaded, "
				+ report.rejections().size() + " Dormant. " + pack).withStyle(ChatFormatting.GOLD), true);
		for (ReloadReport.Rejection r : report.rejections()) {
			source.sendFailure(Component.literal(r.skinId() + " is Dormant: " + r.reason() + " (" + r.file().getFileName() + ")"));
		}
		for (ReloadReport.Warning w : report.warnings()) {
			source.sendSuccess(() -> Component.literal(w.skinId() + ": " + w.message() + " (" + w.file().getFileName() + ")")
					.withStyle(w.prominent() ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
		}
		return loaded;
	}
}
