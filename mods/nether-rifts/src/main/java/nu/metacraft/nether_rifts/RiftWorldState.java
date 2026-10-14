package nu.metacraft.nether_rifts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.util.SavedDataTypeCache;

/** Server-wide: whether rift mode is on. A new world starts with it on. */
public class RiftWorldState extends SavedData {

	private static final Codec<RiftWorldState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("rift_mode").forGetter(s -> s.riftMode)
	).apply(i, RiftWorldState::new));

	private static final SavedDataTypeCache.Type<RiftWorldState, MinecraftServer> TYPE = new SavedDataTypeCache.Type<>(
			server -> new SavedDataType<>(NetherRifts.getID("world_state"), RiftWorldState::new, CODEC, null)
	);

	private boolean riftMode;

	public RiftWorldState() {
		this(true);
	}

	private RiftWorldState(boolean riftMode) {
		this.riftMode = riftMode;
	}

	public static RiftWorldState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(SavedDataTypeCache.get(server, TYPE));
	}

	public boolean riftMode() {
		return riftMode;
	}

	public void setRiftMode(boolean riftMode) {
		this.riftMode = riftMode;
		setDirty();
	}
}
