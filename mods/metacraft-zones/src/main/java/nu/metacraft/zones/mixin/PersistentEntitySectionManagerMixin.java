package nu.metacraft.zones.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import nu.metacraft.zones.EntityExtension;
import nu.metacraft.zones.ZoneManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerMixin<T extends EntityAccess> {

	@Inject(
		method = "addEntity",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"
		)
	)
	public void addEntity(T entity, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Entity e && e.level().getServer() != null) {
			ZoneManager.getInstance(e.level().getServer()).getZonesAt(
					e.level().dimension(), e.blockPosition(), z -> true
			).forEach(zone -> ((EntityExtension) e).metacraft$addToZone(zone));
		}
	}

}
