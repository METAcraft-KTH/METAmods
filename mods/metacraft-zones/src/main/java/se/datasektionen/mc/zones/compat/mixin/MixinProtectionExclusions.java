package se.datasektionen.mc.zones.compat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.zones.compat.leukocyte.ExclusionData;
import se.datasektionen.mc.zones.zone.Zone;
import xyz.nucleoid.leukocyte.rule.ProtectionExclusions;

import java.util.Optional;

@Mixin(value = ProtectionExclusions.class, remap = false)
public class MixinProtectionExclusions implements ExclusionData {

	@Unique
	private Zone zone;

	@Override
	public void metacraft_zones$setZone(Zone zone) {
		this.zone = zone;
	}

	@Override
	public Optional<Zone> metacraft_zones$getZone() {
		return Optional.ofNullable(zone);
	}

	@ModifyReturnValue(method = "copy", at = @At("RETURN"))
	public ProtectionExclusions copy(ProtectionExclusions original) {
		((ExclusionData) (Object) original).metacraft_zones$setZone(zone);
		return original;
	}
}
