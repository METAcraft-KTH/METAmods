package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.core.util.ServerSoundType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Sniffer.class)
public abstract class SnifferMixin extends Animal {

	protected SnifferMixin(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	@WrapOperation(
		method = "emitDiggingParticles",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"
		)
	)
	private void fixSnifferSound(
		Level instance, Entity sourceEntity, SoundEvent sound, SoundSource source, float volume, float pitch,
		Operation<Void> original, @Local(name = "stateBelow") BlockState stateBelow
	) {
		if (stateBelow.getSoundType() instanceof ServerSoundType) {
			level().playSound(null, sourceEntity, sound, source, volume, pitch);
		}
		original.call(instance, sourceEntity, sound, source, volume, pitch);
	}

}
