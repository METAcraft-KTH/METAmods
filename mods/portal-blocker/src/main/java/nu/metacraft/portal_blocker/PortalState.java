package nu.metacraft.portal_blocker;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.NotNull;

public record PortalState(Object2BooleanMap<BlockingType> map) {

	public static final Codec<PortalState> CODEC = Codec.unboundedMap(
			BlockingType.CODEC, Codec.BOOL
	).xmap(
			map -> new PortalState(new Object2BooleanOpenHashMap<>(map)),
			state -> state.map
	);

	public static PortalState from(BlockingType... isBlocked) {
		var state = new PortalState(new Object2BooleanOpenHashMap<>());
		for (var block : isBlocked) {
			state.setBlocked(block, true);
		}
		return state;
	}

	public void setBlocked(BlockingType type, boolean value) {
		map.put(type, value);
	}

	public void removeFromState(BlockingType type) {
		map.removeBoolean(type);
	}

	public boolean isBlocked(BlockingType type) {
		return map.getBoolean(type);
	}

	public boolean isInState(BlockingType type) {
		return map.containsKey(type);
	}

	@Override
	public @NotNull String toString() {
		return "PortalState" + map;
	}

	public enum BlockingType implements StringIdentifiable {
		ACTIVATION("activation"),
		TRAVEL("travel"),
		GENERATION("generation");

		public static final Codec<BlockingType> CODEC = StringIdentifiable.createCodec(BlockingType::values);

		private final String name;

		@Override
		public String toString() {
			return asString();
		}

		BlockingType(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

}
