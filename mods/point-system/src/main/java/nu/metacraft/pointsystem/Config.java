package nu.metacraft.pointsystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class Config {
    public final Text universityScoreText;
    public final Text topPlayersText;

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

    private static Text parseText(JsonElement json) {
        if (json == null) {
            throw new RuntimeException("Missing required text in config.");
        }
        DataResult<Pair<Text, JsonElement>> res = TextCodecs.CODEC.decode(JsonOps.INSTANCE, json);
        Optional<Pair<Text, JsonElement>> opt = res.result();
        if (opt.isEmpty()) {
            throw new RuntimeException("Unable to parse json.");
        }
        Pair<Text, JsonElement> pair = opt.get();
        return pair.getFirst();
    }
}
