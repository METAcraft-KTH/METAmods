package nu.metacraft.zones.mixin;

import nu.metacraft.zones.EntityExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.zone.Zone;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityExtension {
	@Shadow public abstract BlockPos blockPosition();

	@Shadow private Level level;
	@Shadow public int tickCount;

	@Shadow
	public abstract Level level();

	@Unique
	private final Set<Zone> currentZones = new TreeSet<>();

	@Inject(
			method = "tick",
			at = @At("HEAD")
	)
	public void tick(CallbackInfo ci) {
		if (!level.isClientSide()) {
			Set<Zone> removeZones = new HashSet<>();
			for (Zone currentZone : currentZones) {
				if (!currentZone.isPosWithinZoneBoundsNoDimCheck(this.blockPosition())) {
					currentZone.removeFromZone((Entity) (Object) this);
					removeZones.add(currentZone);
				}
			}
			ZoneManager.getInstance(level().getServer()).getZones().forZones(this.level.dimension(), zone -> {
				if (!currentZones.contains(zone) && zone.isPosWithinZoneBoundsNoDimCheck(this.blockPosition())) {
					currentZones.add(zone);
					zone.addToZone((Entity) (Object) this);
				}
			});
			currentZones.removeAll(removeZones);
			for (var zone : currentZones) {
				zone.tick((Entity) (Object) this);
			}
		}
	}

	@Inject(
		method = "setRemoved",
		at = @At("HEAD")
	)
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		for (Zone zone : currentZones) {
			zone.removeFromZone((Entity) (Object) this);
		}
		currentZones.clear();
	}

	@Override
	public void metacraft$addToZone(Zone zone) {
		currentZones.add(zone);
		zone.addToZone((Entity) (Object) this);
	}

}
