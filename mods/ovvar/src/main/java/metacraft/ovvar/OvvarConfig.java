package metacraft.ovvar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/ovvar.json}. Written with defaults when missing; a file that does not parse is an
 * error at startup rather than silently replaced. {@code /ovvar minigame} edits and saves it.
 *
 * @param sewingMinigame       sew on a stand through the stitching dialog ({@link metacraft.ovvar.sewing.SewingGame})
 *                             instead of in one click
 * @param stitches             how many stitches a patch takes in the minigame
 * @param pushAfterCalmSeconds a rebuilt resource pack (a loading screen) is sent to a player only
 *                             after this long without fighting, sewing or moving about
 * @param pushCalmDistance     how far a player may have moved within that time and still count as calm
 */
public record OvvarConfig(boolean sewingMinigame, int stitches, int pushAfterCalmSeconds, double pushCalmDistance) {
    public static final int MIN_STITCHES = 1, MAX_STITCHES = 16;
    public static final Codec<OvvarConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("sewing_minigame").forGetter(OvvarConfig::sewingMinigame),
            Codec.intRange(MIN_STITCHES, MAX_STITCHES).fieldOf("stitches").forGetter(OvvarConfig::stitches),
            Codec.intRange(0, 600).optionalFieldOf("push_after_calm_seconds", 20).forGetter(OvvarConfig::pushAfterCalmSeconds),
            Codec.doubleRange(0, 1000).optionalFieldOf("push_calm_distance", 8.0).forGetter(OvvarConfig::pushCalmDistance)
    ).apply(instance, OvvarConfig::new));
    private static final OvvarConfig DEFAULT = new OvvarConfig(true, 6, 20, 8);
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Ovvar.MOD_ID + ".json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static OvvarConfig current;

    public static OvvarConfig get() {
        if (current == null) throw new IllegalStateException("[" + Ovvar.MOD_ID + "] config read before load()");
        return current;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            DEFAULT.save();
            Ovvar.LOGGER.info("[{}] wrote default config {}", Ovvar.MOD_ID, PATH);
            return;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(PATH));
            current = CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(message -> new IllegalStateException("[" + Ovvar.MOD_ID + "] bad config " + PATH + ": " + message));
        } catch (IOException e) {
            throw new UncheckedIOException("[" + Ovvar.MOD_ID + "] cannot read " + PATH, e);
        }
    }

    /** Makes this the running config and writes it out. */
    public void save() {
        JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, this)
                .getOrThrow(message -> new IllegalStateException("[" + Ovvar.MOD_ID + "] cannot encode config: " + message));
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(json) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("[" + Ovvar.MOD_ID + "] cannot write " + PATH, e);
        }
        current = this;
    }
}
