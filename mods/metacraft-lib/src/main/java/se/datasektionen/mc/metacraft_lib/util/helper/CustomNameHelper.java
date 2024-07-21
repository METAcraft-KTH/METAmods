package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;

import java.util.Optional;

public class CustomNameHelper {

	/**
	 * Returns the player's name.
	 * @param player The player to get the custom name for.
	 * @return An optional containing the player's custom name, or empty if the player does not have a custom name.
	 */
	public static Optional<String> getCustomName(ServerPlayerEntity player) {
		return Optional.ofNullable(((ServerPlayerEntityExtensions) player).METAcraft_Moderation$getCustomName());
	}

	/**
	 * Returns the player's name without formatting codes.
	 * @param player The player to get the custom name for.
	 * @return An optional containing the player's custom name without formatting, or empty if the player does not have a custom name.
	 */
	public static Optional<String> getCustomNameWithoutFormatting(ServerPlayerEntity player) {
		return getCustomName(player).map(CustomNameHelper::stripOutColourCodes);
	}

	/**
	 * Sets the players custom name.
	 * @param player The player to set the name of.
	 * @param name The name to set. Must be at most 16 characters!
	 * @throws IllegalStateException if name is more than 16 characters.
	 */
	public static void setCustomName(ServerPlayerEntity player, @Nullable String name) {
		if (name != null && name.length() > 16) {
			throw new IllegalArgumentException("Name must be at most 16 characters!");
		}
		((ServerPlayerEntityExtensions) player).METAcraft_Moderation$setCustomName(name);
	}

	/**
	 * Removes a player's custom name.
	 * @param player The player to remove the custom name of.
	 */
	public static void removeCustomName(ServerPlayerEntity player) {
		setCustomName(player, null);
	}

	private static String stripOutColourCodes(String name) {
		return name.replaceAll("§.", "");
	}

}
