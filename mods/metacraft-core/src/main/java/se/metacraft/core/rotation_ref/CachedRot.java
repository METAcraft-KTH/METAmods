package se.metacraft.core.rotation_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec2;
import se.metacraft.core.position_ref.Cached;
import se.metacraft.core.registry.RotationRefRegistry;
import se.metacraft.core.util.RefContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CachedRot implements RotationRef {

	public static final MapCodec<CachedRot> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.lazyInitialized(() -> RotationRefRegistry.CODEC).fieldOf("rotation").forGetter(ref -> ref.rotation),
			Codec.BOOL.optionalFieldOf("only_found", true).forGetter(ref -> ref.onlyFound)
		).apply(instance, CachedRot::new)
	);

	private final RotationRef rotation;
	private final boolean onlyFound;
	private final Map<Cached.RefKey, Optional<Vec2>> cache = new HashMap<>();

	public CachedRot(RotationRef rotation, boolean onlyFound) {
		this.rotation = rotation;
		this.onlyFound = onlyFound;
	}

	@Override
	public Optional<Vec2> get(RefContext ctx) {
		var key = Cached.RefKey.from(ctx);
		if (cache.containsKey(key)) {
			return cache.get(key);
		} else {
			var pos = rotation.get(ctx);
			if (pos.isEmpty() && onlyFound) return Optional.empty();
			cache.put(key, pos);
			return pos;
		}
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.CACHED;
	}
}
