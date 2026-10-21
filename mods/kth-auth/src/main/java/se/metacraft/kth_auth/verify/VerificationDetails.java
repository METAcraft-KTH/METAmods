package se.metacraft.kth_auth.verify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Details about a player's verification.
 * <p>
 * One of the fields {@link #email} or {@link #plusOneBy} will be set and the other
 * will be null depending on if the player was invited as a +1 or verified by email.
 *
 * @param playerUuid The UUID of the player.
 * @param email The KTH email of the user. Will be null if the player was invited
 *              as a +1 player.
 * @param verifiedAt The timestamp verification occurred at.
 * @param plusOneBy The UUID of the player that invited this player as a +1. Will
 *                  be null for players verified with their KTH email.
 * @param acceptedGdprAt The timestamp the player accepted the GDPR statement. Will be null
 *                       for players who have not yet accepted it.
 * @param lastLoginAt The timestamp of the player's last login. Will be null for players
 *                    who have never logged in since this was introduced.
 */
public record VerificationDetails(
	UUID playerUuid,
	Optional<String> email,
	long verifiedAt,
	Optional<UUID> plusOneBy,
	Optional<Long> acceptedGdprAt,
	Optional<Long> lastLoginAt
) {
	public static final MapCodec<VerificationDetails> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		UUIDUtil.STRING_CODEC.fieldOf("playerUuid").forGetter(VerificationDetails::playerUuid),
		Codec.STRING.optionalFieldOf("email").forGetter(VerificationDetails::email),
		Codec.LONG.fieldOf("verifiedAt").forGetter(VerificationDetails::verifiedAt),
		UUIDUtil.STRING_CODEC.optionalFieldOf("plusOneBy").forGetter(VerificationDetails::plusOneBy),
		Codec.LONG.optionalFieldOf("acceptedGdprAt").forGetter(VerificationDetails::acceptedGdprAt),
		Codec.LONG.optionalFieldOf("lastLoginAt").forGetter(VerificationDetails::lastLoginAt)
	).apply(instance, VerificationDetails::new));

	public VerificationDetails {
		if (email.isEmpty() && plusOneBy.isEmpty()) {
			throw new IllegalArgumentException("Either email or plusOneBy must be set.");
		}
		if (email.isPresent() && plusOneBy.isPresent()) {
			throw new IllegalArgumentException("email and plusOneBy cannot both be set");
		}
	}

	/**
	 * Get whether this player was invited as a +1 or not.
	 *
	 * @return Whether +1.
	 */
	public boolean isPlusOne() {
		return this.plusOneBy.isPresent();
	}

	/**
	 * Get whether this player has accepted the GDPR Statement.
	 * <p>
	 * +1:s may be verified without having accepted the GDPR Statement, meaning they
	 * need to accept it to proceed.
	 *
	 * @return Whether accepted.
	 */
	public boolean hasAcceptedGdpr() {
		return this.acceptedGdprAt.isPresent();
	}
}
