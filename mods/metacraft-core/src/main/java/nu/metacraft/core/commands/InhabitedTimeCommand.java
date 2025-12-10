package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkAccess;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * A command for querying and freezing the InhabitedTime value of chunks.
 * <p>
 * Freezing the inhabited time incrementation can be useful during testing to
 * prevent the backup system from needing to save chunks that it thinks are
 * important because they have high inhabited time.
 */
public class InhabitedTimeCommand {
	/**
	 * Whether inhabited time incrementation is frozen. Sorry for the static abuse, but
	 * this is to persist across reloads, and ease the mixin code.
	 */
	public static boolean frozen = false;

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
		dispatcher.register(
			literal("inhabitedtime")
				.requires(Permissions.require("metacraft.inhabitedtime", 2))
				.then(
					literal("query")
						.executes(ctx -> {
							CommandSourceStack source = ctx.getSource();
							ChunkAccess chunk = source.getLevel().getChunk(BlockPos.containing(source.getPosition()));
							source.sendSystemMessage(Component.literal("Inhabited time for chunk at " + chunk.getPos() + " is " + chunk.getInhabitedTime() + " ticks."));
							return 1;
						})
						.then(
							argument("chunkX", IntegerArgumentType.integer())
								.then(
									argument("chunkY", IntegerArgumentType.integer())
										.executes(ctx -> {
											CommandSourceStack source = ctx.getSource();
											int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
											int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkY");
											ChunkAccess chunk = source.getLevel().getChunk(chunkX, chunkZ);
											source.sendSystemMessage(Component.literal("Inhabited time for chunk at " + chunk.getPos() + " is " + chunk.getInhabitedTime() + " ticks."));
											return 1;
										})
								)
						)
				)
				.then(
					literal("freeze")
						.executes(ctx -> {
							frozen = true;
							ctx.getSource().sendSystemMessage(Component.literal("Inhabited time incrementation is now frozen."));
							return 1;
						})
				)
				.then(
					literal("unfreeze")
						.executes(ctx -> {
							frozen = false;
							ctx.getSource().sendSystemMessage(Component.literal("Inhabited time incrementation is now unfrozen."));
							return 1;
						})
				)
				.then(
					literal("status")
						.executes(ctx -> {
							ctx.getSource().sendSystemMessage(Component.literal("Inhabited time incrementation is currently " + (frozen ? "frozen." : "unfrozen.")));
							return 1;
						})
				)
		);
	}
}
