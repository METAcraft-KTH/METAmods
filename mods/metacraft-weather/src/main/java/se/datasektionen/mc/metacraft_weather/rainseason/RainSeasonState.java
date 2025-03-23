package se.datasektionen.mc.metacraft_weather.rainseason;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public class RainSeasonState extends PersistentState {

    private boolean isRainSeason;
    private double rainPercentage = 0.5;

    public static final Codec<RainSeasonState> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BOOL.fieldOf("isRainSeason").forGetter(RainSeasonState::isRainSeason),
                    Codec.DOUBLE.fieldOf("rainPercentage").forGetter(RainSeasonState::getRainPercentage)
            ).apply(instance, RainSeasonState::new)
    );

    private static final PersistentStateType<RainSeasonState> TYPE = new PersistentStateType<>(
            "rain-season", RainSeasonState::new, CODEC, null
    );

    public static RainSeasonState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
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
        this.markDirty();
    }

    public void setRainPercentage(double rainPercentage) {
        this.rainPercentage = rainPercentage;
        this.markDirty();
    }
}
