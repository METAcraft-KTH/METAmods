package se.metacraft.kth_auth.verify;

import java.util.UUID;

/**
 * A pending verification.
 *
 * @param verificationCode The verification code.
 * @param expiresAt The epoch timestamp of when this verification expires.
 * @param playerUuid The UUID of the player to verify.
 * @param playerName The name of the player to verify.
 * @param email The email the verification code was sent to.
 */
public record PendingVerification(
	String verificationCode,
	long expiresAt,
	UUID playerUuid,
	String playerName,
	String email) {

	public boolean hasExpired() {
		return System.currentTimeMillis() > this.expiresAt;
	}
}
