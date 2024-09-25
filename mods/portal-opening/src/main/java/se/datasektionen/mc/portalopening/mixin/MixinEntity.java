package se.datasektionen.mc.portalopening.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.portalopening.EntityData;
import se.datasektionen.mc.portalopening.PortalOpeningDimensionData;
import se.datasektionen.mc.portalopening.rifts.PortalRift;

@Mixin(Entity.class)
public class MixinEntity implements EntityData {

	@Shadow private World world;
	@Unique
	private static final String RIFT = "PortalRift";

	@Unique
	private PortalRift rift;

	@Override
	public void portalOpening$setRift(PortalRift rift) {
		this.rift = rift;
	}

	@Inject(method = "writeNbt", at = @At("RETURN"))
	public void toNBT(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
		if (rift != null) {
			nbt.putLong(RIFT, rift.getRandomPos().asLong());
		}
	}

	@Inject(method = "readNbt", at = @At("RETURN"))
	public void fromNBT(NbtCompound nbt, CallbackInfo ci) {
		if (world instanceof ServerWorld && nbt.contains(RIFT)) {
			var pos = BlockPos.fromLong(nbt.getLong(RIFT));
			PortalOpeningDimensionData.getInstance((ServerWorld) world).getRiftAt(pos).ifPresent(rift -> {
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
