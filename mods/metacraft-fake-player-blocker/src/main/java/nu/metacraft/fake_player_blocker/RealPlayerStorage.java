package nu.metacraft.fake_player_blocker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class RealPlayerStorage extends SavedData {

	public static final Codec<RealPlayerStorage> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.STRING.listOf().fieldOf("real_usernames").forGetter(
							data -> data.realUsernames.stream().toList()
					)
			).apply(instance, RealPlayerStorage::new)
	);

	private static final SavedDataType<RealPlayerStorage> TYPE = new SavedDataType<>(
			"metacraft-real-player-storage",
			RealPlayerStorage::new, RealPlayerStorage.CODEC,
			null
	);

	public static RealPlayerStorage getInstance(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private final Set<String> realUsernames;

	public RealPlayerStorage() {
		this(new ArrayList<>());
	}

	public RealPlayerStorage(List<String> realUsernames) {
		this.realUsernames = new HashSet<>(realUsernames);
	}

	public void addRealUsername(String name) {
		if (this.realUsernames.add(name.toLowerCase(Locale.ROOT))) {
			setDirty();
		}
	}

	public boolean isRealUsername(String name) {
		return realUsernames.contains(name.toLowerCase(Locale.ROOT));
	}

}
