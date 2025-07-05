package nu.metacraft.resource_packs;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ResourcePackCommand {

	public static final SuggestionProvider<ServerCommandSource> PACKS = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				ResourcePackConfig.getConfig().getResourcePacks().stream().filter(
						e -> !e.getValue().isGlobal()
				).map(e -> e.getKey().toString()),
				builder
		);
	};

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher, RegistryWrapper.WrapperLookup lookup
	) {
		dispatcher.register(
				literal("metacraft-resource-pack").requires(
						Permissions.require("metacraft.resource-pack", 2)
				).then(
					literal("send").then(
						argument("id", UuidArgumentType.uuid()).suggests(PACKS).executes(
							ctx -> sendResourcePack(
									ctx, UuidArgumentType.getUuid(ctx, "id"),
									List.of(ctx.getSource().getPlayerOrThrow()), true
							)
						).then(
								argument("players", EntityArgumentType.players()).executes(
										ctx -> sendResourcePack(
												ctx, UuidArgumentType.getUuid(ctx, "id"),
												EntityArgumentType.getPlayers(ctx, "players"), true
										)
								)
						)
					)
				).then(
					literal("remove").then(
						argument("id", UuidArgumentType.uuid()).suggests(PACKS).executes(
								ctx -> sendResourcePack(
										ctx, UuidArgumentType.getUuid(ctx, "id"),
										List.of(ctx.getSource().getPlayerOrThrow()), false
								)
						).then(
								argument("players", EntityArgumentType.players()).executes(
										ctx -> sendResourcePack(
												ctx, UuidArgumentType.getUuid(ctx, "id"),
												EntityArgumentType.getPlayers(ctx, "players"), false
										)
								)
						)
					)
				).then(
					literal("reload").executes(ctx -> {
						ResourcePackConfig.reload();
						return 1;
					})
				)
		);
	}

	private static int sendResourcePack(
			CommandContext<ServerCommandSource> ctx, UUID id, Collection<ServerPlayerEntity> players, boolean enable
	) {
		for (var player : players) {
			if (ResourcePackHelper.hasResourcePack(player, id) != enable) {
				if (enable) {
					ResourcePackHelper.enableResourcePack(player, id);
				} else {
					ResourcePackHelper.disableResourcePack(player, id);
				}
			}
		}
		ctx.getSource().sendFeedback(
				() -> Text.literal("Resource pack update sent"), true
		);
		return players.size();
	}

}
