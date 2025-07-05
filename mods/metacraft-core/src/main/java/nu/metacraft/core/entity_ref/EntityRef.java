package nu.metacraft.core.entity_ref;

import net.minecraft.entity.Entity;
import nu.metacraft.core.util.RefContext;

import java.util.stream.Stream;

public interface EntityRef {

	Stream<? extends Entity> get(RefContext ctx);

	EntityRefType<?> getType();

}
