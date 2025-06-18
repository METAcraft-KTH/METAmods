package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.stream.Stream;

public final class SelfRef implements EntityRef {

	private static final SelfRef INSTANCE = new SelfRef();

	public static SelfRef getInstance() {
		return INSTANCE;
	}

	public static final MapCodec<SelfRef> CODEC = MapCodec.unit(INSTANCE);

	private SelfRef() {}

	@Override
	public Stream<? extends Entity> get(RefContext ctx) {
		return ctx.getEntity().stream();
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.SELF;
	}
}
