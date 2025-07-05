package nu.metacraft.weather.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.weather.rainseason.RainSeasonState;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld {
    @Shadow @Final private ServerWorldProperties worldProperties;

    @Shadow @Final public static IntProvider RAIN_WEATHER_DURATION_PROVIDER;


    @Unique private int lastRainTime;

    @Inject(method = "resetWeather", at = @At("HEAD"), cancellable = true)
    public void stopSleepingClearsWeather(CallbackInfo ci) {
        ServerWorld self = (ServerWorld) (Object) this;
        RainSeasonState state = RainSeasonState.get(self);
        if (state.isRainSeason()) {
            ci.cancel();
        }
    }

    @Inject(method = "tickWeather", at = @At("HEAD"))
    public void rainMoreOften(CallbackInfo ci) {
        ServerWorld self = (ServerWorld) (Object) this;
        RainSeasonState state = RainSeasonState.get(self);
        if (!state.isRainSeason()) {
            return;
        }
        if (!self.getDimension().hasSkyLight()) {
            return;
        }
        if (!self.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
            return;
        }

        // if (self.getServer().getTicks() % (20 * 30) == 0) {
        //     // Every 30 seconds, debug
        //     System.out.println("RAIN DEBUG. isRaining: " + self.isRaining());
        //     System.out.println("THUNDER DEBUG. isThundering(): " + self.isThundering());
        // }

        int clearWeatherTime = this.worldProperties.getClearWeatherTime();
        if (clearWeatherTime > 0) {
            return;
        }
        int rainTime = this.worldProperties.getRainTime();
        boolean raining = this.worldProperties.isRaining();

        if (rainTime > 0) {
            return;
        }
        if (raining) {
            rainTime = RAIN_WEATHER_DURATION_PROVIDER.get(self.random);
            this.lastRainTime = rainTime;
            // System.out.println("Will now rain for " + rainTime + " ticks.");
        } else {
            if (this.lastRainTime == 0) {
                // We had no previous rain time, just make a random value.
                this.lastRainTime = RAIN_WEATHER_DURATION_PROVIDER.get(self.random);
            }
            //
            // We want the time rained divided by the total time
            // to equal the rain percentage constant.
            // let k = getRainPercentage()
            // let r be the time rained
            // let c be the time clear weather
            // then,
            //       r
            //     ----- = k
            //     r + c
            // which solving for c gives,
            // c = (r/k) - r
            //
            rainTime = (int) (this.lastRainTime / state.getRainPercentage()) - this.lastRainTime;

            if (rainTime < 2400) {
                rainTime = 2400;
            }
            // System.out.println("Will now be clear for " + rainTime + " ticks since the last rain lasted for " + lastRainTime + " ticks.");
        }
        this.worldProperties.setRainTime(rainTime);
    }
}
