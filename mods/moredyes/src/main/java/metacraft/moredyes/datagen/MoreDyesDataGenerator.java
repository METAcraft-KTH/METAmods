package metacraft.moredyes.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/** {@code ./gradlew runDatagen}: writes every colour-derived file into src/main/generated. */
public final class MoreDyesDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		generator.createPack().addProvider(GeneratedAssets::new);
	}
}
