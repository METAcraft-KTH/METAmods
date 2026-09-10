package metacraft.ovvar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.lib.config.container.ConfigContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

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
    public static final MapCodec<OvvarConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("sewing_minigame").forGetter(OvvarConfig::sewingMinigame),
            Codec.intRange(MIN_STITCHES, MAX_STITCHES).fieldOf("stitches").forGetter(OvvarConfig::stitches),
            Codec.intRange(0, 600).optionalFieldOf("push_after_calm_seconds", 20).forGetter(OvvarConfig::pushAfterCalmSeconds),
            Codec.doubleRange(0, 1000).optionalFieldOf("push_calm_distance", 8.0).forGetter(OvvarConfig::pushCalmDistance)
    ).apply(instance, OvvarConfig::new));

    private static final ConfigContainer<OvvarConfig> CONTAINER = ConfigContainer.Builder.create(
            CODEC, () -> new OvvarConfig(true, 6, 20, 8)
    ).build(FabricLoader.getInstance().getConfigDir().resolve(Ovvar.MOD_ID + ".json"));


    public static OvvarConfig get() {
        return CONTAINER.get();
    }

    public OvvarConfig minigame(boolean on, int stitches) {
        return new OvvarConfig(on, stitches > 0 ? stitches : stitches(), pushAfterCalmSeconds(), pushCalmDistance());
    }

    public static void modify(UnaryOperator<OvvarConfig> config) {
        CONTAINER.replace(config.apply(CONTAINER.get()));
    }
}
