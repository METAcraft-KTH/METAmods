package nu.metacraft.lib.util.helper;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.METAcraftData;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class GameProfileHelper {

	/**
	 * Fetches the game profile for the given UUID.
	 * This provided game profile will contain skin data and such if the given player is online.
	 * However, if the player is offline, the game profile will be fetched from the user cache instead.
	 * @param uuid THe UUID of the player.
	 * @param server The server to work with.
	 * @return A game profile, or empty if not found.
	 */
	public static Optional<GameProfile> getForUUID(UUID uuid, MinecraftServer server) {
		var player = server.getPlayerManager().getPlayer(uuid);
		if (player != null) {
			return Optional.of(player.getGameProfile());
		} else {
			return server.getApiServices().nameToIdCache().getByUuid(uuid).map(
					config -> new GameProfile(config.id(), config.name())
			);
		}
	}

	/**
	 * Returns the name for the given player, taking their custom name into account.
	 * @param profile The game profile of the player.
	 * @param server The server in question.
	 * @return The name to display.
	 */
	public static String getNameFromProfile(GameProfile profile, MinecraftServer server) {
		return METAcraftData.getInstance(server).getName(profile);
	}

	public static StaticProfileComponentBuilder staticComponentBuilder() {
		return new StaticProfileComponentBuilder();
	}


	public static final class StaticProfileComponentBuilder {

		@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
		private Optional<String> name = Optional.empty();

		@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
		private Optional<UUID> id = Optional.empty();

		private PropertyMap propertyMap = PropertyMap.EMPTY;

		private SkinTextures.SkinOverride skinOverride = SkinTextures.SkinOverride.EMPTY;


		private StaticProfileComponentBuilder() {}

		/**
		 * Set the name. Note that this will override the item name component.
		 * @param name The name to display before 's Head as the item name.
		 * @return The builder.
		 */
		public StaticProfileComponentBuilder withName(String name) {
			this.name = Optional.of(name);
			return this;
		}

		public StaticProfileComponentBuilder withID(UUID id) {
			this.id = Optional.of(id);
			return this;
		}

		/**
		 * Set the properties of the game profile.
		 * Primarily used to set a serverside skin (which does not require a resource pack).
		 *
		 * @param propertyMap The property map to insert.
		 * @return The builder.
		 */
		public StaticProfileComponentBuilder withProperties(PropertyMap propertyMap) {
			this.propertyMap = propertyMap;
			return this;
		}

		/**
		 * Helper function to set a serverside skin.
		 * Works with byte64 encoded skins from <a href="https://mineskin.org">https://mineskin.org</a>.
		 * A signature is necessary if you want to use it on metacraft:player, but not for player heads.
		 * @param value The byte64 value of the skin.
		 * @return The builder.
		 */
		public StaticProfileComponentBuilder withServersideSkin(String value, @Nullable String signature) {
			return withProperties(
					new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value, signature)))
			);
		}

		/**
		 * Helper function to set a serverside skin.
		 * Works with byte64 encoded skins from <a href="https://mineskin.org">https://mineskin.org</a>.
		 * If you wish to use the skin with a metacraft:player, you should use {@link StaticProfileComponentBuilder#withServersideSkin(String, String)} instead.
		 * @param value The byte64 value of the skin.
		 * @return The builder.
		 */
		public StaticProfileComponentBuilder withServersideSkin(String value) {
			return withServersideSkin(value, null);
		}

		/**
		 * Use a custom skin, cape or elytra texture from a resource pack.
		 * @param skinOverride The skin override parameter.
		 * @return The builder.
		 */
		public StaticProfileComponentBuilder withSkinOverride(SkinTextures.SkinOverride skinOverride) {
			this.skinOverride = skinOverride;
			return this;
		}

		/**
		 * Builds the final profile component.
		 * @return The profile component.
		 */
		public ProfileComponent build() {
			return new ProfileComponent.Static(
					Either.right(new ProfileComponent.Data(name, id, propertyMap)),
					skinOverride
			);
		}
	}
}
