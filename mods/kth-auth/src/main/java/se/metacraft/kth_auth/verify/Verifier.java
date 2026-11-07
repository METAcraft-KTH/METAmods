package se.metacraft.kth_auth.verify;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import se.metacraft.kth_auth.KTHAuth;
import se.metacraft.kth_auth.KTHAuthTask;
import se.metacraft.kth_auth.WebhookSender;
import se.metacraft.lib.util.helper.JsonHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An object that keeps track of verified players and handles verification of
 * players.
 */
public class Verifier {
	/**
	 * The byte-length of verification codes before being hex encoded.
	 */
	private static final int TOKEN_BYTE_LENGTH = 8;
	/**
	 * The Time To Live (TTL) of verification tokens.
	 */
	private static final long TOKEN_TTL = 600000; // 10 minutes
	/**
	 * The maximum number of accounts that can be verified with the same email.
	 */
	public static final int MAX_ACCOUNTS_PER_EMAIL = 3;
	private static final Codec<List<VerificationDetails>> DATA_CODEC = VerificationDetails.CODEC.codec().listOf();
	private static final Codec<List<BannedEmail>> BANNED_DATA_CODEC = BannedEmail.CODEC.listOf();

	private final Path dataDirectory;
	private final WebhookSender webhookSender;
	private final SecureRandom random = new SecureRandom();
	private final Map<UUID, KTHAuthTask> inProgressVerifications = new HashMap<>();
	private final Map<UUID, VerificationDetails> verifiedPlayers = new HashMap<>();
	private final Map<String, BannedEmail> bannedEmails = new HashMap<>();
	private boolean dirty = false;
	private long disabledUntil = 0;

	public Verifier(Path dataDirectory, WebhookSender webhookSender) {
		this.dataDirectory = dataDirectory;
		this.webhookSender = webhookSender;
		try {
			this.load();
		} catch (IOException e) {
			KTHAuth.LOGGER.error("Failed to load verified players", e);
			throw new RuntimeException(e);
		}
	}

	private Path getDataFile() {
		return this.dataDirectory.resolve("verified_players.json");
	}

	private Path getBannedFile() {
		return this.dataDirectory.resolve("banned_players.json");
	}

