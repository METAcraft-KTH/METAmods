package nu.metacraft.lib;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class METAcraftData extends SavedData {

	public static final Codec<METAcraftData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.unboundedMap(
							UUIDUtil.STRING_CODEC, Codec.STRING
					).fieldOf("offline_name_cache").forGetter(d -> d.nameCache)
			).apply(instance, METAcraftData::new)
	);

	// FIXME Datafixer here!
	private static final SavedDataType<METAcraftData> TYPE = new SavedDataType<>(
			METAcraftLib.getID("data"), METAcraftData::new, CODEC, null
	);

	public METAcraftData(Map<UUID, String> nameCache) {
		this.nameCache = new HashMap<>(nameCache);
	}

	public METAcraftData() {
		this(Map.of());
	}

	private final Map<UUID, String> nameCache;

	public static METAcraftData getInstance(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public void setName(UUID id, @Nullable String name) {
		if (name != null) {
			nameCache.put(id, name);
		} else {
			nameCache.remove(id);
		}
		setDirty();
	}

	public String getName(GameProfile profile) {
		if (nameCache.containsKey(profile.id())) {
			return nameCache.get(profile.id());
		}
		return profile.name();
	}
}
