package se.metacraft.kth_auth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import se.metacraft.kth_auth.KTHAuth;
import se.metacraft.kth_auth.verify.VerificationDetails;
import se.metacraft.kth_auth.verify.Verifier;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code /kth-auth} command for METAcraft moderators.
 */
public class KTHAuthCommand {
	private final Verifier verifier;

	public KTHAuthCommand(Verifier verifier) {
		this.verifier = verifier;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			LiteralArgumentBuilder.<CommandSourceStack>literal("kth-auth")
				.requires(PermissionPredicates.require(
					Identifier.fromNamespaceAndPath(KTHAuth.NAMESPACE, "kth-auth"), PermissionLevel.GAMEMASTERS
				))
				.then(
					Commands.literal("info")
						.then(
							Commands.argument("player", StringArgumentType.greedyString())
								.suggests(this::suggestPlayers)
								.executes(ctx -> {
									String player = StringArgumentType.getString(ctx, "player");
									UUID playerUuid = this.getPlayer(ctx, player);
									if (playerUuid == null) return 0;
									return this.info(ctx, playerUuid);
								})
						)
				)
				.then(
					Commands.literal("unverify")
						.then(
							Commands.argument("player", StringArgumentType.greedyString())
								.suggests(this::suggestPlayers)
								.executes(ctx -> {
									String player = StringArgumentType.getString(ctx, "player");
									UUID playerUuid = this.getPlayer(ctx, player);
									if (playerUuid == null) return 0;
									return this.unVerify(ctx, playerUuid);
								})
						)
				)
				.then(
					Commands.literal("disable")
						.then(
							Commands.argument("duration", StringArgumentType.word())
								.executes(ctx -> this.disable(ctx, StringArgumentType.getString(ctx, "duration")))
						)
				)
				.then(
					Commands.literal("enable")
						.executes(this::enable)
				)
		);
	}

	private UUID getPlayer(CommandContext<CommandSourceStack> ctx, String player) {
		UUID uuid = null;
		ServerPlayer onlinePlayer = ctx.getSource().getServer().getPlayerList().getPlayer(player);
		if (onlinePlayer != null) {
			uuid = onlinePlayer.getUUID();
		}
		if (uuid == null) {
			try {
				uuid = UUID.fromString(player);
			} catch (IllegalArgumentException ignored) {
			}
		}
		if (uuid != null) {
			return uuid;
		}
		ctx.getSource().sendFailure(Component.literal(
			"Could not find player \"" + player + "\". To get player info by username, the " +
				"player needs to be online on the server right now. Otherwise, please provide " +
				"the player's UUID instead."
		));
		return null;
	}

	private Component copy(String text) {
		return Component.literal(text)
			.setStyle(Style.EMPTY
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy to clipboard.")))
				.withClickEvent(new ClickEvent.CopyToClipboard(text))
			);
	}

	private int info(CommandContext<CommandSourceStack> ctx, UUID playerUuid) {
		Optional<VerificationDetails> detailsOptional = this.verifier.getVerificationDetails(playerUuid);
		if (detailsOptional.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal(
				"No verification details found for " + playerUuid
			));
			return 0;
		}
		VerificationDetails details = detailsOptional.get();

		MutableComponent text = Component.literal("");
		text.append(Component.literal("=== Verification details ===\n"));
		text.append(Component.literal("    Player UUID: "));
		text.append(copy(playerUuid.toString()));
		text.append(Component.literal("\n"));

		DateFormat dateFormat = DateFormat.getDateTimeInstance();
		dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Stockholm"));
		Date verifiedDate = new Date(details.verifiedAt());
		Date acceptedGdprDate = details.acceptedGdprAt().isPresent() ? new Date(details.acceptedGdprAt().get()) : null;
		Date lastLoginDate = details.lastLoginAt().isPresent() ? new Date(details.lastLoginAt().get()) : null;
		text.append(Component.literal("    Verified at: " + dateFormat.format(verifiedDate)));
		text.append(Component.literal("\n"));
		text.append(Component.literal("    Accepted GDPR at: " + (acceptedGdprDate == null ? " Not accepted" : dateFormat.format(acceptedGdprDate))));
		text.append(Component.literal("\n"));
		text.append(Component.literal("    Last login: " + (lastLoginDate == null ? " Unknown" : dateFormat.format(lastLoginDate))));
		text.append(Component.literal("\n"));

		if (details.isPlusOne()) {
			text.append(Component.literal("    Type: +1\n"));
			text.append(Component.literal("    Invited by: "));
			text.append(copy("" + details.plusOneBy()));
			text.append(Component.literal("\n"));
		} else {
			text.append(Component.literal("    Type: Verified by KTH-email\n"));
			text.append(Component.literal("    Email: "));
			Optional<String> email = details.email();
			if (email.isEmpty()) throw new RuntimeException(); // Should never happen
			text.append(copy(email.orElse(null)));
			text.append(Component.literal("\n"));

			Collection<VerificationDetails> byEmail = this.verifier.getByEmail(email.get());
			if (byEmail.size() > 1) {
				text.append(Component.literal("    Other accounts with same email:\n"));
				for (Component component : byEmail.stream()
					.map(plusOne -> copy(plusOne.playerUuid().toString()))
					.toList()) {
					text.append(Component.literal("    - "));
					text.append(component);
					text.append(Component.literal("\n"));
				}
			}
		}

		Collection<VerificationDetails> plusOnes = this.verifier.getPlusOnes(playerUuid);
		if (!plusOnes.isEmpty()) {
			text.append(Component.literal("    Plus ones:\n"));
			for (Component component : plusOnes.stream()
				.map(plusOne -> copy(plusOne.playerUuid().toString()))
				.toList()) {
				text.append(Component.literal("    - "));
				text.append(component);
				text.append(Component.literal("\n"));
			}
		}

		ctx.getSource().sendSuccess(() -> text, false);

		return 1;
	}

	private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		List<UUID> alreadyListed = new ArrayList<>();
		for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
			suggest(builder, player.getPlainTextName());
			alreadyListed.add(player.getUUID());
		}
		this.verifier.getAllVerifiedPlayers()
			.stream()
			.filter(d -> !alreadyListed.contains(d.playerUuid()))
			.forEach(d -> suggest(builder, d.playerUuid().toString()));

		return builder.buildFuture();
	}

	private void suggest(SuggestionsBuilder builder, String str) {
		if (str.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
			builder.suggest(str);
		}
	}

	private int disable(CommandContext<CommandSourceStack> ctx, String duration) {
		Matcher matcher = Pattern.compile("(\\d+)([mhd])").matcher(duration);
		if (!matcher.matches()) {
			ctx.getSource().sendFailure(Component.literal(
				"Invalid duration \"" + duration + "\". Use for example 30m, 8h or 2d."
			));
			return 0;
		}
		long amount = Long.parseLong(matcher.group(1));
		TimeUnit unit = switch (matcher.group(2)) {
			case "m" -> TimeUnit.MINUTES;
			case "h" -> TimeUnit.HOURS;
			default -> TimeUnit.DAYS;
		};
		long disabledUntil = System.currentTimeMillis() + unit.toMillis(amount);

		this.verifier.disableUntil(disabledUntil);

		DateFormat dateFormat = DateFormat.getDateTimeInstance();
		dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Stockholm"));
		ctx.getSource().sendSuccess(() -> Component.literal(
			"KTH-Auth is disabled until " + dateFormat.format(new Date(disabledUntil)) + "."
		), true);
		return 1;
	}

	private int enable(CommandContext<CommandSourceStack> ctx) {
		this.verifier.disableUntil(0);
		ctx.getSource().sendSuccess(() -> Component.literal("KTH-Auth is enabled again."), true);
		return 1;
	}

	private int unVerify(CommandContext<CommandSourceStack> ctx, UUID playerUuid) {
		Optional<VerificationDetails> details = this.verifier.getVerificationDetails(playerUuid);
		if (details.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal(
				"No verification details found for " + playerUuid
			));
			return 0;
		}

		this.verifier.unVerify(playerUuid);

		ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayer(playerUuid);
		if (player != null) {
			player.connection.disconnect(Component.translatable("disconnect.kth_auth.unverified"));
		}

		ctx.getSource().sendSuccess(() -> Component.literal(playerUuid + " is no longer verified."), true);

		return 1;
	}
}
