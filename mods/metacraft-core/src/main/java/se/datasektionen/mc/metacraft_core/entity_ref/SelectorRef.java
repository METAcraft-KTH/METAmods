package se.datasektionen.mc.metacraft_core.entity_ref;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.metacraft_core.util.RefContext;
import se.datasektionen.mc.metacraft_core.util.SerializableEntitySelector;

import java.util.stream.Stream;

public class SelectorRef implements EntityRef {

	public static final MapCodec<SelectorRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SerializableEntitySelector.CODEC.fieldOf("selector").forGetter(t -> t.selector)
			).apply(instance, SelectorRef::new)
	);

	private final SerializableEntitySelector selector;

	public SelectorRef(SerializableEntitySelector selector) {
		this.selector = selector;
	}

	@Override
	public Stream<? extends Entity> get(RefContext ctx) {
		try {
			return selector.get().getEntities(ctx.getCommandSource()).stream();
		} catch (CommandSyntaxException e) {
			METAcraftCore.LOGGER.error(e.getMessage(), e);
			return Stream.empty();
		}
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.SELECTOR;
	}
}
