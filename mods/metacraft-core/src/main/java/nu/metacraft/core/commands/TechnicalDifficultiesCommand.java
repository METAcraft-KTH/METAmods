package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class TechnicalDifficultiesCommand {
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
		dispatcher.register(
			CommandManager.literal("td")
				.requires(Permissions.require("metacraft.technical_difficulties", 2))
				.then(
					CommandManager.literal("title")
						.executes(ctx -> {
							var timesPacket = new TitleFadeS2CPacket(20, 72000, 20);
							var subtitlePacket = new SubtitleS2CPacket(Text.literal("Please stand by"));
							var titlePacket = new TitleS2CPacket(Text.literal("Technical difficulties"));
							for (var player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
								player.networkHandler.sendPacket(timesPacket);
								player.networkHandler.sendPacket(subtitlePacket);
								player.networkHandler.sendPacket(titlePacket);
							}
							return 1;
						})
				)
				.then(
					CommandManager.literal("reset")
						.executes(ctx -> {
							var clearPacket = new ClearTitleS2CPacket(true);
							for (var player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
								player.networkHandler.sendPacket(clearPacket);
							}
							return 1;
						})
				)
		);
	}

}
