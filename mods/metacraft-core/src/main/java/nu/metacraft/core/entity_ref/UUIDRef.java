package nu.metacraft.core.entity_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;

public class UUIDRef implements EntityRef {

	public static final MapCodec<UUIDRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					UUIDUtil.AUTHLIB_CODEC.fieldOf("uuid").forGetter(t -> t.uuid)
			).apply(instance, UUIDRef::new)
	);

	private final UUID uuid;

	public UUIDRef(UUID uuid) {
		this.uuid = uuid;
	}

	@Override
	public Stream<? extends Entity> get(RefContext ctx) {
		return Optional.ofNullable(ctx.world().getEntity(uuid)).stream();
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.UUID;
	}
}
