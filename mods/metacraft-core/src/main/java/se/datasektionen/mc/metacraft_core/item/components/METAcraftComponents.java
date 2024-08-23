package se.datasektionen.mc.metacraft_core.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Keyable;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableObject;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

import java.util.function.UnaryOperator;

public class METAcraftComponents {

	/**
	 * Used to make an item disappear or transform into something else after a certain date.
	 * Useful to give out overpowered items for a specific event without affecting progression too much.
	 * See {@link ExpiresComponent}
	 */
	public static final ComponentType<ExpiresComponent> EXPIRES_AT = register(
			"expires_at", builder -> builder.codec(ExpiresComponent.CODEC)
	);

	/**
	 * Marker that states that the component should be removed from containers at load time.
	 * This exists because some vanilla codecs will crash when trying to load an empty item stack from a list
	 * (this is intended behaviour for some reason).
	 * This will not work for all codecs that access item stacks, but it will work with
	 * {@link net.minecraft.item.ItemStack#OPTIONAL_CODEC} and {@link net.minecraft.component.type.ContainerComponent}.
	 * It will also work with {@link Codec#listOf()} and {@link Codec#optionalFieldOf(String)} of {@link net.minecraft.item.ItemStack#CODEC} and
	 * {@link net.minecraft.item.ItemStack#UNCOUNTED_CODEC}, but not in a general case
	 * (for example {@link Codec#simpleMap(Codec, Codec, Keyable)} and {@link Codec#list(Codec)} will not work).
	 * For this reason it is best not to rely on this component too much, and merely use it in addition to other means of removing the item stack.
	 */
	public static final ComponentType<Unit> DELETED = register(
			"deleted", builder -> builder.packetCodec(PacketCodec.unit(Unit.INSTANCE))
	);

	/**
	 * Command runs whenever player right-clicks the item.
	 */
	public static final ComponentType<String> INTERACT_COMMAND = register(
			"interact_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player right-clicks the item on a block.
	 */
	public static final ComponentType<String> INTERACT_BLOCK_COMMAND = register(
			"interact_block_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player right-clicks the item on an entity.
	 */
	public static final ComponentType<String> INTERACT_ENTITY_COMMAND = register(
			"interact_entity_command", builder -> builder.codec(Codec.STRING)
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
	 */
	public static final ComponentType<String> ATTACK_BLOCK_COMMAND = register(
			"attack_block_command", builder -> builder.codec(Codec.STRING)
	);

	/**
	 * Command runs whenever player left-clicks an entity.
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
	 * @return {@link ActionResult#SUCCESS_NO_ITEM_USED} if the command succeeds and returns 1,
	 *          {@link ActionResult#PASS} if the command succeeds and returns 0,
	 *          {@link ActionResult#CONSUME_PARTIAL} if the command succeeds and returns something else,
	 *          {@link ActionResult#FAIL} if the command fails.
	 */
	public static ActionResult runCommand(PlayerEntity player, Vec3d pos, String command) {
		MutableObject<ActionResult> result = new MutableObject<>();
		var source = new ServerCommandSource(
				player.isCreativeLevelTwoOp() ? player : CommandOutput.DUMMY,
				pos, player.getRotationClient(), (ServerWorld) player.getWorld(),
				2, player.getName().getString(), player.getDisplayName(),
				player.getServer(), player
		).withReturnValueConsumer((successful, value) -> {
			if (successful) {
				if (value == 0) {
					result.setValue(ActionResult.PASS);
				} else if (value > 0) {
					result.setValue(ActionResult.SUCCESS_NO_ITEM_USED);
				} else {
					result.setValue(ActionResult.CONSUME_PARTIAL);
				}
			} else {
				result.setValue(ActionResult.FAIL);
			}
		});
		player.getServer().getCommandManager().executeWithPrefix(source, command);
		return result.getValue();
	}



	public static void init() {
		ExpiresComponent.init();
	}

	private static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				Registries.DATA_COMPONENT_TYPE, METAcraftCore.getID(id),
				builderOperator.apply(ComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
