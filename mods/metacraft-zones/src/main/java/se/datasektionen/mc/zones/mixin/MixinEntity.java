package se.datasektionen.mc.zones.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.zones.ZoneManager;
import se.datasektionen.mc.zones.zone.Zone;

import java.util.*;

@Mixin(Entity.class)
public abstract class MixinEntity {
	@Shadow public abstract BlockPos getBlockPos();

	@Shadow @Nullable public abstract MinecraftServer getServer();

	@Shadow private World world;
	@Shadow public int age;

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
				if (!currentZone.contains(this.getBlockPos())) {
					currentZone.removeFromZone((Entity) (Object) this);
					removeZones.add(currentZone);
				}
			}
			ZoneManager.getInstance(getServer()).getZones().forZones(this.world.getRegistryKey(), zone -> {
				if (!currentZones.contains(zone) && zone.contains(this.getBlockPos())) {
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
