package nu.metacraft.resource_packs.mixin;

import net.minecraft.network.Connection;
import nu.metacraft.resource_packs.extension.ConnectionExtension;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

@Mixin(Connection.class)
public class ConnectionMixin implements ConnectionExtension {

	@Unique
	private final AtomicReference<PSet<UUID>> addedPacks = new AtomicReference<>(HashTreePSet.empty());

	@Override
	public Set<UUID> metacraft$getAddedPacks() {
		return addedPacks.get();
	}

	@Override
	public void metacraft$updateAddedPacks(UnaryOperator<PSet<UUID>> updater) {
		addedPacks.getAndUpdate(updater);
	}
}
