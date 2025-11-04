package nu.metacraft.zones.compat.resource_pack;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.resource_packs.ResourcePackHelper;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataEntityTracking;
import nu.metacraft.zones.zone.data.ZoneDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class ResourcePackData extends ZoneDataEntityTracking {

	public static final MapCodec<ResourcePackData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					UUIDUtil.CODEC_SET.fieldOf("resource_packs").forGetter(a -> a.resourcePacks)
			).apply(instance, ResourcePackData::new)
	);

	private final Set<UUID> resourcePacks;

	public ResourcePackData(Set<UUID> resourcePacks) {
		this.resourcePacks = resourcePacks;
	}

	public boolean addPack(UUID pack) {
		if (resourcePacks.contains(pack)) return false;
		this.getZone().getEntities().forEach(e -> {
			if (e instanceof ServerPlayer p) {
				enablePack(p, pack);
			}
		});
		resourcePacks.add(pack);
		markDirty();
		return true;
	}

	public boolean removePack(UUID pack) {
		if (!resourcePacks.contains(pack)) return false;
		this.getZone().getEntities().forEach(e -> {
			if (e instanceof ServerPlayer p) {
				disablePack(p, pack);
			}
		});
		resourcePacks.remove(pack);
		markDirty();
		return true;
	}

	public Collection<UUID> getPacks() {
		return Collections.unmodifiableSet(resourcePacks);
	}

	private void enablePack(ServerPlayer p, UUID pack) {
		if (!ResourcePackHelper.hasResourcePack(p, pack)) {
			ResourcePackHelper.enableResourcePack(p, pack);
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
			resourcePacks.forEach(pack -> {
				enablePack(p, pack);
			});
		}
	}

	@Override
	public void onLeave(Entity entity) {
		if (entity instanceof ServerPlayer p && !p.hasDisconnected()) {
			resourcePacks.forEach(pack -> {
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
