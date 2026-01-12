package nu.metacraft.pause.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.pause.PauseData;
import nu.metacraft.pause.PausePlayerData;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements PausePlayerData {

	@Shadow
	public abstract ServerLevel level();

	@Unique
	private static final String BEFORE_PAUSE_VELOCITY = "metacraft_pause:before_pause_velocity";

	@Unique
	private Vec3 beforePauseVelocity = null;

	public ServerPlayerMixin(Level world, GameProfile profile) {
		super(world, profile);
	}


	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void toNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.storeNullable(BEFORE_PAUSE_VELOCITY, Vec3.CODEC, beforePauseVelocity);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void fromNBT(ValueInput nbt, CallbackInfo ci) {
		if (level() != null && level().getServer() != null && PauseData.getInstance(level().getServer()).isPaused()) {
			beforePauseVelocity = nbt.read(BEFORE_PAUSE_VELOCITY, Vec3.CODEC).orElse(null);
		}
	}

	@Override
	public void metacraft_pause$setBeforePauseVelocity(Vec3 velocity) {
		beforePauseVelocity = velocity;
	}

	@Override
	public Vec3 metacraft_pause$getBeforePauseVelocity() {
		return beforePauseVelocity;
	}

}
