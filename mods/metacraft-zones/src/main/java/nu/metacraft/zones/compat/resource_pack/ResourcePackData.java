package nu.metacraft.zones.compat.resource_pack;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.resource_packs.ResourcePackHelper;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataEntityTracking;
import nu.metacraft.zones.zone.data.ZoneDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;

public class ResourcePackData extends ZoneDataEntityTracking {

	public static final MapCodec<ResourcePackData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					METACodecs.createPCollectionCodec(
							UUIDUtil.CODEC, (PSet<UUID>) HashTreePSet.<UUID>empty()
					).fieldOf("resource_packs").forGetter(a -> a.resourcePacks.get())
			).apply(instance, ResourcePackData::new)
	);

	private final AtomicReference<PSet<UUID>> resourcePacks;

	public ResourcePackData(PSet<UUID> resourcePacks) {
		this.resourcePacks = new AtomicReference<>(resourcePacks);
	}

	public boolean addPack(UUID pack) {
		if (resourcePacks.get().contains(pack)) return false;
		getZone().getWorld().getServer().execute(() -> {
			this.getZone().getEntities().forEach(e -> {
				if (e instanceof ServerPlayer p) {
					enablePack(p, pack);
				}
			});
		});
		resourcePacks.getAndUpdate(packs -> packs.plus(pack));
		markDirty();
		return true;
	}

	public boolean removePack(UUID pack) {
		if (!resourcePacks.get().contains(pack)) return false;
		getZone().getWorld().getServer().execute(() -> {
			this.getZone().getEntities().forEach(e -> {
				if (e instanceof ServerPlayer p) {
					disablePack(p, pack);
				}
			});
		});
		resourcePacks.getAndUpdate(packs -> packs.minus(pack));
		markDirty();
		return true;
	}

	public Collection<UUID> getPacks() {
		return Collections.unmodifiableSet(resourcePacks.get());
	}

	private void enablePack(ServerPlayer p, UUID pack) {
		if (!ResourcePackHelper.hasResourcePack(p, pack)) {
			ResourcePackHelper.enableResourcePack(p, pack, false);
		}
	}

	private void disablePack(ServerPlayer p, UUID pack) {
		if (ResourcePackHelper.hasResourcePack(p, pack)) {
			ResourcePackHelper.disableResourcePack(p, pack);
		}
	}

	@Override
	public void onEnter(Entity entity) {
		if (entity instanceof ServerPlayer p) {
			resourcePacks.get().forEach(pack -> {
				enablePack(p, pack);
			});
		}
	}

	@Override
	public void onLeave(Entity entity) {
		if (entity instanceof ServerPlayer p && !p.hasDisconnected()) {
			resourcePacks.get().forEach(pack -> {
				disablePack(p, pack);
			});
		}
	}

	@Override
	public ZoneDataType<? extends ZoneData> getType() {
		return ResourcePackDataType.RESOURCE_PACK;
	}

	@Override
	public String toString() {
		return "ResourcePackData[" + resourcePacks + "]";
	}
}
