package nu.metacraft.bosses.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface AccessorLivingEntity {

	@Accessor("ACTIVE_EFFECTS_KEY")
	static String getActiveEffectsKey() {
		throw new IllegalStateException("Mixin Error");
	}

}
