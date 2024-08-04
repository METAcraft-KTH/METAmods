package se.datasektionen.mc.simplecustomfeatures.objects.blocks;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import se.datasektionen.mc.simplecustomfeatures.RegistryHelper;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;

public interface BaseBlock extends BaseObject<Block> {

	@Override
	default void onRegistrationFail(Block value) {
		RegistryHelper.removeIntrusiveEntry(Registries.BLOCK, value);
	}

	@Override
	default void onUnregister(RegistryEntry<Block> entry) {
		RegistryHelper.removeBlockStatesFor(entry.value());
	}

	@Override
	default void onRegistrationSuccess(RegistryEntry.Reference<Block> entry) {
		RegistryHelper.finishBlockRegistration(entry.value());
	}
}
