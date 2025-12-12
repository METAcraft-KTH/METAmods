package nu.metacraft.pointsystem;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Map;
import java.util.UUID;

public class PlayerPointStorage {
	private static final Gson GSON = new Gson();
	public static final JsonDeserializer<PlayerPointStorage> GSON_DESERIALIZER = (json, typeOfT, ctx) -> {
		Map<UUID, Integer> map = GSON.fromJson(json.getAsJsonObject().get("map"), new TypeToken<>() {});
		return new PlayerPointStorage(new Object2IntOpenHashMap<>(map));
	};

	private final Object2IntMap<UUID> map;

	public PlayerPointStorage() {
		this.map = new Object2IntOpenHashMap<>();
	}

	public PlayerPointStorage(Object2IntMap<UUID> map) {
		this.map = map;
	}

	public int getPoints(UUID playerUuid) {
		return this.map.getOrDefault(playerUuid, 0);
	}

	public Object2IntMap<UUID> getData() {
		return this.map;
	}

	public void merge(PlayerPointStorage other) {
		for (Object2IntMap.Entry<UUID> entry : other.map.object2IntEntrySet()) {
			UUID key = entry.getKey();
			int pointsToAdd = entry.getIntValue();
			int currentPoints = this.map.getOrDefault(key, 0);
			this.map.put(key, currentPoints + pointsToAdd);
		}
	}

	public void addPoints(UUID playerUuid, int points) {
		int currentPoints = this.map.getOrDefault(playerUuid, 0);
		this.map.put(playerUuid, currentPoints + points);
	}
}
