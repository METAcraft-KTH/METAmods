package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.SoundType;
import nu.metacraft.core.util.ServerSoundType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(SoundType.class)
public class SoundTypeMixin {

	@ModifyExpressionValue(
		method = "<clinit>",
		at = @At(
			value = "NEW",
			target = "(FFLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/world/level/block/SoundType;"
		),
		slice = @Slice(
			from = @At(
				value = "FIELD",
				target = "Lnet/minecraft/world/level/block/SoundType;EMPTY:Lnet/minecraft/world/level/block/SoundType;",
				opcode = Opcodes.PUTSTATIC
			),
			to = @At(
				value = "FIELD",
				target = "Lnet/minecraft/world/level/block/SoundType;WOOD:Lnet/minecraft/world/level/block/SoundType;",
				opcode = Opcodes.PUTSTATIC
			)
		)
	)
	private static SoundType fixWood(SoundType original) {
		return ServerSoundType.copyOf(original);
	}

	@ModifyExpressionValue(
		method = "<clinit>",
		at = @At(
			value = "NEW",
			target = "(FFLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/world/level/block/SoundType;"
		),
		slice = @Slice(
			from = @At(
				value = "FIELD",
				target = "Lnet/minecraft/world/level/block/SoundType;LILY_PAD:Lnet/minecraft/world/level/block/SoundType;",
				opcode = Opcodes.PUTSTATIC
			),
			to = @At(
				value = "FIELD",
				target = "Lnet/minecraft/world/level/block/SoundType;STONE:Lnet/minecraft/world/level/block/SoundType;",
				opcode = Opcodes.PUTSTATIC
			)
		)
	)
	private static SoundType fixStone(SoundType original) {
		return ServerSoundType.copyOf(original);
	}

}
