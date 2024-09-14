package se.datasektionen.mc.zones.compat.resource_pack;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import se.datasektionen.mc.resource_packs.ResourcePackHelper;
import se.datasektionen.mc.zones.zone.data.ZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataEntityTracking;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class ResourcePackData extends ZoneDataEntityTracking {

	public static final MapCodec<ResourcePackData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Uuids.SET_CODEC.fieldOf("resource_packs").forGetter(a -> a.resourcePacks)
			).apply(instance, ResourcePackData::new)
	);

	private final Set<UUID> resourcePacks;

	public ResourcePackData(Set<UUID> resourcePacks) {
		this.resourcePacks = resourcePacks;
	}

	public boolean addPack(UUID pack) {
		if (resourcePacks.contains(pack)) return false;
		this.getZone().getEntities().forEach(e -> {
			if (e instanceof ServerPlayerEntity p) {
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
			if (e instanceof ServerPlayerEntity p) {
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

	private void enablePack(ServerPlayerEntity p, UUID pack) {
		if (!ResourcePackHelper.hasResourcePack(p, pack)) {
			ResourcePackHelper.enableResourcePack(p, pack);
		}
	}

	private void disablePack(ServerPlayerEntity p, UUID pack) {
		if (ResourcePackHelper.hasResourcePack(p, pack)) {
			ResourcePackHelper.disableResourcePack(p, pack);
		}
	}

	@Override
	public void onEnter(Entity entity) {
		if (entity instanceof ServerPlayerEntity p) {
			resourcePacks.forEach(pack -> {
				enablePack(p, pack);
			});
		}
	}

	@Override
	public void onLeave(Entity entity) {
		if (entity instanceof ServerPlayerEntity p) {
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
