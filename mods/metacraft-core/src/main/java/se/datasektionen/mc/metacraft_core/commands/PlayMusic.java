package se.datasektionen.mc.metacraft_core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;
import se.datasektionen.mc.metacraft_core.util.helper.MusicHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PlayMusic {

	private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(e -> e::toString);

	public static MusicEntry parse(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) throws CommandSyntaxException {
		return MusicEntry.CODEC.parse(lookup.getOps(NbtOps.INSTANCE), nbt).getOrThrow(INVALID::create);
	}

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
			literal("play-music").requires(
					Permissions.require("metacraft.play-music", 2)
			).then(
				argument("music", NbtCompoundArgumentType.nbtCompound()).executes(
						ctx -> playMusic(
							ctx, parse(
									NbtCompoundArgumentType.getNbtCompound(ctx, "music"),
									ctx.getSource().getRegistryManager()
							),
							List.of(ctx.getSource().getPlayerOrThrow()),
							false
					)
				).then(
					argument("players", EntityArgumentType.players()).executes(
						ctx -> playMusic(
								ctx, parse(
										NbtCompoundArgumentType.getNbtCompound(ctx, "music"),
										ctx.getSource().getRegistryManager()
								),
								EntityArgumentType.getPlayers(ctx, "players"),
								false
						)
					)
				)
			).then(
				literal("forgettable").then(
					argument("music", NbtCompoundArgumentType.nbtCompound()).executes(
							ctx -> playMusic(
									ctx, parse(
											NbtCompoundArgumentType.getNbtCompound(ctx, "music"),
											ctx.getSource().getRegistryManager()
									),
									List.of(ctx.getSource().getPlayerOrThrow()),
									true
							)
					).then(
							argument("players", EntityArgumentType.players()).executes(
									ctx -> playMusic(
											ctx, parse(
													NbtCompoundArgumentType.getNbtCompound(ctx, "music"),
													ctx.getSource().getRegistryManager()
											),
											EntityArgumentType.getPlayers(ctx, "players"),
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
							List.of(ctx.getSource().getPlayerOrThrow()),
							false
					)
			).then(
				argument("players", EntityArgumentType.players()).executes(
						ctx -> stopMusic(
								ctx, Optional.empty(),
								EntityArgumentType.getPlayers(ctx, "players"),
								false
						)
				).then(
					argument("music", NbtCompoundArgumentType.nbtCompound()).executes(
							ctx -> stopMusic(
									ctx, Optional.of(parse(
											NbtCompoundArgumentType.getNbtCompound(ctx, "music"),
											ctx.getSource().getRegistryManager()
									)),
									EntityArgumentType.getPlayers(ctx, "players"),
									false
							)
					)
				).then(
					literal("all").executes(
							ctx -> stopMusic(
									ctx, Optional.empty(),
									EntityArgumentType.getPlayers(ctx, "players"),
									true
							)
					)
				)
			)
		);
	}

	private static int playMusic(
			CommandContext<ServerCommandSource> ctx,
			MusicEntry music, Collection<ServerPlayerEntity> players, boolean skipQueue
	) {
		for (var p : players) {
			MusicHelper.playMusic(p, music, skipQueue);
		}
		ctx.getSource().sendFeedback(() -> Text.literal("Started music " + music), true);
		return players.size();
	}

	private static int stopMusic(
			CommandContext<ServerCommandSource> ctx,
			Optional<MusicEntry> music, Collection<ServerPlayerEntity> players, boolean all
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
		ctx.getSource().sendFeedback(() -> Text.literal(msg), true);
		return count;
	}

}
