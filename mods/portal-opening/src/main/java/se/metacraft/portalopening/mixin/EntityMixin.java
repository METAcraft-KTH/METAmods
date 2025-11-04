package se.metacraft.portalopening.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.portalopening.EntityData;
import se.metacraft.portalopening.PortalOpeningDimensionData;
import se.metacraft.portalopening.rifts.PortalRift;

@Mixin(Entity.class)
public class EntityMixin implements EntityData {

	@Shadow private Level level;
	@Unique
	private static final String RIFT = "PortalRift";

	@Unique
	private PortalRift rift;

	@Override
	public void portalOpening$setRift(PortalRift rift) {
		this.rift = rift;
	}

	@Inject(method = "saveWithoutId", at = @At("RETURN"))
	public void toNBT(ValueOutput nbt, CallbackInfo ci) {
		if (rift != null) {
			nbt.putLong(RIFT, rift.getRandomPos().asLong());
		}
	}

	@Inject(method = "load", at = @At("RETURN"))
	public void fromNBT(ValueInput nbt, CallbackInfo ci) {
		if (level instanceof ServerLevel) {
			nbt.getLong(RIFT).map(BlockPos::of).flatMap(
					pos -> PortalOpeningDimensionData.getInstance((ServerLevel) level).getRiftAt(pos)
			).ifPresent(rift -> {
				rift.addEntity((Entity) (Object) this);
			});
		}
	}

	@Inject(method = "setRemoved", at = @At("RETURN"))
	public void remove(Entity.RemovalReason reason, CallbackInfo ci) {
		if (rift != null) {
			rift.removeEntity((Entity) (Object) this);
		}
	}

	@Inject(method = "unsetRemoved", at = @At("RETURN"))
	public void unremove(CallbackInfo ci) {
		if (rift != null) {
			rift.addEntity((Entity) (Object) this);
		}
	}
}
