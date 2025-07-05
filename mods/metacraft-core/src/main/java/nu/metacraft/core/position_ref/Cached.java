package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Cached implements PositionRef {

	public static final MapCodec<Cached> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> PositionRefRegistry.CODEC).fieldOf("position").forGetter(ref -> ref.position),
					Codec.BOOL.optionalFieldOf("only_found", true).forGetter(ref -> ref.onlyFound)
			).apply(instance, Cached::new)
	);

	private final PositionRef position;
	private final boolean onlyFound;
	private final Map<RefKey, Optional<Vec3d>> cache = new HashMap<>();

	public Cached(PositionRef position, boolean onlyFound) {
		this.position = position;
		this.onlyFound = onlyFound;
	}

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		var key = RefKey.from(ctx);
		if (cache.containsKey(key)) {
			return cache.get(key);
		} else {
			var pos = position.get(ctx);
			if (pos.isEmpty() && onlyFound) return Optional.empty();
			cache.put(key, pos);
			return pos;
		}
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.CACHED;
	}
	
	public record RefKey(Optional<Entity> entity) {
		public static RefKey from(RefContext ctx) {
			return new RefKey(ctx.getEntity());
		}
	}
}
