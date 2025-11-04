package nu.metacraft.core.util.helper;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.AnimatedGuiElementBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.stream.Stream;

public class SGUIHelper {

	public static AnimatedGuiElementBuilder createPlayerHeadIcon(
			Component name, MinecraftServer server, Stream<? extends Player> players
	) {
		return createGameProfileHeadIcon(name, server, players.map(Player::getGameProfile));
	}

	public static AnimatedGuiElementBuilder createPlayerHeadIcon(
			Component name, int interval, MinecraftServer server, Stream<? extends Player> players
	) {
		return createGameProfileHeadIcon(name, interval, server, players.map(Player::getGameProfile));
	}

	public static AnimatedGuiElementBuilder createGameProfileHeadIcon(
			Component name, MinecraftServer server, Stream<GameProfile> players
	) {
		return createGameProfileHeadIcon(name, 30, server, players);
	}

	public static AnimatedGuiElementBuilder createGameProfileHeadIcon(
			Component name, int interval, MinecraftServer server, Stream<GameProfile> players
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
			builder.setComponent(DataComponents.PROFILE, ResolvableProfile.createUnresolved(
					"MHF_Herobrine"
			));
			builder.setName(name);
			builder.saveItemStack();
		}
		return builder;
	}

}
