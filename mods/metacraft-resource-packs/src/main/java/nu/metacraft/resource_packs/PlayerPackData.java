package nu.metacraft.resource_packs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import nu.metacraft.lib.util.METACodecs;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record PlayerPackData(PSet<UUID> resourcePacks) {

	public static final Codec<PlayerPackData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					METACodecs.createPCollectionCodec(
							UUIDUtil.CODEC, (PSet<UUID>) HashTreePSet.<UUID>empty()
					).fieldOf("resource_packs").forGetter(PlayerPackData::resourcePacks)
			).apply(instance, PlayerPackData::new)
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
