package se.datasektionen.mc.metacraft_weather.rainseason;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

public class RainSeasonState extends PersistentState {
    private static final String KEY = "rain-season";

    private boolean isRainSeason;
    private double rainPercentage = 0.5;

    private static PersistentState.Type<RainSeasonState> getType() {
        return new Type<>(
            RainSeasonState::new, RainSeasonState::fromNbt, null
        );
    }

    private static RainSeasonState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        RainSeasonState state = new RainSeasonState();
        state.readNbt(nbt);
        return state;
    }

    public static RainSeasonState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(getType(), KEY);
    }

    public RainSeasonState() {
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

    public void readNbt(NbtCompound nbt) {
        this.isRainSeason = nbt.getBoolean("isRainSeason");
        this.rainPercentage = nbt.getDouble("rainPercentage");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putBoolean("isRainSeason", this.isRainSeason);
        nbt.putDouble("rainPercentage", this.rainPercentage);
        return nbt;
    }
}
