package nu.metacraft.zones.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.zone.Zone;

import java.util.*;

@Mixin(Entity.class)
public abstract class MixinEntity {
	@Shadow public abstract BlockPos getBlockPos();

	@Shadow private World world;
	@Shadow public int age;

	@Shadow
	public abstract World getEntityWorld();

	@Unique
	private final Set<Zone> currentZones = new TreeSet<>();

	@Inject(
			method = "tick",
			at = @At("HEAD")
	)
	public void tick(CallbackInfo ci) {
		if (!world.isClient() && this.age % 100 == 0) {
			Set<Zone> removeZones = new HashSet<>();
			for (Zone currentZone : currentZones) {
				if (!currentZone.isPosWithinZoneBoundsNoDimCheck(this.getBlockPos())) {
					currentZone.removeFromZone((Entity) (Object) this);
					removeZones.add(currentZone);
				}
			}
			ZoneManager.getInstance(getEntityWorld().getServer()).getZones().forZones(this.world.getRegistryKey(), zone -> {
				if (!currentZones.contains(zone) && zone.isPosWithinZoneBoundsNoDimCheck(this.getBlockPos())) {
					currentZones.add(zone);
					zone.addToZone((Entity) (Object) this);
				}
			});
			currentZones.removeAll(removeZones);
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

}
