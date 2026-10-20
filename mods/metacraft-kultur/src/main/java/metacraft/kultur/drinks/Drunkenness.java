package metacraft.kultur.drinks;

import com.mojang.serialization.Codec;
import metacraft.kultur.Kultur;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player's alcohol, kept the way Brewery keeps it: saved with the player, so it survives logging
 * out and changing dimension (Fabric copies attachments then), and cleared by death (not copied to
 * the respawned player; Brewery only copies it on a live respawn, such as leaving the End).
 */
public final class Drunkenness {
	private static AttachmentType<Double> alcohol;

	private Drunkenness() {}

	static void register() {
		alcohol = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, "alcohol"),
				builder -> builder.persistent(Codec.DOUBLE));
	}

	public static double get(ServerPlayer player) {
		return player.getAttachedOrElse(alcohol, 0.0);
	}

	/** Sets the meter; zero or less removes it, so a sober player carries nothing. */
	public static void set(ServerPlayer player, double value) {
		if (value <= 0) player.removeAttached(alcohol);
		else player.setAttached(alcohol, value);
	}

	/** Adds a drink's alcohol, Brewery's way ({@link Alcohol#afterDrink}). */
	public static void drink(ServerPlayer player, double value) {
		set(player, Alcohol.afterDrink(get(player), value));
	}
}
