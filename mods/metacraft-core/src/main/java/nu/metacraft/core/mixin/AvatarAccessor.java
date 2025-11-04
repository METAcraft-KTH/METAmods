package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

@Mixin(Avatar.class)
public interface AvatarAccessor {

	@Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
	static EntityDataAccessor<Byte> getModelParts() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("DATA_PLAYER_MAIN_HAND")
	static EntityDataAccessor<Byte> getMainArm() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("POSES")
	static Map<Pose, EntityDimensions> getPoseDimensions() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("STANDING_DIMENSIONS")
	static EntityDimensions getStandingDimensions() {
		throw new IllegalStateException("Mixin Error");
	}

}
