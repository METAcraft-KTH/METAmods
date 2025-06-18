package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.stream.Stream;

public class CutsceneRef implements EntityRef {

	public static final MapCodec<CutsceneRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf("id").forGetter(t -> t.id)
			).apply(instance, CutsceneRef::new)
	);

	private final String id;

	public CutsceneRef(String id) {
		this.id = id;
	}

	@Override
	public Stream<? extends Entity> get(RefContext ctx) {
		return CutsceneInstance.getCutscene(ctx).map(scene -> scene.getEntities(id)).orElse(Stream.empty());
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.CUTSCENE;
	}
}
