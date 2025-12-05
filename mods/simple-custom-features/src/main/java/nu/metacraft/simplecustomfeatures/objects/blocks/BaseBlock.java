package nu.metacraft.simplecustomfeatures.objects.blocks;

import nu.metacraft.simplecustomfeatures.RegistryHelper;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;

import java.util.HashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public interface BaseBlock extends BaseObject<Block> {

	@Override
	default void onRegistrationFail(Identifier id, Block value) {
		RegistryHelper.removeIntrusiveEntry(BuiltInRegistries.BLOCK, value);
	}

	@Override
	default void onUnregister(Holder<Block> entry) {
		RegistryHelper.removeFromIdList(Block.BLOCK_STATE_REGISTRY, new HashSet<>(entry.value().getStateDefinition().getPossibleStates()));
	}

	@Override
	default void onRegistrationSuccess(Holder.Reference<Block> entry) {
		entry.value().getStateDefinition().getPossibleStates().forEach(state -> {
			Block.BLOCK_STATE_REGISTRY.add(state);
			state.initCache();
		});
		entry.value().getLootTable();
	}
}
