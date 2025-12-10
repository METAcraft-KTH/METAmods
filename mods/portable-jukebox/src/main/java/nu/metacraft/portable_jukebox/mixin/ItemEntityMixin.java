package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

	public ItemEntityMixin(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Inject(method = "setItem", at = @At("RETURN"))
	public void setStack(ItemStack stack, CallbackInfo ci) {
		PortableJukeboxEntity.transferToItemFromUnknown(
				stack, level(), (ItemEntity) (Object) this
		);
	}

}
