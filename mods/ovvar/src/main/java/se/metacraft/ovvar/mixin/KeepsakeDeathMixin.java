package se.metacraft.ovvar.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.content.enchantment.Keepsake;

/**
 * Keepsake: at the start of a dying player's death drop, with {@code keepInventory} off (exactly when they
 * would be lost), the Keepsake ovvar are set aside. The start of {@code dropAllDeathLoot}, not vanilla's
 * {@code Inventory.dropAll} in {@code Player.dropEquipment}: Universal Graves takes the whole inventory into a
 * grave at the {@code dropEquipment} call, before vanilla's drop is ever reached.
 */
@Mixin(LivingEntity.class)
public abstract class KeepsakeDeathMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
	private void ovvar$keepsake(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && !level.getGameRules().get(GameRules.KEEP_INVENTORY)) Keepsake.stash(player);
	}
}
