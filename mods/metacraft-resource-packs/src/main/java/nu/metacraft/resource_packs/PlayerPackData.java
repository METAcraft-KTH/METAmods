package nu.metacraft.resource_packs;

import com.mojang.serialization.Codec;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import nu.metacraft.lib.util.METACodecs;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record PlayerPackData(PSet<UUID> resourcePacks) {

	public static final String KEY = "metacraft:resource_packs";

	public static final Codec<PlayerPackData> CODEC = METACodecs.createPCollectionCodec(
			UUIDUtil.CODEC, (PSet<UUID>) HashTreePSet.<UUID>empty()
	).xmap(
			PlayerPackData::new, PlayerPackData::resourcePacks
	);
	public static final PlayerPackData EMPTY = new PlayerPackData(HashTreePSet.empty());

	public PlayerPackData addPack(UUID pack) {
		if (resourcePacks.contains(pack)) return this;
		return new PlayerPackData(resourcePacks.plus(pack));
	}

	public PlayerPackData removePack(UUID pack) {
		if (!resourcePacks.contains(pack)) return this;
		if (resourcePacks.size() == 1) return EMPTY;
		return new PlayerPackData(resourcePacks.minus(pack));
	}

	public PlayerPackData updatePacks() {
		var config = ResourcePackConfig.getConfig();
		var packs = resourcePacks;
		for (var pack : resourcePacks) {
			var entry = config.getResourcePack(pack);
			if (entry == null || entry.isGlobal()) {
				packs = packs.minus(pack);
			}
		}
		if (packs != resourcePacks) {
			return new PlayerPackData(packs);
		} else {
			return this;
		}
	}

	public boolean hasPack(UUID pack) {
		return resourcePacks.contains(pack);
	}
}
