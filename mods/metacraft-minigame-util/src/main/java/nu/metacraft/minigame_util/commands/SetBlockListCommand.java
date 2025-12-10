package nu.metacraft.minigame_util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import nu.metacraft.minigame_util.BlockPredicateList;
import nu.metacraft.minigame_util.MinigameUtilState;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SetBlockListCommand {

	private static final DynamicCommandExceptionType GENERIC = new DynamicCommandExceptionType(
			s -> Component.literal((String) s)
	);

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
			literal("set-block-break-rules").requires(
					player -> player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
			).then(
				argument("data", CompoundTagArgument.compoundTag()).executes(ctx -> {
					var data = CompoundTagArgument.getCompoundTag(ctx, "data");
					var list = BlockPredicateList.CODEC.parse(
							ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE),
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
