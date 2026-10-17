package nu.metacraft.nether_rifts;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.nether_rifts.compat.ScfCompat;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.RiftItems;
import nu.metacraft.nether_rifts.mob.RiftModels;
import nu.metacraft.nether_rifts.mob.Showcase;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.rift.Ignition;
import nu.metacraft.nether_rifts.rift.RiftBlocks;
import nu.metacraft.nether_rifts.rift.RiftData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetherRifts implements ModInitializer {

	public static final String MODID = "nether-rifts";
	public static final String NAMESPACE = "nether_rifts";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		NetherRiftsConfig.getInstance();
		RiftModels.init();
		RiftItems.init();
		RiftEntities.init();
		BurningPatches.init();
		RiftBlocks.init();
		Ignition.init();
		RiftMobs.init();
		Showcase.init();
		new IsLoaded("simple-custom-features").ifLoaded(() -> ScfCompat.init());
		ServerTickEvents.END_LEVEL_TICK.register(level -> RiftData.get(level).tick(level));
		RiftsCommand.init();

		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}
}
