package se.datasektionen.mc.metacraft_lib;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class METAcraftData extends PersistentState {

	public static final Codec<METAcraftData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.unboundedMap(
							Uuids.STRING_CODEC, Codec.STRING
					).fieldOf("offline_name_cache").forGetter(d -> d.nameCache)
			).apply(instance, METAcraftData::new)
	);

	private static final PersistentStateType<METAcraftData> TYPE = new PersistentStateType<>(
			METAcraftLib.NAMESPACE + "-data", METAcraftData::new, CODEC, null
	);

	public METAcraftData(Map<UUID, String> nameCache) {
		this.nameCache = new HashMap<>(nameCache);
	}

	public METAcraftData() {
		this(Map.of());
	}

	private final Map<UUID, String> nameCache;

	public static METAcraftData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	public void setName(UUID id, @Nullable String name) {
		if (name != null) {
			nameCache.put(id, name);
		} else {
			nameCache.remove(id);
		}
		markDirty();
	}

	public String getName(GameProfile profile) {
		if (nameCache.containsKey(profile.getId())) {
			return nameCache.get(profile.getId());
		}
		return profile.getName();
	}
}
