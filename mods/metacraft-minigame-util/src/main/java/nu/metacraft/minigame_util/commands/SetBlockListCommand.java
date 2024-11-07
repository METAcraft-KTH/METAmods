package nu.metacraft.minigame_util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import nu.metacraft.minigame_util.BlockPredicateList;
import nu.metacraft.minigame_util.MinigameUtilState;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SetBlockListCommand {

	private static final DynamicCommandExceptionType GENERIC = new DynamicCommandExceptionType(
			s -> Text.literal((String) s)
	);

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
			literal("set-block-break-rules").requires(player -> player.hasPermissionLevel(2)).then(
				argument("data", NbtCompoundArgumentType.nbtCompound()).executes(ctx -> {
					var data = NbtCompoundArgumentType.getNbtCompound(ctx, "data");
					var list = BlockPredicateList.CODEC.parse(
							ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE),
							data
					).getPartialOrThrow(GENERIC::create);
					MinigameUtilState.getInstance(ctx.getSource().getServer()).setCanBreak(list);
					return 1;
				})
			).then(
				literal("remove").executes(ctx -> {
					MinigameUtilState.getInstance(ctx.getSource().getServer()).setCanBreak(null);
					return 1;
				})
			)
		);
	}

}
