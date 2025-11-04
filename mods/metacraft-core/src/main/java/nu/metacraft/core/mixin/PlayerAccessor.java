package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinError;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;

@Mixin(Player.class)
public interface PlayerAccessor {

	@Accessor("DATA_SHOULDER_PARROT_LEFT")
	static EntityDataAccessor<OptionalInt> getLeftShoulderEntity() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("DATA_SHOULDER_PARROT_RIGHT")
	static EntityDataAccessor<OptionalInt> getRightShoulderEntity() {
		throw new IllegalStateException("Mixin Error");
	}

	@Invoker
	static Optional<Parrot.Variant> callExtractParrotVariant(CompoundTag nbt) {
		throw new MixinError("Not Working");
	}

	@Invoker
	static OptionalInt callConvertParrotVariant(Optional<Parrot.Variant> variant) {
		throw new MixinError("Not Working");
	}

}
