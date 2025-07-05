package nu.metacraft.simplecustomfeatures.objects.blocks;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import nu.metacraft.simplecustomfeatures.RegistryHelper;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;

import java.util.HashSet;

public interface BaseBlock extends BaseObject<Block> {

	@Override
	default void onRegistrationFail(Identifier id, Block value) {
		RegistryHelper.removeIntrusiveEntry(Registries.BLOCK, value);
	}

	@Override
	default void onUnregister(RegistryEntry<Block> entry) {
		RegistryHelper.removeFromIdList(Block.STATE_IDS, new HashSet<>(entry.value().getStateManager().getStates()));
	}

	@Override
	default void onRegistrationSuccess(RegistryEntry.Reference<Block> entry) {
		entry.value().getStateManager().getStates().forEach(state -> {
			Block.STATE_IDS.add(state);
			state.initShapeCache();
		});
		entry.value().getLootTableKey();
	}
}
