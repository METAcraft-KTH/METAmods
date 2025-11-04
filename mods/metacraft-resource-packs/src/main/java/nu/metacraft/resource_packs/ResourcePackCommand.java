package nu.metacraft.resource_packs;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ResourcePackCommand {

	public static final SuggestionProvider<CommandSourceStack> PACKS = (ctx, builder) -> {
		return SharedSuggestionProvider.suggest(
				ResourcePackConfig.getConfig().getResourcePacks().stream().filter(
						e -> !e.getValue().isGlobal()
				).map(e -> e.getKey().toString()),
				builder
		);
	};

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher, HolderLookup.Provider lookup
	) {
		dispatcher.register(
				literal("metacraft-resource-pack").requires(
						Permissions.require("metacraft.resource-pack", 2)
				).then(
					literal("send").then(
						argument("id", UuidArgument.uuid()).suggests(PACKS).executes(
							ctx -> sendResourcePack(
									ctx, UuidArgument.getUuid(ctx, "id"),
									List.of(ctx.getSource().getPlayerOrException()), true
							)
						).then(
								argument("players", EntityArgument.players()).executes(
										ctx -> sendResourcePack(
												ctx, UuidArgument.getUuid(ctx, "id"),
												EntityArgument.getPlayers(ctx, "players"), true
										)
								)
						)
					)
				).then(
					literal("remove").then(
						argument("id", UuidArgument.uuid()).suggests(PACKS).executes(
								ctx -> sendResourcePack(
										ctx, UuidArgument.getUuid(ctx, "id"),
										List.of(ctx.getSource().getPlayerOrException()), false
								)
						).then(
								argument("players", EntityArgument.players()).executes(
										ctx -> sendResourcePack(
												ctx, UuidArgument.getUuid(ctx, "id"),
												EntityArgument.getPlayers(ctx, "players"), false
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
			CommandContext<CommandSourceStack> ctx, UUID id, Collection<ServerPlayer> players, boolean enable
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
		ctx.getSource().sendSuccess(
				() -> Component.literal("Resource pack update sent"), true
		);
		return players.size();
	}

}
