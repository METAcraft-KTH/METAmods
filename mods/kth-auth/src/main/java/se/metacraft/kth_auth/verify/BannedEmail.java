package se.metacraft.kth_auth.verify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public record BannedEmail(
	String email,
	Optional<Instant> until,
	Optional<String> reason
) {

	public static final Codec<BannedEmail> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Codec.STRING.fieldOf("email").forGetter(BannedEmail::email),
			ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("until").forGetter(BannedEmail::until),
			Codec.STRING.optionalFieldOf("reason").forGetter(BannedEmail::reason)
		).apply(instance, BannedEmail::new)
	);

	public static Component until(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Instant> until) {
		return until.map(
			u -> Component.translatable(
				"message.kth_auth.banned_until", u.atZone(ZoneId.of("Europe/Stockholm")).format(
					DateTimeFormatter.ofPattern("HH:mm v d MMM uuuu")
				)
			)
		).orElse(Component.translatable("message.kth_auth.banned_permanently"));
	}

	public static Component reason(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> reason) {
		return reason.map(r -> Component.translatable("message.kth_auth.banned_reason", r)).orElse(Component.literal(""));
	}

	public boolean stillBanned() {
		return until.map(until -> until.isAfter(Instant.now())).orElse(true);
	}

}
