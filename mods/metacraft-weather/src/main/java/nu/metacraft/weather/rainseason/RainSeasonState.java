package nu.metacraft.weather.rainseason;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.weather.METAcraftWeather;

public class RainSeasonState extends SavedData {

    private boolean isRainSeason;
    private double rainPercentage = 0.5;

    public static final Codec<RainSeasonState> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BOOL.fieldOf("isRainSeason").forGetter(RainSeasonState::isRainSeason),
                    Codec.DOUBLE.fieldOf("rainPercentage").forGetter(RainSeasonState::getRainPercentage)
            ).apply(instance, RainSeasonState::new)
    );

    private static final SavedDataType<RainSeasonState> TYPE = new SavedDataType<>(
            METAcraftWeather.getId("rain_season"), RainSeasonState::new, CODEC, null
    );

    public static RainSeasonState get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    public RainSeasonState() {
    }

    public RainSeasonState(boolean isRainSeason, double rainPercentage) {
        this.isRainSeason = isRainSeason;
        this.rainPercentage = rainPercentage;
    }

    public boolean isRainSeason() {
        return this.isRainSeason;
    }

    public double getRainPercentage() {
        return this.rainPercentage;
    }

    public void setIsRainSeason(boolean isRainSeason) {
        this.isRainSeason = isRainSeason;
        this.setDirty();
    }

    public void setRainPercentage(double rainPercentage) {
        this.rainPercentage = rainPercentage;
        this.setDirty();
    }
}
