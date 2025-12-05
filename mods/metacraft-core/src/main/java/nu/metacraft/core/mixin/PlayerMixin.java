package nu.metacraft.core.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.gamerules.METAcraftGameRules;
import nu.metacraft.core.item.components.METAcraftComponents;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

	@Shadow public abstract Inventory getInventory();

	protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(
		method = "dropEquipment",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/level/gamerules/GameRules;KEEP_INVENTORY:Lnet/minecraft/world/level/gamerules/GameRule;"
		)
	)
	public void dropInventory(CallbackInfo ci) {
		if (level() instanceof ServerLevel l && l.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			for (int i = 0; i < getInventory().getContainerSize(); ++i) {
				ItemStack stack = getInventory().getItem(i);
				if (stack.has(METAcraftComponents.ANTI_KEEP_INVENTORY)) {
					if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
						this.drop(stack, true, false);
					}
					getInventory().setItem(i, ItemStack.EMPTY);
				}
			}
		}
	}

	@Inject(method = "hurtArmor", at = @At("HEAD"), cancellable = true)
	public void noBreakArmor(DamageSource source, float amount, CallbackInfo ci) {
		if (level() instanceof ServerLevel l) {
			if (!l.getGameRules().get(METAcraftGameRules.ARMOR_DAMAGE)) {
				ci.cancel();
			}
		}
	}
}
