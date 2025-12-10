package nu.metacraft.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(Mannequin.class)
public interface MannequinAccessor {

	@Accessor("DATA_PROFILE")
	static EntityDataAccessor<ResolvableProfile> getDataProfile() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor("DATA_DESCRIPTION")
	static EntityDataAccessor<Optional<Component>> getDataDescription() {
		throw new IllegalStateException("Mixin Error");
	}

}
