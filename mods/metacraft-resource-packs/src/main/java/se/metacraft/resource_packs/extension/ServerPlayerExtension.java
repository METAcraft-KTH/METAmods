package se.metacraft.resource_packs.extension;

import se.metacraft.resource_packs.PlayerPackData;

import java.util.function.UnaryOperator;

public interface ServerPlayerExtension {

	PlayerPackData metacraft$getPackData();

	void metacraft$updatePackData(UnaryOperator<PlayerPackData> packData);

}
