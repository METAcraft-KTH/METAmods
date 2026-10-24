package se.metacraft.qol.void_anchor;

import se.metacraft.qol.Qol;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import se.metacraft.core.block.PolymerBlockSounds;
import se.metacraft.qol.void_anchor.block.VoidAnchorBlock;

import java.util.function.Function;

public class VoidAnchorBlocks {

	public static final Holder.Reference<Block> VOID_ANCHOR = register(
			"void_anchor", VoidAnchorBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.RESPAWN_ANCHOR)
	);

	public static void init() {

	}

	private static Holder.Reference<Block> register(String id, Function<BlockBehaviour.Properties, Block> creator, BlockBehaviour.Properties settings) {
		var key = ResourceKey.create(Registries.BLOCK, Qol.getID(id));
		var block = Registry.registerForHolder(BuiltInRegistries.BLOCK, key, creator.apply(settings.setId(key)));
		// shown as a note block, which would make it sound like wood
		PolymerBlockSounds.patch(block.value());
		return block;
	}

}
