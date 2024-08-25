package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;

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
	public Stream<? extends Entity> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		return cutsceneInstance.getEntities(id);
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.CUTSCENE;
	}
}
