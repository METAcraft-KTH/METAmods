package se.datasektionen.mc.metacraft_core.util.helper;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import eu.pb4.sgui.api.elements.AnimatedGuiElementBuilder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.stream.Stream;

public class SGUIHelper {

	public static AnimatedGuiElementBuilder createPlayerHeadIcon(
			Text name, MinecraftServer server, Stream<? extends PlayerEntity> players
	) {
		return createGameProfileHeadIcon(name, server, players.map(PlayerEntity::getGameProfile));
	}

	public static AnimatedGuiElementBuilder createPlayerHeadIcon(
			Text name, int interval, MinecraftServer server, Stream<? extends PlayerEntity> players
	) {
		return createGameProfileHeadIcon(name, interval, server, players.map(PlayerEntity::getGameProfile));
	}

	public static AnimatedGuiElementBuilder createGameProfileHeadIcon(
			Text name, MinecraftServer server, Stream<GameProfile> players
	) {
		return createGameProfileHeadIcon(name, 30, server, players);
	}

	public static AnimatedGuiElementBuilder createGameProfileHeadIcon(
			Text name, int interval, MinecraftServer server, Stream<GameProfile> players
	) {
		AnimatedGuiElementBuilder builder = new AnimatedGuiElementBuilder();
		boolean foundPlayer = false;
		builder.setInterval(interval);
		for (var player : (Iterable<GameProfile>) players::iterator) {
			builder.setItem(Items.PLAYER_HEAD);
			builder.setName(name);
			builder.setSkullOwner(player, server);
			builder.saveItemStack();
			foundPlayer = true;
		}
		if (!foundPlayer) {
			builder.setItem(Items.PLAYER_HEAD);
			builder.setComponent(DataComponentTypes.PROFILE, new ProfileComponent(
					Optional.of("MHF_Herobrine"), Optional.empty(), new PropertyMap()
			));
			builder.setName(name);
			builder.saveItemStack();
		}
		return builder;
	}

}
