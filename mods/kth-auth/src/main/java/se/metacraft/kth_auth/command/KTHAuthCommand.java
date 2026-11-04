package se.metacraft.kth_auth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import se.metacraft.kth_auth.KTHAuth;
import se.metacraft.kth_auth.verify.BannedEmail;
import se.metacraft.kth_auth.verify.VerificationDetails;
import se.metacraft.kth_auth.verify.Verifier;

import java.text.DateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The {@code /kth-auth} command for METAcraft moderators.
 */
public class KTHAuthCommand {
	private final Verifier verifier;

	public KTHAuthCommand(Verifier verifier) {
		this.verifier = verifier;
	}

	private static ArgumentType<?> untilArgument() {
		return StringArgumentType.string(); // string instead of word to support åäö
	}

	private static final DynamicCommandExceptionType INVALID_UNIT = new DynamicCommandExceptionType(
		unit -> () -> "Invalid unit: " + unit
	);

	private static final DynamicCommandExceptionType NO_UNIT = new DynamicCommandExceptionType(
		time -> () -> time + " ... canaries??? Please specify a unit of time (e.g. seconds, hours or days)"
	);

	private static final SimpleCommandExceptionType NO_NUMBER = new SimpleCommandExceptionType(
		() -> "No number provided"
	);

	private static final Map<String, InstantProvider> TIME_UNITS = new HashMap<>();

	static {
		InstantProvider seconds = Duration::ofSeconds;
		TIME_UNITS.put("s", seconds);
		TIME_UNITS.put("second", seconds);
		TIME_UNITS.put("seconds", seconds);
		TIME_UNITS.put("sekund", seconds);
		TIME_UNITS.put("sekunder", seconds);
		InstantProvider minutes = Duration::ofMinutes;
		TIME_UNITS.put("m", minutes);
		TIME_UNITS.put("minute", minutes);
		TIME_UNITS.put("minutes", minutes);
		TIME_UNITS.put("minuter", minutes);
		TIME_UNITS.put("minut", minutes);
		InstantProvider hours = Duration::ofHours;
		TIME_UNITS.put("h", hours);
		TIME_UNITS.put("hour", hours);
		TIME_UNITS.put("hours", hours);
		TIME_UNITS.put("timmar", hours);
		TIME_UNITS.put("timme", hours);
		InstantProvider days = Duration::ofDays;
		TIME_UNITS.put("d", days);
		TIME_UNITS.put("day", days);
		TIME_UNITS.put("days", days);
		TIME_UNITS.put("dagar", days);
		TIME_UNITS.put("dag", days);
		IntInstantProvider weeks = Period::ofWeeks;
		TIME_UNITS.put("w", weeks);
		TIME_UNITS.put("week", weeks);
		TIME_UNITS.put("weeks", weeks);
		TIME_UNITS.put("vecka", weeks);
		TIME_UNITS.put("veckor", weeks);
		IntInstantProvider months = time -> Period.ofDays(30 * time);
		TIME_UNITS.put("mo", months);
		TIME_UNITS.put("må", months);
		TIME_UNITS.put("month", months);
		TIME_UNITS.put("months", months);
		TIME_UNITS.put("månad", months);
		TIME_UNITS.put("månader", months);
		IntInstantProvider years = time -> Period.ofDays(365 * time);
		TIME_UNITS.put("y", years);
		TIME_UNITS.put("å", years);
		TIME_UNITS.put("years", years);
		TIME_UNITS.put("year", years);
		TIME_UNITS.put("år", years);
	}

	@FunctionalInterface
	public interface InstantProvider {
		TemporalAmount get(long time);
	}

	@FunctionalInterface
	public interface IntInstantProvider extends InstantProvider {

		@Override
		default TemporalAmount get(long time) {
			return get((int) time);
		}

		TemporalAmount get(int time);
	}

