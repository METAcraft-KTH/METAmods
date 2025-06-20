package se.datasektionen.mc.metacraft_core.entity_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.Uuids;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.metacraft_core.util.RefContext;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class UUIDRef implements EntityRef {

	public static final MapCodec<UUIDRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Uuids.CODEC.fieldOf("uuid").forGetter(t -> t.uuid)
			).apply(instance, UUIDRef::new)
	);

	private final UUID uuid;

	public UUIDRef(UUID uuid) {
		this.uuid = uuid;
	}

	@Override
	public Stream<? extends Entity> get(RefContext ctx) {
		return Optional.ofNullable(ctx.getWorld().getEntity(uuid)).stream();
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.UUID;
	}
}
