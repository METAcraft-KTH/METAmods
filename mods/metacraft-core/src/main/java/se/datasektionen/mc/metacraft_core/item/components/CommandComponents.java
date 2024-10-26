package se.datasektionen.mc.metacraft_core.item.components;

import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.function.UnaryOperator;

public class CommandComponents {

	/**
	 * Command runs whenever player right-clicks the item.
	 */
	public static final ComponentType<String> INTERACT_COMMAND = register(
			"interact_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player right-clicks the item on a block.
	 * Runs at bottom center position of the block.
	 */
	public static final ComponentType<String> INTERACT_BLOCK_COMMAND = register(
			"interact_block_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player right-clicks the item on a block.
	 * Runs at the bottom center position of the block offset based on side clicked.
	 * So clicking on top of block will trigger above block, clicking on east side will run centered on block on east side.
	 */
	public static final ComponentType<String> INTERACT_BLOCK_SIDE_COMMAND = register(
			"interact_block_side_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player right-clicks the item on a block.
	 * Runs at exactly where player clicks.
	 */
	public static final ComponentType<String> INTERACT_BLOCK_EXACT_COMMAND = register(
			"interact_block_exact_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player right-clicks the item on an entity.
	 * Runs at the entity's feet position, use @n to access entity.
	 */
	public static final ComponentType<String> INTERACT_ENTITY_COMMAND = register(
			"interact_entity_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player right-clicks the item on an entity.
	 * Runs at the exact position the player clicks.
	 */
	public static final ComponentType<String> INTERACT_ENTITY_EXACT_COMMAND = register(
			"interact_entity_exact_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player swings their main arm.
	 * This will detect left-clicks that do not hit blocks or entities,
	 * but will detect right-clicks they trigger the arm swing animation client-side.
	 */
	public static final ComponentType<String> MAIN_HAND_SWING_COMMAND = register(
			"main_hand_swing_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player left-clicks a block.
	 * Runs at bottom center position of the block.
	 */
	public static final ComponentType<String> ATTACK_BLOCK_COMMAND = register(
			"attack_block_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player left-clicks a block.
	 * Runs at bottom center position of the block offset based on side clicked.
	 * So clicking on top of block will trigger above block, clicking on east side will run centered on block on east side.
	 */
	public static final ComponentType<String> ATTACK_BLOCK_SIDE_COMMAND = register(
			"attack_block_side_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player left-clicks an entity.
	 * Runs at entity's position, access with @n.
	 */
	public static final ComponentType<String> ATTACK_ENTITY_COMMAND = register(
			"attack_entity_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Runs a command for the given player.
	 * Command feedback is only sent to creative operators.
	 * @param player The player to run the command for.
	 * @param pos The position to run the command at.
	 * @param command The command to run.
	 * @return {@link ActionResult#SUCCESS_SERVER} if the command succeeds and returns 1,
	 *          {@link ActionResult#PASS} if the command succeeds and returns 0,
	 *          {@link ActionResult#CONSUME} if the command succeeds and returns something else,
	 *          {@link ActionResult#FAIL} if the command fails.
	 */
	public static ActionResult runCommand(ServerPlayerEntity player, Vec3d pos, String command) {
		MutableObject<ActionResult> result = new MutableObject<>();
		var source = new ServerCommandSource(
				player.isCreativeLevelTwoOp() ? player.getCommandOutput() : CommandOutput.DUMMY,
				pos, player.getRotationClient(), player.getServerWorld(),
				2, player.getName().getString(), player.getDisplayName(),
				player.getServer(), player
		).withReturnValueConsumer((successful, value) -> {
			if (successful) {
				if (value == 0) {
					result.setValue(ActionResult.PASS);
				} else if (value > 0) {
					result.setValue(ActionResult.SUCCESS_SERVER.noIncrementStat());
				} else {
					result.setValue(ActionResult.CONSUME.noIncrementStat());
				}
			} else {
				result.setValue(ActionResult.FAIL);
			}
		});
		player.getServer().getCommandManager().executeWithPrefix(source, command);
		return result.getValue();
	}

	public static void init() {

	}

	private static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		return METAcraftComponents.register(id, builderOperator);
	}

}
