package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface AccessorPlayerEntity {

	@Accessor("PLAYER_MODEL_PARTS")
	static TrackedData<Byte> getModelParts() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("MAIN_ARM")
	static TrackedData<Byte> getMainArm() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("LEFT_SHOULDER_ENTITY")
	static TrackedData<NbtCompound> getLeftShoulderEntity() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("RIGHT_SHOULDER_ENTITY")
	static TrackedData<NbtCompound> getRightShoulderEntity() {
		throw new IllegalStateException("Mixin Error");
	}

}
