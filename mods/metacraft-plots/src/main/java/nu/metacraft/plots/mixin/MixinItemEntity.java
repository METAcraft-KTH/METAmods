package nu.metacraft.plots.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.item.PlotKey;

@Mixin(value = ItemEntity.class, priority = 2000)
public abstract class MixinItemEntity extends Entity {

	public MixinItemEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Shadow public abstract ItemStack getStack();

	@Inject(
		method = "tick",
		slice = @Slice(
			from = @At(
				value = "FIELD",
				target = "Lnet/minecraft/entity/ItemEntity;itemAge:I",
				ordinal = 3
			)
		),
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/ItemEntity;discard()V"
		)
	)
	public void onDespawn(CallbackInfo ci) {
		if (!this.getStack().isEmpty() && this.getStack().isOf(PlotItems.PLOT_KEY)) {
			PlotKey.revokePlotKey(this.getStack(), this.getServer());
		}
	}

}
