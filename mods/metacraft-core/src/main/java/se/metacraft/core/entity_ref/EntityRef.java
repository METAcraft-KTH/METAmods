package se.metacraft.core.entity_ref;

import se.metacraft.core.util.RefContext;

import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;

public interface EntityRef {

	Stream<? extends Entity> get(RefContext ctx);

	EntityRefType<?> getType();

}
