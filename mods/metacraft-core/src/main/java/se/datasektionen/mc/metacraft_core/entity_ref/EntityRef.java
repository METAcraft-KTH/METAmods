package se.datasektionen.mc.metacraft_core.entity_ref;

import net.minecraft.entity.Entity;
import se.datasektionen.mc.metacraft_core.util.RefContext;

import java.util.stream.Stream;

public interface EntityRef {

	Stream<? extends Entity> get(RefContext ctx);

	EntityRefType<?> getType();

}
