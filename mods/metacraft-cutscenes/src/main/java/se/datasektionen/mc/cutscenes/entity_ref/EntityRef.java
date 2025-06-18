package se.datasektionen.mc.cutscenes.entity_ref;

import net.minecraft.entity.Entity;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.stream.Stream;

public interface EntityRef {

	Stream<? extends Entity> get(RefContext ctx);

	EntityRefType<?> getType();

}
