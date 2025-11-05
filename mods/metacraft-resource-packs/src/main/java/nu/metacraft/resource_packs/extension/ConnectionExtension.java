package nu.metacraft.resource_packs.extension;

import org.pcollections.PSet;

import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface ConnectionExtension {

	Set<UUID> metacraft$getAddedPacks();

	void metacraft$updateAddedPacks(UnaryOperator<PSet<UUID>> updater);

}