	/**
	 * Load verified players from disk.
	 *
	 * @throws IOException If an I/O error occurs.
	 */
	private void load() throws IOException {
		var banned = getBannedFile();
		if (Files.exists(banned)) {
			JsonHelper.load(banned, BANNED_DATA_CODEC).ifPresent(bannedEmails -> {
				for (var email : bannedEmails) {
					this.bannedEmails.put(email.email(), email);
				}
			});
		}
		Path path = this.getDataFile();
		if (Files.notExists(path)) {
			return;
		}

		JsonElement json;
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			json = JsonParser.parseReader(reader);
		} catch (JsonParseException e) {
			throw new IOException("Failed to parse " + path, e);
		}
		List<VerificationDetails> verifiedPlayers = DATA_CODEC.parse(JsonOps.INSTANCE, json)
			.getOrThrow(error -> new IOException("Failed to decode " + path + ": " + error));
		for (VerificationDetails details : verifiedPlayers) {
			this.verifiedPlayers.put(details.playerUuid(), details);
		}
	}

	/**
	 * Check whether the verified players have been modified since last save.
	 *
	 * @return Whether dirty.
	 */
	public boolean isDirty() {
		return this.dirty;
	}

	/**
	 * Save verified players to disk.
	 *
	 * @throws IOException If an I/O error occurs.
	 */
	public void save() throws IOException {
		Path path = this.getDataFile();
		Files.createDirectories(path.getParent());

		JsonElement json = DATA_CODEC.encodeStart(JsonOps.INSTANCE, List.copyOf(this.verifiedPlayers.values()))
			.getOrThrow(error -> new IOException("Failed to encode verified players: " + error));
		try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(path))) {
			writer.setIndent("\t");
			Streams.write(json, writer);
		}

		JsonHelper.save(
			getBannedFile(), BANNED_DATA_CODEC, List.copyOf(this.bannedEmails.values())
		);

		this.dirty = false;
	}

	/**
	 * Generate a verification code.
	 *
	 * @param playerUuid The UUID of the player to verify.
	 * @param playerName The name of the player to verify.
	 * @param email The email of the player.
	 * @return The {@link PendingVerification} object.
	 */
	public PendingVerification createCode(UUID playerUuid, String playerName, String email) {
		String verificationCode = this.generateCode();
		long expiresAt = System.currentTimeMillis() + TOKEN_TTL;
		return new PendingVerification(verificationCode, expiresAt, playerUuid, playerName, email);
	}

	private String generateCode() {
		byte[] bytes = new byte[Verifier.TOKEN_BYTE_LENGTH];
		this.random.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	public void addInProgressVerification(UUID uuid, KTHAuthTask task) {
		this.inProgressVerifications.put(uuid, task);
	}

	public Optional<KTHAuthTask> getInProgressVerification(UUID uuid) {
		return Optional.ofNullable(this.inProgressVerifications.get(uuid));
	}

	public void removeInProgressVerification(KTHAuthTask kthAuthTask) {
		this.inProgressVerifications.remove(kthAuthTask.getUUID());
	}

	private void verify(VerificationDetails details) {
		this.verifiedPlayers.put(details.playerUuid(), details);
		this.dirty = true;
	}

	/**
	 * Verify a player by email address.
	 * <p>
	 * The player will also be assumed to have agreed to the GDPR Statement.
	 *
	 * @param pending The pending verification object.
	 */
	public void verifyByEmail(PendingVerification pending) {
		this.inProgressVerifications.remove(pending.playerUuid());
		long now = System.currentTimeMillis();
		VerificationDetails details = new VerificationDetails(
			pending.playerUuid(), Optional.of(pending.email()), now, Optional.empty(), Optional.of(now), Optional.of(now)
		);
		this.verify(details);

		List<VerificationDetails> emailAccounts = this.verifiedPlayers.values()
			.stream()
			.filter(d -> pending.email().equals(d.email().orElse(null)))
			.toList();

		if (emailAccounts.size() > 1) {
			String msg = """
				`%s` verified using email `%s`.
				The same email has been used %d times.
				The same email is now being used for the following accounts: %s
				""".formatted(
					pending.playerName(),
					pending.email(),
					emailAccounts.size(),
					emailAccounts.stream()
						.map(d -> d.playerUuid().toString())
						.collect(Collectors.joining(", "))
				);

			this.webhookSender.sendMessage(msg);
		}
	}

	/**
	 * Verify a player as a +1.
	 *
	 * @param playerUuid The UUID of the player to verify.
	 * @param playerName The name of the player to verify.
	 * @param inviterUuid The UUID of the player that invited the +1.
	 * @param inviterName The name of the player that invited the +1.
	 */
	public void verifyPlusOne(UUID playerUuid, String playerName, UUID inviterUuid, String inviterName) {
		long now = System.currentTimeMillis();
		VerificationDetails details = new VerificationDetails(
			playerUuid, Optional.empty(), now, Optional.of(inviterUuid), Optional.empty(), Optional.of(now)
		);
		this.verify(details);
		KTHAuthTask task = this.inProgressVerifications.get(playerUuid);
		if (task != null) {
			task.sendAcceptGdprDialog();
		}

		// Get all the +1:s that this inviter has invited.
		List<String> allPlusOnes = this.getPlusOnes(inviterUuid)
			.stream()
			.map(d -> d.playerUuid().toString())
			.toList();

		String msg = """
			`%s` has verified `%s` as a +1.
			
			`%s` has now verified a total of `%d` plus one(s): %s
			""".formatted(
				inviterName,
				playerName,
				inviterName,
				allPlusOnes.size(),
				String.join(", ", allPlusOnes)
			);

		this.webhookSender.sendMessage(msg);
	}

	/**
	 * Temporarily disable verification until the specified until. Players currently
	 * in the Limbo are let through.
	 *
	 * @param disabledUntil The until in milliseconds when verification is enabled again.
	 */
	public void disableUntil(long disabledUntil) {
		this.disabledUntil = disabledUntil;
		for (KTHAuthTask task : List.copyOf(this.inProgressVerifications.values())) {
			try {
				task.complete();
			} catch (IllegalStateException e) {
				// Perhaps this task was already completed or interrupted. Remove it.
				this.removeInProgressVerification(task);
			}
		}
		if (this.isDisabled()) {
			KTHAuth.LOGGER.info("KTH Auth disabled until {} ({} ms from now)", disabledUntil, disabledUntil - System.currentTimeMillis());
		} else {
			KTHAuth.LOGGER.info("KTH Auth is now enabled again.");
		}
	}

	/**
	 * Check whether verification is temporarily disabled.
	 *
	 * @return Whether disabled.
	 */
	public boolean isDisabled() {
		return System.currentTimeMillis() < this.disabledUntil;
	}

	/**
	 * Check whether the player with the specified UUID is verified trough email. In
	 * other words, returns {@code false} if the player is not verified or was invited
	 * as a +1.
	 *
	 * @param playerUuid The player UUID.
	 * @return Whether verified by email.
	 */
	public boolean isVerifiedByEmail(UUID playerUuid) {
		VerificationDetails details = this.verifiedPlayers.get(playerUuid);
		return details != null && details.email().isPresent();
	}

	/**
	 * Get verification details about a player, if the player is verified.
	 *
	 * @param playerUuid The UUID of the player.
	 * @return The verification details, or null.
	 */
	public Optional<VerificationDetails> getVerificationDetails(UUID playerUuid) {
		return Optional.ofNullable(this.verifiedPlayers.get(playerUuid));
	}

	/**
	 * Remove verification for a player UUID, if verified.
	 *
	 * @param playerUuid The UUID of the player.
	 */
	public void unVerify(UUID playerUuid) {
		this.verifiedPlayers.remove(playerUuid);
		this.dirty = true;
	}

	/**
	 * Get an unmodifiable collection of players that are not verified but currently
	 * connected to the server. In other words, the players that are currently in the
	 * authentication task in the configuration phase.
	 *
	 * @return The collection.
	 */
	public Collection<KTHAuthTask> getUnverifiedPlayers() {
		return Collections.unmodifiableCollection(this.inProgressVerifications.values());
	}

	private Stream<VerificationDetails> getPlusOnesStream(UUID inviterUuid) {
		return this.verifiedPlayers.values()
			.stream()
			.filter(d -> inviterUuid.equals(d.plusOneBy().orElse(null)));
	}

	/**
	 * Get a collection of all player that the inviter has invited as +1.
	 *
	 * @param inviterUuid The UUID of the inviter.
	 * @return The collection.
	 */
	public Collection<VerificationDetails> getPlusOnes(UUID inviterUuid) {
		return getPlusOnesStream(inviterUuid).toList();
	}

	private Stream<VerificationDetails> getPlusOnesRecursively(UUID inviterUuid, Set<UUID> alreadyFound) {
		var plusOnes = getPlusOnesStream(inviterUuid).map(VerificationDetails::playerUuid).filter(
			id -> !alreadyFound.contains(id)
		).collect(Collectors.toSet());
		return plusOnes.stream().flatMap(
			e -> Stream.concat(getPlusOnesStream(inviterUuid), getPlusOnesRecursively(e, Sets.union(alreadyFound, plusOnes)))
		);
	}

	public Collection<VerificationDetails> getPlusOnes(String email) {
		return getByEmail(email).stream().flatMap(
			details -> getPlusOnesRecursively(details.playerUuid(), Set.of())
		).toList();
	}

	/**
	 * Get a collection of all players that have been verified by the specified email.
	 *
	 * @param email The email.
	 * @return The collection.
	 */
	public Collection<VerificationDetails> getByEmail(@NotNull String email) {
		return this.verifiedPlayers.values()
			.stream()
			.filter(d -> email.equals(d.email().orElse(null)))
			.toList();
	}

	/**
	 * Get an unmodifiable collection of verified players.
	 *
	 * @return The list of verification details.
	 */
	public Collection<VerificationDetails> getAllVerifiedPlayers() {
		return Collections.unmodifiableCollection(this.verifiedPlayers.values());
	}

	public Optional<BannedEmail> getBan(String email) {
		return Optional.ofNullable(bannedEmails.get(email));
	}

	public boolean isBannedEmail(String email) {
		return bannedEmails.containsKey(email);
	}

	public Collection<String> getBannedEmails() {
		return Collections.unmodifiableCollection(bannedEmails.keySet());
	}

	/**
	 * Mark a verified player as having accepted the GDPR statement.
	 *
	 * @param details The verification details of the player.
	 */
	public void acceptGdpr(VerificationDetails details) {
		if (details.hasAcceptedGdpr()) {
			throw new IllegalArgumentException("Already agreed");
		}
		long now = System.currentTimeMillis();
		VerificationDetails newDetails = new VerificationDetails(
			details.playerUuid(), details.email(), details.verifiedAt(), details.plusOneBy(), Optional.of(now), Optional.of(now)
		);
		this.verifiedPlayers.put(details.playerUuid(), newDetails);
		this.dirty = true;
	}

	public String getVerifyingEmail(UUID user) {
		return getVerifyingEmail(user, HashTreePSet.empty());
	}

	private String getVerifyingEmail(UUID user, PSet<UUID> checked) {
		if (checked.contains(user)) return "missingno";
		var player = verifiedPlayers.get(user);
		if (player == null) return "missingno";
		if (player.email().isPresent()) return player.email().get();
		if (player.plusOneBy().isPresent()) {
			return getVerifyingEmail(player.plusOneBy().get(), checked.plus(user));
		}
		throw new IllegalStateException("Huh, how can we have an account with without both email and plus one data?");
	}

	public boolean isBanned(String email, Consumer<Component> disconnect) {
		var ban = getBan(email);
		if (ban.isPresent()) {
			if (ban.get().stillBanned()) {
				disconnect.accept(
					Component.translatable(
						"disconnect.kth_auth.banned_email",
						BannedEmail.until(ban.get().until()),
						BannedEmail.reason(ban.get().reason())
					)
				);
				return true;
			} else {
				this.unban(email);
			}
		}
		return false;
	}

	/**
	 * Update the last login timestamp for a verified player.
	 *
	 * @param details The verification details of the player.
	 */
	public void touch(VerificationDetails details) {
		long now = System.currentTimeMillis();
		VerificationDetails newDetails = new VerificationDetails(
			details.playerUuid(), details.email(), details.verifiedAt(), details.plusOneBy(), details.acceptedGdprAt(), Optional.of(now)
		);
		this.verifiedPlayers.put(details.playerUuid(), newDetails);
		this.dirty = true;
	}

	public void ban(
		String email, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Instant> until,
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> reason
	) {
		bannedEmails.put(
			email, new BannedEmail(email, until, reason)
		);
		this.dirty = true;
	}

	public void unban(String email) {
		bannedEmails.remove(email);
		this.dirty = true;
	}
}
