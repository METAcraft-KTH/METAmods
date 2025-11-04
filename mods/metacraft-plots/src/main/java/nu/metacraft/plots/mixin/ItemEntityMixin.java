package nu.metacraft.plots.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.item.PlotKey;

@Mixin(value = ItemEntity.class, priority = 2000)
public abstract class ItemEntityMixin extends Entity {

	public ItemEntityMixin(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Shadow public abstract ItemStack getItem();

	@Inject(
		method = "tick",
		slice = @Slice(
			from = @At(
				value = "FIELD",
				target = "Lnet/minecraft/world/entity/item/ItemEntity;age:I",
				ordinal = 3
			)
		),
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"
		)
	)
	public void onDespawn(CallbackInfo ci) {
		if (!this.getItem().isEmpty() && this.getItem().is(PlotItems.PLOT_KEY)) {
			PlotKey.revokePlotKey(this.getItem(), this.level().getServer());
		}
	}

}
