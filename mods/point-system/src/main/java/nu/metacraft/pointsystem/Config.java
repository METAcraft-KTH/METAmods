package nu.metacraft.pointsystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public class Config {
	public final Component universityScoreText;
	public final Component topPlayersText;

	public Config(Path configFile) throws IOException {
		if (Files.notExists(configFile)) {
			try (InputStream stream = Config.class.getClassLoader().getResourceAsStream("config.json")) {
				if (stream == null) {
					throw new RuntimeException("No default config found.");
				}
				Files.copy(stream, configFile);
			}
		}
		JsonElement jsonElement = JsonParser.parseReader(Files.newBufferedReader(configFile));
		JsonObject json = jsonElement.getAsJsonObject();
		this.universityScoreText = parseText(json.get("universityScoreText"));
		this.topPlayersText = parseText(json.get("topPlayersText"));
	}

	private static Component parseText(JsonElement json) {
		if (json == null) {
			throw new RuntimeException("Missing required text in config.");
		}
		DataResult<Pair<Component, JsonElement>> res = ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, json);
		Optional<Pair<Component, JsonElement>> opt = res.result();
		if (opt.isEmpty()) {
			throw new RuntimeException("Unable to parse json.");
		}
		Pair<Component, JsonElement> pair = opt.get();
		return pair.getFirst();
	}
}
