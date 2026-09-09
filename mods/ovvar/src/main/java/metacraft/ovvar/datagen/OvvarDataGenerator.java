package metacraft.ovvar.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/** {@code ./gradlew runDatagen}: writes every art-derived file into src/main/generated. */
public final class OvvarDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        generator.createPack().addProvider(GeneratedAssets::new);
    }
}
