package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

public class TechnicalDifficultiesCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
		dispatcher.register(
			Commands.literal("td")
				.requires(Permissions.require("metacraft.technical_difficulties", 2))
				.then(
					Commands.literal("title")
						.executes(ctx -> {
							var timesPacket = new ClientboundSetTitlesAnimationPacket(20, 72000, 20);
							var subtitlePacket = new ClientboundSetSubtitleTextPacket(Component.literal("Please stand by"));
							var titlePacket = new ClientboundSetTitleTextPacket(Component.literal("Technical difficulties"));
							for (var player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
								player.connection.send(timesPacket);
								player.connection.send(subtitlePacket);
								player.connection.send(titlePacket);
							}
							return 1;
						})
				)
				.then(
					Commands.literal("reset")
						.executes(ctx -> {
							var clearPacket = new ClientboundClearTitlesPacket(true);
							for (var player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
								player.connection.send(clearPacket);
							}
							return 1;
						})
				)
		);
	}

}