	private static Instant getUntil(
		CommandContext<?> ctx, String name
	) throws CommandSyntaxException {
		var param = StringArgumentType.getString(ctx, name).replace(" ", "");
		try {
			return Instant.parse(param);
		} catch (DateTimeParseException ignored) {}

		var onlySuffix = param.replaceAll("[0-9]", "");
		if (onlySuffix.isEmpty()) {
			throw NO_UNIT.create(param);
		}
		var onlyNumber = param.replaceAll("[^0-9]", "");
		var unit = TIME_UNITS.get(onlySuffix);
		if (unit == null) {
			throw INVALID_UNIT.create(onlySuffix);
		}
		long time;
		try {
			time = Long.parseLong(onlyNumber);
		} catch (NumberFormatException err) {
			throw NO_NUMBER.create();
		}
		return Instant.now().plus(unit.get(time));
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			LiteralArgumentBuilder.<CommandSourceStack>literal("kth-auth")
				.requires(PermissionPredicates.require(
					Identifier.fromNamespaceAndPath(KTHAuth.NAMESPACE, "kth-auth"), PermissionLevel.ADMINS
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
					Commands.literal("ban")
						.then(
							Commands.argument("kth-account", StringArgumentType.string())
								.suggests(this::suggestUnbannedKTHAccounts)
								.then(
									Commands.argument("reason", StringArgumentType.greedyString())
										.executes(ctx -> this.ban(
											ctx, StringArgumentType.getString(ctx, "kth-account"),
											Optional.empty(),
											Optional.of(StringArgumentType.getString(ctx, "reason"))
										))
								)
								.executes(ctx -> this.ban(
									ctx, StringArgumentType.getString(ctx, "kth-account"),
									Optional.empty(), Optional.empty()
								))
						)
				)
				.then(
					Commands.literal("tempban")
						.then(
							Commands.argument("kth-account", StringArgumentType.string())
								.suggests(this::suggestUnbannedKTHAccounts)
								.then(
									Commands.argument("duration", untilArgument())
										.then(
											Commands.argument("reason", StringArgumentType.greedyString())
												.executes(ctx -> this.ban(
													ctx, StringArgumentType.getString(ctx, "kth-account"),
													Optional.of(getUntil(ctx, "duration")),
													Optional.of(StringArgumentType.getString(ctx, "reason"))
												))
										)
										.executes(ctx -> this.ban(
											ctx, StringArgumentType.getString(ctx, "kth-account"),
											Optional.of(getUntil(ctx, "duration")), Optional.empty()
										))
								)
						)
				)
				.then(
					Commands.literal("unban")
						.then(
							Commands.argument("kth-account", StringArgumentType.greedyString())
								.suggests(this::suggestBannedKTHAccounts)
								.executes(ctx -> this.unban(ctx, StringArgumentType.getString(ctx, "kth-account")))
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

	private CompletableFuture<Suggestions> suggestUnbannedKTHAccounts(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(
			this.verifier.getAllVerifiedPlayers()
				.stream()
				.filter(d -> d.email().isPresent() && !verifier.isBannedEmail(d.email().get()))
				.map(d -> "\"" + d.email().get() + "\"").distinct(),
			builder
		);
	}

	private CompletableFuture<Suggestions> suggestBannedKTHAccounts(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(
			this.verifier.getBannedEmails(),
			builder
		);
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

	public static String name(CommandContext<CommandSourceStack> ctx, UUID uuid) {
		return ctx.getSource().getServer().services().nameToIdCache().get(uuid).map(NameAndId::name).orElseGet(uuid::toString);
	}

	private int ban(
		CommandContext<CommandSourceStack> ctx, String email,
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Instant> until,
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> reason
	) {
		var ban = verifier.getBan(email);
		if (ban.isPresent()) {
			if (ban.get().reason().equals(reason) && ban.get().until().equals(until)) {
				ctx.getSource().sendFailure(Component.literal(
					email + " is already banned!"
				));
				return 0;
			} else {
				verifier.ban(email, until, reason);
				ctx.getSource().sendSuccess(
					() -> Component.literal(
						"Changed " + email + "'s ban to "
					).append(
						BannedEmail.until(until)
					).append(BannedEmail.reason(reason)),
					true
				);
				return 1;
			}
		} else {
			var plusOnes = verifier.getPlusOnes(email);
			var users = verifier.getByEmail(email);
			verifier.ban(email, until, reason);
			var players = ctx.getSource().getServer().getPlayerList();
			for (var user : users) {
				var player = players.getPlayer(user.playerUuid());
				if (player != null) {
					player.connection.disconnect(
						Component.translatable("disconnect.kth_auth.banned_email",
						BannedEmail.until(until), BannedEmail.reason(reason))
					);
				}
			}
			for (var user : plusOnes) {
				var player = players.getPlayer(user.playerUuid());
				if (player != null) {
					player.connection.disconnect(
						Component.translatable(
							"disconnect.kth_auth.banned_plus_one",
							BannedEmail.until(until), BannedEmail.reason(reason)
						)
					);
				}
			}
			ctx.getSource().sendSuccess(
				() -> Component.literal("Banned " + email + " ").append(
					BannedEmail.until(until)
				).append(BannedEmail.reason(reason)).append("!"),
				true
			);
			if (!plusOnes.isEmpty()) {
				ctx.getSource().sendSuccess(
					() -> Component.literal(
						"The following +1's will need to be re-verified by someone else to join: " +
							plusOnes.stream().map(
								plusOne -> name(ctx, plusOne.playerUuid())
							).collect(Collectors.joining(", "))
					),
					true
				);
			}
			return 1;
		}
	}

	private int unban(CommandContext<CommandSourceStack> ctx, String email) {
		if (verifier.isBannedEmail(email)) {
			this.verifier.unban(email);
			ctx.getSource().sendSuccess(
				() -> Component.literal(email + " is no longer banned."),
				true
			);
			return 1;
		} else {
			ctx.getSource().sendFailure(Component.literal(
				email + " is not banned!"
			));
			return 0;
		}
	}
}
