package nu.metacraft.resource_packs;

import java.util.function.UnaryOperator;

public interface ServerPlayerExtension {

	PlayerPackData metacraft$getPackData();

	void metacraft$updatePackData(UnaryOperator<PlayerPackData> packData);

}
