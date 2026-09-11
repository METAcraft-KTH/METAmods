package metacraft.moredyes.mixin;

import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.sheep.SheepColors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sheep.class)
public abstract class SheepMixin {

	/** Shearing a sheep in our colour drops our wool instead of the vanilla shearing loot table. */
	@Inject(method = "shear", at = @At("HEAD"), cancellable = true)
	private void moredyes$shear(ServerLevel level, SoundSource source, ItemStack shears, CallbackInfo ci) {
		Sheep self = (Sheep) (Object) this;
		ModColor color = SheepColors.get(self);
		if (color == null) return;
		level.playSound(null, self, SoundEvents.SHEEP_SHEAR, source, 1.0F, 1.0F);
		self.setSheared(true);
		int count = 1 + level.getRandom().nextInt(3);
		for (int i = 0; i < count; i++) {
			self.spawnAtLocation(level, SheepColors.woolStack(color, 1), 1.0F);
		}
		ci.cancel();
	}

	/** Vanilla changed the colour (dye, command, spawn): ours no longer applies. */
	@Inject(method = "setColor", at = @At("TAIL"))
	private void moredyes$onSetColor(DyeColor color, CallbackInfo ci) {
		SheepColors.onVanillaColorSet((Sheep) (Object) this);
	}

	/**
	 * Lambs: both parents ours and equal → that colour; both ours and different → one of them;
	 * exactly one ours → the vanilla parent's colour wins (vanilla would have mixed with white).
	 */
	@Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/animal/sheep/Sheep;",
			at = @At("RETURN"))
	private void moredyes$breed(ServerLevel level, AgeableMob other, CallbackInfoReturnable<Sheep> cir) {
		Sheep lamb = cir.getReturnValue();
		if (lamb == null || !(other instanceof Sheep mate)) return;
		Sheep self = (Sheep) (Object) this;
		ModColor a = SheepColors.get(self), b = SheepColors.get(mate);
		if (a == null && b == null) return;
		if (a != null && b != null) {
			SheepColors.set(lamb, a == b || level.getRandom().nextBoolean() ? a : b);
		} else {
			lamb.setColor(a == null ? self.getColor() : mate.getColor());
		}
	}
}
