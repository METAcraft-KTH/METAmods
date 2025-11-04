package nu.metacraft.lib.util.helper;

import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

public class CustomNameHelper {

	/**
	 * Returns the player's name.
	 * @param player The player to get the custom name for.
	 * @return An optional containing the player's custom name, or empty if the player does not have a custom name.
	 */
	public static Optional<String> getCustomName(ServerPlayer player) {
		return Optional.ofNullable(((ServerPlayerEntityExtensions) player).metacraft_lib$getCustomName());
	}

	/**
	 * Returns the player's name without formatting codes.
	 * @param player The player to get the custom name for.
	 * @return An optional containing the player's custom name without formatting, or empty if the player does not have a custom name.
	 */
	public static Optional<String> getCustomNameWithoutFormatting(ServerPlayer player) {
		return getCustomName(player).map(CustomNameHelper::stripOutColourCodes);
	}

	/**
	 * Sets the players custom name.
	 * @param player The player to set the name of.
	 * @param name The name to set. Must be at most 16 characters!
	 * @param showInGUI If true, the name cache will be updated, making sure the custom name is visible in guis. Set to true when you change your name because of personal preference, and false when you intend to role-play as another character.
	 * @throws IllegalStateException if name is more than 16 characters.
	 */
	public static void setCustomName(ServerPlayer player, @Nullable String name, boolean showInGUI) {
		if (name != null && name.length() > 16) {
			throw new IllegalArgumentException("Name must be at most 16 characters!");
		}
		((ServerPlayerEntityExtensions) player).metacraft_lib$setCustomName(name, showInGUI);
	}

	/**
	 * Removes a player's custom name.
	 * @param player The player to remove the custom name of.
	 */
	public static void removeCustomName(ServerPlayer player) {
		setCustomName(player, null, true);
	}

	private static String stripOutColourCodes(String name) {
		return name.replaceAll("§.", "");
	}

}
