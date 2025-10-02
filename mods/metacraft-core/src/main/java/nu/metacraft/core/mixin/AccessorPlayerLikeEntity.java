package nu.metacraft.core.mixin;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PlayerLikeEntity.class)
public interface AccessorPlayerLikeEntity {

	@Accessor("PLAYER_MODE_CUSTOMIZATION_ID")
	static TrackedData<Byte> getModelParts() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("MAIN_ARM_ID")
	static TrackedData<Byte> getMainArm() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("POSE_DIMENSIONS")
	static Map<EntityPose, EntityDimensions> getPoseDimensions() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("STANDING_DIMENSIONS")
	static EntityDimensions getStandingDimensions() {
		throw new IllegalStateException("Mixin Error");
	}

}
