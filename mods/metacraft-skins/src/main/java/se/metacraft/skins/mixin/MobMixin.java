package se.metacraft.skins.mixin;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.MobHats;
import se.metacraft.skins.Skins;
import se.metacraft.skins.item.MobHat;

/**
 * As a mob spawns, it may put on a hat ({@link MobHats#spawned}). A zombie's and a skeleton's
 * own {@code finalizeSpawn} call this one first and only then fill their empty armour slots, so a
 * hat keeps any helmet (and Halloween's pumpkin) off. A mob that picks up items never swaps its hat
 * for one.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void putOnHat(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		Skins skins = MetacraftSkins.skins();
		if (skins != null) MobHats.spawned(skins, (Mob) (Object) this, difficulty);
	}

	@Inject(method = "canReplaceCurrentItem", at = @At("HEAD"), cancellable = true)
	private void keepHat(ItemStack candidate, ItemStack current, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
		if (MobHat.skinOf(current).isPresent()) cir.setReturnValue(false);
	}
}
