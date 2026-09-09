package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

import java.util.Comparator;
import java.util.List;

public class TopOfflineScoresCommand {
	public static void register(
		CommandDispatcher<CommandSourceStack> dispatcher,
		CommandBuildContext registryAccess
	) {
		dispatcher.register(
			Commands.literal("top-offline-scores")
				.requires(Permissions.require("metacraft.top-offline-scores", PermissionLevel.GAMEMASTERS))
				.then(
					Commands.argument("objective", ObjectiveArgument.objective())
						.then(
							Commands.argument("amount", IntegerArgumentType.integer(1))
								.then(
									Commands.argument("range", RangeArgument.intRange())
										.then(
											Commands.argument("storage", IdentifierArgument.id())
												// .suggests(StorageDataAccessor.SUGGEST_STORAGE)
												.then(
													Commands.argument("path", NbtPathArgument.nbtPath())
														.executes(ctx -> {
															Objective objective = ObjectiveArgument.getObjective(ctx, "objective");
															int amount = IntegerArgumentType.getInteger(ctx, "amount");
															MinMaxBounds.Ints range = RangeArgument.Ints.getRange(ctx, "range");
															Identifier storage = IdentifierArgument.getId(ctx, "storage");
															NbtPathArgument.NbtPath path = NbtPathArgument.getPath(ctx, "path");
															return run(ctx, objective, amount, range, storage, path);
														})
												)
										)
								)
						)
				)
		);
	}

	private static int run(CommandContext<CommandSourceStack> ctx, Objective objective, int amount, MinMaxBounds.Ints range, Identifier storage, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerScoreboard scoreboard = source.getServer().getScoreboard();
		record ScorePair(ScoreHolder holder, ReadOnlyScoreInfo info) {}
		List<StringTag> stringTagList = scoreboard.getTrackedPlayers().stream()
			.map(scoreHolder -> new ScorePair(scoreHolder, scoreboard.getPlayerScoreInfo(scoreHolder, objective)))
			.filter(pair -> pair.info != null)
			.filter(score -> range.matches(score.info.value()))
			.sorted(Comparator.comparing(pair -> pair.info.value(), Comparator.reverseOrder()))
			.limit(amount)
			.map(score -> score.holder.getScoreboardName())
			.map(StringTag::valueOf)
			.toList();

		ListTag listTag = new ListTag();
		listTag.addAll(stringTagList);

		CommandStorage commandStorage = source.getServer().getCommandStorage();
		CompoundTag storageNbt = commandStorage.get(storage);
		path.set(storageNbt, listTag);
		commandStorage.set(storage, storageNbt);

		source.sendSuccess(() -> Component.literal("Saved top " + stringTagList.size() + " scores to " + storage + " at path " + path), false);
		return listTag.size();
	}
}
