package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntity extends Entity {

	public MixinItemEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "setStack", at = @At("RETURN"))
	public void setStack(ItemStack stack, CallbackInfo ci) {
		PortableJukeboxEntity.transferToItemFromUnknown(
				stack, getWorld(), (ItemEntity) (Object) this
		);
	}

}
