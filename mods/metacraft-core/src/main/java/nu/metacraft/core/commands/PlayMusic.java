package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.core.music.PlayerMusic;
import nu.metacraft.core.util.helper.MusicHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PlayMusic {

	private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(e -> e::toString);

	public static PlayerMusic parse(CompoundTag nbt, HolderLookup.Provider lookup) throws CommandSyntaxException {
		return PlayerMusic.EASY_CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), nbt).getOrThrow(INVALID::create);
	}

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
			literal("play-music").requires(
					Permissions.require("metacraft.play-music", 2)
			).then(
				argument("music", CompoundTagArgument.compoundTag()).executes(
						ctx -> playMusic(
							ctx, parse(
									CompoundTagArgument.getCompoundTag(ctx, "music"),
									ctx.getSource().registryAccess()
							),
							List.of(ctx.getSource().getPlayerOrException()),
							false
					)
				).then(
					argument("players", EntityArgument.players()).executes(
						ctx -> playMusic(
								ctx, parse(
										CompoundTagArgument.getCompoundTag(ctx, "music"),
										ctx.getSource().registryAccess()
								),
								EntityArgument.getPlayers(ctx, "players"),
								false
						)
					)
				)
			).then(
				literal("forgettable").then(
					argument("music", CompoundTagArgument.compoundTag()).executes(
							ctx -> playMusic(
									ctx, parse(
											CompoundTagArgument.getCompoundTag(ctx, "music"),
											ctx.getSource().registryAccess()
									),
									List.of(ctx.getSource().getPlayerOrException()),
									true
							)
					).then(
							argument("players", EntityArgument.players()).executes(
									ctx -> playMusic(
											ctx, parse(
													CompoundTagArgument.getCompoundTag(ctx, "music"),
													ctx.getSource().registryAccess()
											),
											EntityArgument.getPlayers(ctx, "players"),
											true
									)
							)
					)
				)
			)
		);
		dispatcher.register(
			literal("stop-music").requires(
					Permissions.require("metacraft.stop-music", 2)
			).executes(
					ctx -> stopMusic(
							ctx, Optional.empty(),
							List.of(ctx.getSource().getPlayerOrException()),
							false
					)
			).then(
				argument("players", EntityArgument.players()).executes(
						ctx -> stopMusic(
								ctx, Optional.empty(),
								EntityArgument.getPlayers(ctx, "players"),
								false
						)
				).then(
					argument("music", CompoundTagArgument.compoundTag()).executes(
							ctx -> stopMusic(
									ctx, Optional.of(parse(
											CompoundTagArgument.getCompoundTag(ctx, "music"),
											ctx.getSource().registryAccess()
									)),
									EntityArgument.getPlayers(ctx, "players"),
									false
							)
					)
				).then(
					literal("all").executes(
							ctx -> stopMusic(
									ctx, Optional.empty(),
									EntityArgument.getPlayers(ctx, "players"),
									true
							)
					)
				)
			)
		);
	}

	private static int playMusic(
			CommandContext<CommandSourceStack> ctx,
			PlayerMusic music, Collection<ServerPlayer> players, boolean skipQueue
	) {
		for (var p : players) {
			MusicHelper.playMusic(p, music, skipQueue);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Started music " + music), true);
		return players.size();
	}

	private static int stopMusic(
			CommandContext<CommandSourceStack> ctx,
			Optional<PlayerMusic> music, Collection<ServerPlayer> players, boolean all
	) {
		int count = 0;
		for (var p : players) {
			if (all) {
				MusicHelper.clearAllMusic(p);
			} else {
				music.ifPresentOrElse(m -> MusicHelper.stopMusic(p, m), () -> MusicHelper.stopMusic(p));
			}
			count++;
		}
		String msg = "Stopped music" + (music.map(musicEntry -> " " + musicEntry).orElse(""));
		ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
		return count;
	}

}
