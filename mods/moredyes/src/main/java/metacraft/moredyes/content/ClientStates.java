package metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import metacraft.moredyes.MoreDyes;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The only place that asks Polymer for client-side donor states.
 *
 * Fallback policy: a pool that cannot serve a request is a startup failure, not a downgrade. Another
 * Polymer mod on the same server changed the arithmetic, and that needs a human. The {@link #ERROR}
 * state exists for the runtime paths that should never happen; it is deliberately a command block so
 * it is impossible to mistake for real content.
 */
public final class ClientStates {
	/** What a vanilla client is shown when a visual path fails at runtime. Loud on purpose. */
	public static final BlockState ERROR = Blocks.COMMAND_BLOCK.defaultBlockState();

	private ClientStates() {}

	public static BlockState request(String what, BlockModelType type, PolymerBlockModel... models) {
		int left = PolymerBlockResourceUtils.getBlocksLeft(type);
		BlockState state = PolymerBlockResourceUtils.requestBlock(type, models);
		if (state == null) {
			throw new IllegalStateException("[" + MoreDyes.MOD_ID + "] Polymer pool " + type
					+ " is exhausted (" + left + " left) while registering " + what
					+ ". Another Polymer mod is using the same pool; the server cannot start with this content half-registered.");
		}
		return state;
	}

	/** Log and return the loud error state for a server state that has no client mapping. */
	public static BlockState error(BlockState state) {
		MoreDyes.LOGGER.error("[{}] no client state for {} — showing the error block", MoreDyes.MOD_ID, state);
		return ERROR;
	}

	/** The client-side state of another block's default state (ours or vanilla). */
	public static BlockState clientStateOf(Block block, @Nullable PacketContext context) {
		BlockState state = block.defaultBlockState();
		return block instanceof PolymerBlock polymer ? polymer.getPolymerBlockState(state, context) : state;
	}

	public static BlockState requestEmpty(String what, BlockModelType type) {
		BlockState state = PolymerBlockResourceUtils.requestEmpty(type);
		if (state == null) {
			throw new IllegalStateException("[" + MoreDyes.MOD_ID + "] Polymer has no empty donor state for "
					+ type + " while registering " + what);
		}
		return state;
	}
}
