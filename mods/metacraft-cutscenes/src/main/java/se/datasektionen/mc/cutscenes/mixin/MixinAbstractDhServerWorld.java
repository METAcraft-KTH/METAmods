package se.datasektionen.mc.cutscenes.mixin;

import com.seibel.distanthorizons.core.world.AbstractDhServerWorld;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = AbstractDhServerWorld.class, remap = false)
public abstract class MixinAbstractDhServerWorld extends AbstractDhWorld {

	protected MixinAbstractDhServerWorld(EWorldEnvironment environment) {
		super(environment);
	}

	@Inject(
		method = "addPlayer",
		at = @At(
			value = "INVOKE",
			target = "Lcom/seibel/distanthorizons/core/world/AbstractDhServerWorld;getLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/AbstractDhServerLevel;",
			ordinal = 0
		),
		cancellable = true
	)
	public void addPlayer(IServerPlayerWrapper serverPlayer, CallbackInfo ci) {
		if (this.getLevel(serverPlayer.getLevel()) == null) {
			ci.cancel();
		}
	}

	@Inject(
		method = "removePlayer",
		at = @At(
			value = "INVOKE",
			target = "Lcom/seibel/distanthorizons/core/world/AbstractDhServerWorld;getLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/AbstractDhServerLevel;",
			ordinal = 0
		),
		cancellable = true
	)
	public void removePlayer(IServerPlayerWrapper serverPlayer, CallbackInfo ci) {
		if (this.getLevel(serverPlayer.getLevel()) == null) {
			ci.cancel();
		}
	}

	@Inject(
		method = "changePlayerLevel",
		at = @At(
			value = "INVOKE",
			target = "Lcom/seibel/distanthorizons/core/world/AbstractDhServerWorld;getLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/AbstractDhServerLevel;",
			ordinal = 0
		),
		cancellable = true
	)
	public void changePlayerLevel(IServerPlayerWrapper player, IServerLevelWrapper originLevel, IServerLevelWrapper destinationLevel, CallbackInfo ci) {
		if (this.getLevel(originLevel) == null || this.getLevel(destinationLevel) == null) {
			ci.cancel();
		}
	}


}
