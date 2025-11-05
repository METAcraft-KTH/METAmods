package nu.metacraft.resource_packs.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import nu.metacraft.resource_packs.PlayerPackData;
import nu.metacraft.resource_packs.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.UnaryOperator;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ServerPlayerExtension {

	@Unique
	private PlayerPackData data = PlayerPackData.EMPTY;

	@Override
	public PlayerPackData metacraft$getPackData() {
		return data;
	}

	@Override
	public void metacraft$updatePackData(UnaryOperator<PlayerPackData> packData) {
		data = packData.apply(data);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void writeData(ValueOutput output, CallbackInfo ci) {
		if (!data.resourcePacks().isEmpty()) {
			output.store(PlayerPackData.KEY, PlayerPackData.CODEC, data);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void readData(ValueInput input, CallbackInfo ci) {
		data = input.read(PlayerPackData.KEY, PlayerPackData.CODEC).orElse(PlayerPackData.EMPTY).updatePacks();
	}

	@Inject(method = "restoreFrom", at = @At("RETURN"))
	public void restoreFrom(ServerPlayer that, boolean keepEverything, CallbackInfo ci) {
		data = ((ServerPlayerExtension) that).metacraft$getPackData();
	}

}
