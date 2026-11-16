package se.metacraft.ovvar.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import se.metacraft.ovvar.content.PatchLoot;

/** {@code ./gradlew runDatagen}: writes every art-derived file into src/main/generated. */
public final class OvvarDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		// The catalogue's own checks first, so a LOOT that outgrew the dye colour fails here too.
		PatchLoot.check();
		var pack = generator.createPack();
		pack.addProvider(GeneratedAssets::new);
		// The Blockbench plugin's copy of Spot/Patches/Chapter, so it can draw a patch the way the
		// game does without a game running.
		pack.addProvider(BlockbenchManifest::new);
	}
}
