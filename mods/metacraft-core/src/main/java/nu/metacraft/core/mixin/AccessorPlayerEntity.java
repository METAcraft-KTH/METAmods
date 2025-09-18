package nu.metacraft.core.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinError;

import java.util.Optional;
import java.util.OptionalInt;

@Mixin(PlayerEntity.class)
public interface AccessorPlayerEntity {

	@Accessor("LEFT_SHOULDER_PARROT_VARIANT_ID")
	static TrackedData<OptionalInt> getLeftShoulderEntity() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("RIGHT_SHOULDER_PARROT_VARIANT_ID")
	static TrackedData<OptionalInt> getRightShoulderEntity() {
		throw new IllegalStateException("Mixin Error");
	}

	@Invoker
	static Optional<ParrotEntity.Variant> callReadParrotVariant(NbtCompound nbt) {
		throw new MixinError("Not Working");
	}

	@Invoker
	static OptionalInt callMapParrotVariant(Optional<ParrotEntity.Variant> variant) {
		throw new MixinError("Not Working");
	}

}
