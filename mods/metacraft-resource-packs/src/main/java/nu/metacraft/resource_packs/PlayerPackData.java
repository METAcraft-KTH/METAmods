package nu.metacraft.resource_packs;

import com.mojang.serialization.Codec;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import nu.metacraft.lib.util.METACodecs;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record PlayerPackData(PSet<UUID> resourcePacks, PSet<UUID> cachedPacks) {

	public static final String KEY = "metacraft:resource_packs";

	public static final Codec<PlayerPackData> CODEC = METACodecs.createPCollectionCodec(
			UUIDUtil.CODEC, (PSet<UUID>) HashTreePSet.<UUID>empty()
	).xmap(
			packs -> new PlayerPackData(packs, HashTreePSet.empty()), PlayerPackData::resourcePacks
	);

	public static final PlayerPackData EMPTY = new PlayerPackData(HashTreePSet.empty(), HashTreePSet.empty());

	public PlayerPackData addPack(UUID pack, boolean persist) {
		if (resourcePacks.contains(pack) && persist) return this;
		if (cachedPacks.contains(pack) && !persist) return this;
		return new PlayerPackData(
				persist ? resourcePacks.plus(pack) : resourcePacks,
				!persist ? cachedPacks.plus(pack) : cachedPacks
		);
	}

	public PlayerPackData removePack(UUID pack) {
		if (!hasPack(pack)) return this;
		var storedPacks = resourcePacks.minus(pack);
		var cachedPacks = this.cachedPacks.minus(pack);
		if (storedPacks.isEmpty() && cachedPacks.isEmpty()) return EMPTY;
		return new PlayerPackData(storedPacks, cachedPacks);
	}

	public PlayerPackData updatePacks() {
		var config = ResourcePackConfig.getConfig();
		var packs = resourcePacks;
		for (var pack : resourcePacks) {
			var entry = config.getResourcePack(pack);
			if (entry.isEmpty() || entry.get().isGlobal()) {
				packs = packs.minus(pack);
			}
		}
		if (packs != resourcePacks) {
			return new PlayerPackData(packs, cachedPacks);
		} else {
			return this;
		}
	}

	public boolean hasPack(UUID pack) {
		return resourcePacks.contains(pack) || cachedPacks.contains(pack);
	}
}
