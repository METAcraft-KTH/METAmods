package nu.metacraft.core.mixin;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import nu.metacraft.core.METASounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = {ClientboundSoundEntityPacket.class, ClientboundSoundPacket.class})
public class SoundPacketFixMixin {

	@ModifyVariable(
		method = {
			"<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;Lnet/minecraft/world/entity/Entity;FFJ)V",
			"<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;DDDFFJ)V"
		}, at = @At("HEAD"),
		argsOnly = true,
		name = "sound"
	)
	private static Holder<SoundEvent> init(Holder<SoundEvent> sound) {
		return METASounds.replaceVanilla(sound);
	}

}
