package nu.metacraft.core.entity_ref;

import com.mojang.serialization.MapCodec;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;

public final class SelfRef implements EntityRef {

	private static final SelfRef INSTANCE = new SelfRef();

	public static SelfRef getInstance() {
		return INSTANCE;
	}

	public static final MapCodec<SelfRef> CODEC = MapCodec.unit(INSTANCE);

	private SelfRef() {}

	@Override
	public Stream<? extends Entity> get(RefContext ctx) {
		return ctx.entity().stream();
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.SELF;
	}
}
