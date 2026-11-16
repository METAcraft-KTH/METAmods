package se.metacraft.ovvar.compat.resource_packs;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import se.metacraft.ovvar.pack.ComboPack;
import se.metacraft.ovvar.pack.Combos;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import se.metacraft.resource_packs.EarlyPackPacketsCallback;
import se.metacraft.resource_packs.ResourcePackConfig;
import se.metacraft.resource_packs.ServePackCallback;

import java.util.Optional;

/**
 * Serves {@link ComboPack} from metacraft-resource-packs' pack server and sends it to joining
 * players with the other packs. The only class that names that mod's types; reached only when
 * {@link ComboPack#ACTIVE} says it is installed.
 */
public final class ResourcePacksCompat {
	private ResourcePacksCompat() {}

	public static void init() {
		ServePackCallback.EVENT.register((path, verified) -> ComboPack.serve(path));
		EarlyPackPacketsCallback.EVENT.register((server, profile, packets) -> {
			// A client with ovvar-client builds the combinations itself (Combos#drawsCombos): it gets none of this pack.
			if (Combos.drawsCombos(profile.id())) return;
			ComboPack.Version version = ComboPack.current();
			if (version != null) packets.accept(push(version));
		});
	}

	/** The push for one build; as required as the Polymer pack, so it asks the same of the player. */
	public static ClientboundResourcePackPushPacket push(ComboPack.Version version) {
		return new ClientboundResourcePackPushPacket(ComboPack.ID, ResourcePackConfig.getConfig().getBaseUrl() + "/" + version.path(),
				version.sha1(), PolymerResourcePackUtils.isRequired(), Optional.empty());
	}
}
